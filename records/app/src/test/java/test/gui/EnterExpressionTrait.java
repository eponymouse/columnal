package test.gui;

import com.google.common.collect.ImmutableList;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import records.error.InternalException;
import records.grammar.ExpressionLexer;
import records.loadsave.OutputBuilder;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.BinaryOpExpression;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ConstructorExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.FixedTypeExpression;
import records.transformations.expression.IfThenElseExpression;
import records.transformations.expression.Literal;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.NaryOpExpression;
import records.transformations.expression.StandardFunction;
import records.transformations.expression.StringLiteral;
import records.transformations.expression.TupleExpression;
import records.transformations.expression.VarDeclExpression;
import records.transformations.expression.VarUseExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Random;

import static org.junit.Assert.fail;

@SuppressWarnings("recorded")
public interface EnterExpressionTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    public default void enterExpression(Expression expression, boolean needsBrackets, Random r)
    {
        Class<?> c = expression.getClass();
        if (c == TupleExpression.class)
        {
            TupleExpression t = (TupleExpression)expression;
            write("(");
            ImmutableList<Expression> members = t.getMembers();
            for (int i = 0; i < members.size(); i++)
            {
                if (i > 0)
                    write(",");
                enterExpression(members.get(i), true, r);

            }
            write(")");
        }
        else if (c == ArrayExpression.class)
        {
            ArrayExpression t = (ArrayExpression)expression;
            write("[");
            ImmutableList<Expression> members = t._test_getElements();
            for (int i = 0; i < members.size(); i++)
            {
                if (i > 0)
                    write(",");
                enterExpression(members.get(i), true, r);

            }
            write("]");
        }
        else if (c == StringLiteral.class)
        {
            write('"');
            int extraChars = 0;
            for (char singleChar : ((StringLiteral) expression).getRawString().toCharArray())
            {
                if (singleChar != '"')
                {
                    write(singleChar);
                }
                else
                {
                    write('a');
                    push(KeyCode.LEFT);
                    write('"');
                    extraChars += 1;
                }
            }
            for (int i = 0; i < extraChars; i++)
            {
                push(KeyCode.DELETE);
            }
            write('"');
        }
        else if (Literal.class.isAssignableFrom(c))
        {
            write(expression.toString());
        }
        else if (c == ColumnReference.class)
        {
            write(((ColumnReference)expression).getColumnId().getRaw());
            push(KeyCode.ENTER);
        }
        else if (c == CallExpression.class)
        {
            CallExpression call = (CallExpression) expression;
            enterExpression(call._test_getFunction(), false, r);
            write("(");
            enterExpression(call._test_getParam(), false, r);
            write(")");
        }
        else if (c == StandardFunction.class)
        {
            TagExpression tag = (TagExpression)expression;
            write(tag._test_getQualifiedTagName());
            push(KeyCode.ENTER);
            if (tag.getInner() != null)
            {
                enterExpression(tag.getInner(), false, r);
                write(")");
            }
        }
        else if (c == ConstructorExpression.class)
        {
            TagExpression tag = (TagExpression)expression;
            write(tag._test_getQualifiedTagName());
            push(KeyCode.ENTER);
            if (tag.getInner() != null)
            {
                enterExpression(tag.getInner(), false, r);
                write(")");
            }
        }
        else if (c == MatchExpression.class)
        {
            MatchExpression match = (MatchExpression)expression;
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.MATCH));
            enterExpression(match.getExpression(), false, r);
            for (MatchClause matchClause : match.getClauses())
            {
                write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.CASE));
                for (int i = 0; i < matchClause.getPatterns().size(); i++)
                {
                    if (i > 0)
                        write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ORCASE));
                    Pattern pattern = matchClause.getPatterns().get(i);
                    enterExpression(pattern.getPattern(), false, r);
                    @Nullable Expression guard = pattern.getGuard();
                    if (guard != null)
                    {
                        write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.CASEGUARD));
                        enterExpression(guard, false, r);
                    }
                }
                write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.THEN));
                enterExpression(matchClause.getOutcome(), false, r);
            }
            // To finish whole match expression:
            write(")");
        }
        else if (c == IfThenElseExpression.class)
        {
            IfThenElseExpression ite = (IfThenElseExpression) expression;
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.IF));
            enterExpression(ite._test_getCondition(), false, r);
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.THEN));
            enterExpression(ite._test_getThen(), false, r);
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ELSE));
            enterExpression(ite._test_getElse(), false, r);
            write(")");
        }
        else if (NaryOpExpression.class.isAssignableFrom(c))
        {
            NaryOpExpression n = (NaryOpExpression)expression;
            if (needsBrackets)
                write("(");
            for (int i = 0; i < n.getChildren().size(); i++)
            {
                enterExpression(n.getChildren().get(i), true, r);
                if (i < n.getChildren().size() - 1)
                {
                    write(n._test_getOperatorEntry(i));
                }
            }
            if (needsBrackets)
                write(")");
        }
        else if (BinaryOpExpression.class.isAssignableFrom(c))
        {
            BinaryOpExpression b = (BinaryOpExpression)expression;
            if (needsBrackets)
                write("(");
            enterExpression(b.getLHS(), true, r);
            write(b._test_getOperatorEntry());
            enterExpression(b.getRHS(), true, r);
            if (needsBrackets)
                write(")");
        }
        else if (c == VarDeclExpression.class)
        {
            write(((VarDeclExpression)expression).getName());
            // Have to manually move on because it won't auto-complete:
            push(KeyCode.ENTER); // TODO make sure we've scrolled to new-var in cases of overlap
        }
        else if (c == VarUseExpression.class)
        {
            write(((VarUseExpression)expression).getName());
            push(KeyCode.ENTER); // TODO make sure we've scrolled to new-var in cases of overlap
        }
        else if (c == FixedTypeExpression.class)
        {
            write("@type");
            FixedTypeExpression f = (FixedTypeExpression)expression; 
            write(f.getType().either(s -> s, t -> {
                try
                {
                    return t.save(new OutputBuilder()).toString();
                }
                catch (InternalException e)
                {
                    return t.toString();
                }
            }));
            push(KeyCode.RIGHT);
            enterExpression(f.getInner(), false, r);
            write(")");
        }
        else
        {
            fail("Not yet supported: " + c);
        }
        // TODO add randomness to entry methods (e.g. selection from auto-complete
        // TODO check position of auto-complete
    }
}
