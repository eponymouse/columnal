package records.data.datatype;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberDisplayInfo.Padding;
import records.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.FormatParser;
import records.grammar.FormatParser.*;
import records.grammar.MainParser.TypesContext;
import records.jellytype.JellyType;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.GraphUtility;
import utility.IdentifierUtility;
import utility.Pair;
import utility.TaggedValue;
import utility.Utility;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static records.data.datatype.DataType.BOOLEAN;
import static records.data.datatype.DataType.TEXT;

/**
 * Created by neil on 21/12/2016.
 */
public class TypeManager
{
    private final UnitManager unitManager;
    private final HashMap<TypeId, TaggedTypeDefinition> userTypes = new HashMap<>();
    private final HashMap<TypeId, TaggedTypeDefinition> builtInTypes = new HashMap<>();
    private final HashMap<TypeId, TaggedTypeDefinition> allKnownTypes = new HashMap<>();
    private final TaggedTypeDefinition voidType;
    private final TaggedTypeDefinition maybeType;
    // Only need one value for Missing:
    private final TaggedValue maybeMissing;
    
    private final TaggedTypeDefinition typeGADT;
    private final TaggedTypeDefinition unitGADT;

    public TypeManager(UnitManager unitManager) throws InternalException
    {
        this.unitManager = unitManager;
        maybeType = new TaggedTypeDefinition(new TypeId("Maybe"), ImmutableList.of(new Pair<TypeVariableKind, @ExpressionIdentifier  String>(TypeVariableKind.TYPE, "a")), ImmutableList.of(
            new TagType<>("Missing", null),
            new TagType<>("Present", JellyType.typeVariable("a"))
        ));
        maybeMissing = new TaggedValue(0, null);
        builtInTypes.put(maybeType.getTaggedTypeName(), maybeType);
        voidType = new TaggedTypeDefinition(new TypeId("Void"), ImmutableList.of(), ImmutableList.of());
        builtInTypes.put(voidType.getTaggedTypeName(), voidType);
        // TODO make this into a GADT:
        typeGADT = new TaggedTypeDefinition(new TypeId("Type"), ImmutableList.of(new Pair<TypeVariableKind, @ExpressionIdentifier String>(TypeVariableKind.TYPE, "t")), ImmutableList.of(new TagType<>("Type", null)));
        builtInTypes.put(new TypeId("Type"), typeGADT);
        // TODO make this into a GADT:
        unitGADT = new TaggedTypeDefinition(new TypeId("Unit"), ImmutableList.of(new Pair<TypeVariableKind, @ExpressionIdentifier String>(TypeVariableKind.UNIT, "u")), ImmutableList.of(new TagType<>("Unit", null)));
        builtInTypes.put(new TypeId("Unit"), unitGADT);
        allKnownTypes.putAll(builtInTypes);
    }

    public TaggedValue maybeMissing()
    {
        return maybeMissing;
    }
    
    public TaggedValue maybePresent(@Value Object content)
    {
        return new TaggedValue(1, content);
    }
    
    public void unregisterTaggedType(TypeId typeName)
    {
        TaggedTypeDefinition typeDefinition = userTypes.remove(typeName);
        // Only remove from all types if it is the user type.  Don't remove
        // built-in types if they are there under this name:
        if (typeDefinition != null)
            allKnownTypes.remove(typeName, typeDefinition);
    }
    
    // Either makes a new one, or fetches the existing one if it is the same type
    // or renames it to a spare name and returns that.
    public @Nullable TaggedTypeDefinition registerTaggedType(@ExpressionIdentifier String typeName, ImmutableList<Pair<TypeVariableKind, @ExpressionIdentifier String>> typeVariables, ImmutableList<TagType<JellyType>> tagTypes) throws InternalException
    {
        if (tagTypes.isEmpty())
            throw new InternalException("Tagged type cannot have zero tags");

        TypeId idealTypeId = new TypeId(typeName);
        if (allKnownTypes.containsKey(idealTypeId))
        {
            TaggedTypeDefinition existingType = allKnownTypes.get(idealTypeId);
            // Check if it's the same:
            if (tagTypes.equals(existingType.getTags()))
            {
                // It is; all is well
                return existingType;
            }
            else
            {
                return null;
            }
        }
        else
        {
            // TODO run sanity check for duplicate tag type names
            TaggedTypeDefinition newType = new TaggedTypeDefinition(idealTypeId, typeVariables, tagTypes);
            allKnownTypes.put(idealTypeId, newType);
            userTypes.put(idealTypeId, newType);
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
        @SuppressWarnings("identifier")
        TypeId typeName = new TypeId(typeDeclContext.typeName().getText());
        ImmutableList<Pair<TypeVariableKind, @ExpressionIdentifier String>> typeParams = Utility.<TagDeclParamContext, Pair<TypeVariableKind, @ExpressionIdentifier String>>mapListExI(typeDeclContext.taggedDecl().tagDeclParam(), var -> {
            return new Pair<TypeVariableKind, @ExpressionIdentifier String>(var.TYPEVAR() != null ? TypeVariableKind.TYPE : TypeVariableKind. UNIT, IdentifierUtility.fromParsed(var.ident()));
        });
        List<TagType<JellyType>> tags = new ArrayList<>();
        for (TagItemContext item : typeDeclContext.taggedDecl().tagItem())
        {
            @ExpressionIdentifier String tagName = IdentifierUtility.fromParsed(item.ident());

            if (tags.stream().anyMatch(t -> t.getName().equals(tagName)))
                throw new UserException("Duplicate tag names in format: \"" + tagName + "\"");

            if (item.type() != null && item.type().size() > 0)
            {
                if (item.type().size() == 1)
                    tags.add(new TagType<JellyType>(tagName, JellyType.load(item.type(0), this)));
                else
                    tags.add(new TagType<JellyType>(tagName, JellyType.tuple(Utility.mapListExI(item.type(), t -> JellyType.load(t, this)))));
            }
            else
                tags.add(new TagType<JellyType>(tagName, null));
        }

        TaggedTypeDefinition typeDefinition = new TaggedTypeDefinition(typeName, typeParams, ImmutableList.copyOf(tags));
        userTypes.put(typeName, typeDefinition);
        allKnownTypes.put(typeName, typeDefinition);
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
        else if (type.applyRef() != null)
        {
            if (type.applyRef().ident() == null)
                throw new UserException("Missing tag name: " + type.applyRef());
            ImmutableList<Either<Unit, DataType>> typeParams = Utility.mapListExI(type.applyRef().tagRefParam(), t -> {
                if (t.UNIT() != null)
                    return Either.left(unitManager.loadUse(t.UNIT().getText()));
                // TODO UNITVAR
                else if (t.UNITVAR() != null)
                    return Either.right(DataType.BOOLEAN);
                else
                    return Either.right(loadTypeUse(t.bracketedType()));
            });
            @SuppressWarnings("identifier")
            DataType taggedType = lookupType(new TypeId(type.applyRef().ident().getText()), typeParams);
            if (taggedType == null)
                throw new UserException("Undeclared tagged type: \"" + type.applyRef().ident().getText() + "\"");
            return taggedType;
        }
        else if (type.array() != null)
        {
            return DataType.array(loadTypeUse(type.array().type()));
        }
        else if (type.functionType() != null)
        {
            return DataType.function(Utility.mapListExI(type.functionType().functionArgs().type(), t -> loadTypeUse(t)), loadTypeUse(type.functionType().type()));
        }
        else if (type.ident() != null && type.TYPEVAR() == null)
        {
            @SuppressWarnings("identifier")
            DataType taggedType = lookupType(new TypeId(type.ident().getText()), ImmutableList.of());
            if (taggedType == null)
                throw new UserException("Undeclared tagged type: \"" + type.ident().getText() + "\"");
            return taggedType;
        }
        else
            throw new InternalException("Unrecognised case: \"" + type.getText() + "\"");
    }

    public @Nullable DataType lookupType(TypeId typeId, ImmutableList<Either<Unit, DataType>> typeVariableSubs) throws InternalException, UserException
    {
        TaggedTypeDefinition taggedTypeDefinition = allKnownTypes.get(typeId);
        if (taggedTypeDefinition == null)
            return null;
        else
            return taggedTypeDefinition.instantiate(typeVariableSubs, this);
    }
    
    public @Nullable TaggedTypeDefinition lookupDefinition(TypeId typeId)
    {
        return getKnownTaggedTypes().get(typeId);
    }

    /**
     * Gets all the known (tagged) types, including built-in.
     */
    public Map<TypeId, TaggedTypeDefinition> getKnownTaggedTypes()
    {
        return Collections.unmodifiableMap(allKnownTypes);
    }

    /**
     * Gets all the user-defined tagged types for this file (excl built-in).
     */
    public Map<TypeId, TaggedTypeDefinition> getUserTaggedTypes()
    {
        return Collections.unmodifiableMap(userTypes);
    }

    @OnThread(Tag.Simulation)
    public String save()
    {
        return save(t -> true);
    }
    
    @OnThread(Tag.Simulation)
    public String save(Predicate<TypeId> saveType)
    {
        List<TaggedTypeDefinition> ignoreTypes = Arrays.asList(voidType, maybeType, unitGADT, typeGADT);
        List<TaggedTypeDefinition> typesToSave = userTypes.values().stream().filter(t -> !Utility.containsRef(ignoreTypes, t) && saveType.test(t.getTaggedTypeName())).collect(Collectors.<TaggedTypeDefinition>toList());
        
        Map<@NonNull TaggedTypeDefinition, Collection<TaggedTypeDefinition>> incomingRefs = new HashMap<>();
        for (TaggedTypeDefinition taggedTypeDefinition : typesToSave)
        {
            for (TagType<JellyType> tagType : taggedTypeDefinition.getTags())
            {
                if (tagType.getInner() == null)
                    continue;

                tagType.getInner().forNestedTagged(typeName -> {
                    @Nullable TaggedTypeDefinition referencedType = userTypes.get(typeName);
                    if (referencedType != null && !Utility.containsRef(ignoreTypes, referencedType))
                        incomingRefs.computeIfAbsent(referencedType, t -> new ArrayList<>()).add(taggedTypeDefinition);
                });
            }
        }

        List<TaggedTypeDefinition> typeDefinitions = new ArrayList<>(typesToSave);
        // Sort by name by default:
        Collections.<TaggedTypeDefinition>sort(typeDefinitions, Comparator.<TaggedTypeDefinition, String>comparing((TaggedTypeDefinition t) -> t.getTaggedTypeName().getRaw()));
        
        List<TaggedTypeDefinition> orderedDataTypes = GraphUtility.<TaggedTypeDefinition>lineariseDAG(typeDefinitions, incomingRefs, Collections.<TaggedTypeDefinition>emptyList());
        // lineariseDAG makes all edges point forwards, but we want them pointing backwards
        // so reverse:
        Collections.reverse(orderedDataTypes);
        OutputBuilder b = new OutputBuilder();
        for (TaggedTypeDefinition taggedTypeDefinition : orderedDataTypes)
        {
            b.t(FormatLexer.TYPE, FormatLexer.VOCABULARY);
            b.unquoted(taggedTypeDefinition.getTaggedTypeName());
            taggedTypeDefinition.save(b);
            b.nl();
        }
        return b.toString();
    }

    public UnitManager getUnitManager()
    {
        return unitManager;
    }

    public void _test_copyTaggedTypesFrom(TypeManager typeManager) throws IllegalStateException
    {
        for (TaggedTypeDefinition taggedTypeDefinition : typeManager.userTypes.values())
        {
            if (!userTypes.containsKey(taggedTypeDefinition.getTaggedTypeName()))
            {
                userTypes.put(taggedTypeDefinition.getTaggedTypeName(), taggedTypeDefinition);
                allKnownTypes.put(taggedTypeDefinition.getTaggedTypeName(), taggedTypeDefinition);
            }
            else
            {
                if (!taggedTypeDefinition.equals(userTypes.get(taggedTypeDefinition.getTaggedTypeName())))
                {
                    throw new IllegalStateException("Two user types have same name but different definitions; abort!");
                }
            }
        }
        
    }

    public Either<String, TagInfo> lookupTag(@ExpressionIdentifier String typeName, String constructorName)
    {
        @Nullable TaggedTypeDefinition type = allKnownTypes.get(new TypeId(typeName));
        if (type == null)
            return Either.left(constructorName);

        try
        {
            @NonNull TaggedTypeDefinition typeFinal = type;
            Optional<Pair<Integer, TagType<JellyType>>> matchingTag = Utility.streamIndexed(type.getTags()).filter(tt -> tt.getSecond().getName().equals(constructorName)).findFirst();
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
    
    public TaggedTypeDefinition getVoidType()
    {
        return voidType;
    }

    // Basically, t -> Type t
    public DataType typeGADTFor(DataType type) throws InternalException, UserException
    {
        return typeGADT.instantiate(ImmutableList.of(Either.<Unit, DataType>right(type)), this);
    }

    // Basically, u -> Unit u
    public DataType unitGADTFor(Unit unit) throws InternalException, UserException
    {
        return unitGADT.instantiate(ImmutableList.of(Either.<Unit, DataType>left(unit)), this);
    }

    public void clearAllUser()
    {
        userTypes.clear();
        allKnownTypes.clear();
        allKnownTypes.putAll(builtInTypes);
    }

    public boolean ambiguousTagName(String tagName)
    {
        return getKnownTaggedTypes().values().stream().flatMap(t -> t.getTags().stream().map(tt -> tt.getName()))
            .filter(n -> n.equals(tagName)).count() > 1;
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
        public TagType<JellyType> getTagInfo()
        {
            return wholeType.getTags().get(tagIndex);
        }

    }
}
