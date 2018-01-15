package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.types.NumTypeExp;
import records.types.TypeExp;
import records.types.units.MutUnitVar;
import records.types.units.UnitExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static records.transformations.expression.AddSubtractExpression.Op.ADD;
import static records.transformations.expression.ErrorAndTypeRecorder.QuickFix.ReplacementTarget.PARENT;

/**
 * Created by neil on 10/12/2016.
 */
public class AddSubtractExpression extends NaryOpExpression
{
    public static enum Op { ADD, SUBTRACT };
    private final List<Op> ops;
    private @Nullable @Recorded TypeExp type;

    public AddSubtractExpression(List<Expression> expressions, List<Op> ops)
    {
        super(expressions);
        this.ops = ops;
    }

    @Override
    public NaryOpExpression copyNoNull(List<Expression> replacements)
    {
        return new AddSubtractExpression(replacements, ops);
    }

    @Override
    public Optional<Rational> constantFold()
    {
        Rational running = Rational.ZERO;
        for (int i = 0; i < expressions.size(); i++)
        {
            Optional<Rational> r = expressions.get(i).constantFold();
            if (r.isPresent())
            {
                running = i == 0 || ops.get(i) == Op.ADD ? running.plus(r.get()) : running.minus(r.get());
            }
            else
                return Optional.empty();
        }
        return Optional.of(running);
    }

    @Override
    protected String saveOp(int index)
    {
        return ops.get(index) == ADD ? "+" : "-";
    }

    @Override
    public @Recorded @Nullable TypeExp check(RecordSet data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        type = checkAllOperandsSameType(new NumTypeExp(this, new UnitExp(new MutUnitVar())), data, state, onError, p -> {
            StyledString err = StyledString.concat(StyledString.s("Operands to '+'/'-' must be numbers with matching units but found "), p.getFirst().toStyledString());
            try
            {
                // Note: we don't unify here because we don't want to alter the type.  We could try a 
                // "could this be string?" unification attempt, but really we're only interested in offering
                // the quick fix if it is definitely a string, for which we can use equals:
                if (p.getFirst().equals(TypeExp.fromConcrete(null, DataType.TEXT)) && ops.stream().allMatch(op -> op.equals(Op.ADD)))
                {
                    return new Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression>>>(err, ImmutableList.of(new QuickFix<Expression>("Change to string concatenation", params -> {
                        return new Pair<>(PARENT, new StringConcatExpression(expressions));
                    })));
                }
            }
            catch (InternalException e)
            {
                Utility.report(e);
            }
            return new Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression>>>(err, ImmutableList.of());
        });
        return type;
    }

    @Override
    @OnThread(Tag.Simulation)
    public @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        Number n = (Number)expressions.get(0).getValue(rowIndex, state);
        for (int i = 1; i < expressions.size(); i++)
        {
            //System.err.println("Actual Cur: " + Utility.toBigDecimal(n).toPlainString() + " after " + expressions.get(i-1).save(true));
            n = Utility.addSubtractNumbers(n, (Number)expressions.get(i).getValue(rowIndex, state), ops.get(i - 1) == ADD);
        }
        //System.err.println("Actual Result: " + Utility.toBigDecimal(n).toPlainString() + " after " + expressions.get(expressions.size()-1).save(true));
        return DataTypeUtility.value(n);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(type)));
    }
}
