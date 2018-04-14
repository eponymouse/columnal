package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.FormatParser;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.transformations.expression.type.InvalidOpTypeExpression;
import records.transformations.expression.type.ListTypeExpression;
import records.transformations.expression.type.TupleTypeExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeParent;
import records.transformations.expression.type.UnfinishedTypeExpression;
import utility.Pair;
import utility.UnitType;
import utility.Utility;

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
        ops.add(new Pair<>(":", "Fill in tagged type"));
        if (parent.isTuple())
            ops.add(new Pair<>(",", "Pair/triple/etc"));
        return ops.build();
    }

    @Override
    public boolean isOperatorAlphabet(char character, TypeParent parent)
    {
        return character == ':' || (parent.isTuple() && character == ',');
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
    public TypeExpression makeExpression(ErrorDisplayerRecord errorDisplayers, ImmutableList<@Recorded TypeExpression> expressionExps, List<String> ops, BracketedStatus bracketedStatus)
    {
        // First, bunch up any colons into sub-expressions:
        if (ops.stream().anyMatch(s -> s.equals(":")))
        {
            // TODO
        }
        
        // From now on, there should be no colons left:
                
        if (ops.isEmpty())
        {
            if (bracketedStatus == BracketedStatus.DIRECT_SQUARE_BRACKETED)
                return new ListTypeExpression(expressionExps.get(0));
            return expressionExps.get(0);
        }
        else if (ops.stream().allMatch(s -> s.equals(",")))
        {
            if (bracketedStatus == BracketedStatus.DIRECT_ROUND_BRACKETED)
                return new TupleTypeExpression(expressionExps);
            // else offer a quick fix to add brackets
        }
        
        // Return an unfinished expression:
        return makeInvalidOpExpression(expressionExps, ops);
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
