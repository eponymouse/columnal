package records.rinterop;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Ints;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.*;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TaggedTypeDefinition.TaggedInstantiationException;
import records.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import records.jellytype.JellyType.UnknownTypeException;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListExList;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class ConvertFromR
{

    private static ColumnId getColumnName(@Nullable RValue listColumnNames, int index) throws UserException, InternalException
    {
        @SuppressWarnings("identifier")
        @ExpressionIdentifier String def = "Column " + index;
        if (listColumnNames != null)
        {
            @Value String s = RUtility.getString(RUtility.getListItem(listColumnNames, index));
            return new ColumnId(IdentifierUtility.fixExpressionIdentifier(s == null ? "" : s, def));
        }
        return new ColumnId(def);
    }

    /**
     * The returned type is the type of each list element (i.e. is not [necessarily] an array type).
     */
    public static Pair<DataType, ImmutableList<@Value Object>> convertRToTypedValueList(TypeManager typeManager, RValue rValue) throws InternalException, UserException
    {
        return rValue.visit(new RVisitor<Pair<DataType, ImmutableList<@Value Object>>>()
        {
            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitNil() throws InternalException, UserException
            {
                throw new UserException("Cannot turn nil into value");
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
            {
                if (s != null)
                    return new Pair<>(DataType.TEXT, ImmutableList.<@Value Object>of(DataTypeUtility.value(s)));
                else
                    return new Pair<>(typeManager.getMaybeType().instantiate(ImmutableList.<Either<Unit, DataType>>of(Either.<Unit, DataType>right(DataType.TEXT)), typeManager), ImmutableList.<@Value Object>of(typeManager.maybeMissing()));
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException
            {
                return new Pair<>(DataType.BOOLEAN, Utility.<@ImmediateValue Boolean, @Value Object>mapListI(Booleans.asList(values), b -> DataTypeUtility.value(b)));
            }

            @Override
            public Pair<DataType,ImmutableList<@Value Object>> visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return new Pair<>(DataType.NUMBER, Utility.<@ImmediateValue Integer, @Value Object>mapListI(Ints.asList(values), i -> DataTypeUtility.value(i)));
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return new Pair<>(DataType.NUMBER, DoubleStream.of(values).<@ImmediateValue Object>mapToObj(d -> doubleToValue(d)).collect(ImmutableList.<@Value @NonNull Object>toImmutableList()));
            }

            @Override
            @SuppressWarnings("optional")
            public Pair<DataType, ImmutableList<@Value Object>> visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (values.stream().allMatch(v -> v.isPresent()))
                {
                    return new Pair<>(DataType.TEXT, Utility.<Optional<@Value String>, @Value Object>mapListI(values, v -> v.get()));
                }
                else 
                {
                    return new Pair<>(typeManager.getMaybeType().instantiate(ImmutableList.<Either<Unit, DataType>>of(Either.<Unit, DataType>right(DataType.TEXT)), typeManager), Utility.<Optional<@Value String>, @Value Object>mapListI(values, v -> v.<@Value Object>map(s -> typeManager.maybePresent(s)).orElseGet(typeManager::maybeMissing)));
                }
            }

            @SuppressWarnings("optional")
            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitTemporalList(DateTimeType dateTimeType, ImmutableList<Optional<@Value TemporalAccessor>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                DateTimeInfo t = new DateTimeInfo(dateTimeType);
                if (values.stream().allMatch(v -> v.isPresent()))
                {
                    return new Pair<>(DataType.date(t), Utility.<Optional<@Value TemporalAccessor>, @Value Object>mapListI(values, v -> v.get()));
                }
                else
                {
                    return new Pair<>(typeManager.getMaybeType().instantiate(ImmutableList.<Either<Unit, DataType>>of(Either.<Unit, DataType>right(DataType.date(t))), typeManager), Utility.<Optional<@Value TemporalAccessor>, @Value Object>mapListI(values, v -> v.map(typeManager::maybePresent).orElseGet(typeManager::maybeMissing)));
                }
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
            {
                Pair<DataType, ImmutableList<@Value Object>> inner = rListToValueList(typeManager, values);
                return new Pair<>(inner.getFirst(), inner.getSecond());
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                throw new UserException("List found when single value expected: " + RPrettyPrint.prettyPrint(rValue));
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                TaggedTypeDefinition taggedTypeDefinition = getTaggedTypeForFactors(levelNames, typeManager);
                return new Pair<>(taggedTypeDefinition.instantiate(ImmutableList.of(), typeManager), IntStream.of(values).mapToObj(n -> new TaggedValue(n - 1, null, taggedTypeDefinition)).collect(ImmutableList.<@Value Object>toImmutableList()));
            }
        });
    }

    private static TaggedTypeDefinition getTaggedTypeForFactors(ImmutableList<String> levelNames, TypeManager typeManager) throws InternalException, UserException
    {
        ImmutableList<@ExpressionIdentifier String> processedNames = Streams.<String, @ExpressionIdentifier String>mapWithIndex(levelNames.stream(), (s, i) -> IdentifierUtility.fixExpressionIdentifier(s, IdentifierUtility.identNum("Factor", (int) i))).collect(ImmutableList.<@ExpressionIdentifier String>toImmutableList());

        ImmutableSet<@ExpressionIdentifier String> namesAsSet = ImmutableSet.copyOf(processedNames);
        TaggedTypeDefinition existing = typeManager.getKnownTaggedTypes().values().stream().filter(ttd ->
                ttd.getTags().stream().<@ExpressionIdentifier String>map(tt -> tt.getName()).collect(ImmutableSet.<@ExpressionIdentifier String>toImmutableSet()).equals(namesAsSet)
        ).findFirst().orElse(null);
        if (existing != null)
            return existing;
        
        for (int i = 0; i < 100; i++)
        {
            @SuppressWarnings("identifier")
            @ExpressionIdentifier String hint = "F " + i;
            @ExpressionIdentifier String typeName = IdentifierUtility.fixExpressionIdentifier(levelNames.stream().sorted().findFirst().orElse("F") + " " + levelNames.size(), hint);
            TaggedTypeDefinition taggedTypeDefinition = typeManager.registerTaggedType(typeName, ImmutableList.<Pair<TypeVariableKind, @ExpressionIdentifier String>>of(), levelNames.stream().map(s -> new TagType<JellyType>(IdentifierUtility.fixExpressionIdentifier(s, "Factor"), null)).collect(ImmutableList.<TagType<JellyType>>toImmutableList()));
            if (taggedTypeDefinition != null)
                return taggedTypeDefinition;
        }
        throw new UserException("Type named F1 through F100 already exists but with different tags");
    }

    public static Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> convertRToColumn(TypeManager typeManager, RValue rValue, ColumnId columnName) throws UserException, InternalException
    {
        return rValue.visit(new RVisitor<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>>()
        {
            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitNil() throws InternalException, UserException
            {
                throw new UserException("Cannot make column from nil value");
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
            {
                return visitStringList(ImmutableList.<Optional<@Value String>>of(Optional.<@Value String>ofNullable(s)), null);
            }

            @SuppressWarnings("optional")
            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (values.stream().allMatch(v -> v.isPresent()))
                    return new Pair<>(rs -> new MemoryStringColumn(rs, columnName, Utility.mapList(values, v -> Either.<String, String>right(v.get())), ""), values.size());
                else
                    return makeMaybeColumn(DataType.TEXT, Utility.mapListI(values, v -> Either.<String, TaggedValue>right(v.map(typeManager::maybePresent).orElseGet(typeManager::maybeMissing))));
            }

            @SuppressWarnings("optional")
            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitTemporalList(DateTimeType dateTimeType, ImmutableList<Optional<@Value TemporalAccessor>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                DateTimeInfo t = new DateTimeInfo(dateTimeType);
                if (values.stream().allMatch(v -> v.isPresent()))
                    return new Pair<>(rs -> new MemoryTemporalColumn(rs, columnName, t, Utility.mapListI(values, v -> Either.<String, TemporalAccessor>right(v.get())), t.getDefaultValue()), values.size());
                else
                    return makeMaybeColumn(DataType.date(t), Utility.mapListI(values, v -> Either.<String, TaggedValue>right(v.map(typeManager::maybePresent).orElseGet(typeManager::maybeMissing))));
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (isNA == null)
                    return new Pair<>(rs -> new MemoryBooleanColumn(rs, columnName, Booleans.asList(values).stream().map(n -> Either.<String, Boolean>right(n)).collect(ImmutableList.<Either<String, Boolean>>toImmutableList()), false), values.length);
                else
                {
                    ImmutableList.Builder<Either<String, @Value TaggedValue>> maybeValues = ImmutableList.builderWithExpectedSize(values.length);
                    for (int i = 0; i < values.length; i++)
                    {
                        if (isNA[i])
                            maybeValues.add(Either.right(typeManager.maybeMissing()));
                        else
                            maybeValues.add(Either.right(typeManager.maybePresent(DataTypeUtility.value(values[i]))));
                    }

                    return makeMaybeColumn(DataType.BOOLEAN, maybeValues.build());
                }
            }

            private Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> makeMaybeColumn(DataType inner, ImmutableList<Either<String, @Value TaggedValue>> maybeValues) throws TaggedInstantiationException, InternalException, UnknownTypeException
            {
                ImmutableList<Either<Unit, DataType>> typeVar = ImmutableList.of(Either.<Unit, DataType>right(inner));
                DataType maybeDataType = typeManager.getMaybeType().instantiate(typeVar, typeManager);
                return new Pair<>(rs -> new MemoryTaggedColumn(rs, columnName, typeManager.getMaybeType().getTaggedTypeName(), typeVar, maybeDataType.apply(new SpecificDataTypeVisitor<ImmutableList<TagType<DataType>>>() {
                    @Override
                    public ImmutableList<TagType<DataType>> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
                    {
                        return tags;
                    }
                }), maybeValues, typeManager.maybeMissing()), maybeValues.size());
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return new Pair<>(rs -> new MemoryNumericColumn(rs, columnName, NumberInfo.DEFAULT, IntStream.of(values).mapToObj(n -> Either.<String, Number>right(n)).collect(ImmutableList.<Either<String, Number>>toImmutableList()), DataTypeUtility.value(0)), values.length);
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                boolean hasNaNs = false;
                for (int i = 0; i < values.length; i++)
                {
                    if (Double.isNaN(values[i]))
                    {
                        hasNaNs = true;
                        break;
                    }
                }
                if (hasNaNs)
                {
                    ImmutableList.Builder<Either<String, @Value TaggedValue>> maybeValues = ImmutableList.builderWithExpectedSize(values.length);
                    for (int i = 0; i < values.length; i++)
                    {
                        if (Double.isNaN(values[i]))
                            maybeValues.add(Either.right(typeManager.maybeMissing()));
                        else
                            maybeValues.add(Either.right(typeManager.maybePresent(doubleToValue(values[i]))));
                    }

                    return makeMaybeColumn(DataType.NUMBER, maybeValues.build());
                }
                else
                {
                    return new Pair<>(rs -> new MemoryNumericColumn(rs, columnName, NumberInfo.DEFAULT, DoubleStream.of(values).mapToObj(n -> {
                        try
                        {
                            return Either.<String, Number>right(doubleToValue(n));
                        }
                        catch (NumberFormatException e)
                        {
                            return Either.<String, Number>left(Double.toString(n));
                        }
                    }).collect(ImmutableList.<Either<String, Number>>toImmutableList()), DataTypeUtility.value(0)), values.length);
                }
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                boolean hasNAs = false;
                for (int value : values)
                {
                    if (value == RUtility.NA_AS_INTEGER)
                    {
                        hasNAs = true;
                        break;
                    }
                }

                TaggedTypeDefinition taggedTypeDefinition = getTaggedTypeForFactors(levelNames, typeManager);
                ImmutableList.Builder<Either<String, TaggedValue>> factorValueBuilder = ImmutableList.builderWithExpectedSize(values.length);
                for (int n : values)
                {
                    if (hasNAs)
                    {
                        factorValueBuilder.add(Either.<String, TaggedValue>right(n == RUtility.NA_AS_INTEGER ? typeManager.maybeMissing() : typeManager.maybePresent(new TaggedValue(lookupTag(n, levelNames, taggedTypeDefinition), null, taggedTypeDefinition))));
                    }
                    else
                    {
                        factorValueBuilder.add(Either.<String, TaggedValue>right(new TaggedValue(lookupTag(n, levelNames, taggedTypeDefinition), null, taggedTypeDefinition)));
                    }
                }
                ImmutableList<Either<String, TaggedValue>> factorValues = factorValueBuilder.build();
                
                if (hasNAs)
                {
                    
                    return makeMaybeColumn(taggedTypeDefinition.instantiate(ImmutableList.of(), typeManager), factorValues);
                }
                else
                {
                    return new Pair<>(rs -> {
                        return new MemoryTaggedColumn(rs, columnName, taggedTypeDefinition.getTaggedTypeName(), ImmutableList.of(), Utility.mapList(taggedTypeDefinition.getTags(), t -> new TagType<>(t.getName(), null)), factorValues, new TaggedValue(0, null, taggedTypeDefinition));
                    }, values.length);
                }
            }

            private int lookupTag(int tagIndex, ImmutableList<String> levelNames, TaggedTypeDefinition taggedTypeDefinition) throws UserException
            {
                if (tagIndex > levelNames.size())
                    throw new UserException("Factor index does not have name");
                // Map one-based back to zero-based:
                @SuppressWarnings("identifier")
                String name = IdentifierUtility.fixExpressionIdentifier(levelNames.get(tagIndex - 1), levelNames.get(tagIndex - 1));
                return Utility.findFirstIndex(taggedTypeDefinition.getTags(), tt -> tt.getName().equals(name)).orElseThrow(() -> new UserException("Could not find tag named " + name + " in definition"));
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
            {
                Pair<DataType, ImmutableList<@Value Object>> loaded = rListToValueList(typeManager, values);
                return new Pair<>(loaded.getFirst().makeImmediateColumn(columnName, Utility.<@Value Object, Either<String, @Value Object>>mapListExI(loaded.getSecond(), v -> Either.<String, @Value Object>right(v)), DataTypeUtility.makeDefaultValue(loaded.getFirst())), loaded.getSecond().size());
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                // For some reason, factor columns appear as pair list of two with an int[] as second item:
                if (items.size() == 2)
                {
                    RVisitor<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>> outer = this;
                    @Nullable Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> asFactors = items.get(0).item.visit(new DefaultRVisitor<@Nullable Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>>(null)
                    {
                        @Override
                        public @Nullable Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
                        {
                            return outer.visitFactorList(values, levelNames);
                        }
                    });
                    if (asFactors != null)
                        return asFactors;
                }
                
                throw new UserException("Pair list found when column expected: " + RPrettyPrint.prettyPrint(rValue));
            }
        });
    }

    private static @ImmediateValue BigDecimal doubleToValue(double value)
    {
        // Go through Double.toString which zeroes out the boring end part:
        return DataTypeUtility.value(new BigDecimal(Double.toString(value)));
    }

    private static SimulationFunction<@Value Object, @Value Object> getOrInternal(ImmutableMap<DataType, SimulationFunction<@Value Object, @Value Object>> map, DataType key) throws InternalException
    {
        SimulationFunction<@Value Object, @Value Object> f = map.get(key);
        if (f == null)
            throw new InternalException("No conversion found for type " + key);
        return f;
    }
    
    // The DataType is the type of the list *elements*, not the list as a whole.
    // e.g. the return may be (Number, [1,2,3])
    private static Pair<DataType, ImmutableList<@Value Object>> rListToValueList(TypeManager typeManager, List<RValue> values) throws UserException, InternalException
    {
        // The type here will be the type of the object on the right
        ImmutableList<Pair<DataType, @Value Object>> typedPairs = Utility.<RValue, Pair<DataType, @Value Object>>mapListExI(values, v -> {
            Pair<DataType, ImmutableList<@Value Object>> r = convertRToTypedValueList(typeManager, v);
            if (r.getSecond().size() == 1)
                return r.replaceSecond(r.getSecond().get(0));
            else
                return new Pair<>(DataType.array(r.getFirst()), DataTypeUtility.value(r.getSecond()));
        });
        Pair<DataType, ImmutableMap<DataType, SimulationFunction<@Value Object, @Value Object>>> m = generaliseType(Utility.mapListExI(typedPairs, p -> p.getFirst()));
        ImmutableList<@Value Object> loaded = Utility.<Pair<DataType, @Value Object>, @Value Object>mapListExI(typedPairs, p -> getOrInternal(m.getSecond(), p.getFirst()).apply(p.getSecond()));
        return new Pair<DataType, ImmutableList<@Value Object>>(m.getFirst(), loaded);
    }
    
    private static Pair<DataType, ImmutableMap<DataType, SimulationFunction<@Value Object, @Value Object>>> generaliseType(ImmutableList<DataType> types) throws UserException
    {
        if (types.isEmpty())
            return new Pair<>(DataType.TEXT, ImmutableMap.<DataType, SimulationFunction<@Value Object, @Value Object>>of());
        DataType curType = types.get(0);
        HashMap<DataType, SimulationFunction<@Value Object, @Value Object>> conversions = new HashMap<>();
        conversions.put(curType, x -> x);
        for (DataType nextType : types.subList(1, types.size()))
        {
            if (nextType.equals(curType))
                continue;
            if (DataTypeUtility.isNumber(nextType) && DataTypeUtility.isNumber(curType))
            {
                conversions.put(nextType, x -> x);
                continue;
            }
            if (nextType.equals(DataType.array(curType)))
            {
                conversions.put(curType, x -> DataTypeUtility.value(ImmutableList.<@Value Object>of(x)));
                conversions.put(nextType, x -> x);
                curType = nextType;
                continue;
            }
            if (curType.equals(DataType.array(nextType)))
            {
                conversions.put(nextType, x -> DataTypeUtility.value(ImmutableList.<@Value Object>of(x)));
                continue;
            }
            throw new UserException("Cannot generalise " + curType + " and " + nextType + " into a single type");
        }
        return new Pair<>(curType, ImmutableMap.copyOf(conversions));
    }
    
    public static ImmutableList<Pair<String, EditableRecordSet>> convertRToTable(TypeManager typeManager, RValue rValue) throws UserException, InternalException
    {
        // R tables are usually a list of columns, which suits us:
        return rValue.visit(new RVisitor<ImmutableList<Pair<String, EditableRecordSet>>>()
        {
            private ImmutableList<Pair<String, EditableRecordSet>> singleColumn() throws UserException, InternalException
            {
                Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> p = convertRToColumn(typeManager, rValue, new ColumnId("Result"));
                return ImmutableList.<Pair<String, EditableRecordSet>>of(new Pair<>("Value", new <EditableColumn>EditableRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(p.getFirst()), () -> p.getSecond())));
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitNil() throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitTemporalList(DateTimeType dateTimeType, ImmutableList<Optional<@Value TemporalAccessor>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                ImmutableList.Builder<Pair<String, EditableRecordSet>> r = ImmutableList.builder();
                for (PairListEntry item : items)
                {
                    ImmutableList<Pair<String, EditableRecordSet>> found = convertRToTable(typeManager, item.item);
                    if (item.tag != null && found.size() == 1)
                    {
                        String name = RUtility.getString(item.tag);
                        if (name != null)
                        {
                            // Skip the random seed; very unlikely they want that as a table:
                            if (name.equals(".Random.seed"))
                                continue;
                            
                            found = ImmutableList.<Pair<String, EditableRecordSet>>of(found.get(0).<String>replaceFirst(name));
                        }
                    }
                    r.addAll(found);
                }
                return r.build();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
            {
                // Tricky; could be a list of tables, a list of columns or a list of values!
                // First try as table (list of columns):
                final ImmutableMap<String, RValue> attrMap = RUtility.pairListToMap(attributes);

                boolean isDataFrame = isObject && isDataFrameOrTibble(attrMap);
                if (isDataFrame)
                {
                    ImmutableList<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>> columns = Utility.mapListExI_Index(values, (i, v) -> convertRToColumn(typeManager, v, getColumnName(attrMap.get("names"), i)));
                    // Check columns are all the same length:
                    if (!columns.isEmpty() && columns.stream().mapToInt(p -> p.getSecond()).distinct().count() == 1)
                    {
                        return ImmutableList.of(new Pair<>("Table", new <EditableColumn>EditableRecordSet(Utility.<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>, SimulationFunction<RecordSet, EditableColumn>>mapList(columns, p -> p.getFirst()), () -> columns.get(0).getSecond())));
                    }
                    throw new UserException("Columns are of differing lengths: " + columns.stream().map(p -> "" + p.getSecond()).collect(Collectors.joining(", ")));
                }
                else
                {
                    boolean hasDataFrames = false;
                    for (RValue value : values)
                    {
                        boolean valueIsDataFrame = value.visit(new DefaultRVisitor<Boolean>(false)
                        {
                            @Override
                            public Boolean visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
                            {
                                final ImmutableMap<String, RValue> valueAttrMap = RUtility.pairListToMap(attributes);
                                return isObject && isDataFrameOrTibble(valueAttrMap);
                            }
                        });
                        
                        if (valueIsDataFrame)
                        {
                            hasDataFrames = true;
                            break;
                        }
                    }
                    if (!hasDataFrames)
                        return singleColumn();
                    
                    ImmutableList.Builder<Pair<String, EditableRecordSet>> r = ImmutableList.builder();
                    for (RValue value : values)
                    {
                        r.addAll(convertRToTable(typeManager, value));
                    }
                    return r.build();
                }
            }
        });
    }

    private static boolean isDataFrameOrTibble(ImmutableMap<String, RValue> attrMap) throws InternalException, UserException
    {
        return RUtility.isClass(attrMap, RUtility.CLASS_DATA_FRAME) || RUtility.isClass(attrMap, RUtility.CLASS_TIBBLE);
    }

    public static enum TableType { DATA_FRAME, TIBBLE }

    public static String usToRTable(TableId tableId)
    {
        return tableId.getRaw().replace(" ", ".");
    }

}
