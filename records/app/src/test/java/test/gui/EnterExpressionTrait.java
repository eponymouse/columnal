package test.gui;

import com.google.common.collect.ImmutableList;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import records.error.InternalException;
import records.grammar.ExpressionLexer;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.BinaryOpExpression;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ConstructorExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.TypeLiteralExpression;
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
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Random;

import static org.junit.Assert.fail;

@SuppressWarnings("recorded")
public interface EnterExpressionTrait extends FxRobotInterface, EnterTypeTrait
{
    static final int DELAY = 1;
    
    @OnThread(Tag.Any)
    public default void enterExpression(Expression expression, boolean needsBrackets, Random r) throws InternalException
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
            write(expression.toString(), DELAY);
        }
        else if (c == ColumnReference.class)
        {
            write(((ColumnReference)expression).getColumnId().getRaw(), DELAY);
            push(KeyCode.ENTER);
        }
        else if (c == CallExpression.class)
        {
            CallExpression call = (CallExpression) expression;
            enterExpression(call.getFunction(), false, r);
            write("(");
            enterExpression(call.getParam(), false, r);
            write(")");
        }
        else if (c == StandardFunction.class)
        {
            StandardFunction function = (StandardFunction) expression;
            write(function.getName(), DELAY);
            if (r.nextBoolean())
            {
                push(KeyCode.ENTER);
                // Get rid of brackets; if in a call expression, we will add them again:
                push(KeyCode.BACK_SPACE);
            }
        }
        else if (c == ConstructorExpression.class)
        {
            ConstructorExpression tag = (ConstructorExpression) expression;
            write(tag.getName(), DELAY);
            push(KeyCode.ENTER);
            if (tag._test_hasInner())
            {
                // Get rid of brackets; if in a call expression, we will add them again:
                push(KeyCode.BACK_SPACE);
            }
        }
        else if (c == MatchExpression.class)
        {
            MatchExpression match = (MatchExpression)expression;
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.MATCH), DELAY);
            enterExpression(match.getExpression(), false, r);
            for (MatchClause matchClause : match.getClauses())
            {
                write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.CASE), DELAY);
                for (int i = 0; i < matchClause.getPatterns().size(); i++)
                {
                    if (i > 0)
                        write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ORCASE), DELAY);
                    Pattern pattern = matchClause.getPatterns().get(i);
                    enterExpression(pattern.getPattern(), false, r);
                    @Nullable Expression guard = pattern.getGuard();
                    if (guard != null)
                    {
                        write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.CASEGUARD), DELAY);
                        enterExpression(guard, false, r);
                    }
                }
                write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.THEN), DELAY);
                enterExpression(matchClause.getOutcome(), false, r);
            }
            // To finish whole match expression:
            write(")");
        }
        else if (c == IfThenElseExpression.class)
        {
            IfThenElseExpression ite = (IfThenElseExpression) expression;
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.IF), DELAY);
            enterExpression(ite._test_getCondition(), false, r);
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.THEN), DELAY);
            enterExpression(ite._test_getThen(), false, r);
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ELSE), DELAY);
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
            write(((VarDeclExpression)expression).getName(), DELAY);
            // Have to manually move on because it won't auto-complete:
            push(KeyCode.ENTER); // TODO make sure we've scrolled to new-var in cases of overlap
        }
        else if (c == IdentExpression.class)
        {
            write(((IdentExpression)expression).getText(), DELAY);
            push(KeyCode.ENTER); // TODO make sure we've scrolled to new-var in cases of overlap
        }
        else if (c == TypeLiteralExpression.class)
        {
            write("type{", DELAY);
            TypeLiteralExpression f = (TypeLiteralExpression)expression; 
            enterType(f.getType(), r);
            write("}");
        }
        else
        {
            fail("Not yet supported: " + c);
        }
        // TODO add randomness to entry methods (e.g. selection from auto-complete
        // TODO check position of auto-complete
    }
}
