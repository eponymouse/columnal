package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.StreamTreeBuilder;
import utility.Utility;
import utility.Utility.ListEx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * An array expression like [0, x, 3].  This could be called an array literal, but didn't want to confuse
 * as the items in the array don't have to be literals.  But this expression is for constructing
 * arrays of a known length from a fixed set of expressions (like [0, y] but not just "xs" which happens
 * to be of array type).
 */
public class ArrayExpression extends Expression
{
    private final ImmutableList<@Recorded Expression> items;
    private @Nullable TypeExp elementType;
    private @MonotonicNonNull List<TypeExp> _test_originalTypes;

    public ArrayExpression(ImmutableList<@Recorded Expression> items)
    {
        this.items = items;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState state, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // Empty array - special case:
        if (items.isEmpty())
            return onError.recordType(this, ExpressionKind.EXPRESSION, state, TypeExp.list(this, new MutVar(this)));
        TypeExp[] typeArray = new TypeExp[items.size()];
        ExpressionKind kind = ExpressionKind.EXPRESSION;
        for (int i = 0; i < typeArray.length; i++)
        {
            @Nullable CheckedExp c = items.get(i).check(dataLookup, state, LocationInfo.UNIT_DEFAULT, onError);
            if (c == null)
                return null;
            typeArray[i] = c.typeExp;
            state = c.typeState;
            kind = kind.or(c.expressionKind);
        }
        this.elementType = onError.recordError(this, TypeExp.unifyTypes(ImmutableList.copyOf(typeArray)));
        _test_originalTypes = Arrays.asList(typeArray);
        if (elementType == null)
            return null;
        return onError.recordType(this, kind, state, TypeExp.list(this, elementType));
    }

    @Override
    @OnThread(Tag.Simulation)
    public @Nullable EvaluateState matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        if (value instanceof ListEx)
        {
            ListEx list = (ListEx)value;
            if (list.size() != items.size())
                return null; // Not an exception, just means the value has different size to the pattern, so can't match
            @Nullable EvaluateState curState = state;
            for (int i = 0; i < items.size(); i++)
            {
                curState = items.get(i).matchAsPattern(list.get(i), curState);
                if (curState == null)
                    return null;
            }
            return curState;
        }
        throw new InternalException("Expected array but found " + value.getClass());
    }

    @Override
    @OnThread(Tag.Simulation)
    public Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        List<@Value Object> values = new ArrayList<>(items.size());
        for (Expression item : items)
        {
            Pair<@Value Object, EvaluateState> valueAndState = item.getValue(state);
            values.add(valueAndState.getFirst());
            state = valueAndState.getSecond();
        }
        return new Pair<>(DataTypeUtility.value(values), state);
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return items.stream().flatMap(Expression::allColumnReferences);
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "[" + items.stream().map(e -> e.save(structured, items.size() == 1 ? BracketedStatus.DIRECT_SQUARE_BRACKETED : BracketedStatus.MISC, renames)).collect(Collectors.joining(", ")) + "]";
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        return StyledString.concat(StyledString.s("["), items.stream().map(e -> e.toDisplay(items.size() == 1 ? BracketedStatus.DIRECT_SQUARE_BRACKETED : BracketedStatus.MISC)).collect(StyledString.joining(", ")), StyledString.s("]"));
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        StreamTreeBuilder<SingleLoader<Expression, ExpressionSaver>> r = new StreamTreeBuilder<>();
        r.add(GeneralExpressionEntry.load(Keyword.OPEN_SQUARE));
        for (int i = 0; i < items.size(); i++)
        {
            if (i > 0)
                r.add(GeneralExpressionEntry.load(Op.COMMA));
            Expression item = items.get(i);
            r.addAll(item.loadAsConsecutive(BracketedStatus.MISC));
        }
        r.add(GeneralExpressionEntry.load(Keyword.CLOSE_SQUARE));
        return r.stream();
    }

    @SuppressWarnings("recorded")
    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return IntStream.range(0, items.size()).mapToObj(i ->
            items.get(i)._test_allMutationPoints().map(p -> p.<Function<Expression, Expression>>replaceSecond(newExp -> new ArrayExpression(Utility.replaceList(items, i, p.getSecond().apply(newExp)))))).flatMap(s -> s);
    }

    @SuppressWarnings("recorded")
    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        if (items.size() <= 1)
            return null; // Can't cause a failure with 1 or less items; need 2+ to have a mismatch
        int index = r.nextInt(items.size());
        if (elementType == null || _test_originalTypes == null)
            throw new InternalException("Calling _test_typeFailure despite type-check failure");
        // If all items other than this one are blank arrays, won't cause type error:
        boolean hasOtherNonBlank = false;
        for (int i = 0; i < items.size(); i++)
        {
            if (i == index)
                continue;
            
            // We test if it is non-blank by unifying with an array type of MutVar, and seeing if the MutVat points to anything after pruning:
            MutVar mut = new MutVar(null);
            TypeExp arrayOfMut = TypeExp.list(null, mut);

            Either<StyledString, TypeExp> unifyResult = TypeExp.unifyTypes(_test_originalTypes.get(i), arrayOfMut);
            // If it doesn't match, not an array:
            if (unifyResult.isLeft())
            {
                hasOtherNonBlank = true;
            }
            else
            {
                hasOtherNonBlank = !(mut.prune() instanceof MutVar);
            }
        }
        if (!hasOtherNonBlank)
            return null; // Won't make a failure
        return new ArrayExpression(Utility.replaceList(items, index, newExpressionOfDifferentType.getDifferentType(elementType)));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ArrayExpression that = (ArrayExpression) o;

        return items.equals(that.items);
    }

    @Override
    public int hashCode()
    {
        return items.hashCode();
    }

    public ImmutableList<@Recorded Expression> _test_getElements()
    {
        return items;
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new ArrayExpression(Utility.mapListI(items, e -> e.replaceSubExpression(toReplace, replaceWith)));
    }
}
