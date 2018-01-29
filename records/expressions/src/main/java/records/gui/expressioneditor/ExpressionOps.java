package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.GeneralExpressionEntry.Status;
import records.transformations.expression.AddSubtractExpression;
import records.transformations.expression.AddSubtractExpression.Op;
import records.transformations.expression.AndExpression;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.ComparisonExpression;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.DivideExpression;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.InvalidOperatorExpression;
import records.transformations.expression.MatchesOneExpression;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.OrExpression;
import records.transformations.expression.PlusMinusPatternExpression;
import records.transformations.expression.RaiseExpression;
import records.transformations.expression.StringConcatExpression;
import records.transformations.expression.TimesExpression;
import records.transformations.expression.TupleExpression;
import records.transformations.expression.UnfinishedExpression;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static records.gui.expressioneditor.DeepNodeTree.opD;

class ExpressionOps implements OperandOps<Expression, ExpressionNodeParent>
{
    // Remember: earlier means more likely to be inner-bracketed.  Outer list is groups of operators
    // with equal bracketing likelihood/precedence.
    private final static ImmutableList<ImmutableList<OperatorExpressionInfo<Expression>>> OPERATORS = ImmutableList.of(
        // Raise does come above arithmetic, because I think it is more likely that 1 * 2 ^ 3 is actually 1 * (2 ^ 3)
        ImmutableList.of(
            new OperatorExpressionInfo<>(
                opD("^", "op.raise")
            , (lhs, rhs, _b) -> new RaiseExpression(lhs, rhs))
        ),
        
        // Arithmetic operators are all one group.  I know we could separate +- from */, but if you see
        // an expression like 1 + 2 * 3, I'm not sure either bracketing is obviously more likely than the other.
        ImmutableList.of(
            new OperatorExpressionInfo<>(ImmutableList.of(
                opD("+", "op.plus"),
                opD("-", "op.minus")
            ), (args, ops, _b) -> new AddSubtractExpression(args, Utility.mapList(ops, op -> op.equals("+") ? Op.ADD : Op.SUBTRACT))),
            new OperatorExpressionInfo<Expression>(ImmutableList.of(
                opD("*", "op.times")
            ), (args, _ops, _b) -> new TimesExpression(args)),
            new OperatorExpressionInfo<Expression>(
                opD("/", "op.divide")
            , (lhs, rhs, _b) -> new DivideExpression(lhs, rhs))
        ),
    
        // String concatenation lower than arithmetic.  If you write "val: (" ; 1 * 2; ")" then what you meant
        // is "val: (" ; to.string(1 * 2); ")" which requires an extra function call, but bracketing the arithmetic
        // will be the first step, and much more likely than ("val: (" ; 1) * (2; ")")
        ImmutableList.of(
            new OperatorExpressionInfo<>(ImmutableList.of(
                opD(";", "op.stringConcat")
            ), (args, _ops, _b) -> new StringConcatExpression(args))    
        ),
    
        // It's moot really whether this is before or after string concat, but feels odd putting them in same group:
        ImmutableList.of(
            new OperatorExpressionInfo<>(
                opD("\u00B1", "op.plusminus")
            , (lhs, rhs, _b) -> new PlusMinusPatternExpression(lhs, rhs))
        ),
    
        // Equality and comparison operators:
        ImmutableList.of(
            new OperatorExpressionInfo<>(ImmutableList.of(
                opD("=", "op.equal")
            ), (args, _ops, _b) -> new EqualExpression(args)),
            new OperatorExpressionInfo<>(
                opD("<>", "op.notEqual")
            , (lhs, rhs, _b) -> new NotEqualExpression(lhs, rhs)),
            new OperatorExpressionInfo<>(ImmutableList.of(
                opD("<", "op.lessThan"),
                opD("<=", "op.lessThanOrEqual")
            ), (args, ops, _b) -> new ComparisonExpression(args, Utility.mapListI(ops, op -> op.equals("<") ? ComparisonOperator.LESS_THAN : ComparisonOperator.LESS_THAN_OR_EQUAL_TO))),
            new OperatorExpressionInfo<>(ImmutableList.of(
                opD(">", "op.greaterThan"),
                opD(">=", "op.greaterThanOrEqual")
            ), (args, ops, _b) -> new ComparisonExpression(args, Utility.mapListI(ops, op -> op.equals(">") ? ComparisonOperator.GREATER_THAN : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO))),
            new OperatorExpressionInfo<>(
                opD("~", "op.matches")
            , (lhs, rhs, _b) -> new MatchesOneExpression(lhs, rhs))
        ),
        
        // Boolean and, or expressions come near-last.  If you see a = b & c = d, it's much more likely you wanted (a = b) & (c = d) than
        // a = (b & c) = d.
        ImmutableList.of(
            new OperatorExpressionInfo<>(ImmutableList.of(
                opD("&", "op.and")
            ), (args, _ops, _b) -> new AndExpression(args)),
            new OperatorExpressionInfo<>(ImmutableList.of(
                opD("|", "op.or")
            ), (args, _ops, _b) -> new OrExpression(args))
        ),
        
        // But the very last is the comma separator.  If you see (a & b, c | d), almost certain that you want a tuple
        // like that, rather than a & (b, c) | d.  Especially since tuples can't be fed to any binary operators besides comparison!
        ImmutableList.of(
            new OperatorExpressionInfo<Expression>(
                opD(",", "op.separator")
            , (lhs, rhs, _b) -> /* Dummy, see below: */ lhs)
            {
                @Override
                public OperatorSection<Expression> makeOperatorSection(int operatorSetPrecedence, String initialOperator, int initialIndex)
                {
                    return new NaryOperatorSection<Expression>(operators, operatorSetPrecedence, /* Dummy: */ (args, _ops, bracketedStatus) -> {
                        switch (bracketedStatus)
                        {
                            case DIRECT_SQUARE_BRACKETED:
                                return new ArrayExpression(args);
                            case DIRECT_ROUND_BRACKETED:
                                return new TupleExpression(args);
                        }
                        return null;
                    }, initialIndex, initialOperator);
                    
                }
            }
        )
    );
    private final Set<Integer> ALPHABET = makeAlphabet();

    private static Set<@NonNull Integer> makeAlphabet()
    {
        return OPERATORS.stream().flatMap(l -> l.stream()).flatMap(oei -> oei.operators.stream().map((Pair<String, @Localized String> p) -> p.getFirst())).flatMapToInt(String::codePoints).boxed().collect(Collectors.<@NonNull Integer>toSet());
    }

    private static String getOp(Pair<String, @Localized String> p)
    {
        return p.getFirst();
    }

    @Override
    public ImmutableList<Pair<String, @Localized String>> getValidOperators(ExpressionNodeParent parent)
    {
        return Stream.concat(OPERATORS.stream().flatMap(l -> l.stream()).flatMap(oei -> oei.operators.stream()), parent.operatorKeywords().stream()).collect(ImmutableList.toImmutableList());
    }

    public boolean isOperatorAlphabet(char character, ExpressionNodeParent expressionNodeParent)
    {
        return ALPHABET.contains((Integer)(int)character) || expressionNodeParent.operatorKeywords().stream().anyMatch((Pair<String, @Localized String> k) -> getOp(k).codePointAt(0) == character);
    }

    @Override
    public OperandNode<Expression, ExpressionNodeParent> makeGeneral(ConsecutiveBase<Expression, ExpressionNodeParent> parent, ExpressionNodeParent semanticParent, @Nullable String initialContent)
    {
        return new GeneralExpressionEntry(initialContent == null ? "" : initialContent, true, Status.UNFINISHED, parent, semanticParent);
    }

    @Override
    public Class<Expression> getOperandClass()
    {
        return Expression.class;
    }

    @Override
    public Expression makeUnfinished(String s)
    {
        return new UnfinishedExpression(s);
    }

    @Override
    public @UnknownIfRecorded Expression makeExpression(ErrorDisplayerRecord errorDisplayers, ImmutableList<@Recorded Expression> originalExps, List<String> ops, BracketedStatus bracketedStatus)
    {
        // Make copy for editing:
        ArrayList<Expression> expressionExps = new ArrayList<>(originalExps);
        ops = new ArrayList<>(ops);

        // Trim blanks from end:
        ConsecutiveBase.removeBlanks(expressionExps, ops, (Object o) -> o instanceof String ? ((String)o).trim().isEmpty() : o instanceof UnfinishedExpression && ((UnfinishedExpression)o).getText().trim().isEmpty(), o -> false, o -> {}, false, null);

        @Nullable Expression expression = null;
        if (ops.isEmpty())
        {
            if (bracketedStatus == BracketedStatus.DIRECT_SQUARE_BRACKETED && expressionExps.size() <= 1)
                expression = new ArrayExpression(ImmutableList.copyOf(expressionExps));
            else if (expressionExps.size() == 1)
                expression = expressionExps.get(0);
        }
        else
        {
            expression = makeExpressionWithOperators(OPERATORS, errorDisplayers.getRecorder(), ImmutableList.copyOf(expressionExps), ops, bracketedStatus);
        }
        if (expression == null)
            expression = new InvalidOperatorExpression(expressionExps, ops);

        return expression;
        /*
        else if (ops.stream().allMatch(op -> op.equals("+") || op.equals("-")))
        {
            return errorDisplayers.record(displayer, new AddSubtractExpression(expressionExps, Utility.<String, Op>mapList(ops, op -> op.equals("+") ? Op.ADD : Op.SUBTRACT)));
        }
        else if (ops.stream().allMatch(op -> op.equals("*")))
        {
            return errorDisplayers.record(displayer, new TimesExpression(expressionExps));
        }
        else if (ops.stream().allMatch(op -> op.equals("&")))
        {
            return errorDisplayers.record(displayer, new AndExpression(expressionExps));
        }
        else if (ops.stream().allMatch(op -> op.equals("|")))
        {
            return errorDisplayers.record(displayer, new OrExpression(expressionExps));
        }
        else if (ops.stream().allMatch(op -> op.equals(";")))
        {
            return errorDisplayers.record(displayer, new StringConcatExpression(expressionExps));
        }
        else if (ops.stream().allMatch(op -> op.equals("/")))
        {
            if (expressionExps.size() == 2)
                return errorDisplayers.record(displayer, new DivideExpression(expressionExps.get(0), expressionExps.get(1)));
        }
        else if (ops.stream().allMatch(op -> op.equals("^")))
        {
            if (expressionExps.size() == 2)
                return errorDisplayers.record(displayer, new RaiseExpression(expressionExps.get(0), expressionExps.get(1)));
        }
        else if (ops.stream().allMatch(op -> op.equals("=")))
        {
            return errorDisplayers.record(displayer, new EqualExpression(expressionExps));
        }
        else if (ops.stream().allMatch(op -> op.equals("<>")))
        {
            if (expressionExps.size() == 2)
                return errorDisplayers.record(displayer, new NotEqualExpression(expressionExps.get(0), expressionExps.get(1)));
        }
        else if (ops.stream().allMatch(op -> op.equals(">") || op.equals(">=")) || ops.stream().allMatch(op -> op.equals("<") || op.equals("<=")))
        {
            try
            {
                return errorDisplayers.record(displayer, new ComparisonExpression(expressionExps, Utility.mapListExI(ops, ComparisonOperator::parse)));
            }
            catch (UserException | InternalException e)
            {
                Log.log(e);
                // Fall-through...
            }
        }
        else if (ops.stream().allMatch(op -> op.equals(",")))
        {
            switch (bracketedStatus)
            {
                case DIRECT_ROUND_BRACKETED:
                    return errorDisplayers.record(displayer, new TupleExpression(ImmutableList.copyOf(expressionExps)));
                case DIRECT_SQUARE_BRACKETED:
                    return errorDisplayers.record(displayer, new ArrayExpression(ImmutableList.copyOf(expressionExps)));
                default:
                    break; // Invalid: fall-through....
            }
            // TODO offer fix to bracket this?
        }
        */
    }

    @Override
    public String save(Expression child)
    {
        return child.save(BracketedStatus.MISC);
    }

    @Override
    public OperandNode<Expression, ExpressionNodeParent> loadOperand(String curItem, ConsecutiveBase<Expression, ExpressionNodeParent> consecutiveBase) throws UserException, InternalException
    {
        return Expression.parse(null, curItem, consecutiveBase.getEditor().getTypeManager()).loadAsSingle().load(consecutiveBase, consecutiveBase.getThisAsSemanticParent());
    }

    @Override
    public Expression makeInvalidOpExpression(ImmutableList<Expression> expressionExps, List<String> ops)
    {
        return new InvalidOperatorExpression(expressionExps, ops);
    }
}
