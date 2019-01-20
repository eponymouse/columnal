package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditorUtil;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static records.transformations.expression.AddSubtractExpression.AddSubtractOp.ADD;

/**
 * Created by neil on 10/12/2016.
 */
public class AddSubtractExpression extends NaryOpExpression
{
    public static enum AddSubtractOp
    { ADD, SUBTRACT };
    private final List<AddSubtractOp> ops;
    private @Nullable CheckedExp type;

    public AddSubtractExpression(List<@Recorded Expression> expressions, List<AddSubtractOp> addSubtractOps)
    {
        super(expressions);
        this.ops = addSubtractOps;
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
    protected Op loadOp(int index)
    {
        return ops.get(index) == ADD ? Op.ADD : Op.SUBTRACT;
    }

    @Override
    public @Nullable CheckedExp checkNaryOp(ColumnLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        type = onError.recordType(this, ExpressionKind.EXPRESSION, state, checkAllOperandsSameTypeAndNotPatterns(new NumTypeExp(this, new UnitExp(new MutUnitVar())), dataLookup, state, LocationInfo.UNIT_CONSTRAINED, onError, p -> {
            @Nullable TypeExp ourType = p.getOurType();
            if (ourType == null)
                return new Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression,ExpressionSaver>>>(null, ImmutableList.of());
            @Nullable StyledString err = ourType == null || p.expressionTypes.stream().filter(Optional::isPresent).count() <= 1
                    ? null : StyledString.concat(StyledString.s("You can only add/subtract numbers (with identical units), but found "), ourType.toStyledString());
            ImmutableList.Builder<QuickFix<Expression,ExpressionSaver>> fixes = ImmutableList.builder();
            // Is the problematic type text, and all ops '+'? If so, offer to convert it 
            
            // Note: we don't unify here because we don't want to alter the type.  We could try a 
            // "could this be string?" unification attempt, but really we're only interested in offering
            // the quick fix if it is definitely a string, for which we can use equals:
            if (ourType.equals(TypeExp.text(null)) && ops.stream().allMatch(op -> op.equals(AddSubtractOp.ADD)))
            {
                fixes.add(new QuickFix<Expression,ExpressionSaver>("fix.stringConcat", this, () -> new StringConcatExpression(expressions)));
            }

            if (ourType instanceof NumTypeExp)
                fixes.addAll(ExpressionEditorUtil.getFixesForMatchingNumericUnits(state, p));
            ImmutableList<QuickFix<Expression, ExpressionSaver>> builtFixes = fixes.build();
            return err == null && builtFixes.isEmpty() ? null : new Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression,ExpressionSaver>>>(err, builtFixes);
        }));
        return type;
    }

    @Override
    @OnThread(Tag.Simulation)
    public Pair<@Value Object, EvaluateState> getValueNaryOp(EvaluateState state) throws UserException, InternalException
    {
        @Value Number n = Utility.cast(expressions.get(0).getValue(state).getFirst(), Number.class);
        for (int i = 1; i < expressions.size(); i++)
        {
            //System.err.println("Actual Cur: " + Utility.toBigDecimal(n).toPlainString() + " after " + expressions.get(i-1).save(true));
            n = Utility.addSubtractNumbers(n, Utility.cast(expressions.get(i).getValue(state).getFirst(), Number.class), ops.get(i - 1) == ADD);
        }
        //System.err.println("Actual Result: " + Utility.toBigDecimal(n).toPlainString() + " after " + expressions.get(expressions.size()-1).save(true));
        return new Pair<>(n, state);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(type == null ? null : type.typeExp)));
    }
}
