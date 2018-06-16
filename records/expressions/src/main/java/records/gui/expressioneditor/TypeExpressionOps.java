package records.gui.expressioneditor;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeParent;
import utility.FXPlatformConsumer;

import java.util.stream.Stream;

public class TypeExpressionOps implements OperandOps<TypeExpression, TypeParent>
{
    @Override
    public EntryNode<TypeExpression, TypeParent> makeGeneral(ConsecutiveBase<TypeExpression, TypeParent> parent, @Nullable String initialContent)
    {
        return new TypeEntry(parent, initialContent == null ? "" : initialContent);
    }

    /*
    @Override
    public ImmutableList<Pair<String, @Localized String>> getValidOperators(TypeParent parent)
    {
        ImmutableList.Builder<Pair<String, @Localized String>> ops = ImmutableList.builder();
        ops.add(new Pair<String, @Localized String>("-", TranslationUtility.getString("tagged.tuple.apply")));
        if (parent.isRoundBracketed())
            ops.add(new Pair<String, @Localized String>(",", TranslationUtility.getString("type.tuple")));
        return ops.build();
    }
    */

    @Override
    public boolean isOperatorAlphabet(char character)
    {
        return character == '-' || character == ',';
    }

    @Override
    public Class<TypeExpression> getOperandClass()
    {
        return TypeExpression.class;
    }

    /*
    @Override
    public TypeExpression makeUnfinished(String s)
    {
        return new UnfinishedTypeExpression(s);
    }

    @Override
    public TypeExpression makeExpression(ErrorDisplayerRecord errorDisplayers, ImmutableList<@Recorded TypeExpression> originalExpressionExps, List<String> originalOps, BracketedStatus bracketedStatus)
    {
        ArrayList<@Recorded TypeExpression> expressionExps = new ArrayList<>(originalExpressionExps);
        ArrayList<String> ops = new ArrayList<>(originalOps);
        
        //Log.debug("Making expression from " + Utility.listToString(expressionExps) + " and " + Utility.listToString(ops));
        
        // Trim empty ops and expressions from the end until we find a non-empty:
        for (int i = Math.max(expressionExps.size() - 1, ops.size() - 1); i >= 0; i--)
        {
            // Ops is later than expressionExps so remove that first:
            if (i < ops.size() && ops.get(i).isEmpty())
                ops.remove(i);
            else
                break;
            
            if (i > 0 && i < expressionExps.size() && expressionExps.get(i).isEmpty())
                expressionExps.remove(i);
            else
                break;
        }
        
        // First, bunch up any type applications into sub-expressions:
        if (ops.stream().anyMatch(s -> s.equals("-")))
        {
            int i = 0;
            while (i < ops.size())
            {
                if (ops.get(i).equals("-"))
                {
                    ImmutableList.Builder<Either<UnitExpression, TypeExpression>> applArgs = ImmutableList.builder();
                    applArgs.add(Either.right(expressionExps.get(i)));
                    int opIndex = i;
                    while (opIndex < ops.size() && ops.get(opIndex).equals("-") && opIndex + 1 < expressionExps.size())
                    {
                        applArgs.add(Either.right(expressionExps.get(opIndex + 1)));
                        opIndex += 1;
                    }
                    expressionExps.subList(i, opIndex + 1).clear();
                    ops.subList(i, opIndex).clear();
                    expressionExps.add(i, new TypeApplyExpression(applArgs.build()));
                    // Move on and keep going:
                    i += 1;
                }
                else
                {
                    // Skip and examine next:
                    i += 1;
                }
            }
        }
        
        // From now on, there should be no type applications left:
                
        if (ops.isEmpty())
        {
            if (bracketedStatus == BracketedStatus.DIRECT_SQUARE_BRACKETED)
                return new ListTypeExpression(expressionExps.get(0));
            return expressionExps.get(0);
        }
        else if (ops.stream().allMatch(s -> s.equals(",")))
        {
            if (bracketedStatus == BracketedStatus.DIRECT_ROUND_BRACKETED)
                return new TupleTypeExpression(ImmutableList.copyOf(expressionExps));
            // else offer a quick fix to add brackets
        }
        
        // Return an unfinished expression:
        return makeInvalidOpExpression(ImmutableList.copyOf(expressionExps), ops);
    }
    */

    @Override
    public String save(TypeExpression typeExpression, TableAndColumnRenames renames)
    {
        return typeExpression.save(renames);
    }

    @Override
    public TypeParent saveToClipboard(ConsecutiveBase<TypeExpression, TypeParent> parent)
    {
        return new TypeParent()
        {
            @Override
            public void saveOperand(UnitExpression unitExpression, ErrorDisplayer<TypeExpression, TypeParent> unitLiteralTypeNode, FXPlatformConsumer<Context> withContext)
            {
                
            }
        };
    }

    @Override
    public Stream<SingleLoader<TypeExpression, TypeParent>> replaceAndLoad(TypeExpression topLevel, TypeExpression toReplace, TypeExpression replaceWith, BracketedStatus childrenBracketedStatus)
    {
        return topLevel.replaceSubExpression(toReplace, replaceWith).loadAsConsecutive(childrenBracketedStatus);
    }
}
