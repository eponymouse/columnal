package records.types;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.types.units.UnitExp;
import styled.StyledShowable;
import styled.StyledString;
import utility.Either;
import utility.Utility;

import java.util.Arrays;
import java.util.List;

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
    public static final String CONS_TEXT = "Text";
    public static final String CONS_BOOLEAN = "Boolean";
    public static final String CONS_LIST = "List";
    // For recording errors:
    protected final @Nullable ExpressionBase src;
    
    protected TypeExp(@Nullable ExpressionBase src)
    {
        this.src = src;
    }
    
    public static Either<StyledString, TypeExp> unifyTypes(TypeExp... types) throws InternalException
    {
        return unifyTypes(Arrays.asList(types));
    }
    
    public static Either<StyledString, TypeExp> unifyTypes(List<TypeExp> types) throws InternalException
    {
        if (types.isEmpty())
            return Either.left(StyledString.s("Error: no types available"));
        else if (types.size() == 1)
            return Either.right(types.get(0));
        else
        {
            Either<StyledString, TypeExp> r = types.get(0).unifyWith(types.get(1));
            for (int i = 2; i < types.size(); i++)
            {
                if (r.isLeft())
                    return r;
                TypeExp next = types.get(i);
                r = r.getRight().unifyWith(next);
            }
            return r;
        }
        
    }
    
    // package-protected:
    final Either<StyledString, TypeExp> unifyWith(TypeExp b) throws InternalException
    {
        TypeExp aPruned = prune();
        TypeExp bPruned = b.prune();
        // Make sure param isn't a MutVar unless it has to be:
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
    public abstract Either<StyledString, TypeExp> _unify(TypeExp b) throws InternalException;
    
    // Prunes any MutVars at the outermost level.
    public TypeExp prune()
    {
        return this;
    }

    /**
     * If possible, return a variant of this type without the containing
     * MutVar (basically, if you are a disjunction, eliminate those possibilities).  If null is returned, that wasn't possible: a cycle
     * is inevitable.
     */
    public abstract @Nullable TypeExp withoutMutVar(MutVar mutVar);
    
    protected final Either<StyledString, TypeExp> typeMismatch(TypeExp other)
    {
        return Either.left(StyledString.concat(StyledString.s("Type mismatch: "), toStyledString(), StyledString.s(" versus "), other.toStyledString()));
    }
    
    public static TypeExp fromTagged(@Nullable ExpressionBase src, TaggedTypeDefinition taggedTypeDefinition) throws InternalException
    {
        return new TypeCons(src, taggedTypeDefinition.getTaggedTypeName().getRaw(), taggedTypeDefinition.getTypeArguments().stream().map(_name -> new MutVar(src)).collect(ImmutableList.toImmutableList()));
    }

    public static TypeExp fromConcrete(@Nullable ExpressionBase src, DataType dataType) throws InternalException
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
                return new TypeCons(src, CONS_TEXT);
            }

            @Override
            public TypeExp date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
            {
                return new TypeCons(src, dateTimeInfo.getType().toString());
            }

            @Override
            public TypeExp bool() throws InternalException, InternalException
            {
                return new TypeCons(src, CONS_BOOLEAN);
            }

            @Override
            public TypeExp tagged(TypeId typeName, ImmutableList<DataType> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
            {
                return new TypeCons(src, typeName.getRaw(), Utility.mapListInt(typeVars, v -> fromConcrete(src, v)));
            }

            @Override
            public TypeExp tuple(ImmutableList<DataType> inner) throws InternalException, InternalException
            {
                return new TupleTypeExp(src, Utility.mapListInt(inner, t -> fromConcrete(src, t)), true);
            }

            @Override
            public TypeExp array(@Nullable DataType inner) throws InternalException, InternalException
            {
                return new TypeCons(src, CONS_LIST, inner == null ? new MutVar(src) : fromConcrete(src, inner));
            }
        });
    }

    public Either<TypeConcretisationError, DataType> toConcreteType(TypeManager typeManager) throws InternalException, UserException
    {
        return prune()._concrete(typeManager);
        
    }

    protected abstract Either<TypeConcretisationError, DataType> _concrete(TypeManager typeManager) throws InternalException, UserException;

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
}
