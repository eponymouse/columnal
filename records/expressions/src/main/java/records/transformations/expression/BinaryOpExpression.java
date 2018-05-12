package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.BracketedExpression;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
    protected @Nullable TypeExp lhsType;
    protected @Nullable TypeExp rhsType;

    protected BinaryOpExpression(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        String inner = lhs.save(BracketedStatus.MISC, renames) + " " + saveOp() + " " + rhs.save(BracketedStatus.MISC, renames);
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

    protected abstract String saveOp();

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
    @OnThread(Tag.Simulation)
    public final @Value Object getValue(EvaluateState state) throws UserException, InternalException
    {
        if (lhs instanceof ImplicitLambdaArg || rhs instanceof  ImplicitLambdaArg)
        {
            return ImplicitLambdaArg.makeImplicitFunction(ImmutableList.of(lhs, rhs), state, s -> getValueBinaryOp(s));
        }
        else
            return getValueBinaryOp(state);
    }

    @OnThread(Tag.Simulation)
    public abstract @Value Object getValueBinaryOp(EvaluateState state) throws UserException, InternalException;

    @Override
    public @Nullable @Recorded TypeExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        Pair<UnaryOperator<@Nullable TypeExp>, TypeState> lambda = ImplicitLambdaArg.detectImplicitLambda(this, ImmutableList.of(lhs, rhs), typeState);
        typeState = lambda.getSecond();
        lhsType = lhs.check(dataLookup, typeState, onError);
        rhsType = rhs.check(dataLookup, typeState, onError);
        if (lhsType == null || rhsType == null)
            return null;
        return onError.recordType(this, lambda.getFirst().apply(checkBinaryOp(dataLookup, typeState, onError)));
    }

    @RequiresNonNull({"lhsType", "rhsType"})
    protected abstract @Nullable TypeExp checkBinaryOp(TableLookup data, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException;

    @SuppressWarnings("recorded")
    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.<Pair<Expression, Function<Expression, Expression>>>concat(
            lhs._test_allMutationPoints().map(p -> new Pair<>(p.getFirst(), newLHS -> copy(p.getSecond().apply(newLHS), rhs))),
            rhs._test_allMutationPoints().map(p -> new Pair<>(p.getFirst(), newRHS -> copy(lhs, p.getSecond().apply(newRHS))))
        );
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        // Most binary ops require same type, so this is typical (can always override):
        if (r.nextBoolean())
        {
            return copy(lhsType != null && lhsType.prune() instanceof NumTypeExp ? newExpressionOfDifferentType.getNonNumericType() : newExpressionOfDifferentType.getDifferentType(rhsType), rhs);
        }
        else
        {
            return copy(lhs, rhsType != null && rhsType.prune() instanceof NumTypeExp ? newExpressionOfDifferentType.getNonNumericType() : newExpressionOfDifferentType.getDifferentType(lhsType));
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
    public Pair<List<SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>>>, List<SingleLoader<Expression, ExpressionNodeParent, OperatorEntry<Expression, ExpressionNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        return new Pair<>(Arrays.asList(lhs.loadAsSingle(), rhs.loadAsSingle()), Collections.singletonList((p, s) -> new OperatorEntry<Expression, ExpressionNodeParent>(Expression.class, saveOp(), false, p)));
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> new BracketedExpression(p, SingleLoader.withSemanticParent(loadAsConsecutive(true), s), ')');
    }

    public String _test_getOperatorEntry()
    {
        return saveOp();
    }
}
