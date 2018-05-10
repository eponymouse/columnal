package records.data.datatype;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberDisplayInfo.Padding;
import records.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.ParseException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.FormatParser;
import records.grammar.FormatParser.BracketedTypeContext;
import records.grammar.FormatParser.DateContext;
import records.grammar.FormatParser.DecimalPlacesContext;
import records.grammar.FormatParser.NumberContext;
import records.grammar.FormatParser.TagItemContext;
import records.grammar.FormatParser.TypeContext;
import records.grammar.FormatParser.TypeDeclContext;
import records.grammar.FormatParser.TypeDeclsContext;
import records.grammar.FormatParser.UnbracketedTypeContext;
import records.grammar.MainParser.TypesContext;
import records.loadsave.OutputBuilder;
import styled.StyledShowable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.GraphUtility;
import utility.Pair;
import utility.TaggedValue;
import utility.UnitType;
import utility.Utility;
import utility.ValueFunction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import static records.data.datatype.DataType.BOOLEAN;
import static records.data.datatype.DataType.TEXT;

/**
 * Created by neil on 21/12/2016.
 */
public class TypeManager
{
    private final UnitManager unitManager;
    private final HashMap<TypeId, TaggedTypeDefinition> knownTypes = new HashMap<>();
    private final TaggedTypeDefinition maybeType;
    // Only need one value for Missing:
    private final TaggedValue maybeMissing;
    
    private final TaggedTypeDefinition typeGADT;

    public TypeManager(UnitManager unitManager) throws InternalException
    {
        this.unitManager = unitManager;
        maybeType = new TaggedTypeDefinition(new TypeId("Maybe"), ImmutableList.of(new Pair<>(TypeVariableKind.TYPE, "a")), ImmutableList.of(
            new TagType<>("Missing", null),
            new TagType<>("Present", DataType.typeVariable("a"))
        ));
        maybeMissing = new TaggedValue(0, null);
        knownTypes.put(maybeType.getTaggedTypeName(), maybeType);
        // TODO make this into a GADT:
        typeGADT = new TaggedTypeDefinition(new TypeId("Type"), ImmutableList.of(new Pair<>(TypeVariableKind.TYPE, "t")), ImmutableList.of(new TagType<>("Type", null)));
        knownTypes.put(new TypeId("Type"), typeGADT);
        // TODO make this into a GADT:
        knownTypes.put(new TypeId("Unit"), new TaggedTypeDefinition(new TypeId("Unit"), ImmutableList.of(new Pair<>(TypeVariableKind.UNIT, "u")), ImmutableList.of(new TagType<>("Unit", null))));
    }
    
    public TaggedValue maybeMissing()
    {
        return maybeMissing;
    }
    
    public TaggedValue maybePresent(@Value Object content)
    {
        return new TaggedValue(1, content);
    }
    
    
    // Either makes a new one, or fetches the existing one if it is the same type
    // or renames it to a spare name and returns that.
    public TaggedTypeDefinition registerTaggedType(String idealTypeName, ImmutableList<Pair<TypeVariableKind, String>> typeVariables, ImmutableList<TagType<DataType>> tagTypes) throws InternalException
    {
        if (tagTypes.isEmpty())
            throw new InternalException("Tagged type cannot have zero tags");

        TypeId idealTypeId = new TypeId(idealTypeName);
        if (knownTypes.containsKey(idealTypeId))
        {
            TaggedTypeDefinition existingType = knownTypes.get(idealTypeId);
            // Check if it's the same:
            if (tagTypes.equals(existingType.getTags()))
            {
                // It is; all is well
                return existingType;
            }
            else
            {
                // Keep trying new names:
                return registerTaggedType(increaseNumber(idealTypeName), typeVariables, tagTypes);
            }
        }
        else
        {
            // TODO run sanity check for duplicate tag type names
            TaggedTypeDefinition newType = new TaggedTypeDefinition(idealTypeId, typeVariables, tagTypes);
            knownTypes.put(idealTypeId, newType);
            return newType;
        }
    }


    public void loadTypeDecls(TypesContext types) throws UserException, InternalException
    {
        TypeDeclsContext typeDecls = Utility.parseAsOne(types.detail().DETAIL_LINE().stream().<String>map(l -> l.getText()).filter(s -> !s.trim().isEmpty()).collect(Collectors.joining("\n")), FormatLexer::new, FormatParser::new, p -> p.typeDecls());
        for (TypeDeclContext typeDeclContext : typeDecls.typeDecl())
        {
            loadTypeDecl(typeDeclContext);
        }
    }

    private void loadTypeDecl(TypeDeclContext typeDeclContext) throws UserException, InternalException
    {
        TypeId typeName = new TypeId(typeDeclContext.typeName().getText());
        ImmutableList<Pair<TypeVariableKind, String>> typeParams = Utility.mapListExI(typeDeclContext.taggedDecl().tagDeclParam(), var -> {
            return new Pair<>(var.TYPEVAR() != null ? TypeVariableKind.TYPE : TypeVariableKind. UNIT, var.ident().getText());
        });
        List<TagType<DataType>> tags = new ArrayList<>();
        for (TagItemContext item : typeDeclContext.taggedDecl().tagItem())
        {
            String tagName = item.ident().getText();

            if (tags.stream().anyMatch(t -> t.getName().equals(tagName)))
                throw new UserException("Duplicate tag names in format: \"" + tagName + "\"");

            if (item.type() != null && item.type().size() > 0)
            {
                if (item.type().size() == 1)
                    tags.add(new TagType<DataType>(tagName, loadTypeUse(item.type(0))));
                else
                    tags.add(new TagType<DataType>(tagName, DataType.tuple(Utility.mapListEx(item.type(), this::loadTypeUse))));
            }
            else
                tags.add(new TagType<DataType>(tagName, null));
        }
        
        knownTypes.put(typeName, new TaggedTypeDefinition(typeName, typeParams, ImmutableList.copyOf(tags)));
    }

    public DataType loadTypeUse(String type) throws InternalException, UserException
    {
        return Utility.parseAsOne(type, FormatLexer::new, FormatParser::new, p -> loadTypeUse(p.completeType().type()));
    }

    public DataType loadTypeUse(TypeContext type) throws InternalException, UserException
    {
        if (type.bracketedType() != null)
            return loadTypeUse(type.bracketedType());
        else if (type.unbracketedType() != null)
            return loadTypeUse(type.unbracketedType());
        else
            throw new InternalException("Unrecognised case: \"" + type.getText() + "\"");
    }

    private DataType loadTypeUse(BracketedTypeContext type) throws InternalException, UserException
    {
        if (type.tuple() != null)
        {
            return DataType.tuple(Utility.mapListEx(type.tuple().type(), t -> loadTypeUse(t)));
        }
        else if (type.functionType() != null)
        {
            return DataType.function(loadTypeUse(type.functionType().type(0)), loadTypeUse(type.functionType().type(1)));
        }
        else if (type.type() != null)
        {
            return loadTypeUse(type.type());
        }
        else
            throw new InternalException("Unrecognised case: \"" + type.getText() + "\"");
    }
    
    private DataType loadTypeUse(UnbracketedTypeContext type) throws InternalException, UserException
    {
        if (type.BOOLEAN() != null)
            return BOOLEAN;
        else if (type.TEXT() != null)
            return TEXT;
        else if (type.date() != null)
        {
            DateContext d = type.date();
            if (d.YEARMONTHDAY() != null)
                return DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY));
            else if (d.YEARMONTH() != null)
                return DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH));
            else if (d.DATETIME() != null)
                return DataType.date(new DateTimeInfo(DateTimeType.DATETIME));
            else if (d.DATETIMEZONED() != null)
                return DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED));
            else if (d.TIMEOFDAY() != null)
                return DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY));
            //else if (d.TIMEOFDAYZONED() != null)
                //return DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED));

            throw new UserException("Unrecognised date/time type: " + d.getText());
        }
        else if (type.number() != null)
        {
            NumberContext n = type.number();
            Unit unit = n.UNIT() == null ? Unit.SCALAR : unitManager.loadUse(n.UNIT().getText());
            @Nullable NumberDisplayInfo ndi = null;
            if (n.decimalPlaces() != null)
            {
                DecimalPlacesContext dp = n.decimalPlaces();
                OptionalInt minDP = Utility.parseIntegerOpt(dp.DIGITS(0).getText());
                OptionalInt maxDP = dp.DIGITS().size() == 1 ? minDP : Utility.parseIntegerOpt(dp.DIGITS(1).getText());
                if (!minDP.isPresent() || !maxDP.isPresent())
                    throw new InternalException("Cannot parse integer from digits: " + dp.getText());
                ndi = new NumberDisplayInfo(minDP.getAsInt(), maxDP.getAsInt(), dp.ZERO_KWD() != null ? Padding.ZERO : Padding.SPACE);
            }
            return DataType.number(new NumberInfo(unit));
        }
        else if (type.tagRef() != null)
        {
            if (type.tagRef().ident() == null)
                throw new UserException("Missing tag name: " + type.tagRef());
            ImmutableList<Either<Unit, DataType>> typeParams = Utility.mapListExI(type.tagRef().tagRefParam(), t -> {
                if (t.UNIT() != null)
                    return Either.left(unitManager.loadUse(t.UNIT().getText()));
                // TODO UNITVAR
                else if (t.UNITVAR() != null)
                    return Either.right(DataType.BOOLEAN);
                else
                    return Either.right(loadTypeUse(t.bracketedType()));
            });
            DataType taggedType = lookupType(new TypeId(type.tagRef().ident().getText()), typeParams);
            if (taggedType == null)
                throw new UserException("Undeclared tagged type: \"" + type.tagRef().ident().getText() + "\"");
            return taggedType;
        }
        else if (type.array() != null)
        {
            return DataType.array(loadTypeUse(type.array().type()));
        }
        else if (type.typeVar() != null)
        {
            return DataType.typeVariable(type.typeVar().ident().getText());
        }
        else
            throw new InternalException("Unrecognised case: \"" + type.getText() + "\"");
    }

    private static String increaseNumber(String str)
    {
        if (str.length() <= 1)
            return str + "0";
        // Don't alter first char even if digit:
        int i;
        for (i = str.length() - 1; i >= 0; i--)
        {
            if (str.charAt(i) < '0' || str.charAt(i) > '9')
            {
                i = i + 1;
                break;
            }
        }
        String numberPart = str.substring(i);
        BigInteger num = numberPart.isEmpty() ? BigInteger.ZERO : new BigInteger(numberPart);
        return str.substring(0, i) + num.add(BigInteger.ONE).toString();
    }

    public @Nullable DataType lookupType(String typeName, ImmutableList<Either<Unit, DataType>> typeVariableSubs) throws UserException, InternalException
    {
        return lookupType(new TypeId(typeName), typeVariableSubs);
    }

    public @Nullable DataType lookupType(TypeId typeId, ImmutableList<Either<Unit, DataType>> typeVariableSubs) throws InternalException, UserException
    {
        TaggedTypeDefinition taggedTypeDefinition = knownTypes.get(typeId);
        if (taggedTypeDefinition == null)
            return null;
        else
            return taggedTypeDefinition.instantiate(typeVariableSubs);
    }
    
    public @Nullable TaggedTypeDefinition lookupDefinition(TypeId typeId)
    {
        return getKnownTaggedTypes().get(typeId);
    }

    /**
     * Gets all the known (tagged) types.
     */
    public Map<TypeId, TaggedTypeDefinition> getKnownTaggedTypes()
    {
        return Collections.unmodifiableMap(knownTypes);
    }

    @OnThread(Tag.Simulation)
    public String save() throws InternalException, UserException
    {
        Map<@NonNull TaggedTypeDefinition, Collection<TaggedTypeDefinition>> incomingRefs = new HashMap<>();
        for (TaggedTypeDefinition taggedTypeDefinition : knownTypes.values())
        {
            for (TagType<DataType> tagType : taggedTypeDefinition.getTags())
            {
                if (tagType.getInner() == null)
                    continue;

                tagType.getInner().apply(new DataTypeVisitor<UnitType>()
                {
                    @Override
                    public UnitType number(NumberInfo displayInfo) throws InternalException, UserException
                    {
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType text() throws InternalException, UserException
                    {
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                    {
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType bool() throws InternalException, UserException
                    {
                        return UnitType.UNIT;
                    }
                    
                    @Override
                    public UnitType tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                    {
                        @Nullable TaggedTypeDefinition referencedType = knownTypes.get(typeName);
                        if (referencedType != null)
                            incomingRefs.computeIfAbsent(referencedType, t -> new ArrayList<>()).add(taggedTypeDefinition);
                        for (TagType<DataType> tag : tags)
                        {
                            @Nullable DataType inner = tag.getInner();
                            if (inner != null)
                                inner.apply(this);
                        }
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType tuple(ImmutableList<DataType> inner) throws InternalException, UserException
                    {
                        for (DataType type : inner)
                        {
                            type.apply(this);
                        }
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType array(@Nullable DataType inner) throws InternalException, UserException
                    {
                        if (inner != null)
                            inner.apply(this);
                        return UnitType.UNIT;
                    }

                    @Override
                    public UnitType typeVariable(String typeVariableName) throws InternalException, UserException
                    {
                        return UnitType.UNIT;
                    }
                });
            }
        }

        List<TaggedTypeDefinition> orderedDataTypes = GraphUtility.<TaggedTypeDefinition>lineariseDAG(knownTypes.values(), incomingRefs, Collections.emptyList());
        // lineariseDAG makes all edges point forwards, but we want them pointing backwards
        // so reverse:
        Collections.reverse(orderedDataTypes);
        OutputBuilder b = new OutputBuilder();
        for (TaggedTypeDefinition taggedTypeDefinition : orderedDataTypes)
        {
            b.t(FormatLexer.TYPE, FormatLexer.VOCABULARY);
            b.quote(taggedTypeDefinition.getTaggedTypeName());
            taggedTypeDefinition.save(b);
            b.nl();
        }
        return b.toString();
    }

    public UnitManager getUnitManager()
    {
        return unitManager;
    }

    public void _test_copyTaggedTypesFrom(TypeManager typeManager)
    {
        knownTypes.putAll(typeManager.knownTypes);
    }

    public Either<String, TagInfo> lookupTag(String typeName, String constructorName)
    {
        @Nullable TaggedTypeDefinition type = knownTypes.get(new TypeId(typeName));
        if (type == null)
            return Either.left(constructorName);

        try
        {
            @NonNull TaggedTypeDefinition typeFinal = type;
            Optional<Pair<Integer, TagType<DataType>>> matchingTag = Utility.streamIndexed(type.getTags()).filter(tt -> tt.getSecond().getName().equals(constructorName)).findFirst();
            if (matchingTag.isPresent())
                return Either.<String, TagInfo>right(new TagInfo(typeFinal, matchingTag.get().getFirst()));
            else
                return Either.left(typeName);
        }
        catch (InternalException e)
        {
            Utility.report(e);
            return Either.left(constructorName);
        }
    }

    public TaggedTypeDefinition getMaybeType()
    {
        return maybeType;
    }

    // Basically, t -> Type t
    public DataType typeGADTFor(DataType type) throws InternalException, UserException
    {
        return typeGADT.instantiate(ImmutableList.of(Either.right(type)));
    }

    public static class TagInfo
    {
        public final TaggedTypeDefinition wholeType;
        public final int tagIndex;

        public TagInfo(TaggedTypeDefinition wholeType, int tagIndex) throws InternalException
        {
            this.wholeType = wholeType;
            this.tagIndex = tagIndex;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TagInfo tagInfo = (TagInfo) o;

            if (tagIndex != tagInfo.tagIndex) return false;
            return wholeType.equals(tagInfo.wholeType);
        }

        @Override
        public int hashCode()
        {
            int result = wholeType.hashCode();
            result = 31 * result + tagIndex;
            return result;
        }

        public TypeId getTypeName()
        {
            return wholeType.getTaggedTypeName();
        }
        
        @Pure
        public TagType<DataType> getTagInfo()
        {
            return wholeType.getTags().get(tagIndex);
        }
        
        public @Value Object makeValue()
        {
            TagType<DataType> tag = getTagInfo();
            if (tag.getInner() == null)
                return new TaggedValue(tagIndex, null);
            else
                return DataTypeUtility.value(new ValueFunction()
                {
                    @Override
                    public @OnThread(Tag.Simulation) @Value Object call(@Value Object arg) throws InternalException, UserException
                    {
                        return new TaggedValue(tagIndex, arg);
                    }
                });
        }
    }
}
