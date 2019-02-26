package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.StreamTreeBuilder;

import java.util.Random;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Created by neil on 25/11/2016.
 */
public abstract class BinaryOpExpression extends Expression
{
    /*
    public static enum Op
    {
        AND("&"), OR("|"), EQUALS("="), ADD("+"), SUBTRACT("-");

        private final String symbol;

        Op(String symbol)
        {
            this.symbol = symbol;
        }

        public static @Nullable Op parse(String text)
        {
            for (Op op : values())
                if (op.symbol.equals(text))
                    return op;
            return null;
        }
    }

    private final Op op;*/
    protected final @Recorded Expression lhs;
    protected final @Recorded Expression rhs;
    protected @MonotonicNonNull CheckedExp lhsType;
    protected @MonotonicNonNull CheckedExp rhsType;

    protected BinaryOpExpression(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String inner = lhs.save(structured, BracketedStatus.MISC, renames) + " " + saveOp() + " " + rhs.save(structured, BracketedStatus.MISC, renames);
        if (surround != BracketedStatus.MISC)
            return inner;
        else
            return "(" + inner + ")";
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        StyledString inner = StyledString.concat(lhs.toDisplay(BracketedStatus.MISC), StyledString.s(" " + saveOp() + " "), rhs.toDisplay(BracketedStatus.MISC));
        if (surround != BracketedStatus.MISC)
            return inner;
        else
            return StyledString.roundBracket(inner);
    }

    // For saving to string:
    protected abstract String saveOp();
    
    // For loading in expression editor:
    protected abstract Op loadOp();

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.concat(lhs.allColumnReferences(), rhs.allColumnReferences());
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BinaryOpExpression that = (BinaryOpExpression) o;

        if (!saveOp().equals(that.saveOp())) return false;
        if (!lhs.equals(that.lhs)) return false;
        return rhs.equals(that.rhs);
    }

    @Override
    public int hashCode()
    {
        int result = lhs.hashCode();
        result = 31 * result + saveOp().hashCode();
        result = 31 * result + rhs.hashCode();
        return result;
    }
    
    public abstract BinaryOpExpression copy(@Nullable @Recorded Expression replaceLHS, @Nullable @Recorded Expression replaceRHS);

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return copy(lhs.replaceSubExpression(toReplace, replaceWith), rhs.replaceSubExpression(toReplace, replaceWith));
    }

    @Override
    @OnThread(Tag.Simulation)
    public final ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        if (lhs instanceof ImplicitLambdaArg || rhs instanceof ImplicitLambdaArg)
        {
            return new ValueResult(ImplicitLambdaArg.makeImplicitFunction(ImmutableList.of(lhs, rhs), state, s -> {
                Pair<@Value Object, EvaluateState> r = getValueBinaryOp(s);
                explanation = makeExplanation(state, new ValueResult(r.getFirst(), state, ImmutableList.of(lhs, rhs)));
                return r.getFirst();
            }), ImmutableList.of(lhs, rhs));
        }
        else
        {
            Pair<@Value Object, EvaluateState> r = getValueBinaryOp(state);
            return new ValueResult(r.getFirst(), r.getSecond(), ImmutableList.of(lhs, rhs));
        }
    }

    @OnThread(Tag.Simulation)
    public abstract Pair<@Value Object, EvaluateState> getValueBinaryOp(EvaluateState state) throws UserException, InternalException;

    @Override
    public final @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        Pair<@Nullable UnaryOperator<@Recorded TypeExp>, TypeState> lambda = ImplicitLambdaArg.detectImplicitLambda(this, ImmutableList.of(lhs, rhs), typeState, onError);
        typeState = lambda.getSecond();
        @Nullable CheckedExp lhsChecked = lhs.check(dataLookup, typeState, argLocationInfo(), onError);
        if (lhsChecked == null)
            return null;
        @Nullable CheckedExp rhsChecked = rhs.check(dataLookup, lhsChecked.typeState, argLocationInfo(), onError);
        if (rhsChecked == null)
            return null;
        if (lhsChecked.expressionKind == ExpressionKind.PATTERN)
            onError.recordError(lhs, StyledString.s("Operand to " + saveOp() + " cannot be a pattern"));
        if (rhsChecked.expressionKind == ExpressionKind.PATTERN)
            onError.recordError(rhs, StyledString.s("Operand to " + saveOp() + " cannot be a pattern"));
        lhsType = lhsChecked;
        rhsType = rhsChecked;
        @Nullable CheckedExp checked = checkBinaryOp(dataLookup, typeState, onError);
        return checked == null ? null : checked.applyToType(lambda.getFirst());
    }

    protected LocationInfo argLocationInfo()
    {
        return LocationInfo.UNIT_DEFAULT;
    }

    @RequiresNonNull({"lhsType", "rhsType"})
    protected abstract @Nullable CheckedExp checkBinaryOp(ColumnLookup data, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException;

    @SuppressWarnings("recorded")
    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.<Pair<Expression, Function<Expression, Expression>>>concat(
            lhs._test_allMutationPoints().<Pair<Expression, Function<Expression, Expression>>>map(p -> new Pair<>(p.getFirst(), newLHS -> copy(p.getSecond().apply(newLHS), rhs))),
            rhs._test_allMutationPoints().<Pair<Expression, Function<Expression, Expression>>>map(p -> new Pair<>(p.getFirst(), newRHS -> copy(lhs, p.getSecond().apply(newRHS))))
        );
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        // Most binary ops require same type, so this is typical (can always override):
        if (r.nextBoolean())
        {
            return copy(lhsType != null && lhsType.typeExp.prune() instanceof NumTypeExp ? newExpressionOfDifferentType.getNonNumericType() : newExpressionOfDifferentType.getDifferentType(rhsType == null ? null : rhsType.typeExp), rhs);
        }
        else
        {
            return copy(lhs, rhsType != null && rhsType.typeExp.prune() instanceof NumTypeExp ? newExpressionOfDifferentType.getNonNumericType() : newExpressionOfDifferentType.getDifferentType(lhsType == null ? null : lhsType.typeExp));
        }
    }

    public Expression getLHS()
    {
        return lhs;
    }

    public Expression getRHS()
    {
        return rhs;
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        StreamTreeBuilder<SingleLoader<Expression, ExpressionSaver>> r = new StreamTreeBuilder<>();
        roundBracket(bracketedStatus, false, r, () -> {
            r.addAll(lhs.loadAsConsecutive(BracketedStatus.MISC));
            r.add(GeneralExpressionEntry.load(loadOp()));
            r.addAll(rhs.loadAsConsecutive(BracketedStatus.MISC));
        });
        return r.stream();
    }

    public String _test_getOperatorEntry()
    {
        return saveOp();
    }
}
