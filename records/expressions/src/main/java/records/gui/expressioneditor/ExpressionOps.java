package records.gui.expressioneditor;

import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.transformations.expression.*;
import records.transformations.expression.LoadableExpression.SingleLoader;
import utility.Pair;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ExpressionOps implements OperandOps<Expression, ExpressionSaver>
{
    private static final Set<Integer> OPERATOR_ALPHABET = makeAlphabet();

    private static Set<@NonNull Integer> makeAlphabet()
    {
        return ExpressionSaver.getOperators().stream().flatMap(l -> l.stream()).flatMap(oei -> oei.operators.stream().map((Pair<Op, @Localized String> p) -> p.getFirst().getContent())).flatMapToInt(String::codePoints).boxed().collect(Collectors.<@NonNull Integer>toSet());
    }

    @Override
    public EntryNode<Expression, ExpressionSaver> makeGeneral(ConsecutiveBase<Expression, ExpressionSaver> parent, @Nullable String initialContent)
    {
        return new GeneralExpressionEntry(initialContent == null ? "" : initialContent, parent);
    }

    @Override
    public Class<Expression> getOperandClass()
    {
        return Expression.class;
    }

    /*
    @Override
    public Expression makeUnfinished(String s)
    {
        return new IdentExpression(s);
    }

    @Override
    public @UnknownIfRecorded Expression makeExpression(ErrorDisplayerRecord errorDisplayers, ImmutableList<@Recorded Expression> originalExps, List<String> ops, BracketedStatus bracketedStatus)
    {
        // Make copy for editing:
        ArrayList<@Recorded Expression> expressionExps = new ArrayList<>(originalExps);
        ops = new ArrayList<>(ops);

        // Trim blanks from end:
        ConsecutiveBase.removeBlanks(expressionExps, ops, (Object o) -> o instanceof String ? ((String)o).trim().isEmpty() : o instanceof IdentExpression && ((IdentExpression)o).getText().trim().isEmpty(), o -> false, o -> {}, false, null);

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
            // Two things to group up: function calls with function args, and numeric literals with units

            int i = 0;
            while (i < expressionExps.size())
            {
                if (i + 1 < expressionExps.size()
                    && expressionExps.get(i) instanceof NumericLiteral
                    && ops.get(i).isEmpty()
                    && expressionExps.get(i + 1) instanceof UnitLiteralExpression)
                {
                    NumericLiteral numericLiteral = (NumericLiteral) expressionExps.get(i);
                    UnitLiteralExpression unitLiteralExpression = (UnitLiteralExpression) expressionExps.get(i + 1);
                    expressionExps.set(i, new NumericLiteral(numericLiteral.getNumber(), unitLiteralExpression.getUnit()));
                    ops.remove(i);
                    expressionExps.remove(i + 1);
                    
                }
                else if (i + 1 < expressionExps.size()
                    && isCallTarget(expressionExps.get(i))
                    && ops.get(i).isEmpty())
                {
                    // TODO do we somehow need to check that arg is bracketed?
                    expressionExps.set(i, new CallExpression(expressionExps.get(i), expressionExps.get(i + 1)));
                    ops.remove(i);
                    expressionExps.remove(i + 1);
                }
                else
                {
                    i += 1;
                }
            }

            expression = OperandOps.makeExpressionWithOperators(this, OPERATORS, errorDisplayers.getRecorder(), ImmutableList.copyOf(expressionExps), ops, bracketedStatus);
        }
        if (expression == null)
            expression = new InvalidOperatorExpression(expressionExps, ops);

        return expression;
    }
    */
        
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

    public static boolean isCallTarget(Expression expression)
    {
        // callTarget : varRef | standardFunction | constructor | unfinished;
        return expression instanceof IdentExpression
                || expression instanceof StandardFunction
                || expression instanceof ConstructorExpression;
    }

    @Override
    public String save(Expression child, TableAndColumnRenames renames)
    {
        return child.save(BracketedStatus.MISC, renames);
    }

    @Override
    public ExpressionSaver saveToClipboard(ConsecutiveBase<Expression, ExpressionSaver> parent)
    {
        return new ExpressionSaver(parent);
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> replaceAndLoad(Expression topLevel, Expression toReplace, Expression replaceWith, BracketedStatus childrenBracketedStatus)
    {
        return topLevel.replaceSubExpression(toReplace, replaceWith).loadAsConsecutive(childrenBracketedStatus);
    }

    public static boolean differentAlphabet(String current, int newCodepoint)
    {
        // Special case: @ can only be followed by letters:
        if (current.startsWith("@"))
        {
            return !Character.isLetter(newCodepoint);
        }   
        // Numeric literal starting with +/-, don't compare to first character: 
        if ((current.startsWith("+") || current.startsWith("-")) && (current.length() == 1 || current.codePoints().anyMatch(Character::isDigit)))
        {
            return OperandOps.alphabetDiffers(Arrays.asList(ExpressionAlphabet.values()), "0" + current.substring(1), newCodepoint);
        }
        // Identifiers can have numbers after first char:
        if (current.codePoints().limit(1).anyMatch(ExpressionAlphabet.WORD::test) && Character.isDigit(newCodepoint))
        {
            return false;
        }
        
        return OperandOps.alphabetDiffers(Arrays.asList(ExpressionAlphabet.values()), current, newCodepoint);
    }

    private static enum ExpressionAlphabet implements Alphabet
    {
        WORD(c -> Character.isAlphabetic(c) || c == ' ' || c == '$' /*for declarations */),
        DIGIT(Character::isDigit),
        BRACKET(Alphabet.containsCodepoint("(){}[]")),
        QUOTE(Alphabet.containsCodepoint("\"")),
        OPERATOR(OPERATOR_ALPHABET::contains);

        private Predicate<Integer> match;

        ExpressionAlphabet(Predicate<Integer> match)
        {
            this.match = match;
        }

        @Override
        public boolean test(int codepoint)
        {
            return match.test(codepoint);
        }
    }
}
