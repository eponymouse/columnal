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
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.data.KnownLengthRecordSet;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.TransformationLexer;
import xyz.columnal.grammar.TransformationParser;
import xyz.columnal.grammar.TransformationParser.ConcatMissingContext;
import xyz.columnal.grammar.Versions.ExpressionVersion;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.id.TableId;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.function.simulation.SimulationSupplier;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 18/01/2017.
 */
@OnThread(Tag.Simulation)
public class Concatenate extends VisitableTransformation
{
    // Note: these names are used directly for saving, so must match the parser.
    public static enum IncompleteColumnHandling
    {
        // Leave out any column which is not in all sources:
        OMIT, 
        // Include all columns, using default value for the type:
        DEFAULT,
        // Wrap them in a Maybe type:
        WRAPMAYBE
    }
    
    @OnThread(Tag.Any)
    private final ImmutableList<TableId> sources;

    @OnThread(Tag.Any)
    private final IncompleteColumnHandling incompleteColumnHandling;

    @OnThread(Tag.Any)
    private final @Nullable String error;
    @OnThread(Tag.Any)
    private final @Nullable RecordSet recordSet;
            
    @OnThread(Tag.Any)
    private final boolean includeMarkerColumn;

    public Concatenate(TableManager mgr, InitialLoadDetails initialLoadDetails, ImmutableList<TableId> sources, IncompleteColumnHandling incompleteColumnHandling, boolean includeMarkerColumn) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.sources = sources;
        this.incompleteColumnHandling = incompleteColumnHandling;
        this.includeMarkerColumn = includeMarkerColumn;

        KnownLengthRecordSet rs = null;
        @Nullable String err = null;
        List<Table> tables = Collections.emptyList();
        try
        {
            class ColumnDetails
            {
                private final DataType dataType;
                private final @Value Object defaultValue;
                // defaultValue, wrapped into a single item DataTypeValue:
                private final DataTypeValue defaultValueWrapped;
                // Function to wrap a value which does exist.  Null means the identity function:
                private final @Nullable Function<@Value Object, @Value Object> wrapValue;

                public ColumnDetails(DataType dataType, @Value Object defaultValue, @Nullable Function<@Value Object, @Value Object> wrapValue) throws InternalException, UserException
                {
                    this.dataType = dataType;
                    this.defaultValue = defaultValue;
                    this.defaultValueWrapped = dataType.fromCollapsed((i, prog) -> defaultValue);
                    this.wrapValue = wrapValue;
                }
            }
            
            tables = Utility.mapListEx(sources, mgr::getSingleTableOrThrow);
            // We used LinkedHashMap to keep original column ordering:
            // Function is either identity (represented by null), or has a default, or is a custom wrapping function:.
            LinkedHashMap<ColumnId, ColumnDetails> ourColumns = new LinkedHashMap<>();
            for (Entry<ColumnId, Multiset<DataType>> entry : getColumnsNameAndTypes(tables).entrySet())
            {
                // If there is more than one type for a given column name, it's a definite error:
                if (entry.getValue().elementSet().size() > 1)
                {
                    // This will get caught at end of constructor:
                    throw new UserException("Column " + entry.getKey() + " has differing types in the source tables: " + Utility.<DataType>listToString(ImmutableList.<DataType>copyOf(entry.getValue().elementSet())));
                }
                DataType origType = entry.getValue().elementSet().iterator().next();
                // Otherwise there's one type, so we know the type.  Based on how many copies there are of that type, we know
                // how many columns contained the original column.  We only need to do anything special if less than all
                // of the tables have that column:
                if (entry.getValue().size() < tables.size())
                {
                    // What do we do with an incomplete column?
                    switch (incompleteColumnHandling)
                    {
                        case OMIT:
                            // Leave that column out
                            break;
                        case WRAPMAYBE:
                            
                            DataType wrappedInMaybe = mgr.getTypeManager().getMaybeType().instantiate(ImmutableList.of(Either.<Unit, DataType>right(origType)), mgr.getTypeManager());
                            ourColumns.put(entry.getKey(), new ColumnDetails(wrappedInMaybe, mgr.getTypeManager().maybeMissing(), orig -> {
                                return mgr.getTypeManager().maybePresent(orig);
                            }));
                            break;
                        default: // Which includes case DEFAULT
                            @Value Object defaultValue = DataTypeUtility.makeDefaultValue(origType);
                            ourColumns.put(entry.getKey(), new ColumnDetails(origType, defaultValue, null));
                            break;
                    }
                }
                else
                {
                    // Default will never be used in this case:
                    ourColumns.put(entry.getKey(), new ColumnDetails(origType, DataTypeUtility.value(""), null));
                }
            }
            
            
            int totalLength = 0;
            List<Integer> ends = new ArrayList<>();
            for (Table table : tables)
            {
                int len = table.getData().getLength();
                totalLength += len;
                ends.add(totalLength);
            }

            List<Table> tablesFinal = tables;
            ArrayList<SimulationFunction<RecordSet, Column>> columns = new ArrayList<>(Utility.<Entry<ColumnId, ColumnDetails>, SimulationFunction<RecordSet, Column>>mapList(new ArrayList<>(ourColumns.entrySet()), (Entry<ColumnId, ColumnDetails> colDetails) -> new SimulationFunction<RecordSet, Column>()
            {
                @Override
                @OnThread(Tag.Simulation)
                public Column apply(RecordSet rs) throws InternalException, UserException
                {
                    return new Column(rs, colDetails.getKey())
                    {
                        public @MonotonicNonNull DataTypeValue type;

                        @Override
                        public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
                        {
                            if (type == null)
                            {
                                type = addManualEditSet(getName(), DataTypeValue.copySeveral(colDetails.getValue().dataType, concatenatedRow ->
                                {
                                    for (int srcTableIndex = 0; srcTableIndex < ends.size(); srcTableIndex++)
                                    {
                                        if (concatenatedRow < ends.get(srcTableIndex))
                                        {
                                            int start = srcTableIndex == 0 ? 0 : ends.get(srcTableIndex - 1);
                                            // First one with end beyond our target must be right one:
                                            @Nullable Column oldColumn = tablesFinal.get(srcTableIndex).getData().getColumnOrNull(colDetails.getKey());
                                            if (oldColumn == null)
                                            {
                                                return new Pair<>(colDetails.getValue().defaultValueWrapped, 0);
                                            }
                                            else if (colDetails.getValue().wrapValue == null)
                                            {
                                                return new Pair<>(oldColumn.getType(), concatenatedRow - start);
                                            }
                                            else
                                            {
                                                Function<@Value Object, @Value Object> wrapValue = colDetails.getValue().wrapValue;
                                                DataTypeValue wrapped = colDetails.getValue().dataType.fromCollapsed((i, progB) -> wrapValue.apply(oldColumn.getType().getCollapsed(concatenatedRow - start)));
                                                return new Pair<>(wrapped, 0);
                                            }
                                        }
                                    }
                                    throw new InternalException("Attempting to access beyond end of concatenated tables: index" + (concatenatedRow + 1) + " but only length " + ends.get(ends.size() - 1));
                                }));
                            }
                            return type;
                        }

                        @Override
                        public @OnThread(Tag.Any) AlteredState getAlteredState()
                        {
                            return AlteredState.OVERWRITTEN;
                        }
                    };
                }
            }));
            
            if (includeMarkerColumn)
            {
                // TODO guard against duplicate Source column name
                columns.add(0, recordSet -> new Column(recordSet, new ColumnId("Source"))
                {
                    @Override
                    public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
                    {
                        return addManualEditSet(getName(), DataTypeValue.text((concatenatedRow, prog) -> {
                            for (int srcTableIndex = 0; srcTableIndex < ends.size(); srcTableIndex++)
                            {
                                if (concatenatedRow < ends.get(srcTableIndex))
                                {
                                    return DataTypeUtility.value(sources.get(srcTableIndex).getRaw());
                                }
                            }
                            throw new InternalException("Attempting to access beyond end of concatenated tables: index" + (concatenatedRow + 1) + " but only length " + ends.get(ends.size() - 1));
                        }));
                    }

                    @Override
                    public @OnThread(Tag.Any) AlteredState getAlteredState()
                    {
                        return AlteredState.OVERWRITTEN;
                    }
                });
            }
            
            rs = new KnownLengthRecordSet(columns, totalLength);
        }
        catch (UserException e)
        {
            String msg = e.getLocalizedMessage();
            if (msg != null)
                err = msg;
        }
        this.recordSet = rs;
        this.error = err;
    }
    
    @Override
    @OnThread(Tag.Any)
    public Stream<TableId> getPrimarySources()
    {
        return sources.stream();
    }

    @Override
    protected @OnThread(Tag.Any) Stream<TableId> getSourcesFromExpressions()
    {
        return Stream.empty();
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return "concatenate";
    }

    @Override
    protected @OnThread(Tag.Any) List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        return ImmutableList.of("@INCOMPLETE " + incompleteColumnHandling.toString() + (includeMarkerColumn ? " SOURCE " : ""));
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException, InternalException
    {
        if (recordSet == null)
            throw new UserException(error == null ? "Unknown error" : error);
        return recordSet;
    }

    private static LinkedHashMap<ColumnId, Multiset<DataType>> getColumnsNameAndTypes(Collection<Table> tables) throws InternalException, UserException
    {
        LinkedHashMap<ColumnId, Multiset<DataType>> r = new LinkedHashMap<>();
        for (Table t : tables)
        {
            List<Column> columns = new ArrayList<>(t.getData().getColumns());
            //Collections.sort(columns, Comparator.<Column, ColumnId>comparing(c -> c.getName()));
            for (Column c : columns)
            {
                Multiset<DataType> ms = r.get(c.getName());
                if (ms == null)
                {
                    ms = HashMultiset.create();
                    r.put(c.getName(), ms);
                }
                ms.add(c.getType().getType());
            }
        }
        return r;
    }

    public static class Info extends TransformationInfo
    {
        public Info()
        {
            super("concatenate", "transform.concatenate", "preview-concatenate.png", "concatenate.explanation.short",Arrays.asList("append", "join"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation load(TableManager mgr, InitialLoadDetails initialLoadDetails, List<TableId> source, String detail, ExpressionVersion expressionVersion) throws InternalException, UserException
        {
            Map<ColumnId, Pair<DataType, Optional<@Value Object>>> missingInstr = new HashMap<>();
            ConcatMissingContext ctx = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, p -> p.concatMissing());
            IncompleteColumnHandling incompleteColumnHandling;
            if (ctx.concatOmit() != null)
                incompleteColumnHandling = IncompleteColumnHandling.OMIT;
            else if (ctx.concatWrapMaybe() != null)
                incompleteColumnHandling = IncompleteColumnHandling.WRAPMAYBE;
            else
                incompleteColumnHandling = IncompleteColumnHandling.DEFAULT;
            return new Concatenate(mgr, initialLoadDetails, ImmutableList.copyOf(source), incompleteColumnHandling, ctx.concatIncludeSource() != null);
        }

        @Override
        public @OnThread(Tag.FXPlatform) @Nullable SimulationSupplier<Transformation> make(TableManager mgr, CellPosition destination, FXPlatformSupplier<Optional<Table>> askForSingleSrcTable)
        {
            return () -> new Concatenate(mgr, new InitialLoadDetails(null, null, destination, null), ImmutableList.of(), IncompleteColumnHandling.DEFAULT, true);
        }
    }

    public IncompleteColumnHandling getIncompleteColumnHandling()
    {
        return incompleteColumnHandling;
    }

    @OnThread(Tag.Any)
    public boolean isIncludeMarkerColumn()
    {
        return includeMarkerColumn;
    }

    @Override
    public boolean transformationEquals(Transformation o)
    {
        Concatenate that = (Concatenate) o;

        if (!sources.equals(that.sources)) return false;

        if (incompleteColumnHandling != that.incompleteColumnHandling)
            return false;
        
        return true;
    }

    @Override
    public int transformationHashCode()
    {
        int result = sources.hashCode();
        result = 31 * result + incompleteColumnHandling.hashCode();
        return result;
    }

    @Override
    @OnThread(Tag.Any)
    public <T> T visit(TransformationVisitor<T> visitor)
    {
        return visitor.concatenate(this);
    }

    @Override
    public TableId getSuggestedName()
    {
        return suggestedName(sources);
    }

    public static TableId suggestedName(ImmutableList<TableId> sources)
    {
        ImmutableList.Builder<@ExpressionIdentifier String> parts = ImmutableList.builder();
        parts.add("Conc");
        if (sources.isEmpty())
            parts.add("none");
        else
            parts.add(IdentifierUtility.shorten(sources.get(0).getRaw()));
        if (sources.size() > 1)
            parts.add("and").add(IdentifierUtility.shorten(sources.get(1).getRaw()));
        return new TableId(IdentifierUtility.spaceSeparated(parts.build()));
    }


}
