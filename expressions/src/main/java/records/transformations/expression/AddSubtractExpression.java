package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.function.FunctionLookup;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import records.typeExp.TypeExp.TypeError;
import records.typeExp.units.MutUnitVar;
import records.typeExp.units.UnitExp;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.Utility;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static records.transformations.expression.AddSubtractExpression.AddSubtractOp.ADD;

/**
 * Created by neil on 10/12/2016.
 */
public class AddSubtractExpression extends NaryOpTotalExpression
{
    public static enum AddSubtractOp
    { ADD, SUBTRACT };
    private final ImmutableList<AddSubtractOp> ops;
    private @Nullable CheckedExp type;

    public AddSubtractExpression(List<@Recorded Expression> expressions, List<AddSubtractOp> addSubtractOps)
    {
        super(expressions);
        this.ops = ImmutableList.copyOf(addSubtractOps);
        if (addSubtractOps.isEmpty())
            Log.logStackTrace("Ops empty");
    }

    @Override
    public NaryOpExpression copyNoNull(List<@Recorded Expression> replacements)
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
                running = i == 0 || ops.get(i) == AddSubtractOp.ADD ? running.plus(r.get()) : running.minus(r.get());
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
    public @Nullable CheckedExp checkNaryOp(@Recorded AddSubtractExpression this, ColumnLookup dataLookup, TypeState state, ExpressionKind kind, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        type = onError.recordType(this, state, checkAllOperandsSameTypeAndNotPatterns(new NumTypeExp(this, new UnitExp(new MutUnitVar())), dataLookup, state, LocationInfo.UNIT_CONSTRAINED, onError, p -> {
            @Nullable TypeExp ourType = p.getOurType();
            if (ourType == null)
                return ImmutableMap.of();
            if (ourType.prune() instanceof NumTypeExp)
            {
                ImmutableList<QuickFix<Expression>> fixes = ExpressionUtil.getFixesForMatchingNumericUnits(state, p);
                if (fixes.isEmpty())
                    return ImmutableMap.of();
                else
                    return ImmutableMap.of(this, new Pair<@Nullable TypeError, ImmutableList<QuickFix<Expression>>>(null, fixes));
            }
            @Nullable TypeError err = null;
            if (p.getAvailableTypesForError().size() > 1)
            {
                err = new TypeError(StyledString.concat(StyledString.s("Adding/subtracting requires numbers (with identical units), but found "), ourType.toStyledString()), p.getAvailableTypesForError());
            }
            ImmutableList.Builder<QuickFix<Expression>> fixes = ImmutableList.builder();
            // Is the problematic type text, and all ops '+'? If so, offer to convert it 
            
            // Note: we don't unify here because we don't want to alter the type.  We could try a 
            // "could this be string?" unification attempt, but really we're only interested in offering
            // the quick fix if it is definitely a string, for which we can use equals:
            if (ourType.equals(TypeExp.text(null)) && ops.stream().allMatch(op -> op.equals(AddSubtractOp.ADD)))
            {
                fixes.add(new QuickFix<Expression>("fix.stringConcat", this, () -> new StringConcatExpression(expressions)));
            }
            try
            {
                FunctionLookup functionLookup = state.getFunctionLookup();
                TypeExp pruned = ourType.prune();
                if (pruned.equals(TypeExp.fromDataType(null, DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)))) && expressions.size() == 2 && ops.size() == 1 && ops.get(0) == AddSubtractOp.SUBTRACT)
                {
                    fixes.add(new QuickFix<Expression>("fix.daysBetween", this, () -> new CallExpression(functionLookup, "days between", expressions.toArray(new Expression[0]))));
                }
                else if (pruned.equals(TypeExp.fromDataType(null, DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)))) && expressions.size() == 2 && ops.size() == 1 && ops.get(0) == AddSubtractOp.SUBTRACT)
                {
                    fixes.add(new QuickFix<Expression>("fix.secondsBetween", this, () -> new CallExpression(functionLookup, "seconds between", expressions.toArray(new Expression[0]))));
                }
            }
            catch (InternalException e)
            {
                Log.log(e);
            }

            if (ourType instanceof NumTypeExp)
                fixes.addAll(ExpressionUtil.getFixesForMatchingNumericUnits(state, p));
            ImmutableList<QuickFix<Expression>> builtFixes = fixes.build();
            return err == null && builtFixes.isEmpty() ? ImmutableMap.<@Recorded Expression, Pair<@Nullable TypeError, ImmutableList<QuickFix<Expression>>>>of() : ImmutableMap.<@Recorded Expression, Pair<@Nullable TypeError, ImmutableList<QuickFix<Expression>>>>of(p.getOurExpression(), new Pair<@Nullable TypeError, ImmutableList<QuickFix<Expression>>>(err, builtFixes));
        }));
        return type;
    }

    @Override
    @OnThread(Tag.Simulation)
    public ValueResult getValueNaryOp(ImmutableList<ValueResult> values, EvaluateState state) throws InternalException
    {
        @Value Number n = Utility.cast(values.get(0).value, Number.class);
        for (int i = 1; i < expressions.size(); i++)
        {
            //System.err.println("Actual Cur: " + Utility.toBigDecimal(n).toPlainString() + " after " + expressions.get(i-1).save(true));
            n = Utility.addSubtractNumbers(n, Utility.cast(values.get(i).value, Number.class), ops.get(i - 1) == ADD);
        }
        //System.err.println("Actual Result: " + Utility.toBigDecimal(n).toPlainString() + " after " + expressions.get(expressions.size()-1).save(true));
        return result(n, state, values);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(type == null ? null : type.typeExp)));
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.addSubtract(this, expressions, ops);
    }
}
