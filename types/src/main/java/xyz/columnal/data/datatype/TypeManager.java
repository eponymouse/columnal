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

package xyz.columnal.data.datatype;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.identifier.qual.UnitIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TaggedInstantiationException;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitDeclaration;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.FormatLexer;
import xyz.columnal.grammar.FormatParser;
import xyz.columnal.grammar.FormatParser.BracketedTypeContext;
import xyz.columnal.grammar.FormatParser.DateContext;
import xyz.columnal.grammar.FormatParser.NumberContext;
import xyz.columnal.grammar.FormatParser.RecordContext;
import xyz.columnal.grammar.FormatParser.TagDeclParamContext;
import xyz.columnal.grammar.FormatParser.TagItemContext;
import xyz.columnal.grammar.FormatParser.TypeContext;
import xyz.columnal.grammar.FormatParser.TypeDeclContext;
import xyz.columnal.grammar.FormatParser.TypeDeclsContext;
import xyz.columnal.grammar.FormatParser.UnbracketedTypeContext;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.jellytype.JellyType.UnknownTypeException;
import xyz.columnal.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.GraphUtility;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static xyz.columnal.data.datatype.DataType.BOOLEAN;
import static xyz.columnal.data.datatype.DataType.TEXT;

/**
 * Created by neil on 21/12/2016.
 */
public class TypeManager
{
    public static final @ExpressionIdentifier String MAYBE_NAME = "Optional";
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
        // This needs to be built-in because we use it ourselves
        // e.g. when importing data which may be missing.s
        maybeType = new TaggedTypeDefinition(new TypeId(MAYBE_NAME), ImmutableList.of(new Pair<TypeVariableKind, @ExpressionIdentifier  String>(TypeVariableKind.TYPE, "a")), ImmutableList.of(
            new TagType<>("None", null),
            new TagType<>("Is", JellyType.typeVariable("a"))
        ));
        maybeMissing = new TaggedValue(0, null, maybeType);
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

    public @Value TaggedValue maybeMissing()
    {
        return maybeMissing;
    }
    
    public @Value TaggedValue maybePresent(@Value Object content)
    {
        return new TaggedValue(1, content, maybeType);
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
            // No need to check for duplicate tag type names as TaggedTypeDefinition constructor does it for us:
            TaggedTypeDefinition newType = new TaggedTypeDefinition(idealTypeId, typeVariables, tagTypes);
            allKnownTypes.put(idealTypeId, newType);
            userTypes.put(idealTypeId, newType);
            return newType;
        }
    }


    public void loadTypeDecls(String typeContent) throws UserException, InternalException
    {
        TypeDeclsContext typeDecls = Utility.parseAsOne(typeContent, FormatLexer::new, FormatParser::new, p -> p.typeDecls());
        for (TypeDeclContext typeDeclContext : typeDecls.typeDecl())
        {
            loadTypeDecl(typeDeclContext);
        }
    }

    @SuppressWarnings("recorded") // Won't actually be used in editor
    private void loadTypeDecl(TypeDeclContext typeDeclContext) throws UserException, InternalException
    {
        TypeId typeName = new TypeId(IdentifierUtility.fromParsed(typeDeclContext.typeName().ident()));
        ImmutableList<Pair<TypeVariableKind, @ExpressionIdentifier String>> typeParams = Utility.<TagDeclParamContext, Pair<TypeVariableKind, @ExpressionIdentifier String>>mapListExI(typeDeclContext.taggedDecl().tagDeclParam(), var -> {
            return new Pair<TypeVariableKind, @ExpressionIdentifier String>(var.TYPEVAR() != null ? TypeVariableKind.TYPE : TypeVariableKind. UNIT, IdentifierUtility.fromParsed(var.ident()));
        });
        List<TagType<JellyType>> tags = new ArrayList<>();
        for (TagItemContext item : typeDeclContext.taggedDecl().tagItem())
        {
            @ExpressionIdentifier String tagName = IdentifierUtility.fromParsed(item.ident());

            if (tags.stream().anyMatch(t -> t.getName().equals(tagName)))
                throw new UserException("Duplicate tag names in format: \"" + tagName + "\"");

            if (item.bracketedType() != null)
            {
                tags.add(new TagType<JellyType>(tagName, JellyType.load(item.bracketedType(), this)));
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
        if (type.record() != null)
        {
            RecordContext rec = type.record();
            HashMap<@ExpressionIdentifier String, DataType> fields = new HashMap<>();
            for (int i = 0; i < rec.columnName().size(); i++)
            {
                @ExpressionIdentifier String name = IdentifierUtility.fromParsed(rec.columnName(i));
                DataType dataType = loadTypeUse(rec.type(i));
                if (fields.put(name, dataType) != null)
                    throw new UserException("Duplicated field: \"" + name + "\"");
            }
            return DataType.record(fields);
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
            DataType taggedType = lookupType(new TypeId(IdentifierUtility.fromParsed(type.applyRef().ident())), typeParams);
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
            DataType taggedType = lookupType(new TypeId(IdentifierUtility.fromParsed(type.ident())), ImmutableList.of());
            if (taggedType == null)
                throw new UserException("Undeclared tagged type: \"" + type.ident().getText() + "\"");
            return taggedType;
        }
        else
            throw new InternalException("Unrecognised case: \"" + type.getText() + "\"");
    }

    public @Nullable DataType lookupType(TypeId typeId, ImmutableList<Either<Unit, DataType>> typeVariableSubs) throws InternalException, TaggedInstantiationException, UnknownTypeException
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
    public List<String> save()
    {
        return save(t -> true);
    }
    
    @OnThread(Tag.Simulation)
    public List<String> save(Predicate<TypeId> saveType)
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
        ImmutableList.Builder<String> savedTypes = ImmutableList.builderWithExpectedSize(orderedDataTypes.size());
        
        for (TaggedTypeDefinition taggedTypeDefinition : orderedDataTypes)
        {
            OutputBuilder b = new OutputBuilder();
            b.t(FormatLexer.TYPE, FormatLexer.VOCABULARY);
            b.unquoted(taggedTypeDefinition.getTaggedTypeName());
            taggedTypeDefinition.save(b);
            savedTypes.add(b.toString());
        }
        return savedTypes.build();
    }

    public UnitManager getUnitManager()
    {
        return unitManager;
    }

    // Also copies units from the unit manager
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

        for (Entry<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> unit : typeManager.getUnitManager().getAllUserDeclared().entrySet())
        {
            if (!unitManager.getAllUserDeclared().containsKey(unit.getKey()))
            {
                unitManager.addUserUnit(new Pair<>(unit.getKey(), unit.getValue()));
            }
        }
    }

    // Either error message or tag
    public Either<String, TagInfo> lookupTag(@Nullable @ExpressionIdentifier String typeName, @ExpressionIdentifier String constructorName)
    {
        @Nullable TaggedTypeDefinition type;
        if (typeName != null)
        {
            type = allKnownTypes.get(new TypeId(typeName));
        }
        else
        {
            ImmutableList<Pair<TaggedTypeDefinition, TagType<JellyType>>> possibles = allKnownTypes.values().stream().flatMap(ttd -> ttd.getTags().stream().map(tt -> new Pair<>(ttd, tt))).filter(tt -> tt.getSecond().getName().equals(constructorName)).collect(ImmutableList.<Pair<TaggedTypeDefinition, TagType<JellyType>>>toImmutableList());
            if (possibles.isEmpty())
                return Either.left("No such tag found: " + constructorName);
            else if (possibles.size() >= 2)
                return Either.left("Multiple tags found: " + possibles.stream().map(p -> p.getFirst().getTaggedTypeName().getRaw()).collect(Collectors.joining(", ")));
            type = possibles.get(0).getFirst();
        }
        if (type == null)
            return Either.left("No such type found: " + typeName);

        try
        {
            @NonNull TaggedTypeDefinition typeFinal = type;
            Optional<Pair<Integer, TagType<JellyType>>> matchingTag = Utility.streamIndexed(type.getTags()).filter(tt -> tt.getSecond().getName().equals(constructorName)).findFirst();
            if (matchingTag.isPresent())
                return Either.<String, TagInfo>right(new TagInfo(typeFinal, matchingTag.get().getFirst()));
            else
                return Either.<String, TagInfo>left("No such tag: " + constructorName);
        }
        catch (InternalException e)
        {
            Utility.report(e);
            return Either.left("Error fetching: " + constructorName);
        }
    }

    public TaggedTypeDefinition getMaybeType()
    {
        return maybeType;
    }
    
    public DataType makeMaybeType(DataType inner) throws InternalException, TaggedInstantiationException, UnknownTypeException
    {
        return maybeType.instantiate(ImmutableList.<Either<Unit, DataType>>of(Either.<Unit, DataType>right(inner)), this);
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

        public @Value TaggedValue makeTag(@Nullable @Value Object inner)
        {
            return new TaggedValue(tagIndex, inner, wholeType);
        }
    }
}
