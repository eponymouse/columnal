package records.data.datatype;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.TagType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.FormatParser;
import records.grammar.FormatParser.DateContext;
import records.grammar.FormatParser.NumberContext;
import records.grammar.FormatParser.TagItemContext;
import records.grammar.FormatParser.TypeContext;
import records.grammar.FormatParser.TypeDeclContext;
import records.grammar.FormatParser.TypeDeclsContext;
import records.grammar.MainParser.TypesContext;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.GraphUtility;
import utility.Pair;
import utility.UnitType;
import utility.Utility;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public TypeManager(UnitManager unitManager)
    {
        this.unitManager = unitManager;
        maybeType = new TaggedTypeDefinition(new TypeId("Maybe"), ImmutableList.of("type"), ImmutableList.of(
            new TagType<>("Missing", null),
            new TagType<>("Present", DataType.typeVariable("type"))
        ));
        knownTypes.put(maybeType.getTaggedTypeName(), maybeType);
    }

    // Either makes a new one, or fetches the existing one if it is the same type
    // or renames it to a spare name and returns that.
    public TaggedTypeDefinition registerTaggedType(String idealTypeName, ImmutableList<TagType<DataType>> tagTypes) throws InternalException
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
                return registerTaggedType(increaseNumber(idealTypeName), tagTypes);
            }
        }
        else
        {
            // TODO run sanity check for duplicate tag type names
            TaggedTypeDefinition newType = new TaggedTypeDefinition(idealTypeId, ImmutableList.of(), tagTypes);
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
        List<TagType<DataType>> tags = new ArrayList<>();
        for (TagItemContext item : typeDeclContext.taggedDecl().tagItem())
        {
            String tagName = item.constructor().getText();

            if (tags.stream().anyMatch(t -> t.getName().equals(tagName)))
                throw new UserException("Duplicate tag names in format: \"" + tagName + "\"");

            if (item.type() != null)
                tags.add(new TagType<DataType>(tagName, loadTypeUse(item.type())));
            else
                tags.add(new TagType<DataType>(tagName, null));
        }
        
        knownTypes.put(typeName, new TaggedTypeDefinition(typeName, ImmutableList.of(), ImmutableList.copyOf(tags)));
    }


    public DataType loadTypeUse(TypeContext type) throws InternalException, UserException
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
            else if (d.TIMEOFDAYZONED() != null)
                return DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED));

            throw new InternalException("Unrecognised date/time type: " + d.getText());
        }
        else if (type.number() != null)
        {
            NumberContext n = type.number();
            Unit unit = unitManager.loadUse(n.UNIT().getText());
            return DataType.number(new NumberInfo(unit, null /*TODO */));
        }
        else if (type.tagRef() != null)
        {
            if (type.tagRef().STRING() == null)
                throw new UserException("Missing tag name: " + type.tagRef());
            DataType taggedType = lookupType(new TypeId(type.tagRef().STRING().getText()), ImmutableList.of());
            if (taggedType == null)
                throw new UserException("Undeclared tagged type: \"" + type.tagRef().STRING().getText() + "\"");
            return taggedType;
        }
        else if (type.tuple() != null)
        {
            return DataType.tuple(Utility.mapListEx(type.tuple().type(), this::loadTypeUse));
        }
        else if (type.array() != null)
        {
            return DataType.array(loadTypeUse(type.array().type()));
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

    public @Nullable DataType lookupType(String typeName, ImmutableList<DataType> typeVariableSubs) throws UserException, InternalException
    {
        return lookupType(new TypeId(typeName), typeVariableSubs);
    }

    public @Nullable DataType lookupType(TypeId typeId, ImmutableList<DataType> typeVariableSubs) throws InternalException, UserException
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
                    boolean topLevel = true;

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
                    public UnitType tagged(TypeId typeName, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                    {
                        if (!topLevel)
                        {
                            @Nullable TaggedTypeDefinition referencedType = knownTypes.get(typeName);
                            if (referencedType != null)
                                incomingRefs.computeIfAbsent(referencedType, t -> new ArrayList<>()).add(taggedTypeDefinition);
                        }
                        topLevel = false;
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
    }
}
