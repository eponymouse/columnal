package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.types.TypeExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * An expression with mixed operators, which make it invalid.  Can't be run, but may be
 * used while editing and for loading/saving invalid expressions.
 */
public class InvalidOperatorExpression extends NaryOpExpression
{
    private final List<String> operators;

    public InvalidOperatorExpression(List<Expression> operands, List<String> operators)
    {
        super(operands);
        this.operators = operators;
    }

    @Override
    public @Nullable @Recorded TypeExp check(RecordSet data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // TODO give error and quick fix (bracketing)
        return null; // Invalid expressions can't type check
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Cannot get value for invalid expression");
    }

    @Override
    public NaryOpExpression copyNoNull(List<Expression> replacements)
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
    public Pair<List<SingleLoader<OperandNode<Expression>>>, List<SingleLoader<OperatorEntry<Expression, ExpressionNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        List<SingleLoader<OperatorEntry<Expression, ExpressionNodeParent>>> ops = new ArrayList<>();
        for (int i = 0; i < operators.size(); i++)
        {
            int iFinal = i;
            ops.add((p, s) -> new OperatorEntry<>(Expression.class, operators.get(iFinal), false, p));
        }
        return new Pair<>(Utility.mapList(expressions, e -> e.loadAsSingle()), ops);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
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
