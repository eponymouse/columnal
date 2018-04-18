package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.transformations.expression.type.InvalidOpTypeExpression;
import records.transformations.expression.type.ListTypeExpression;
import records.transformations.expression.type.TupleTypeExpression;
import records.transformations.expression.type.TypeApplyExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeParent;
import records.transformations.expression.type.UnfinishedTypeExpression;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.List;

public class TypeExpressionOps implements OperandOps<TypeExpression, TypeParent>
{
    @Override
    public OperandNode<TypeExpression, TypeParent> makeGeneral(ConsecutiveBase<TypeExpression, TypeParent> parent, TypeParent semanticParent, @Nullable String initialContent)
    {
        return new TypeEntry(parent, semanticParent, initialContent == null ? "" : initialContent);
    }

    @Override
    public ImmutableList<Pair<String, @Localized String>> getValidOperators(TypeParent parent)
    {
        ImmutableList.Builder<Pair<String, @Localized String>> ops = ImmutableList.builder();
        ops.add(new Pair<String, @Localized String>("-", TranslationUtility.getString("tagged.tuple.apply")));
        if (parent.isRoundBracketed())
            ops.add(new Pair<String, @Localized String>(",", TranslationUtility.getString("type.tuple")));
        return ops.build();
    }

    @Override
    public boolean isOperatorAlphabet(char character, TypeParent parent)
    {
        return character == '-' || character == ',';
    }

    @Override
    public Class<TypeExpression> getOperandClass()
    {
        return TypeExpression.class;
    }

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
        
        Log.debug("Making expression from " + Utility.listToString(expressionExps) + " and " + Utility.listToString(ops));
        
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
                    ImmutableList.Builder<TypeExpression> applArgs = ImmutableList.builder();
                    applArgs.add(expressionExps.get(i));
                    int opIndex = i;
                    while (opIndex < ops.size() && ops.get(opIndex).equals("-") && opIndex + 1 < expressionExps.size())
                    {
                        applArgs.add(expressionExps.get(opIndex + 1));
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

    @Override
    public String save(TypeExpression typeExpression, TableAndColumnRenames renames)
    {
        return typeExpression.save(renames);
    }

    @Override
    public OperandNode<TypeExpression, TypeParent> loadOperand(String src, ConsecutiveBase<TypeExpression, TypeParent> parent) throws UserException, InternalException
    {
        return TypeExpression.parseTypeExpression(parent.getEditor().getTypeManager(), src).loadAsSingle().load(parent, parent.getThisAsSemanticParent());
    }

    @Override
    public TypeExpression makeInvalidOpExpression(ImmutableList<@Recorded TypeExpression> expressionExps, List<String> ops)
    {
        return new InvalidOpTypeExpression(expressionExps, ops);
    }
}
