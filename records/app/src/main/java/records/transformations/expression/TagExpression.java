package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import utility.TaggedValue;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import records.loadsave.OutputBuilder;
import records.transformations.expression.TypeState.TypeAndTagInfo;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;

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
     * The tag: first item is the type name, second item is the tag name
     */
    private final Pair<String, String> tagName;
    /**
     * The inner expression.  May be empty if it's just a nullary tag name.
     */
    private final @Nullable Expression inner;
    /**
     * During type-checking, we store the index of the tag if we successfully
     * find the type and tag that we match
     */
    private int index;
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
    public TagExpression(Pair<String, String> tagName, @Nullable Expression inner)
    {
        this.tagName = tagName;
        this.inner = inner;
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        // First step is to the find the tag
        @Nullable TypeAndTagInfo typeAndIndex = state.findTaggedType(tagName, onError.recordError(this));
        // Not found; nothing more we can do:
        if (typeAndIndex == null)
            return null;
        index = typeAndIndex.tagIndex;

        innerDerivedType = inner == null ? null : inner.check(data, state, onError);
        // We must not pass nulls to checkSame if inner is empty, as that counts as failed checking, not optional items:
        boolean innerExpAndTypeBlank = inner == null && typeAndIndex.innerType == null;
        if (innerExpAndTypeBlank)
            return typeAndIndex.wholeType;
        // If inner expression is null, it's meant to be there:
        if (inner == null)
        {
            onError.recordError(this, "Tag " + tagName.getFirst() + ":" + tagName.getSecond() + " requires value, but none given");
            return null;
        }
        if (typeAndIndex.innerType == null)
        {
            onError.recordError(this, "Tag " + tagName.getFirst() + ":" + tagName.getSecond() + " has no inner value, but one was given");
            return null;
        }
        if (innerExpAndTypeBlank || DataType.checkSame(typeAndIndex.innerType, innerDerivedType, onError.recordError(this)) != null)
        {
            return typeAndIndex.wholeType;
        }
        else
        {
            return null;
        }
    }

    @Override
    public @Nullable Pair<DataType, TypeState> checkAsPattern(boolean varAllowed, DataType srcType, RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        @Nullable TypeAndTagInfo typeAndIndex = state.findTaggedType(tagName, onError.recordError(this));
        if (typeAndIndex == null)
            return null;
        index = typeAndIndex.tagIndex;

        @Nullable Pair<DataType, TypeState> typeAndState = (inner == null || typeAndIndex.innerType == null) ? null : inner.checkAsPattern(varAllowed, typeAndIndex.innerType, data, state, onError);
        if (typeAndState != null)
            innerDerivedType = typeAndState.getFirst();
        // We must not pass nulls to checkSame as that counts as failed checking, not optional items
        if ((inner == null && typeAndIndex.innerType == null && typeAndState == null) ||
            (inner != null && typeAndIndex.innerType != null && typeAndState != null && DataType.checkSame(typeAndIndex.innerType, innerDerivedType, onError.recordError(this)) != null))
        {
            return new Pair<>(typeAndIndex.wholeType, typeAndState == null ? state : typeAndState.getSecond());
        }
        else
            return null;
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return new TaggedValue(index, inner == null ? null : inner.getValue(rowIndex, state));
    }

    @Override
    @OnThread(Tag.Simulation)
    public @Nullable EvaluateState matchAsPattern(int rowIndex, @Value Object value, EvaluateState state) throws InternalException, UserException
    {
        if (value instanceof TaggedValue)
        {
            TaggedValue themTagged = (TaggedValue) value;
            int theirIndex = themTagged.getTagIndex();
            if (index != theirIndex)
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
        @Nullable String typeName = tagName.getFirst();
        String tag = "@tag " + OutputBuilder.quotedIfNecessary(typeName) + "\\" + OutputBuilder.quotedIfNecessary(tagName.getSecond());
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
    public FXPlatformFunction<ConsecutiveBase<Expression>, OperandNode<Expression>> loadAsSingle()
    {
        throw new RuntimeException("TODO");
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return inner == null ? Stream.empty() : inner._test_allMutationPoints().map(p -> p.replaceSecond(e -> new TagExpression(tagName, p.getSecond().apply(e))));
    }

    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        // TODO could replace with known invalid tag
        if (inner == null)
            // Shouldn't have type; add one:
            return new TagExpression(tagName, newExpressionOfDifferentType.getAnyType());
        else
        {
            if (r.nextBoolean())
                // Should have type; scrap it:
                return new TagExpression(tagName, null);
            else
                // Should have type, but replace with different:
                return new TagExpression(tagName, newExpressionOfDifferentType.getDifferentType(innerDerivedType));
        }
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TagExpression that = (TagExpression) o;

        if (!tagName.equals(that.tagName)) return false;
        return inner != null ? inner.equals(that.inner) : that.inner == null;
    }

    @Override
    public int hashCode()
    {
        int result = tagName.hashCode();
        result = 31 * result + (inner != null ? inner.hashCode() : 0);
        return result;
    }

    public Pair<String, String> _test_getTagName()
    {
        return tagName;
    }

    @Pure
    public @Nullable Expression getInner()
    {
        return inner;
    }

    public static Expression _testMake(String typeName, String tagName, Expression inner)
    {
        return new TagExpression(new Pair<>(typeName, tagName), inner);
    }
}
