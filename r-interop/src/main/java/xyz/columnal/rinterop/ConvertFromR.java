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

package xyz.columnal.rinterop;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Ints;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.ColumnUtility;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataType.FlatDataTypeVisitor;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TaggedInstantiationException;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableId;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.jellytype.JellyType.UnknownTypeException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.Record;
import xyz.columnal.utility.Utility.RecordMap;

import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

public class ConvertFromR
{
    private static ColumnId getColumnName(@Nullable RValue listColumnNames, int index) throws UserException, InternalException
    {
        @ExpressionIdentifier String def = IdentifierUtility.identNum("Column", index);
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
                return visitStringList(ImmutableList.<Optional<@Value String>>of(Optional.<@Value String>ofNullable(s)), null);
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (isNA == null)
                    return new Pair<>(DataType.BOOLEAN, Utility.<@ImmediateValue Boolean, @Value Object>mapListI(Booleans.asList(values), b -> DataTypeUtility.value(b)));

                ImmutableList.Builder<@Value Object> maybeValues = ImmutableList.builderWithExpectedSize(values.length);
                for (int i = 0; i < values.length; i++)
                {
                    if (isNA[i])
                        maybeValues.add(typeManager.maybeMissing());
                    else
                        maybeValues.add(typeManager.maybePresent(DataTypeUtility.value(values[i])));
                }
                return new Pair<>(typeManager.makeMaybeType(DataType.BOOLEAN), maybeValues.build());
            }

            @Override
            public Pair<DataType,ImmutableList<@Value Object>> visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                // int[] doesn't use NA; always doubles if has NA
                return new Pair<>(DataType.NUMBER, Utility.<@ImmediateValue Integer, @Value Object>mapListI(Ints.asList(values), i -> DataTypeUtility.value(i)));
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
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
                    ImmutableList.Builder<@Value Object> maybeValues = ImmutableList.builderWithExpectedSize(values.length);
                    for (int i = 0; i < values.length; i++)
                    {
                        if (Double.isNaN(values[i]))
                            maybeValues.add(typeManager.maybeMissing());
                        else
                            maybeValues.add(typeManager.maybePresent(RUtility.doubleToValue(values[i])));
                    }

                    return new Pair<>(typeManager.makeMaybeType(DataType.NUMBER), maybeValues.build());
                }
                else
                {
                    return new Pair<>(DataType.NUMBER, DoubleStream.of(values).<@ImmediateValue Object>mapToObj(d -> RUtility.doubleToValue(d)).collect(ImmutableList.<@Value @NonNull Object>toImmutableList()));
                }
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
                    return new Pair<>(typeManager.makeMaybeType(DataType.TEXT), Utility.<Optional<@Value String>, @Value Object>mapListI(values, v -> v.<@Value Object>map(s -> typeManager.maybePresent(s)).orElseGet(typeManager::maybeMissing)));
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
                    return new Pair<>(typeManager.makeMaybeType(DataType.date(t)), Utility.<Optional<@Value TemporalAccessor>, @Value Object>mapListI(values, v -> v.map(typeManager::maybePresent).orElseGet(typeManager::maybeMissing)));
                }
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
            {
                if (attributes != null)
                {
                    ImmutableMap<String, RValue> attrMap = RUtility.pairListToMap(attributes);
                    RValue namesRValue = attrMap.get("names");
                    if (namesRValue != null)
                    {
                        ImmutableList<Optional<@Value String>> names = namesRValue.visit(new SpecificRVisitor<ImmutableList<Optional<@Value String>>>()
                        {
                            @Override
                            public ImmutableList<Optional<@Value String>> visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
                            {
                                return values;
                            }
                        });
                        if (names.size() != values.size())
                        {
                            throw new UserException("List has named values, but names not same length as items");
                        }

                        ImmutableList.Builder<Pair<String, RValue>> paired = ImmutableList.builder();
                        for (int i = 0; i < names.size(); i++)
                        {
                            paired.add(new Pair<String, RValue>(names.get(i).orElse(DataTypeUtility.value("Unnamed field " + i)), values.get(i)));
                        }

                        Pair<DataType, @Value Object> fieldTypesAndValue = record(typeManager, paired.build());
                        return new Pair<>(fieldTypesAndValue.getFirst(), ImmutableList.<@Value Object>of(fieldTypesAndValue.getSecond()));
                    }
                }
                return rListToValueList(typeManager, values);
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                // For some reason, factor columns appear as pair list of two with an int[] as second item:
                if (items.size() == 2)
                {
                    RVisitor<Pair<DataType, ImmutableList<@Value Object>>> outer = this;
                    @Nullable Pair<DataType, ImmutableList<@Value Object>> asFactors = items.get(0).item.visit(new DefaultRVisitor<@Nullable Pair<DataType, ImmutableList<@Value Object>>>(null)
                    {
                        @Override
                        public @Nullable Pair<DataType, ImmutableList<@Value Object>> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
                        {
                            return outer.visitFactorList(values, levelNames);
                        }
                    });
                    if (asFactors != null)
                        return asFactors;
                }

                ImmutableList<Pair<String, RValue>> pairs = Utility.mapListExI(items, e -> {
                    @Value String name = null;
                    if (e.tag != null)
                    {
                        name = RUtility.getString(e.tag);
                    }
                    if (name == null)
                        name = DataTypeUtility.value("");
                    return new Pair<>(name, e.item);
                });

                Pair<DataType, @Value Object> record = record(typeManager, pairs);
                return new Pair<DataType, ImmutableList<@Value Object>>(record.getFirst(), ImmutableList.<@Value Object>of(record.getSecond()));
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
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
                ImmutableList.Builder<@Value Object> factorValueBuilder = ImmutableList.builderWithExpectedSize(values.length);
                for (int n : values)
                {
                    if (hasNAs && n == RUtility.NA_AS_INTEGER)
                    {
                        factorValueBuilder.add(typeManager.maybeMissing());
                    }
                    else
                    {
                        @Value TaggedValue taggedValue = new TaggedValue(lookupTag(n, levelNames, taggedTypeDefinition), null, taggedTypeDefinition);
                        factorValueBuilder.add(hasNAs ? typeManager.maybePresent(taggedValue) : taggedValue);
                    }
                }
                ImmutableList<@Value Object> factorValues = factorValueBuilder.build();

                DataType taggedDataType = taggedTypeDefinition.instantiate(ImmutableList.of(), typeManager);
                return new Pair<>(hasNAs ? typeManager.makeMaybeType(taggedDataType) : taggedDataType, factorValues);
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
        Pair<DataType, ImmutableList<@Value Object>> converted = convertRToTypedValueList(typeManager, rValue);
        return new Pair<>(ColumnUtility.makeImmediateColumn(converted.getFirst(), columnName, Utility.<@Value Object, Either<String, @Value Object>>mapListI(converted.getSecond(), x -> Either.<String, @Value Object>right(x)), DataTypeUtility.makeDefaultValue(converted.getFirst())), converted.getSecond().size());
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
        Pair<DataType, ImmutableMap<DataType, SimulationFunction<@Value Object, @Value Object>>> m = generaliseType(typeManager, Utility.mapListExI(typedPairs, p -> p.getFirst()));
        ImmutableList<@Value Object> loaded = Utility.<Pair<DataType, @Value Object>, @Value Object>mapListExI(typedPairs, p -> getOrInternal(m.getSecond(), p.getFirst()).apply(p.getSecond()));
        return new Pair<DataType, ImmutableList<@Value Object>>(m.getFirst(), loaded);
    }
    
    // If smaller can be generalised into larger (or some even larger type), returns the conversion function from small to large and the destination type.  If not (including case where larger can generalise to smaller), return null
    static @Nullable GeneralisedTypePair generaliseType(TypeManager typeManager, DataType smaller, DataType larger) throws InternalException, TaggedInstantiationException, UnknownTypeException
    {
        if (smaller.equals(larger))
            return new GeneralisedTypePair(larger, x -> x);
        if (larger.equals(typeManager.makeMaybeType(smaller)))
            return new GeneralisedTypePair(larger, x -> typeManager.maybePresent(x));
        DataType largeInner = getArrayInner(larger);
        if (largeInner != null)
        {
            if (largeInner.equals(smaller))
                return new GeneralisedTypePair(larger, x -> DataTypeUtility.value(ImmutableList.of(x)));
            else if (largeInner.equals(typeManager.makeMaybeType(smaller)))
                return new GeneralisedTypePair(larger, x -> DataTypeUtility.value(ImmutableList.of(typeManager.maybePresent(x))));

            DataType smallInner = getArrayInner(smaller);
            @Nullable GeneralisedTypePair genInners = smallInner == null ? null : generaliseType(typeManager, smallInner, largeInner);
            if (genInners != null)
            {
                return new GeneralisedTypePair(DataType.array(genInners.resultingType), applyToList(genInners.smallToResult), applyToList(genInners.largeToResult));
            }
        }
        @Nullable ImmutableMap<@ExpressionIdentifier String, DataType> smallFields = getRecordFields(smaller);
        @Nullable ImmutableMap<@ExpressionIdentifier String, DataType> largeFields = getRecordFields(larger);
        if (smallFields != null && largeFields != null)
        {
            // Generalisation is possible if all shared fields can be generalised pairwise, and all non-shared fields can be generalised into optional
            // true if large
            HashMap<@ExpressionIdentifier String, Pair<Boolean, DataType>> nonShared = new HashMap<>();
            // small, large in pair:
            HashMap<@ExpressionIdentifier String, Pair<DataType, DataType>> shared = new HashMap<>();
            nonShared.putAll(Utility.mapValues(smallFields, t -> new Pair<>(false, t)));
            for (Entry<@ExpressionIdentifier String, DataType> l : largeFields.entrySet())
            {
                Pair<Boolean, DataType> s = nonShared.remove(l.getKey());
                if (s == null)
                {
                    // Not in small; add it
                    nonShared.put(l.getKey(), new Pair<>(true, l.getValue()));
                }
                else
                {
                    // Move to shared:
                    shared.put(l.getKey(), new Pair<>(s.getSecond(), l.getValue()));
                }
            }
            // Now shared and nonShared are set-up, so get processing into result:
            HashMap<@ExpressionIdentifier String, GeneralisedTypePair> result = new HashMap<>();
            
            // Non-shared types need to be optional.
            // (We don't nest optionals)
            for (Entry<@ExpressionIdentifier String, Pair<Boolean, DataType>> e : nonShared.entrySet())
            {
                GeneralisedTypePair r;
                if (isOptional(e.getValue().getSecond()))
                {
                    r = new GeneralisedTypePair(e.getValue().getSecond(), x -> x);
                }
                else
                {
                    DataType optType = typeManager.makeMaybeType(e.getValue().getSecond());
                    if (e.getValue().getFirst()) // Large
                        r = new GeneralisedTypePair(optType, x -> x, x -> typeManager.maybePresent(x));
                    else
                        r = new GeneralisedTypePair(optType, x -> typeManager.maybePresent(x));
                }
                result.put(e.getKey(), r);
                
            }

            for (Entry<@ExpressionIdentifier String, Pair<DataType, DataType>> e : shared.entrySet())
            {
                @Nullable GeneralisedTypePair gen = generaliseType(typeManager, e.getValue().getFirst(), e.getValue().getSecond());
                if (gen == null)
                    return null;//throw new UserException("Cannot generalise shared field named " + e.getKey() + " types: " + e.getValue().getFirst() + " and " + e.getValue().getSecond() + " are incompatible");
                result.put(e.getKey(), gen);
            }
            
            return new GeneralisedTypePair(DataType.record(Utility.mapValues(result, p -> p.resultingType)), applyToRecord(typeManager, Utility.<@ExpressionIdentifier String, GeneralisedTypePair, SimulationFunction<@Value Object, @Value Object>>mapValues(result, p -> p.smallToResult)), applyToRecord(typeManager, Utility.<@ExpressionIdentifier String, GeneralisedTypePair, SimulationFunction<@Value Object, @Value Object>>mapValues(result, p -> p.largeToResult)));
        }
        
        
        return null;
    }

    private static boolean isOptional(DataType dataType) throws InternalException
    {
        return dataType.apply(new FlatDataTypeVisitor<Boolean>(false) {
            @Override
            public Boolean tagged(TypeId typeName, ImmutableList typeVars, ImmutableList tags) throws InternalException, InternalException
            {
                return typeName.getRaw().equals("Optional");
            }
        });
    }

    private static SimulationFunction<@Value Object, @Value Object> applyToRecord(TypeManager typeManager, ImmutableMap<@ExpressionIdentifier String, SimulationFunction<@Value Object, @Value Object>> applyToInner)
    {
        return x -> {
            @Value Record record = Utility.cast(x, Record.class);
            ImmutableMap.Builder<@ExpressionIdentifier String, @Value Object> r = ImmutableMap.builder();
            HashMap<@ExpressionIdentifier String, SimulationFunction<@Value Object, @Value Object>> stillToApply = new HashMap<>(applyToInner);
            for (Entry<@ExpressionIdentifier String, @Value Object> entry : record.getFullContent().entrySet())
            {
                SimulationFunction<@Value Object, @Value Object> f = stillToApply.remove(entry.getKey());
                if (f == null)
                    throw new InternalException("Problem fetching value for " + entry.getKey());
                r.put(entry.getKey(), f.apply(entry.getValue()));
            }
            // Other fields must now be Optional, so insert None values:
            for (@ExpressionIdentifier String key : stillToApply.keySet())
            {
                r.put(key, typeManager.maybeMissing());
            }
            return DataTypeUtility.value(new RecordMap(r.build()));
        };
    }

    private static SimulationFunction<@Value Object, @Value Object> applyToList(SimulationFunction<@Value Object, @Value Object> applyToInner)
    {
        return x -> {
            @Value ListEx list = Utility.cast(x, ListEx.class);
            int length = list.size();
            ImmutableList.Builder<@Value Object> processed = ImmutableList.builderWithExpectedSize(length);
            for (int i = 0; i < length; i++)
            {
                processed.add(applyToInner.apply(list.get(i)));
            }
            return DataTypeUtility.value(processed.build());
        };
    }

    private static @Nullable ImmutableMap<@ExpressionIdentifier String, DataType> getRecordFields(DataType dataType) throws InternalException
    {
        return dataType.apply(new FlatDataTypeVisitor<@Nullable ImmutableMap<@ExpressionIdentifier String, DataType>>(null) {
            @Override
            public @Nullable ImmutableMap<@ExpressionIdentifier String, DataType> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                return fields;
            }
        });
    }

    private static @Nullable DataType getArrayInner(DataType dataType) throws InternalException
    {
        return dataType.apply(new FlatDataTypeVisitor<@Nullable DataType>(null) {
            @Override
            public DataType array(DataType inner) throws InternalException, InternalException
            {
                return inner;
            }
        });
    }

    /**
     * Represents the result of generalising two types
     * (labelled smaller and larger, although these are really just labels)
     * into a third result type, and gives the value transformations
     * needed for turning small/large into result.
     */
    private static class GeneralisedTypePair
    {
        public final DataType resultingType;
        public final SimulationFunction<@Value Object, @Value Object> smallToResult;
        public final SimulationFunction<@Value Object, @Value Object> largeToResult;

        public GeneralisedTypePair(DataType resultingType, SimulationFunction<@Value Object, @Value Object> smallToResult, SimulationFunction<@Value Object, @Value Object> largeToResult)
        {
            this.resultingType = resultingType;
            this.smallToResult = smallToResult;
            this.largeToResult = largeToResult;
        }

        // For when large is identity transformation
        public GeneralisedTypePair(DataType resultingType, SimulationFunction<@Value Object, @Value Object> smallToResult)
        {
            this(resultingType, smallToResult, x -> x);
        }
    }

    /**
     * The generalisations available here are x -> Optional(x), and x -> List(x), as well as List(x) -> List(Optional(x))
     * @param typeManager
     * @param types
     * @return
     * @throws UserException
     * @throws InternalException
     */
    
    private static Pair<DataType, ImmutableMap<DataType, SimulationFunction<@Value Object, @Value Object>>> generaliseType(TypeManager typeManager, ImmutableList<DataType> types) throws UserException, InternalException
    {
        if (types.isEmpty())
            return new Pair<>(DataType.TEXT, ImmutableMap.<DataType, SimulationFunction<@Value Object, @Value Object>>of());
        DataType curType = types.get(0);
        HashMap<DataType, SimulationFunction<@Value Object, @Value Object>> conversions = new HashMap<>();
        conversions.put(curType, x -> x);
        for (DataType nextType : types.subList(1, types.size()))
        {
            if (conversions.containsKey(nextType))
            {
                // Fine, already dealt with
                continue;
            }
            @Nullable GeneralisedTypePair curToNext = generaliseType(typeManager, curType, nextType);
            if (curToNext != null)
            {
                conversions.put(curType, x -> x);
                composeAllWith(conversions, curToNext.smallToResult);
                curType = curToNext.resultingType;
                conversions.put(nextType, curToNext.largeToResult);
                continue;
            }
            @Nullable GeneralisedTypePair nextToCur = generaliseType(typeManager, nextType, curType);
            if (nextToCur != null)
            {
                composeAllWith(conversions, nextToCur.largeToResult);
                conversions.put(nextType, nextToCur.smallToResult);
                curType = nextToCur.resultingType;
                continue;
            }
            throw new UserException("Cannot generalise " + curType + " and " + nextType + " into a single type");
        }
        
        return new Pair<>(curType, ImmutableMap.copyOf(conversions));
    }

    private static void composeAllWith(HashMap<DataType, SimulationFunction<@Value Object, @Value Object>> current, SimulationFunction<@Value Object, @Value Object> thenApply)
    {
        for (Entry<DataType, SimulationFunction<@Value Object, @Value Object>> e : current.entrySet())
        {
            // Must take value outside lambda, since we're about to change it:
            SimulationFunction<@Value Object, @Value Object> prev = e.getValue();
            e.setValue(new SimulationFunction<@Value Object, @Value Object>()
            {
                @Override
                public @Value Object apply(@Value Object x) throws InternalException, UserException
                {
                    return thenApply.apply(prev.apply(x));
                }
            });
        }
    }

    public static ImmutableList<Pair<String, EditableRecordSet>> convertRToTable(TypeManager typeManager, RValue rValue, boolean allowMultipleTables) throws UserException, InternalException
    {
        // R tables are usually a list of columns, which suits us:
        return rValue.visit(new RVisitor<ImmutableList<Pair<String, EditableRecordSet>>>()
        {
            private ImmutableList<Pair<String, EditableRecordSet>> singleColumn() throws UserException, InternalException
            {
                Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> p = convertRToColumn(typeManager, rValue, new ColumnId("Result"));
                return ImmutableList.<Pair<String, EditableRecordSet>>of(new Pair<>("Value", new <EditableColumn>EditableRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(p.getFirst()), () -> p.getSecond())));
            }
            
            private ImmutableList<Pair<String, EditableRecordSet>> recordOrTable(ImmutableList<Pair<String, RValue>> fields) throws UserException, InternalException
            {
                int rowCount = -1;

                ImmutableList.Builder<SimulationFunction<RecordSet, EditableColumn>> columns = ImmutableList.builderWithExpectedSize(fields.size());
                for (int i = 0; i < fields.size(); i++)
                {
                    Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> col = convertRToColumn(typeManager, fields.get(i).getSecond(), new ColumnId(IdentifierUtility.fixExpressionIdentifier(fields.get(i).getFirst(), IdentifierUtility.identNum("Column", i))));
                    if (rowCount != -1 && col.getSecond() != rowCount)
                    {
                        // Not all the same size, treat as record:
                        Pair<DataType, @Value Object> recTypeVal = record(typeManager, fields);
                        
                        return ImmutableList.of(new Pair<String, EditableRecordSet>("Value", new EditableRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(ColumnUtility.makeImmediateColumn(recTypeVal.getFirst(), new ColumnId("Object"), ImmutableList.<Either<String, @Value Object>>of(Either.<String, @Value Object>right(recTypeVal.getSecond())), DataTypeUtility.makeDefaultValue(recTypeVal.getFirst()))), () -> 1)));
                    }
                    
                    rowCount = col.getSecond();
                    columns.add(col.getFirst());
                }
                
                int rowCountFinal = rowCount;
                return ImmutableList.of(new Pair<>("Value", new EditableRecordSet(columns.build(), () -> rowCountFinal)));
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
                    ImmutableList<Pair<String, EditableRecordSet>> found = convertRToTable(typeManager, item.item, allowMultipleTables);
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
                    if (!allowMultipleTables)
                        break;
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
                    if (allowMultipleTables)
                    {
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
                    }
                    if (!hasDataFrames)
                    {
                        // Is it an object?
                        if (isObject && values.size() > 1)
                        {
                            // Does it have names for class fields?
                            RValue names = attrMap.get("names");
                            ImmutableList.Builder<Pair<String, RValue>> named = ImmutableList.builder();
                            for (int i = 0; i < values.size(); i++)
                            {
                                String name = names == null ? null : RUtility.getString(RUtility.getListItem(names, i));
                                named.add(new Pair<>(name == null ? "" : name, values.get(i)));
                            }
                            
                            return recordOrTable(ImmutableList.sortedCopyOf(Pair.<String, RValue>comparatorFirst(), named.build()));
                        }
                        else if (isObject && values.size() == 1)
                        {
                            // Dig in:
                            return convertRToTable(typeManager, values.get(0), allowMultipleTables);
                        }
                        
                        return singleColumn();
                    }
                    else
                    {
                        ImmutableList.Builder<Pair<String, EditableRecordSet>> r = ImmutableList.builder();
                        for (RValue value : values)
                        {
                            r.addAll(convertRToTable(typeManager, value, allowMultipleTables));
                        }
                        return r.build();
                    }
                }
            }
        });
    }

    private static boolean isDataFrameOrTibble(ImmutableMap<String, RValue> attrMap) throws InternalException, UserException
    {
        return RUtility.isClass(attrMap, RUtility.CLASS_DATA_FRAME) || RUtility.isClass(attrMap, RUtility.CLASS_TIBBLE);
    }

    public static enum TableType { DATA_FRAME, TIBBLE }

    @OnThread(Tag.Any)
    public static String usToRTable(TableId tableId)
    {
        return tableId.getRaw().replace(" ", ".");
    }

    private static Pair<DataType, @Value Object> record(TypeManager typeManager, ImmutableList<Pair<String, RValue>> fields) throws UserException, InternalException
    {
        Builder<@ExpressionIdentifier String, Pair<DataType, @Value Object>> r = ImmutableMap.builder();

        for (int j = 0; j < fields.size(); j++)
        {
            Pair<String, RValue> recField = fields.get(j);
            Pair<DataType, ImmutableList<@Value Object>> asList = convertRToTypedValueList(typeManager, recField.getSecond());
            @ExpressionIdentifier String name = IdentifierUtility.fixExpressionIdentifier(recField.getFirst(), IdentifierUtility.identNum("Field", j));
            r.put(name, new Pair<DataType, @Value Object>(asList.getSecond().size() == 1 ? asList.getFirst() : DataType.array(asList.getFirst()), asList.getSecond().size() == 1 ? asList.getSecond().get(0) : DataTypeUtility.value(asList.getSecond())));
        }

        ImmutableMap<@ExpressionIdentifier String, Pair<DataType, @Value Object>> typedFields = r.build();

        DataType recordType = DataType.record(Utility.mapValues(typedFields, p -> p.getFirst()));

        @Value Record recordValue = DataTypeUtility.value(new RecordMap(Utility.<@ExpressionIdentifier String, Pair<DataType, @Value Object>, @Value Object>mapValues(typedFields, p -> p.getSecond())));

        return new Pair<>(recordType, recordValue);
    }
}
