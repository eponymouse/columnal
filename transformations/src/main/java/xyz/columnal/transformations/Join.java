/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.transformations;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.data.NumericColumnStorage;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.id.TableId;
import xyz.columnal.log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.TransformationLexer;
import xyz.columnal.grammar.TransformationParser;
import xyz.columnal.grammar.TransformationParser.JoinColumnLineContext;
import xyz.columnal.grammar.TransformationParser.JoinContext;
import xyz.columnal.grammar.Versions.ExpressionVersion;
import xyz.columnal.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.function.simulation.SimulationSupplier;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class Join extends VisitableTransformation
{
    private final TableId primarySource;
    private final TableId secondarySource;
    private final ImmutableList<Pair<ColumnId, ColumnId>> columnsToMatch; 
    /**
     * Should we put records from the primary source into the output even
     * if we found no match in the other part of the join?  If true, and we are keeping them,
     * the types of all records in the other table become wrapped in Optional.
     */
    private final boolean keepPrimaryWithNoMatch;

    // Not actually a column by itself, but holds a list of integers so reasonable to re-use:
    // Each item is a source index in the corresponding original table.
    // So if it holds [2, 5, 5] that means we have three items so far,
    // the first is from index 2 in primary table, second is from index 5, third is also from index 5.  These two items will always
    // be of the same size, but if secondary has -1 in, it means
    // there is no row (only happens when keepPrimaryWithNoMatch is true)
    private final NumericColumnStorage primaryIndexMap;
    private final NumericColumnStorage secondaryIndexMap;
    private boolean examinedAllSourceRows = false;
    
    @OnThread(Tag.Any)
    private final @Nullable String error;
    private final @Nullable RecordSet recordSet;

    @OnThread(Tag.Simulation)
    public Join(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId primarySource, TableId secondarySource, boolean keepPrimaryWithNoMatch, ImmutableList<Pair<ColumnId, ColumnId>> columnsToMatch) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.primarySource = primarySource;
        this.secondarySource = secondarySource;
        this.keepPrimaryWithNoMatch = keepPrimaryWithNoMatch;
        this.columnsToMatch = columnsToMatch;
        
        this.primaryIndexMap = new NumericColumnStorage(false);
        this.secondaryIndexMap = new NumericColumnStorage(false);
        
        RecordSet theRecordSet = null;
        String theError = null;
        try
        {
            RecordSet primary = mgr.getSingleTableOrThrow(primarySource).getData();
            RecordSet secondary = mgr.getSingleTableOrThrow(secondarySource).getData();
            
            
            Pair<RecordSet, RecordSet> primSec = new Pair<>(primary, secondary);
            
            // There's three kinds of column from tables P(rimary) and S(econdary):
            // Join columns of P."a" = S."b".  These remain in, named "c" if it does not also occur in a non-joined S column, or "P a" if there is a name clash. 
            // Left columns of P."c".  These remain in, named "c" if it does not also occur in a non-joined S column, or "P c" if there is a name clash.
            // Right columns of S."d".  These remain in, named "d" if it does not also occur in a non-joined P column, or "S c" if there is a name clash.
            // After joining index maps are sorted, the first and second category is the same
            theRecordSet = new <Column>RecordSet(Utility.<SimulationFunction<RecordSet, Column>>concatI(
                Utility.<Column, SimulationFunction<RecordSet, Column>>mapListI(primary.getColumns(), c -> copyColumn(c, avoidNameClash(primarySource, c.getName(), secondary.getColumnIds()), primaryIndexMap, primSec, null)),
                Utility.<Column, SimulationFunction<RecordSet, Column>>mapListI(secondary.getColumns(), c -> copyColumn(c, avoidNameClash(secondarySource, c.getName(), primary.getColumnIds()), secondaryIndexMap, primSec, keepPrimaryWithNoMatch ? mgr.getTypeManager() : null))
            ))
            {
                @Override
                public boolean indexValid(int index) throws UserException, InternalException
                {
                    if (index < primaryIndexMap.filled())
                        return true;

                    Utility.later(Join.this).fillJoinMapTo(index,primSec);
                    return index < primaryIndexMap.filled();
                }
            };
        }
        catch (InternalException | UserException e)
        {
            if (e instanceof InternalException)
                Log.log(e);
            theError = e.getLocalizedMessage();
        }
        recordSet = theRecordSet;
        error = theError;
    }

    @SuppressWarnings("identifier")
    private static ColumnId avoidNameClash(TableId tableName, ColumnId columnName, List<ColumnId> namesToNotClashWith)
    {
        while (namesToNotClashWith.contains(columnName))
        {
            // Keep prepending table name until it's unique:
            String proposed = tableName.getRaw() + " " + columnName.getRaw();
            columnName = new ColumnId(IdentifierUtility.fixExpressionIdentifier(proposed, proposed));
        }
        return columnName;
    }

    // If typeManager != null, wrap into optional, else keep original type
    private SimulationFunction<RecordSet, Column> copyColumn(@UnknownInitialization(Transformation.class) Join this, Column c, ColumnId name, NumericColumnStorage indexMap, Pair<RecordSet, RecordSet> recordSets, @Nullable TypeManager typeManager)
    {
        return rs -> new Column(rs, name)
        {
            @Override
            public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
            {
                return addManualEditSet(getName(), typeManager == null ? 
                c.getType().copyReorder(i -> {
                    Utility.later(Join.this).fillJoinMapTo(i, recordSets);
                    return indexMap.getInt(i);
                })
                : c.getType().copyReorderWrapOptional(typeManager, i ->
                {
                    Utility.later(Join.this).fillJoinMapTo(i, recordSets);
                    int mapped = indexMap.getInt(i);
                    return mapped < 0 ? null : mapped;
                }));
            }

            @Override
            public @OnThread(Tag.Any) AlteredState getAlteredState()
            {
                return AlteredState.FILTERED_OR_REORDERED;
            }
        };
    }

    @OnThread(Tag.Simulation)
    private void fillJoinMapTo(int destIndex, Pair<RecordSet, RecordSet> recordSets) throws InternalException, UserException
    {
        if (examinedAllSourceRows)
            return;
        // We go down the rows of the primary table, looking for secondary matches
        // If (none && keepPrimaryWithNoMatch) || some, we add a result row 
        int nextPrimaryToExamine = primaryIndexMap.filled() == 0 ? 0 : (primaryIndexMap.getInt(primaryIndexMap.filled() - 1) + 1);
        while (recordSets.getFirst().indexValid(nextPrimaryToExamine))
        {
            boolean foundSecondary = false;
            for (int secondaryIndex = 0; recordSets.getSecond().indexValid(secondaryIndex); secondaryIndex++)
            {
                boolean allMatch = true;
                for (Pair<ColumnId, ColumnId> toMatch : columnsToMatch)
                {
                    @Value Object primaryVal = recordSets.getFirst().getColumn(toMatch.getFirst()).getType().getCollapsed(nextPrimaryToExamine);
                    @Value Object secondaryVal = recordSets.getSecond().getColumn(toMatch.getSecond()).getType().getCollapsed(secondaryIndex);
                    if (Utility.compareValues(primaryVal, secondaryVal) != 0)
                    {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch)
                {
                    foundSecondary = true;
                    primaryIndexMap.add(nextPrimaryToExamine);
                    secondaryIndexMap.add(secondaryIndex);
                }
            }
            
            if (!foundSecondary && keepPrimaryWithNoMatch)
            {
                // Add row with blank secondary
                primaryIndexMap.add(nextPrimaryToExamine);
                secondaryIndexMap.add(-1);
            }
            
            if (primaryIndexMap.filled() > destIndex)
                return;
            else
                nextPrimaryToExamine += 1;
        }
        examinedAllSourceRows = true;
    }

    @Override
    protected @OnThread(Tag.Any) Stream<TableId> getSourcesFromExpressions()
    {
        return Stream.of();
    }

    @Override
    protected @OnThread(Tag.Any) Stream<TableId> getPrimarySources()
    {
        return Stream.of(primarySource, secondarySource);
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return "join";
    }

    @Override
    protected @OnThread(Tag.Simulation) List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        return Utility.<String>prependToList(keepPrimaryWithNoMatch ? "LEFTJOIN" : "INNERJOIN",
            Utility.<Pair<ColumnId, ColumnId>, String>mapListI(columnsToMatch, p -> OutputBuilder.quoted(p.getFirst().getRaw()) + " EQUALS " + OutputBuilder.quoted(p.getSecond().getRaw())) 
        );
    }

    @Override
    protected int transformationHashCode()
    {
        return Objects.hash(primarySource, secondarySource, keepPrimaryWithNoMatch, columnsToMatch);
    }

    @Override
    public boolean transformationEquals(Transformation transformation)
    {
        if (this == transformation) return true;
        if (transformation == null || getClass() != transformation.getClass()) return false;
        Join join = (Join) transformation;
        return keepPrimaryWithNoMatch == join.keepPrimaryWithNoMatch &&
                primarySource.equals(join.primarySource) &&
                columnsToMatch.equals(join.columnsToMatch) &&
                secondarySource.equals(join.secondarySource);
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException, InternalException
    {
        if (recordSet != null)
            return recordSet;
        else
            throw new UserException(error == null ? "Unknown error in join" : error);
    }

    /**
     * The primary table on the "left" of the join
     */
    public TableId getPrimarySource()
    {
        return primarySource;
    }

    /**
     * The secondary table on the "right" of the join.
     */
    public TableId getSecondarySource()
    {
        return secondarySource;
    }

    /**
     * A list of the (primary, secondary) columns where each pair
     * must have equal values in the join.  Empty list means result table will be
     * full cross join with all possible pairings.
     */
    public ImmutableList<Pair<ColumnId, ColumnId>> getColumnsToMatch()
    {
        return columnsToMatch;
    }

    /**
     * If true, it does a left join; all rows from primary table
     * are included at least once, even if they have no match,
     * and all secondary items become Optional wrapped.  If false,
     * does an inner join.
     */
    public boolean isKeepPrimaryWithNoMatch()
    {
        return keepPrimaryWithNoMatch;
    }

    public static class Info extends TransformationInfo
    {
        public Info()
        {
            super("join", "transform.join", "preview-join.png", "join.explanation.short", Arrays.asList("merge"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation load(TableManager mgr, InitialLoadDetails initialLoadDetails, List<TableId> source, String detail, ExpressionVersion expressionVersion) throws InternalException, UserException
        {
            if (source.size() != 2)
                throw new UserException("Expected two tables as join sources but found " + source.size());
            JoinContext whole = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, p -> p.join());
            ArrayList<Pair<ColumnId, ColumnId>> columns = new ArrayList<>();
            for (JoinColumnLineContext ctx : whole.joinColumnLine())
            {
                @ExpressionIdentifier String a = IdentifierUtility.asExpressionIdentifier(ctx.columnA.getText());
                @ExpressionIdentifier String b = IdentifierUtility.asExpressionIdentifier(ctx.columnB.getText());
                if (a == null)
                    throw new UserException("Invalid column id: \"" + ctx.columnA.getText() + "\"");
                if (b == null)
                    throw new UserException("Invalid column id: \"" + ctx.columnB.getText() + "\"");
                columns.add(new Pair<>(new ColumnId(a), new ColumnId(b)));
            }
            return new Join(mgr, initialLoadDetails, source.get(0), source.get(1), whole.joinTypeLine().leftJoinKW() != null, ImmutableList.copyOf(columns));
        }

        @SuppressWarnings("identifier")
        @Override
        public @OnThread(Tag.FXPlatform) @Nullable SimulationSupplier<Transformation> make(TableManager mgr, CellPosition destination, FXPlatformSupplier<Optional<Table>> askForSingleSrcTable)
        {
            return () -> new Join(mgr, new InitialLoadDetails(null, null, destination, null), new TableId(""), new TableId(""), false, ImmutableList.of());
        }
    }

    @Override
    public <T> T visit(TransformationVisitor<T> visitor)
    {
        return visitor.join(this);
    }

    @Override
    public TableId getSuggestedName()
    {
        return suggestedName(this.columnsToMatch);
    }

    public static TableId suggestedName(ImmutableList<Pair<ColumnId, ColumnId>> columnsToMatch)
    {
        return new TableId(IdentifierUtility.spaceSeparated("Join", columnsToMatch.stream().<@ExpressionIdentifier String>map(p -> IdentifierUtility.spaceSeparated(IdentifierUtility.shorten(p.getFirst().getRaw()), "to", IdentifierUtility.shorten(p.getFirst().getRaw()))).findFirst().orElse("none")));
    }
}
