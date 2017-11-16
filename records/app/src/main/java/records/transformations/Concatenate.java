package records.transformations;

import annotation.qual.Value;
import com.google.common.collect.Sets;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.DataLexer;
import records.grammar.DataParser;
import records.grammar.FormatLexer;
import records.grammar.FormatParser;
import records.grammar.FormatParser.TypeContext;
import records.grammar.TransformationLexer;
import records.grammar.TransformationParser;
import records.grammar.TransformationParser.ConcatMissingColumnContext;
import records.grammar.TransformationParser.ConcatMissingContext;
import records.gui.View;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by neil on 18/01/2017.
 */
@OnThread(Tag.Simulation)
public class Concatenate extends TransformationEditable
{
    @OnThread(Tag.Any)
    private final List<TableId> sources;

    @OnThread(Tag.Any)
    private final List<Table> sourceTables;

    // If there is a column which is not in every source table, it must appear in this map
    // If any column is mapped to Optional.empty then it should be omitted in the concatenated version, even if it appears in all.
    // If it's mapped to Optional.of then it should be given that value in the concatenated version
    // for any source table which lacks the column.
    @OnThread(Tag.Any)
    private final Map<ColumnId, Pair<DataType, Optional<@Value Object>>> missingValues;

    @OnThread(Tag.Any)
    private @Nullable String error = null;
    @OnThread(Tag.Any)
    private final @Nullable RecordSet recordSet;

    @SuppressWarnings("initialization")
    public Concatenate(TableManager mgr, @Nullable TableId tableId, List<TableId> sources, Map<ColumnId, Pair<DataType, Optional<@Value Object>>> missingVals) throws InternalException
    {
        super(mgr, tableId);
        this.sources = sources;
        this.missingValues = new HashMap<>(missingVals);

        KnownLengthRecordSet rs = null;
        List<Table> tables = Collections.emptyList();
        try
        {
            tables = Utility.mapListEx(sources, getManager()::getSingleTableOrThrow);
            LinkedHashMap<ColumnId, DataType> prevColumns = getColumnsNameAndType(tables.get(0));
            int totalLength = tables.get(0).getData().getLength();
            List<Integer> ends = new ArrayList<>();
            ends.add(totalLength);

            // Remove all from prevColumns that have an omit instruction:
            for (Iterator<Entry<ColumnId, DataType>> iterator = prevColumns.entrySet().iterator(); iterator.hasNext(); )
            {
                Entry<ColumnId, DataType> entry = iterator.next();
                @Nullable Pair<DataType, Optional<@Value Object>> typedInstruction = missingValues.get(entry.getKey());
                if (typedInstruction != null && !typedInstruction.getSecond().isPresent())
                {
                    if (DataType.checkSame(typedInstruction.getFirst(), entry.getValue(), s -> {}) == null)
                        throw new UserException("Types do not match for column " + entry.getKey() + ": saved type " + typedInstruction.getFirst() + " but column in source table(s) has type " + entry.getValue());
                    iterator.remove();
                }
            }
            // After this point, prevColumns will never have an entry for columns with an omit instruction

            // There are 4 categories of tables:
            // 1. Present in all, type matches in all, instruction is omit or no instruction.
            // 2. Type doesn't match in every instance where column name is present and instruction is not to omit
            // 3. It is present in some, and type matches in all, and it has a default value/instruction
            // 4. It is present in some, and type matches in all where present, and it doesn't have a default value
            // Numbers 1 and 3 are valid, but 2 and 4 are not.

            for (int i = 1; i < tables.size(); i++)
            {
                LinkedHashMap<ColumnId, DataType> curColumns = getColumnsNameAndType(tables.get(i));
                // Need to check that for every entry in curColumns, the type matches that of
                // prevColumns if present

                Set<ColumnId> matchedPrev = new HashSet<>();
                for (Entry<ColumnId, DataType> curEntry : curColumns.entrySet())
                {
                    @Nullable DataType prev = prevColumns.get(curEntry.getKey());
                    if (prev == null)
                    {
                        // This is fine, as long as there is an instruction for that column
                        @Nullable Pair<DataType, Optional<@Value Object>> onMissing = missingValues.get(curEntry.getKey());
                        if (onMissing == null)
                        {
                            // Case 4
                            throw new UserException("Column " + curEntry.getKey() + " from table " + tables.get(i).getId() + " is not present in all tables and the handling is unspecified");
                        }
                        if (onMissing.getSecond().isPresent())
                        {
                            // Case 3
                            if (DataType.checkSame(onMissing.getFirst(), curEntry.getValue(), s -> {}) == null)
                                throw new UserException("Types do not match for column " + curEntry.getKey() + ": saved type " + onMissing.getFirst() + " but column in source table(s) has type " + curEntry.getValue());
                            prevColumns.put(curEntry.getKey(), curEntry.getValue());
                        }
                        // Otherwise if empty optional, leave out column
                    }
                    else
                    {
                        // Was already there; check type matches
                        if (!prev.equals(curEntry.getValue()))
                        {
                            // No need to check for omit instruction: if it was in prevColumns, it's not omitted
                            // Case 2
                            throw new UserException("Type does not match for column " + curEntry.getKey() + " in table " + tables.get(i).getId() + " and previous tables: " + prev + " vs " + curEntry.getValue());
                        }
                        // else type matches, in which case it's fine
                        matchedPrev.add(curEntry.getKey());
                    }
                }
                // Handle all those already present in prev but which did not occur in cur:
                Collection<@KeyFor("prevColumns") ColumnId> notPresentInCur = Sets.difference(prevColumns.keySet(), matchedPrev);
                for (ColumnId id : notPresentInCur)
                {
                    // Since it was in prev, it can't have an omit instruction, make sure it has default value:
                    @Nullable Pair<DataType, Optional<@Value Object>> instruction = missingValues.get(id);
                    if (instruction == null)
                    {
                        // Case 4
                        throw new UserException("Column " + id + " is not present in all tables and the handling is unspecified");
                    }
                    if (!instruction.getSecond().isPresent())
                        throw new InternalException("Column " + id + " was instructed to omit but is present in prev");
                }

                int length = tables.get(i).getData().getLength();
                totalLength += length;
                ends.add(totalLength);
            }
            List<Table> tablesFinal = tables;
            rs = new KnownLengthRecordSet(Utility.<Entry<ColumnId, DataType>, ExFunction<RecordSet, Column>>mapList(new ArrayList<>(prevColumns.entrySet()), (Entry<ColumnId, DataType> oldC) -> new ExFunction<RecordSet, Column>()
            {
                @Override
                @OnThread(Tag.Simulation)
                public Column apply(RecordSet rs) throws InternalException, UserException
                {
                    return new Column(rs, oldC.getKey())
                    {

                        public @MonotonicNonNull DataTypeValue type;

                        @Override
                        public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
                        {
                            if (type == null)
                            {
                                type = DataTypeValue.copySeveral(oldC.getValue(), (concatenatedRow, prog) ->
                                {
                                    for (int srcTableIndex = 0; srcTableIndex < ends.size(); srcTableIndex++)
                                    {
                                        if (concatenatedRow < ends.get(srcTableIndex))
                                        {
                                            int start = srcTableIndex == 0 ? 0 : ends.get(srcTableIndex - 1);
                                            // First one with end beyond our target must be right one:
                                            @Nullable Column oldColumn = tablesFinal.get(srcTableIndex).getData().getColumnOrNull(oldC.getKey());
                                            if (oldColumn == null)
                                                return new Pair<DataTypeValue, Integer>(oldC.getValue().fromCollapsed((i, progB) -> missingValues.get(oldC.getKey()).getSecond().get()), 0);
                                            else
                                                return new Pair<DataTypeValue, Integer>(oldColumn.getType(), concatenatedRow - start);
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
        sourceTables = tables;
        this.recordSet = rs;
    }

    @Override
    public @OnThread(Tag.FXPlatform) String getTransformationLabel()
    {
        return "concat";
    }

    @Override
    @OnThread(Tag.Any)
    public List<TableId> getSources()
    {
        return sources;
    }

    @Override
    public @OnThread(Tag.FXPlatform) TransformationEditor edit(View view)
    {
        return new Editor(view, sources, sourceTables, missingValues);
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return "concatenate";
    }

    @Override
    protected @OnThread(Tag.Any) List<String> saveDetail(@Nullable File destination)
    {
        return missingValues.entrySet().stream().map((Entry<ColumnId, Pair<DataType, Optional<@Value Object>>> e) ->
        {
            try
            {
                OutputBuilder b = new OutputBuilder();
                b.id(e.getKey()).kw("@TYPE");
                e.getValue().getFirst().save(b, false);
                if (e.getValue().getSecond().isPresent())
                    b.kw("@VALUE").dataValue(e.getValue().getFirst(), e.getValue().getSecond().get());
                else
                    b.kw("@OMIT");
                return b.toString();
            }
            catch (InternalException | UserException e1)
            {
                Utility.log(e1);
                // Not correct but at least will manage to save:
                return e.getKey().getOutput() + " @TYPE BOOLEAN @VALUE " + e.getValue().getSecond().get();
            }
        }).collect(Collectors.<String>toList());
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException, InternalException
    {
        if (recordSet == null)
            throw new UserException(error == null ? "Unknown error" : error);
        return recordSet;
    }

    private static LinkedHashMap<ColumnId, DataType> getColumnsNameAndType(Table t) throws InternalException, UserException
    {
        List<Column> columns = new ArrayList<>(t.getData().getColumns());
        Collections.sort(columns, Comparator.<Column, ColumnId>comparing(c -> c.getName()));
        LinkedHashMap<ColumnId, DataType> r = new LinkedHashMap<>();
        for (Column c : columns)
        {
            r.put(c.getName(), c.getType());
        }
        return r;
    }

    private static class Editor extends TransformationEditor
    {
        private final List<TableId> srcTableIds;
        private final List<Table> srcs; // May be empty
        private final Map<ColumnId, Pair<DataType, Optional<@Value Object>>> missingVals;

        private Editor(View view, List<TableId> srcTableIds, List<Table> srcs, Map<ColumnId, Pair<DataType, Optional<@Value Object>>> missingVals)
        {
            this.srcTableIds = srcTableIds;
            this.srcs = srcs;
            this.missingVals = missingVals;
        }

        @Override
        public TransformationInfo getInfo()
        {
            return new Info();
        }

        @Override
        public @Localized String getDisplayTitle()
        {
            return TranslationUtility.getString("transformEditor.concatenate.title");
        }

        @Override
        public Pair<@LocalizableKey String, @LocalizableKey String> getDescriptionKeys()
        {
            return new Pair<>("todo", "todo");
        }

        @Override
        public Pane getParameterDisplay(FXPlatformConsumer<Exception> reportError)
        {
            return new Pane(new Label("TODO"));
        }

        @Override
        public BooleanExpression canPressOk()
        {
            return new ReadOnlyBooleanWrapper(true);
        }

        @Override
        public SimulationSupplier<Transformation> getTransformation(TableManager mgr, TableId tableId)
        {
            return () -> new Concatenate(mgr, tableId, srcTableIds, missingVals);
        }

        @Override
        public TableId getSourceId()
        {
            return srcTableIds.get(0);
        }
    }

    public static class Info extends TransformationInfo
    {
        public Info()
        {
            super("concatenate", "Concatenate", "preview-concatenate.png", Arrays.asList("append", "join"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation load(TableManager mgr, TableId tableId, List<TableId> source, String detail) throws InternalException, UserException
        {
            Map<ColumnId, Pair<DataType, Optional<@Value Object>>> missingInstr = new HashMap<>();
            ConcatMissingContext ctx = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, p -> p.concatMissing());
            for (ConcatMissingColumnContext missing : ctx.concatMissingColumn())
            {
                String columnName = missing.concatMissingColumnName().getText();
                TypeContext typeSrc = Utility.parseAsOne(missing.type().TYPE().getText().trim(), FormatLexer::new, FormatParser::new, p -> p.type());
                DataType dataType = mgr.getTypeManager().loadTypeUse(typeSrc);
                Pair<DataType, Optional<@Value Object>> instruction = new Pair<>(dataType, Optional.<@Value Object>empty());
                if (missing.value() != null)
                {
                    DataParser p = Utility.parseAsOne(missing.value().VALUE().getText().trim(), DataLexer::new, DataParser::new, parser -> parser);
                    instruction = new Pair<>(dataType, Optional.<@Value Object>of(DataType.loadSingleItem(dataType, p, false)));
                }
                missingInstr.put(new ColumnId(columnName), instruction);
            }
            return new Concatenate(mgr, tableId, source, missingInstr);
        }

        @Override
        public @OnThread(Tag.FXPlatform) TransformationEditor editNew(View view, TableManager mgr, @Nullable TableId srcTableId, @Nullable Table src)
        {
            return new Editor(view,  srcTableId == null ? Collections.emptyList() : Collections.singletonList(srcTableId), src == null ? Collections.emptyList() : Collections.singletonList(src), Collections.emptyMap());
        }
    }

    @Override
    public boolean transformationEquals(Transformation o)
    {
        Concatenate that = (Concatenate) o;

        if (!sources.equals(that.sources)) return false;

        if (!missingValues.keySet().equals(that.missingValues.keySet()))
            return false;
        for (ColumnId columnId : missingValues.keySet())
        {
            try
            {
                Pair<DataType, Optional<@Value Object>> us = missingValues.get(columnId);
                Pair<DataType, Optional<@Value Object>> them = that.missingValues.get(columnId);
                if (them == null)
                    return false; // Shouldn't happen if key sets are equal, but just in case.
                if (!us.getFirst().equals(them.getFirst()))
                    return false;
                if (!us.getSecond().isPresent() && !them.getSecond().isPresent())
                    continue; // Both missing, fine
                if (!us.getSecond().isPresent() || !them.getSecond().isPresent())
                    return false; // One missing, not equal
                if (Utility.compareValues(us.getSecond().get(), them.getSecond().get()) != 0)
                    return false;
            }
            catch (InternalException | UserException e)
            {
                // I believe this method is only used in testing, so runtime should be okay:
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    @Override
    public int transformationHashCode()
    {
        int result = sources.hashCode();
        result = 31 * result + missingValues.hashCode();
        return result;
    }
}
