package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.gui.expressioneditor.UnitEntry.UnitText;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.InvalidOperatorUnitExpression;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnitDivideExpression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitExpressionIntLiteral;
import records.transformations.expression.UnitRaiseExpression;
import records.transformations.expression.UnitTimesExpression;
import utility.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class UnitExpressionOps implements OperandOps<UnitExpression, UnitNodeParent>
{
    private final static ImmutableList<Pair<String, @Localized String>> OPERATORS = ImmutableList.<Pair<String, @Localized String>>copyOf(Arrays.<Pair<String, @Localized String>>asList(
        DeepNodeTree.opD("*", "op.times"),
        DeepNodeTree.opD("/", "op.divide"),
        DeepNodeTree.opD("^", "op.raise")
    ));
    private final Set<Integer> ALPHABET = OPERATORS.stream().map(UnitExpressionOps::getOp).flatMapToInt(String::codePoints).boxed().collect(Collectors.<@NonNull Integer>toSet());

    private static String getOp(Pair<String, @Localized String> p)
    {
        return p.getFirst();
    }

    @Override
    public EntryNode<UnitExpression, UnitNodeParent> makeGeneral(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, @Nullable String initialContent)
    {
        return new UnitEntry(parent, new UnitText(initialContent == null ? "" : initialContent));
    }

    //@Override
    //public ImmutableList<Pair<String, @Localized String>> getValidOperators()
    //{
        //return OPERATORS;
    //}

    @Override
    public boolean isOperatorAlphabet(char character)
    {
        return ALPHABET.contains((int)character);
    }

    @Override
    public Class<UnitExpression> getOperandClass()
    {
        return UnitExpression.class;
    }

    /*
    @Override
    public @UnknownIfRecorded UnitExpression makeExpression(ErrorDisplayerRecord errorDisplayers, ImmutableList<@Recorded UnitExpression> originalOperands, List<String> ops, BracketedStatus bracketedStatus)
    {
        // Make copy for editing:
        ArrayList<@Recorded UnitExpression> operands = new ArrayList<>(originalOperands);
        ops = new ArrayList<>(ops);

        //System.err.println("Original ops: " + Utility.listToString(ops) + " " + ops.size());
        //System.err.println("  Operands: " + Utility.listToString(Utility.mapList(operands, o -> o.getClass().getName() + ":" + o.save(false))));

        // Trim blanks from end:
        ConsecutiveBase.removeBlanks(operands, ops, (Object o) -> o instanceof String ? ((String)o).trim().isEmpty() : o instanceof SingleUnitExpression && ((SingleUnitExpression)o).getText().trim().isEmpty(), o -> false, o -> {}, false, null);

        //System.err.println("  Trimmed: " + Utility.listToString(ops) + " " + ops.size());

        // Go through and sort out any raise expressions:
        int i = 0;
        while (i < ops.size())
        {
            // Raise binds tightest, so just take the adjacent operands:
            // It will bind left, but that doesn't matter as chained raise is an error anyway:
            if (ops.get(i).trim().equals("^") && i + 1 < operands.size() && operands.get(i + 1) instanceof UnitExpressionIntLiteral)
            {
                @SuppressWarnings("recorded")
                @Recorded UnitRaiseExpression unitRaiseExpression = new UnitRaiseExpression(operands.get(i), ((UnitExpressionIntLiteral) operands.get(i + 1)).getNumber());
                operands.set(i, unitRaiseExpression);
                operands.remove(i + 1);
                ops.remove(i);
            }
            else
            {
                i += 1;
            }
        }

        //System.err.println("  Raised: " + Utility.listToString(ops) + " " + ops.size());

        if (operands.size() == 2 && ops.size() == 1 && ops.get(0).equals("/"))
        {
            return new UnitDivideExpression(operands.get(0), operands.get(1));
        }
        else if (operands.size() == 2 && ops.size() == 1 && ops.get(0).equals("^") && operands.get(1) instanceof UnitExpressionIntLiteral)
        {
            return new UnitRaiseExpression(operands.get(0), ((UnitExpressionIntLiteral)operands.get(1)).getNumber());
        }
        else if (ops.size() > 0 && ops.stream().allMatch(o -> o.equals("*")))
        {
            return new UnitTimesExpression(ImmutableList.copyOf(operands));
        }
        else if (ops.size() == 0 && operands.size() == 1)
        {
            return operands.get(0);
        }

        return new InvalidOperatorUnitExpression(ImmutableList.copyOf(operands), ImmutableList.copyOf(ops));
    }
    */

    @Override
    public String save(UnitExpression unitExpression, TableAndColumnRenames renames)
    {
        return unitExpression.save(true);
    }

    @Override
    public UnitNodeParent saveToClipboard()
    {
        return new UnitNodeParent();
    }
}
