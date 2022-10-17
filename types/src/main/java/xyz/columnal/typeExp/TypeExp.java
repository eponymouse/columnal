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

package xyz.columnal.typeExp;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.IdentityHashSet;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitorEx;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.typeExp.units.UnitExp;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * We have the following concrete/terminal types:
 *  - Boolean
 *  - Number
 *  - String
 *  - Date/time types
 * The following composite types:
 *  - an ADT, which at the type level can take a list of 0+ type parameters
 *  - a list, which can be thought of as an in-built ADT taking one type parameter
 *  - a tuple, which can be thought as an in-built ADT taking 2+ parameters
 * Because some functions take a tuple with any number of parameters, we have a special case for this
 *
 * 
 * The following is a pseudo-GADT for types which we will one day expose to the user for dynamic typing
 * so that you can process a column dynamically as (Type a, [a]) :
 * 
 * data Constructor a where
 *   -- construct, deconstruct, Type
 *   Cons :: (b -> a) -> (a -> Maybe b) -> Type b -> Constructor a 
 * 
 * data ConcreteComposite a where
 *   Concrete :: [(String, Maybe (Constructor a))] -> CompositeDef a
 * 
 * data CompositeDef a where
 *   -- End version, with no type args:
 *   Conc :: ConcreteComposite a -> CompositeDef a 
 *   -- Type variable:
 *   Poly :: (Type b -> CompositeDef a) -> CompositeDef (Type b -> a)
 * 
 * data TupleElem a where
 *   -- deconstruct, setter
 *   Elem :: (a -> b) -> (b -> a -> a) -> Type b -> TupleElem a
 * 
 * data TypeArg where
 *   TypeArg :: Type a -> TypeArg
 * 
 * data Type a where
 *   Num :: Type Number
 *   Bool :: Type Boolean
 *   Text :: Type String
 *   Date :: Type Date (etc -- several of these)
 *   -- Tagged data types: 
 *   CompositeConcrete :: ConcreteComposite a -> Type a
 *   CompositeApp :: CompositeDef (Type b -> a) -> Type b -> Type a
 *   List :: Type a -> Type [a]
 *   Tuple :: [TupleElem a] -> Type a
 *   
 *   
 *   
 * Meanwhile, we need a prospective type while doing type inference.  That is similar to the above
 * but has some different fields ready for unification:
 * 
 * -- Note in Sheard that TypeExp a is only carrying around ST parameter, TypeExp doesn't actually
 * -- need a type argument. 
 * 
 * data TypeExp where
 *   -- Note, in the Sheard paper that Ptr is a custom type, not an in-built one!  Unfolded here:
 *   MutVar :: IORef (Maybe TypeExp) -> TypeExp
 *   -- Don't really understand GenVar, yet:
 *   GenVar :: Int -> TypeExp
 *   -- We need a special case for numbers due to units:
 *   NumType :: Unit -> TypeExp
 *   -- Includes non-numeric primitives, lists, algebraic:
 *   TypeCons :: String -> [TypeExp] -> TypeExp
 *   -- Tuples are difficult primarily due to type of functions like "first":
 *   TupleType :: Tuple -> TypeExp
 *   -- Overloaded operators and functions:
 *   Or :: [TypeExp] -> TypeExp
 *   
 * -- This is like a list type, but distinguishes don't-care (used by "first" for the
 * rest of the tuple) from empty.
 * data Tuple where
 *   Any :: Tuple
 *   End :: Tuple
 *   TCons :: TypeExp -> Tuple -> Tuple
 */

public abstract class TypeExp implements StyledShowable
{
    public static final ImmutableSet<String> ALL_TYPE_CLASSES = ImmutableSet.of(
        "Equatable", "Comparable", "Readable", "Showable"
    );
    
    public static final @ExpressionIdentifier String CONS_TEXT = "Text";
    public static final @ExpressionIdentifier String CONS_BOOLEAN = "Boolean";
    public static final @ExpressionIdentifier String CONS_LIST = "List";
    public static final @ExpressionIdentifier String CONS_FUNCTION = "Function";
    public static final @ExpressionIdentifier String CONS_TYPE = "Type";
    public static final @ExpressionIdentifier String CONS_UNIT = "Unit";
    // For recording errors:
    protected final @Nullable ExpressionBase src;
    
    protected TypeExp(@Nullable ExpressionBase src)
    {
        this.src = src;
    }
    
    public static Either<TypeError, TypeExp> unifyTypes(TypeExp... types) throws InternalException
    {
        return unifyTypes(Arrays.asList(types));
    }
    
    public static Either<TypeError, TypeExp> unifyTypes(List<TypeExp> types) throws InternalException
    {
        if (types.isEmpty())
            return Either.left(new TypeError(StyledString.s("Error: no types available"), ImmutableList.of()));
        else if (types.size() == 1)
            return Either.right(types.get(0));
        else
        {
            Either<TypeError, TypeExp> r = types.get(0).unifyWith(types.get(1));
            for (int i = 2; i < types.size(); i++)
            {
                if (r.isLeft())
                    return r;
                TypeExp next = types.get(i);
                r = r.getRight("Impossible").unifyWith(next);
            }
            return r;
        }
        
    }

    // This is like a function: type -> Type type, except that the parameter is not a value of that type, it's the type itself.
    public static TypeExp dataTypeToTypeGADT(@Nullable ExpressionBase src, DataType dataType) throws InternalException
    {
        return typeExpToTypeGADT(src, fromDataType(src, dataType));
    }

    // This is like a function: type -> Type type, except that the parameter is not a value of that type, it's the type itself.
    public static TypeExp typeExpToTypeGADT(@Nullable ExpressionBase src, TypeExp exp) throws InternalException
    {
        return new TypeCons(src, CONS_TYPE, ImmutableList.of(Either.<UnitExp, TypeExp>right(exp)), ImmutableSet.of());
    }

    // This is like a function: unit -> Unit unit, except that the parameter is not a value of that unit, it's the unit itself.
    public static TypeExp unitExpToUnitGADT(@Nullable ExpressionBase src, UnitExp exp) throws InternalException
    {
        return new TypeCons(src, CONS_UNIT, ImmutableList.of(Either.<UnitExp, TypeExp>left(exp)), ImmutableSet.of());
    }
    
    public static TypeExp record(@Nullable ExpressionBase src, Map<@ExpressionIdentifier String, TypeExp> fieldTypes, boolean complete)
    {
        return new RecordTypeExp(src, ImmutableMap.copyOf(fieldTypes), complete);
    }

    // package-protected:
    final Either<TypeError, TypeExp> unifyWith(TypeExp b) throws InternalException
    {
        TypeExp aPruned = prune();
        TypeExp bPruned = b.prune();
        // Make sure param isn't a MutVar unless it has to be (because they are both MutVar):
        if (bPruned instanceof MutVar && !(aPruned instanceof MutVar))
        {
            return bPruned._unify(aPruned);
        }
        else
        {
            return aPruned._unify(bPruned);
        }
    }

    /**
     * You can assume that b is pruned, and is not a MutVar unless
     * this is also a MutVar.
     */
    public abstract Either<TypeError, TypeExp> _unify(TypeExp b) throws InternalException;
    
    // Prunes any MutVars at the outermost level.
    public TypeExp prune()
    {
        return this;
    }

    /**
     * Is this TypeExp equal to, or does it contain, the given MutVar?
     * Used to check for cycles.
     */
    public abstract boolean containsMutVar(MutVar mutVar);
    
    protected final Either<TypeError, TypeExp> typeMismatch(TypeExp other)
    {
        return Either.left(new TypeError(StyledString.concat(StyledString.s("Type mismatch: "), toStyledString(), StyledString.s(" versus "), other.toStyledString()), ImmutableList.of(this, other)));
    }
    
    // The first part of the pair is the overall tagged type.  The second part is a list, for all the constructors,
    // of the types of that constructor
    public static Pair<TypeExp, ImmutableList<TypeExp>> fromTagged(@Nullable ExpressionBase src, TaggedTypeDefinition taggedTypeDefinition) throws InternalException
    {
        ImmutableList.Builder<Either<UnitExp, TypeExp>> typeVarsInOrder = ImmutableList.builder();
        Map<String, Either<MutUnitVar, MutVar>> typeVarsByName = new HashMap<>();

        for (Pair<TypeVariableKind, String> typeVar : taggedTypeDefinition.getTypeArguments())
        {
            Either<MutUnitVar, MutVar> mutVar = typeVar.getFirst() == TypeVariableKind.TYPE ? Either.right(new MutVar(src)) : Either.left(new MutUnitVar());
            typeVarsInOrder.add(mutVar.<UnitExp, TypeExp>mapBoth(UnitExp::new, v -> v));
            typeVarsByName.put(typeVar.getSecond(), mutVar);
        }

        TypeCons overallType = new TypeCons(src, taggedTypeDefinition.getTaggedTypeName().getRaw(), typeVarsInOrder.build(), ALL_TYPE_CLASSES);
        ImmutableList.Builder<TypeExp> tagTypes = ImmutableList.builder();

        for (TagType<JellyType> tagType : taggedTypeDefinition.getTags())
        {
            if (tagType.getInner() == null)
            {
                tagTypes.add(overallType);
            }
            else
            {
                TypeExp innerTypeExp = tagType.getInner().makeTypeExp(ImmutableMap.copyOf(typeVarsByName));
                tagTypes.add(TypeCons.function(src, ImmutableList.of(innerTypeExp), overallType));
            }
        }
        
        return new Pair<>(
            overallType
        , tagTypes.build());
    }

    public static TypeExp bool(@Nullable ExpressionBase src)
    {
        return new TypeCons(src, TypeExp.CONS_BOOLEAN, ALL_TYPE_CLASSES);
    }
    
    public static TypeExp text(@Nullable ExpressionBase src)
    {
        return new TypeCons(src, CONS_TEXT, ALL_TYPE_CLASSES);
    }

    public static TypeExp plainNumber(@Nullable ExpressionBase src)
    {
        return new NumTypeExp(src, UnitExp.SCALAR);
    }

    public static TypeExp list(@Nullable ExpressionBase src, TypeExp inner)
    {
        return new TypeCons(src, TypeExp.CONS_LIST, ImmutableList.of(Either.<UnitExp, TypeExp>right(inner)), ALL_TYPE_CLASSES);
    }
    
    public static TypeExp function(@Nullable ExpressionBase src, ImmutableList<TypeExp> paramTypes, TypeExp returnType)
    {
        return new TypeCons(src, TypeExp.CONS_FUNCTION, Utility.<Either<UnitExp, TypeExp>>concatI(Utility.<TypeExp, Either<UnitExp, TypeExp>>mapListI(paramTypes, p -> Either.<UnitExp, TypeExp>right(p)), ImmutableList.<Either<UnitExp, TypeExp>>of(Either.<UnitExp, TypeExp>right(returnType))), ImmutableSet.of());
    }

    public static TypeExp fromDataType(@Nullable ExpressionBase src, DataType dataType) throws InternalException
    {
        return dataType.apply(new DataTypeVisitorEx<TypeExp, InternalException>()
        {
            @Override
            public TypeExp number(NumberInfo numberInfo) throws InternalException, InternalException
            {
                return new NumTypeExp(src, UnitExp.fromConcrete(numberInfo.getUnit()));
            }

            @Override
            public TypeExp text() throws InternalException, InternalException
            {
                return TypeExp.text(src);
            }

            @Override
            public TypeExp date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
            {
                @SuppressWarnings("identifier")
                @ExpressionIdentifier String typeName = dataType.toString();
                return new TypeCons(src, typeName, ALL_TYPE_CLASSES);
            }

            @Override
            public TypeExp bool() throws InternalException, InternalException
            {
                return TypeExp.bool(src);
            }

            @Override
            public TypeExp tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
            {
                return new TypeCons(src, typeName.getRaw(), Utility.mapListInt(typeVars, e -> e.mapBothInt(u -> UnitExp.fromConcrete(u), v -> fromDataType(src, v))), ALL_TYPE_CLASSES);
            }

            @Override
            public TypeExp record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                return new RecordTypeExp(src, Utility.mapValuesInt(fields, t -> fromDataType(src, t)), true);
            }

            @Override
            public TypeExp array(@Nullable DataType inner) throws InternalException, InternalException
            {
                return new TypeCons(src, CONS_LIST, ImmutableList.of(Either.<UnitExp, TypeExp>right(inner == null ? new MutVar(src) : fromDataType(src, inner))), ALL_TYPE_CLASSES);
            }

            @Override
            public TypeExp function(ImmutableList<DataType> argTypes, DataType resultType) throws InternalException, InternalException
            {
                return new TypeCons(src, CONS_FUNCTION, Utility.<Either<UnitExp, TypeExp>>concatI(Utility.<DataType, Either<UnitExp, TypeExp>>mapListInt(argTypes, (DataType a) -> Either.<UnitExp, TypeExp>right(fromDataType(null, a))), ImmutableList.<Either<UnitExp, TypeExp>>of(Either.<UnitExp, TypeExp>right(fromDataType(null, resultType)))), ImmutableSet.of());
            }
        });
    }

    public Either<TypeConcretisationError, DataType> toConcreteType(TypeManager typeManager, boolean substituteDefaultIfPossible) throws InternalException, UserException
    {
        return prune()._concrete(typeManager, substituteDefaultIfPossible);
    }

    public Either<TypeConcretisationError, DataType> toConcreteType(TypeManager typeManager) throws InternalException, UserException
    {
        return toConcreteType(typeManager, false);

    }

    protected abstract Either<TypeConcretisationError, DataType> _concrete(TypeManager typeManager, boolean substituteDefaultIfPossible) throws InternalException, UserException;

    @Override
    @SuppressWarnings("i18n")
    public final @Localized String toString()
    {
        return toStyledString().toPlain();
    }

    public static boolean isList(TypeExp typeExp)
    {
        return typeExp instanceof TypeCons && ((TypeCons)typeExp).name.equals(TypeCons.CONS_LIST);
    }

    public static boolean isFunction(TypeExp typeExp)
    {
        return typeExp instanceof TypeCons && ((TypeCons)typeExp).name.equals(TypeCons.CONS_FUNCTION);
    }
    
    public static @Nullable ImmutableList<TypeExp> getFunctionArg(TypeExp functionTypeExp)
    {
        if (isFunction(functionTypeExp))
        {
            ImmutableList<Either<UnitExp, TypeExp>> operands = ((TypeCons) functionTypeExp).operands;
            return Either.<UnitExp, TypeExp, Either<UnitExp, TypeExp>>mapM(operands.subList(0, operands.size() - 1), Function.<Either<UnitExp, TypeExp>>identity()).<@Nullable ImmutableList<TypeExp>>either(u -> null, t -> t);
        }
        return null;
    }

    public static @Nullable TypeExp getFunctionResult(TypeExp functionTypeExp)
    {
        if (isFunction(functionTypeExp))
        {
            ImmutableList<Either<UnitExp, TypeExp>> operands = ((TypeCons) functionTypeExp).operands;
            return operands.get(operands.size() - 1).<@Nullable TypeExp>either(u -> null, t -> t);
        }
        return null;
    }

    /**
     * Adds all the given type-classes as constraints to this TypeExp if possible.  If not, an error is returned.
     */
    public final @Nullable TypeError requireTypeClasses(TypeClassRequirements typeClasses)
    {
        return requireTypeClasses(typeClasses, new IdentityHashSet<>());
    }

    /**
     * Adds all the given type-classes as constraints to this TypeExp if possible.  If not, an error is returned.
     * The visitedMutVar keeps track of visited MutVar to prevent infinite
     * recursion in case of there being a cycle in the type.
     */
    public abstract @Nullable TypeError requireTypeClasses(TypeClassRequirements typeClasses, IdentityHashSet<MutVar> visitedMutVar);

    @Override
    public final StyledString toStyledString()
    {
        return toStyledString(4);
    }
    
    protected abstract StyledString toStyledString(int maxDepth);
    
    public static final class TypeError
    {
        private final StyledString message;
        private final ImmutableList<TypeExp> couldNotUnify;

        public TypeError(StyledString message, ImmutableList<TypeExp> couldNotUnify)
        {
            this.message = message;
            this.couldNotUnify = couldNotUnify;
        }

        public StyledString getMessage()
        {
            return message;
        }

        public ImmutableList<TypeExp> getCouldNotUnify()
        {
            return couldNotUnify;
        }
        
        // For testing
        @Override
        public String toString()
        {
            return getMessage().toPlain();
        }
    }
}
