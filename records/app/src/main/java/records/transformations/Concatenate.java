package records.transformations;

import annotation.qual.Value;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.TableAndColumnRenames;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.TransformationLexer;
import records.grammar.TransformationParser;
import records.grammar.TransformationParser.ConcatMissingContext;
import records.gui.View;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.ExFunction;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.SimulationFunction;
import utility.SimulationSupplier;
import utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
public class Concatenate extends Transformation
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
    private @Nullable String error = null;
    @OnThread(Tag.Any)
    private final @Nullable RecordSet recordSet;

    @SuppressWarnings("initialization")
    public Concatenate(TableManager mgr, InitialLoadDetails initialLoadDetails, ImmutableList<TableId> sources, IncompleteColumnHandling incompleteColumnHandling) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.sources = sources;
        this.incompleteColumnHandling = incompleteColumnHandling;

        KnownLengthRecordSet rs = null;
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
            
            tables = Utility.mapListEx(sources, getManager()::getSingleTableOrThrow);
            // We used LinkedHashMap to keep original column ordering:
            // Function is either identity (represented by null), or has a default, or is a custom wrapping function:.
            LinkedHashMap<ColumnId, ColumnDetails> ourColumns = new LinkedHashMap<>();
            for (Entry<ColumnId, Multiset<DataType>> entry : getColumnsNameAndTypes(tables).entrySet())
            {
                // If there is more than one type for a given column name, it's a definite error:
                if (entry.getValue().elementSet().size() > 1)
                {
                    // This will get caught at end of constructor:
                    throw new UserException("Column " + entry.getKey() + " has differing types in the source tables: " + Utility.listToString(ImmutableList.copyOf(entry.getValue().elementSet())));
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
                            
                            DataType wrappedInMaybe = mgr.getTypeManager().getMaybeType().instantiate(ImmutableList.of(Either.right(origType)), mgr.getTypeManager());
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
            rs = new KnownLengthRecordSet(Utility.<Entry<ColumnId, ColumnDetails>, SimulationFunction<RecordSet, Column>>mapList(new ArrayList<>(ourColumns.entrySet()), (Entry<ColumnId, ColumnDetails> colDetails) -> new SimulationFunction<RecordSet, Column>()
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
                                type = DataTypeValue.copySeveral(colDetails.getValue().dataType, (concatenatedRow, prog) ->
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
                                                DataTypeValue wrapped = colDetails.getValue().dataType.fromCollapsed((i, progB) -> colDetails.getValue().wrapValue.apply(oldColumn.getType().getCollapsed(concatenatedRow - start)));
                                                return new Pair<>(wrapped, 0);
                                            }
                                        }
                                    }
                                    throw new InternalException("Attempting to access beyond end of concatenated tables: index" + (concatenatedRow + 1) + " but only length " + ends.get(ends.size() - 1));
                                });
                            }
                            return type;
                        }

                        @Override
                        public boolean isAltered()
                        {
                            return true;
                        }
                    };
                }
            }), totalLength);


        }
        catch (UserException e)
        {
            String msg = e.getLocalizedMessage();
            if (msg != null)
                this.error = msg;
        }
        this.recordSet = rs;
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
        return ImmutableList.of("@INCOMPLETE " + incompleteColumnHandling.toString());
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
                ms.add(c.getType());
            }
        }
        return r;
    }

    public static class Info extends TransformationInfo
    {
        public Info()
        {
            super("concatenate", "Concatenate", "preview-concatenate.png", "concatenate.explanation.short",Arrays.asList("append", "join"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation load(TableManager mgr, InitialLoadDetails initialLoadDetails, List<TableId> source, String detail) throws InternalException, UserException
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
            return new Concatenate(mgr, initialLoadDetails, ImmutableList.copyOf(source), incompleteColumnHandling);
        }

        @Override
        public @OnThread(Tag.FXPlatform) @Nullable SimulationSupplier<Transformation> make(View view, TableManager mgr, CellPosition destination, FXPlatformSupplier<Optional<Table>> askForSingleSrcTable)
        {
            return () -> new Concatenate(mgr, new InitialLoadDetails(null, destination, null), ImmutableList.of(), IncompleteColumnHandling.DEFAULT);
        }
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
}
