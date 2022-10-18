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

import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.SingleSourceTransformation;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.TransformationLexer;
import xyz.columnal.grammar.TransformationParser;
import xyz.columnal.grammar.TransformationParser.HideColumnsContext;
import xyz.columnal.grammar.Versions.ExpressionVersion;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.id.TableId;
import xyz.columnal.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Keeps all data as-is, but hides a given set of columns from the resulting
 * data set.  Hidden is decided by black-list.
 */
@OnThread(Tag.Simulation)
public class HideColumns extends VisitableTransformation implements SingleSourceTransformation
{
    public static final String NAME = "drop.columns";
    @OnThread(Tag.Any)
    private final TableId srcTableId;
    private final @Nullable Table src;
    private final @Nullable RecordSet result;
    @OnThread(Tag.Any)
    private final ImmutableList<ColumnId> hideIds;
    // The list of columns from the source table which are not hidden:
    private final List<Column> shownColumns = new ArrayList<>();
    @OnThread(Tag.Any)
    private String error;

    public HideColumns(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, ImmutableList<ColumnId> toHide) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.srcTableId = srcTableId;
        this.src = mgr.getSingleTableOrNull(srcTableId);
        this.error = "Unknown error with table \"" + getId() + "\"";
        this.hideIds = toHide;
        if (this.src == null)
        {
            this.result = null;
            error = "Could not find source table: \"" + srcTableId + "\"";
            return;
        }


        @Nullable RecordSet theResult = null;
        try
        {
            ArrayList<ColumnId> stillToHide = new ArrayList<>(toHide);
            RecordSet srcRecordSet = this.src.getData();
            for (Column c : srcRecordSet.getColumns())
            {
                if (!stillToHide.remove(c.getName()))
                {
                    shownColumns.add(c);
                }
            }

            if (!stillToHide.isEmpty())
                throw new UserException("Source table does not contain columns " + Utility.listToString(stillToHide));

            if (shownColumns.isEmpty())
                throw new UserException("Cannot hide all columns");

            theResult = new <Column>RecordSet(Utility.<Column, SimulationFunction<RecordSet, Column>>mapList(shownColumns, c -> (SimulationFunction<RecordSet, Column>)(rs -> new Column(rs, c.getName())
            {
                @Override
                public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
                {
                    return addManualEditSet(getName(), c.getType());
                }

                @Override
                public @OnThread(Tag.Any) AlteredState getAlteredState()
                {
                    return AlteredState.UNALTERED;
                }
            })))
            {
                @Override
                public boolean indexValid(int index) throws UserException, InternalException
                {
                    return srcRecordSet.indexValid(index);
                }

                @Override
                public @TableDataRowIndex int getLength() throws UserException, InternalException
                {
                    return srcRecordSet.getLength();
                }
            };
        }
        catch (UserException e)
        {
            String msg = e.getLocalizedMessage();
            if (msg != null)
                this.error = msg;
        }

        result = theResult;
    }
    
    @Override
    @OnThread(Tag.Any)
    public Stream<TableId> getPrimarySources()
    {
        return Stream.of(srcTableId);
    }

    @Override
    protected @OnThread(Tag.Any) Stream<TableId> getSourcesFromExpressions()
    {
        return Stream.empty();
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return NAME;
    }

    @Override
    protected @OnThread(Tag.Any) List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        renames.useColumnsFromTo(srcTableId, getId());
        OutputBuilder b = new OutputBuilder();
        for (ColumnId c : hideIds)
            b.kw("HIDE").id(renames.columnId(getId(), c, srcTableId).getSecond()).nl();
        return b.toLines();
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException, InternalException
    {
        if (result == null)
            throw new UserException(error);
        return result;
    }

    @OnThread(Tag.Any)
    public TableId getSrcTableId()
    {
        return srcTableId;
    }

    @Override
    public @OnThread(Tag.Simulation) Transformation withNewSource(TableId newSrcTableId) throws InternalException
    {
        return new HideColumns(getManager(), getDetailsForCopy(getId()), newSrcTableId, hideIds);
    }

    @OnThread(Tag.Any)
    public ImmutableList<ColumnId> getHiddenColumns()
    {
        return hideIds;
    }

    public static class Info extends SingleSourceTransformationInfo
    {
        public Info()
        {
            super(NAME, "transform.hideColumns", "preview-hide.png", "hide.explanation.short", Arrays.asList("collapse"));
        }

        @Override
        @SuppressWarnings("identifier")
        protected @OnThread(Tag.Simulation) Transformation loadSingle(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, String detail, ExpressionVersion expressionVersion) throws InternalException, UserException
        {
            HideColumnsContext loaded = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, TransformationParser::hideColumns);

            return new HideColumns(mgr, initialLoadDetails, srcTableId, loaded.hideColumn().stream().map(hc -> new ColumnId(hc.column.getText())).collect(ImmutableList.<ColumnId>toImmutableList()));
        }

        @Override
        protected @OnThread(Tag.Simulation) Transformation makeWithSource(TableManager mgr, CellPosition destination, Table srcTable) throws InternalException
        {
            return new HideColumns(mgr, new InitialLoadDetails(null, null, destination, null), srcTable.getId(), ImmutableList.of());
        }
    }

    @Override
    public boolean transformationEquals(Transformation o)
    {
        HideColumns that = (HideColumns) o;

        if (!srcTableId.equals(that.srcTableId)) return false;
        return hideIds.equals(that.hideIds);
    }

    @Override
    public int transformationHashCode()
    {
        int result = srcTableId.hashCode();
        result = 31 * result + hideIds.hashCode();
        return result;
    }

    @Override
    @OnThread(Tag.Any)
    public <T> T visit(TransformationVisitor<T> visitor)
    {
        return visitor.hideColumns(this);
    }

    @Override
    public TableId getSuggestedName()
    {
        return suggestedName(hideIds);
    }

    public static TableId suggestedName(ImmutableList<ColumnId> newHidden)
    {
        if (newHidden.isEmpty())
            return new TableId(IdentifierUtility.spaceSeparated("Hiding none"));
        else if (newHidden.size() == 1)
            return new TableId(IdentifierUtility.spaceSeparated("Hiding", newHidden.get(0).getRaw()));
        else
            return new TableId(IdentifierUtility.spaceSeparated("Hiding", newHidden.get(0).getRaw(), "and", newHidden.get(1).getRaw()));
    }
}
