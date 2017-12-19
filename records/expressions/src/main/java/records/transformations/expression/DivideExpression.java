package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.types.NumTypeExp;
import records.types.TypeExp;
import records.types.units.UnitExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Created by neil on 10/12/2016.
 */
public class DivideExpression extends BinaryOpExpression
{
    public DivideExpression(Expression lhs, Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "/";
    }

    @Override
    public BinaryOpExpression copy(@Nullable Expression replaceLHS, @Nullable Expression replaceRHS)
    {
        return new DivideExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    @Override
    @RequiresNonNull({"lhsType", "rhsType"})
    protected @Nullable TypeExp checkBinaryOp(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        @NonNull TypeExp lhsTypeFinal = lhsType;
        @NonNull TypeExp rhsTypeFinal = rhsType;
        UnitExp topUnit = UnitExp.makeVariable();
        UnitExp bottomUnit = UnitExp.makeVariable();
        // You can divide any number by any other number
        if (onError.recordError(this, TypeExp.unifyTypes(new NumTypeExp(this, topUnit), lhsTypeFinal)) == null
            || onError.recordError(this, TypeExp.unifyTypes(new NumTypeExp(this, bottomUnit), rhsTypeFinal)) == null)
        {
            return null;
        }
        return new NumTypeExp(this, topUnit.divideBy(bottomUnit));
    }

    @Override
    @OnThread(Tag.Simulation)
    public @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return DataTypeUtility.value(Utility.divideNumbers((Number)lhs.getValue(rowIndex, state), (Number)rhs.getValue(rowIndex, state)));
    }

    @Override
    public Optional<Rational> constantFold()
    {
        Optional<Rational> l = lhs.constantFold();
        Optional<Rational> r = rhs.constantFold();
        if (l.isPresent() && r.isPresent())
            return Optional.of(l.get().divides(r.get()));
        else
            return Optional.empty();
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        if (r.nextBoolean())
        {
            return copy(newExpressionOfDifferentType.getNonNumericType(), rhs);
        }
        else
        {
            return copy(lhs, newExpressionOfDifferentType.getNonNumericType());
        }
    }
}
