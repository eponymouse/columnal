package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.jetbrains.annotations.NotNull;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.TypeManager.TagInfo;
import records.gui.expressioneditor.TagExpressionNode;
import utility.Either;
import utility.TaggedValue;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.expressioneditor.OperandNode;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * An expression with a tagged type's tag (e.g. "Just" or "Nothing"), followed
 * optionally by an inner expression.
 */
public class TagExpression extends NonOperatorExpression
{
    /**
     * If unknown type or tag, Left(tagName).  If known type and
     * tag, Right(tagInfo).
     */
    private final Either<String, TagInfo> tag;
    /**
     * The inner expression.  May be empty if it's just a nullary tag name.
     */
    private final @Nullable Expression inner;
    /**
     * During type-checking, if we have an inner expression, we store the type
     * which we figured out for it
     */
    private @Nullable DataType innerDerivedType;

    /**
     *
     * @param tagName The (type name, tag name) of the tag this expression refers to.
     *                It is not guaranteed to be a valid type/tag combination;
     *                that is checked later during type-checking
     * @param inner   The optional inner expression for this tag expression.
     */
    public TagExpression(Either<String, TagInfo> tag, @Nullable Expression inner)
    {
        this.tag = tag;
        this.inner = inner;
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        @Nullable TagInfo typeAndIndex = tag.<@Nullable TagInfo>either(s -> null, x -> x);
        // Not valid tag; nothing more we can do:
        if (typeAndIndex == null)
            return null;

        innerDerivedType = inner == null ? null : inner.check(data, state, onError);
        // We must not pass nulls to checkSame if inner is empty, as that counts as failed checking, not optional items:
        boolean innerExpAndTypeBlank = inner == null && typeAndIndex.tagInfo.getInner() == null;
        if (innerExpAndTypeBlank)
            return typeAndIndex.wholeType;
        // If inner expression is null, it's meant to be there:
        if (inner == null)
        {
            onError.recordError(this, "Tag " + getQualifiedTagName() + " requires value, but none given");
            return null;
        }
        if (typeAndIndex.tagInfo.getInner() == null)
        {
            onError.recordError(this, "Tag " + getQualifiedTagName() + " has no inner value, but one was given");
            return null;
        }
        if (innerExpAndTypeBlank || DataType.checkSame(typeAndIndex.tagInfo.getInner(), innerDerivedType, onError.recordError(this)) != null)
        {
            return typeAndIndex.wholeType;
        }
        else
        {
            return null;
        }
    }

    @NotNull
    private String getQualifiedTagName()
    {
        return tag.either(s -> "?:" + s, p -> p.wholeTypeName + ":" + p.tagInfo.getName());
    }

    @Override
    public @Nullable Pair<DataType, TypeState> checkAsPattern(boolean varAllowed, DataType srcType, RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        @Nullable TagInfo typeAndIndex = tag.<@Nullable TagInfo>either(s -> null, x -> x);
        if (typeAndIndex == null)
            return null;

        @Nullable Pair<DataType, TypeState> typeAndState = (inner == null || typeAndIndex.tagInfo.getInner() == null) ? null : inner.checkAsPattern(varAllowed, typeAndIndex.tagInfo.getInner(), data, state, onError);
        if (typeAndState != null)
            innerDerivedType = typeAndState.getFirst();
        // We must not pass nulls to checkSame as that counts as failed checking, not optional items
        if ((inner == null && typeAndIndex.tagInfo.getInner() == null && typeAndState == null) ||
            (inner != null && typeAndIndex.tagInfo.getInner() != null && typeAndState != null && DataType.checkSame(typeAndIndex.tagInfo.getInner(), innerDerivedType, onError.recordError(this)) != null))
        {
            return new Pair<>(typeAndIndex.wholeType, typeAndState == null ? state : typeAndState.getSecond());
        }
        else
            return null;
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        if (tag.isLeft())
            throw new InternalException("Calling getValue when type is invalid");
        return new TaggedValue(tag.getRight().tagIndex, inner == null ? null : inner.getValue(rowIndex, state));
    }

    @Override
    @OnThread(Tag.Simulation)
    public @Nullable EvaluateState matchAsPattern(int rowIndex, @Value Object value, EvaluateState state) throws InternalException, UserException
    {
        if (tag.isLeft())
            throw new InternalException("Calling matchAsPattern when type is invalid");

        if (value instanceof TaggedValue)
        {
            TaggedValue themTagged = (TaggedValue) value;
            int theirIndex = themTagged.getTagIndex();
            if (tag.getRight().tagIndex != theirIndex)
                return null;
            if (inner == null)
                return state; // Nothing further
            if (themTagged.getInner() == null)
                throw new InternalException("Type says inner type should be present but is instead null: " + this.inner);
            return inner.matchAsPattern(rowIndex, themTagged.getInner(), state);
        }
        throw new InternalException("Expected TaggedValue but found: " + value.getClass());
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return inner == null ? Stream.empty() : inner.allColumnNames();
    }

    @Override
    public String save(boolean topLevel)
    {
        String tag = this.tag.either(s -> "@unknowntag " + OutputBuilder.quotedIfNecessary(s), t -> "@tag " + OutputBuilder.quotedIfNecessary(t.wholeTypeName.getRaw()) + "\\" + OutputBuilder.quotedIfNecessary(t.tagInfo.getName()));
        if (inner == null)
            return tag;
        else
            return tag + ":" + inner.save(false);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public SingleLoader<OperandNode<Expression>> loadAsSingle()
    {
        return (p, s) -> new TagExpressionNode(p, s, tag, inner);
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return inner == null ? Stream.empty() : inner._test_allMutationPoints().map(p -> p.replaceSecond(e -> new TagExpression(tag, p.getSecond().apply(e))));
    }

    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        // TODO could replace with known invalid tag
        if (inner == null)
            // Shouldn't have type; add one:
            return new TagExpression(tag, newExpressionOfDifferentType.getAnyType());
        else
        {
            if (r.nextBoolean())
                // Should have type; scrap it:
                return new TagExpression(tag, null);
            else
                // Should have type, but replace with different:
                return new TagExpression(tag, newExpressionOfDifferentType.getDifferentType(innerDerivedType));
        }
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TagExpression that = (TagExpression) o;

        if (!tag.equals(that.tag)) return false;
        return inner != null ? inner.equals(that.inner) : that.inner == null;
    }

    @Override
    public int hashCode()
    {
        int result = tag.hashCode();
        result = 31 * result + (inner != null ? inner.hashCode() : 0);
        return result;
    }

    public String _test_getQualifiedTagName()
    {
        return getQualifiedTagName();
    }

    @Pure
    public @Nullable Expression getInner()
    {
        return inner;
    }

}
