package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperatorEntry;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Random;

/**
 * An expression with mixed operators, which make it invalid.  Can't be run, but may be
 * used while editing and for loading/saving invalid expressions.
 */
public class InvalidOperatorExpression extends NonOperatorExpression
{
    private final ImmutableList<Either<String, Expression>> items;

    public InvalidOperatorExpression(ImmutableList<Either<String, @Recorded Expression>> operands)
    {
        this.items = operands;
    }

    @Override
    public @Nullable CheckedExp checkNaryOp(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        onError.recordError(this, StyledString.s("Mixed or invalid operators in expression"));
        return null; // Invalid expressions can't type check
    }

    @Override
    public Pair<@Value Object, EvaluateState> getValueNaryOp(EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Cannot get value for invalid expression");
    }

    @Override
    public NaryOpExpression copyNoNull(List<@Recorded Expression> replacements)
    {
        return new InvalidOperatorExpression(replacements, operators);
    }

    @Override
    protected String getSpecialPrefix()
    {
        return "@invalidops ";
    }

    @Override
    protected String saveOp(int index)
    {
        return "\"" + GrammarUtility.escapeChars(operators.get(index)) + "\"";
    }

    @Override
    public ImmutableList<SingleLoader<Expression, ExpressionNodeParent>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        ImmutableList.Builder<SingleLoader<Expression, ExpressionNodeParent>> r = ImmutableList.builder();
        for (int i = 0; i < operators.size(); i++)
        {
            int iFinal = i;
            ops.add((p, s) -> new OperatorEntry<>(Expression.class, operators.get(iFinal), false, p));
        }
        return new Pair<>(Utility.mapList(expressions, e -> e.loadAsSingle()), ops);
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        InvalidOperatorExpression that = (InvalidOperatorExpression) o;

        return operators.equals(that.operators);
    }

    @Override
    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + operators.hashCode();
        return result;
    }
}
