package records.transformations;

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.TreeMultimap;
import log.Log;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.*;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeUtility.ComparableValue;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.InvalidImmediateValueException;
import records.error.UserException;
import records.grammar.DataLexer;
import records.grammar.DataParser;
import records.grammar.TransformationLexer;
import records.grammar.TransformationParser;
import records.grammar.TransformationParser.EditColumnContext;
import records.grammar.TransformationParser.EditColumnDataContext;
import records.grammar.TransformationParser.EditContext;
import records.grammar.TransformationParser.ValueContext;
import records.gui.View;
import records.loadsave.OutputBuilder;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ComparableEither;
import utility.Either;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.gui.FXUtility;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collector;
import java.util.stream.Stream;

@OnThread(Tag.Simulation)
public class ManualEdit extends Transformation implements SingleSourceTransformation
{
    public static final String NAME = "correct";
    
    private final TableId srcTableId;
    private final @Nullable Table src;
    // Kept for saving, in case issue with loading column for keyColumn
    private final @Nullable Pair<ColumnId, DataType> originalKeyColumn;
    // If null, we use the row number as the replacement key.
    // We cache the type to avoid checked exceptions fetching it from the column
    @OnThread(Tag.Any)
    private final @Nullable Pair<Column, DataType> keyColumn;
    @OnThread(Tag.Any)
    private final Either<StyledString, RecordSet> recordSet;
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private final HashMap<ColumnId, ColumnReplacementValues> replacements;

    public ManualEdit(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, @Nullable Pair<ColumnId, DataType> replacementKey, ImmutableMap<ColumnId, ColumnReplacementValues> replacements) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.srcTableId = srcTableId;
        this.originalKeyColumn = replacementKey;
        this.replacements = new HashMap<>(replacements);

        Table srcTable = null;
        Either<StyledString, RecordSet> data;
        Pair<Column, DataType> keyColumn = null;
        try
        {
            srcTable = mgr.getSingleTableOrThrow(srcTableId);
            RecordSet srcData = srcTable.getData();
            if (replacementKey != null)
            {
                // Check key exists and has same type and unique values for replacement keys:
                Column keyCol = srcData.getColumnOrNull(replacementKey.getFirst());
                if (keyCol == null)
                {
                    throw new UserException("Could not find identifier column " + replacementKey.getFirst().getRaw());
                }
                keyColumn = new Pair<>(keyCol, keyCol.getType().getType());
                if (!keyColumn.getSecond().equals(replacementKey.getSecond()))
                {
                    throw new UserException("Last recorded type of identifier column " + replacementKey.getFirst().getRaw() + " does not match actual column type.");
                }
                TreeSet<ComparableValue> keyValues = new TreeSet<>();
                @TableDataRowIndex int srcDataLength = srcData.getLength();
                DataTypeValue keyColType = keyCol.getType();
                for (int i = 0; i < srcDataLength; i++)
                {
                    try
                    {
                        @Value Object value = keyColType.getCollapsed(i);
                        if (!keyValues.add(new ComparableValue(value)))
                        {
                            throw new UserException("Duplicate keys: " + DataTypeUtility.valueToString(keyColType.getType(), value, null));
                        }
                    }
                    catch (UserException e)
                    {
                        // We don't actually mind errors in the key column...
                    }
                }
            }
            
            List<SimulationFunction<RecordSet, Column>> columns = Utility.mapList(srcData.getColumns(), c -> rs -> new ReplacedColumn(rs, c));
            data = Either.right(new <Column>RecordSet(columns)
            {
                @Override
                public boolean indexValid(int index) throws UserException, InternalException
                {
                    return srcData.indexValid(index);
                }

                @Override
                public @TableDataRowIndex int getLength() throws UserException, InternalException
                {
                    return srcData.getLength();
                }
            });
        }
        catch (UserException e)
        {
            data = Either.left(e.getStyledMessage());
        }

        this.src = srcTable;
        this.recordSet = data;
        this.keyColumn = keyColumn;
    }

    @Override
    protected @OnThread(Tag.Any) Stream<TableId> getSourcesFromExpressions()
    {
        return Stream.of(srcTableId);
    }

    @Override
    protected @OnThread(Tag.Any) Stream<TableId> getPrimarySources()
    {
        return Stream.of(srcTableId);
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return NAME;
    }

    @Override
    @OnThread(Tag.Simulation)
    protected synchronized List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        /*
        editKW : {_input.LT(1).getText().equals("EDIT")}? ATOM;
editHeader : editKW (key=item type | NEWLINE);
editColumnKW : {_input.LT(1).getText().equals("EDITCOLUMN")}? ATOM;
editColumnHeader : editColumnKW column=item type;
editColumnDataKW : {_input.LT(1).getText().equals("REPLACEMEMT")}? ATOM;
editColumnData : editColumnDataKW value value;
editColumn : editColumnHeader editColumnData+;
edit : editHeader editColumn*;
         */
        OutputBuilder r = new OutputBuilder();
        r.raw("EDIT");
        DataType keyType;
        if (originalKeyColumn != null)
        {
            r.quote(originalKeyColumn.getFirst());
            r.t(TransformationLexer.TYPE_BEGIN, TransformationLexer.VOCABULARY);
            try
            {
                keyType = originalKeyColumn.getSecond();
                keyType.save(r);
            }
            catch (InternalException e)
            {
                Log.log(e);
                // Bail out completely:
                return ImmutableList.of();
            }
        }
        else
        {
            keyType = DataType.NUMBER;
        }
        r.nl();
        replacements.forEach((c, crv) -> {
            try
            {
                r.raw("EDITCOLUMN");
                r.quote(c);
                r.t(TransformationLexer.TYPE_BEGIN, TransformationLexer.VOCABULARY);
                crv.dataType.save(r);
                r.nl();
            }
            catch (InternalException e)
            {
                Log.log(e);
                r.undoCurLine();
            }
            crv.replacementValues.forEach((k, v) -> {
                try
                {
                    r.raw("REPLACEMENT @VALUE ");
                    r.data(keyType.fromCollapsed((i, prog) -> k.getValue()), 0);
                    r.raw("@ENDVALUE ");
                    v.eitherEx_(err -> {
                        r.raw("@INVALIDVALUE ");
                        r.s(err);
                    }, val -> {
                        r.raw("@VALUE ");
                        r.data(crv.dataType.fromCollapsed((i, prog) -> val.getValue()), 0);
                    });
                    r.raw(" @ENDVALUE");
                    r.nl();
                }
                catch (InternalException | UserException e)
                {
                    Log.log(e);
                    r.undoCurLine();
                }
            });
        });
        
        return r.toLines();
    }

    @Override
    protected synchronized int transformationHashCode()
    {
        return Objects.hash(srcTableId, keyColumn, replacements);
    }

    @Override
    protected synchronized boolean transformationEquals(Transformation obj)
    {
        if (!(obj instanceof ManualEdit))
            return false;
        ManualEdit m = (ManualEdit)obj;
        return Objects.equals(srcTableId, m.srcTableId)
                && Objects.equals(keyColumn, m.keyColumn)
                && Objects.equals(replacements, m.replacements);
    }

    @Override
    public @NonNull @OnThread(Tag.Any) RecordSet getData() throws UserException, InternalException
    {
        return recordSet.eitherEx(e -> {throw new UserException(e);}, rs -> rs);
    }

    @Pure
    @OnThread(Tag.Any)
    public Optional<ColumnId> getReplacementIdentifier()
    {
        return keyColumn == null ? Optional.empty() : Optional.of(keyColumn.getFirst().getName());
    }
    
    @OnThread(Tag.Simulation)
    public synchronized ManualEdit swapReplacementsTo(ImmutableMap<ColumnId, ColumnReplacementValues> newReplacements) throws InternalException
    {
        return new ManualEdit(getManager(), getDetailsForCopy(), getSrcTableId(), keyColumn == null ? null : keyColumn.mapFirst(c -> c.getName()), newReplacements);
    }
    
    @OnThread(Tag.Simulation)
    public synchronized ManualEdit swapReplacementIdentifierTo(@Nullable ColumnId newReplacementKey) throws InternalException, UserException
    {
        if (src == null)
            throw new UserException("Cannot modify manual edit when original table is missing.");
        
        RecordSet srcRecordSet = src.getData();
        int length = srcRecordSet.getLength();
        Column newKeyColumn = newReplacementKey == null ? null : srcRecordSet.getColumn(newReplacementKey);
        
        // Need to go through existing replacements and find the value in the new column:

        Collector<Pair<ComparableValue, Pair<ColumnId, ComparableEither<String, ComparableValue>>>, ?, TreeMultimap<ComparableValue, Pair<ColumnId, ComparableEither<String, ComparableValue>>>> collector = Multimaps.<Pair<ComparableValue, Pair<ColumnId, ComparableEither<String, ComparableValue>>>, ComparableValue, Pair<ColumnId, ComparableEither<String, ComparableValue>>, TreeMultimap<ComparableValue, Pair<ColumnId, ComparableEither<String, ComparableValue>>>>toMultimap(p -> p.getFirst(), p -> p.getSecond(), () -> TreeMultimap.<ComparableValue, Pair<ColumnId, ComparableEither<String, ComparableValue>>>create(Comparator.<ComparableValue>naturalOrder(), Pair.<ColumnId, ComparableEither<String, ComparableValue>>comparator()));
        
        // Each item is (value in identifier column, (target column, value to change to))
        TreeMultimap<ComparableValue, Pair<ColumnId, ComparableEither<String, ComparableValue>>> toFind = replacements.entrySet().stream().<Pair<ComparableValue, Pair<ColumnId, ComparableEither<String, ComparableValue>>>>flatMap(e -> e.getValue().replacementValues.entrySet().stream().<Pair<ComparableValue, Pair<ColumnId, ComparableEither<String, ComparableValue>>>>map(rv -> new Pair<ComparableValue, Pair<ColumnId, ComparableEither<String, ComparableValue>>>(rv.getKey(), new Pair<ColumnId, ComparableEither<String, ComparableValue>>(e.getKey(), rv.getValue())))).collect(collector);

        HashMap<ColumnId, ColumnReplacementValues> newReplacements = new HashMap<>();
        
        for (int row = 0; row < length; row++)
        {
            if (toFind.isEmpty())
                break;
            
            ComparableValue existingKeyValue = keyColumn == null ? new ComparableValue(DataTypeUtility.value(row)) : new ComparableValue(keyColumn.getFirst().getType().getCollapsed(row));
            NavigableSet<Pair<ColumnId, ComparableEither<String, ComparableValue>>> matchingValues = toFind.get(existingKeyValue);
            
            if (!matchingValues.isEmpty())
            {
                for (Pair<ColumnId, ComparableEither<String, ComparableValue>> matchingValue : matchingValues)
                {
                    Column column = src.getData().getColumn(matchingValue.getFirst());
                    DataType columnType = column.getType().getType();
                    ComparableValue newKeyValue = newKeyColumn == null ? new ComparableValue(DataTypeUtility.value(row)) : new ComparableValue(newKeyColumn.getType().getCollapsed(row));
                    newReplacements.computeIfAbsent(matchingValue.getFirst(), k -> new ColumnReplacementValues(columnType, ImmutableList.of())).replacementValues.put(newKeyValue, matchingValue.getSecond());
                }
                matchingValues.clear();
            }
        }
        
        return new ManualEdit(getManager(), getDetailsForCopy(), srcTableId, newKeyColumn == null ? null : new Pair<>(newKeyColumn.getName(), newKeyColumn.getType().getType()), ImmutableMap.copyOf(newReplacements));
    }
    
    @Pure
    @OnThread(Tag.Any)
    public @Nullable Table getSrcTable()
    {
        return src;
    }

    @Pure
    @OnThread(Tag.Any)
    public TableId getSrcTableId()
    {
        return srcTableId;
    }

    public synchronized HashMap<ColumnId, TreeMap<ComparableValue, ComparableEither<String, ComparableValue>>> _test_getReplacements()
    {
        HashMap<ColumnId, TreeMap<ComparableValue, ComparableEither<String, ComparableValue>>> r = new HashMap<>();
        replacements.forEach((c, crv) -> r.put(c, crv.replacementValues));
        return r;
    }

    @OnThread(Tag.Any)
    public synchronized int getReplacementCount()
    {
        return replacements.values().stream().mapToInt(crv -> crv.replacementValues.size()).sum();
    }

    /**
     * Note -- this is an expensive operation, so call sparingly.
     */
    public synchronized ImmutableMap<ColumnId, ColumnReplacementValues> getReplacements()
    {
        ImmutableMap.Builder<ColumnId, ColumnReplacementValues> r = ImmutableMap.builderWithExpectedSize(replacements.size());
        
        replacements.forEach((c, crv) -> {
            ColumnReplacementValues crvCopy = crv.makeCopy();
            r.put(c, crvCopy);
        });
        
        return r.build();
    }

    private class ReplacedColumn extends Column
    {
        private final Column original;
        private @MonotonicNonNull DataTypeValue dataType;

        public ReplacedColumn(RecordSet recordSet, Column original)
        {
            super(recordSet, original.getName());
            this.original = original;
        }

        @Override
        public @OnThread(Tag.Any) EditableStatus getEditableStatus()
        {
            return new EditableStatus(true, rowIndex -> {
                // Check if that row has valid identifier for replacement.
                if (keyColumn == null)
                    return true; // Row number is always valid
                try
                {
                    // If there's an error, an exception will be thrown:
                    keyColumn.getFirst().getType().getCollapsed(rowIndex);
                    return true;
                }
                catch (UserException e)
                {
                    return false;
                }
            });
        }

        @Override
        public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
        {
            if (dataType == null)
            {
                DataTypeValue originalType = original.getType();
                DataTypeValue getType = originalType.getType().fromCollapsed((i, prog) -> {
                    ColumnReplacementValues columnReplacements = replacements.get(getName());
                    @Nullable ComparableEither<String, ComparableValue> replaced = null;
                    try
                    {
                        @Value Object replaceKey = keyColumn == null ? DataTypeUtility.value(i) : keyColumn.getFirst().getType().getCollapsed(i);
                        replaced = columnReplacements == null ? null : columnReplacements.replacementValues.get(new ComparableValue(replaceKey));
                    }
                    catch (UserException e)
                    {
                        // If error in fetching key, don't replace.
                    }
                    if (replaced != null)
                        return replaced.<@Value Object>eitherEx(err -> {throw new InvalidImmediateValueException(StyledString.s(err), err);}, v -> v.getValue());
                    else
                        return originalType.getCollapsed(i);
                });
                
                dataType = getType.withSet((index, value) -> {
                    ColumnReplacementValues columnReplacements = replacements.computeIfAbsent(getName(), k -> new ColumnReplacementValues(getType.getType(), ImmutableList.of()));
                    FXUtility.alertOnError_("Error looking up identifier value to store edit against", () ->
                    columnReplacements.replacementValues.put(
                        getReplacementKeyForRow(index),
                        value.<ComparableEither<String, ComparableValue>>either(err -> ComparableEither.<String, ComparableValue>left(err), v -> ComparableEither.<String, ComparableValue>right(new ComparableValue(v)))
                    ));
                    // Notify dependents:
                    recordSet.modified(getName(), index);
                });
                        
                        /*
                        original.getType().applyGet(new DataTypeVisitorGetEx<DataTypeValue, InternalException>()
                {
                    @Override
                    public DataTypeValue number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException
                    {
                        return DataTypeValue.number(displayInfo, replace(g, Number.class));
                    }

                    @Override
                    public DataTypeValue text(GetValue<@Value String> g) throws InternalException
                    {
                        return DataTypeValue.text(replace(g, String.class));
                    }

                    @Override
                    public DataTypeValue bool(GetValue<@Value Boolean> g) throws InternalException
                    {
                        return DataTypeValue.bool(replace(g, Boolean.class));
                    }

                    @Override
                    public DataTypeValue date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException
                    {
                        return DataTypeValue.date(dateTimeInfo, replace(g, TemporalAccessor.class));
                    }

                    @Override
                    public DataTypeValue tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException
                    {
                        return null;
                    }

                    @Override
                    public DataTypeValue tuple(ImmutableList<DataTypeValue> types) throws InternalException
                    {
                        return DataTypeValue.tupleV(Utility.mapList_Index(types, (Integer i, DataTypeValue t) -> replace((ind, prog) -> t.getCollapsed(ind), tuple -> Utility.castTuple(tuple, types.size())[i])));
                    }

                    @Override
                    public DataTypeValue array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException
                    {
                        if (inner == null)
                            return DataTypeValue.array();
                            
                        return DataTypeValue.array(inner, replace(g, obj -> {
                            @Value ListEx list = Utility.cast(obj, ListEx.class);
                            return new Pair<>(list.size(), inner.fromCollapsed((i, prog) -> list.get(i)));
                        }));
                    }

                    private <T> GetValue<@Value T> replace(GetValue<@Value T> original, Class<T> theClass)
                    {
                        return replace(original, v -> Utility.cast(v, theClass));
                    }

                    private <T> GetValue<@Value T> replace(GetValue<@Value T> original, ExFunction<@Value Object, @Value T> transformReplacement)
                    {
                        TreeMap<@Value Object, @Value Object> columnReplacements = replacements.get(getName());
                        if (columnReplacements == null)
                        {
                            return original;
                        }
                        else
                        {
                            return new GetValue<@Value T>()
                            {
                                @NonNull
                                @Override
                                public @Value T getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
                                {
                                    @Value Object replaced = columnReplacements.get(index);
                                    if (replaced != null)
                                        return transformReplacement.apply(replaced);
                                    return original.getWithProgress(index, progressListener);
                                }
                                
                                // TODO override set
                            };
                        }
                    }
                });
                */
            }
            return dataType;
        }

        @Override
        public @OnThread(Tag.Any) boolean isAltered()
        {
            return replacements.containsKey(getName());
        }
    }

    private ComparableValue getReplacementKeyForRow(int index) throws InternalException, UserException
    {
        if (keyColumn == null)
            return new ComparableValue(DataTypeUtility.value(index));
        return new ComparableValue(keyColumn.getFirst().getType().getCollapsed(index));
    }

    @Override
    @OnThread(Tag.Simulation)
    public synchronized Transformation withNewSource(TableId newSrcTableId) throws InternalException
    {
        return new ManualEdit(getManager(), getDetailsForCopy(), newSrcTableId, keyColumn == null ? null : keyColumn.mapFirst(c -> c.getName()), ImmutableMap.copyOf(replacements));
    }

    @OnThread(Tag.Simulation)
    public static class ColumnReplacementValues
    {
        // Must be exactly equal to the source column's type
        private final DataType dataType;
        
        // The key is either a value in ManualEdit.this.replacementKey column
        // or if that is null, it's a row number.
        // Keys must be comparable for TreeMap, values are comparable to
        // help with equals definition.
        private final TreeMap<ComparableValue, ComparableEither<String, ComparableValue>> replacementValues;

        public ColumnReplacementValues(DataType dataType, List<Pair<@Value Object, Either<String, @Value Object>>> replacementValues)
        {
            this.dataType = dataType;
            this.replacementValues = new TreeMap<>();
            for (Pair<@Value Object, Either<String, @Value Object>> replacementValue : replacementValues)
            {
                this.replacementValues.put(new ComparableValue(replacementValue.getFirst()), replacementValue.getSecond().<ComparableEither<String, ComparableValue>>either(l -> ComparableEither.<String, ComparableValue>left(l), r -> ComparableEither.<String, ComparableValue>right(new ComparableValue(r))));
            }
        }

        @Override
        public int hashCode()
        {
            // We don't hash the map because that is unreliable,
            // so we restrict ourselves and accept hash collisions:
            return Objects.hash(dataType);
        }

        @Override
        @OnThread(value = Tag.Simulation, ignoreParent = true)
        public boolean equals(@Nullable Object obj)
        {
            if (!(obj instanceof ColumnReplacementValues))
                return false;
            ColumnReplacementValues crv = (ColumnReplacementValues) obj;
            return Objects.equals(dataType, crv.dataType) && Objects.equals(replacementValues, crv.replacementValues);
        }

        public ColumnReplacementValues makeCopy()
        {
            return new ColumnReplacementValues(dataType, replacementValues.entrySet().stream().<Pair<@Value Object, Either<String, @Value Object>>>map(e -> new Pair<@Value Object, Either<String, @Value Object>>(e.getKey().getValue(), e.getValue().<@Value Object>map(v -> v.getValue()))).collect(ImmutableList.<Pair<@Value Object, Either<String, @Value Object>>>toImmutableList()));
        }

        public Stream<Pair<ComparableValue, ComparableEither<String, ComparableValue>>> streamAll()
        {
            return replacementValues.entrySet().stream().map(e -> new Pair<>(e.getKey(), e.getValue()));
        }
    }

    public static class Info extends SingleSourceTransformationInfo
    {
        public Info()
        {
            super(NAME, "transform.edit", "preview-edit.png", "edit.explanation.short", ImmutableList.of());
        }
        
        @Override
        public @OnThread(Tag.Simulation) Transformation loadSingle(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, String detail) throws InternalException, UserException
        {
            EditContext editContext = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, p -> p.edit());
            @Nullable Pair<ColumnId, DataType> replacementKey;
            if (editContext.editHeader().key != null)
            {
                replacementKey = new Pair<>(new ColumnId(editContext.editHeader().key.getText()), mgr.getTypeManager().loadTypeUse(editContext.editHeader().type().TYPE().getText()));
            }
            else
            {
                replacementKey = null;
            }
            
            HashMap<ColumnId, ColumnReplacementValues> replacements = new HashMap<>();
            for (EditColumnContext editColumnContext : editContext.editColumn())
            {
                ColumnId columnId = new ColumnId(editColumnContext.editColumnHeader().column.getText());
                DataType dataType = mgr.getTypeManager().loadTypeUse(editColumnContext.editColumnHeader().type().TYPE().getText());
                List<Pair<@Value Object, Either<String, @Value Object>>> replacementValues = new ArrayList<>();
                for (EditColumnDataContext editColumnDatum : editColumnContext.editColumnData())
                {
                    @Value Object key = Utility.<Either<String, @Value Object>, DataParser>parseAsOne(editColumnDatum.value(0).VALUE().getText().trim(), DataLexer::new, DataParser::new, p -> DataType.loadSingleItem(replacementKey == null ? DataType.NUMBER : replacementKey.getSecond(), p, false)).<@Value Object>eitherEx(s -> {throw new UserException(s);}, x -> x);
                    ValueContext dataValue = editColumnDatum.value(1);
                    Either<String, @Value Object> value = Utility.<Either<String, @Value Object>, DataParser>parseAsOne(dataValue.VALUE().getText().trim(), DataLexer::new, DataParser::new, p -> dataValue.INVALID_VALUE_BEGIN() != null ? Either.<String, @Value Object>left(p.string().STRING().getText()) : DataType.loadSingleItem(dataType, p, false));
                    replacementValues.add(new Pair<>(key, value));
                }
                
                replacements.put(columnId, new ColumnReplacementValues(dataType, replacementValues));
            }
            
            return new ManualEdit(mgr, initialLoadDetails, srcTableId, replacementKey, ImmutableMap.copyOf(replacements));
        }

        @Override
        protected @OnThread(Tag.Simulation) Transformation makeWithSource(View view, TableManager mgr, CellPosition destination, Table srcTable) throws InternalException
        {
            return new ManualEdit(mgr, new InitialLoadDetails(null, destination, null), srcTable.getId(), null, ImmutableMap.of());
        }
    }
}
