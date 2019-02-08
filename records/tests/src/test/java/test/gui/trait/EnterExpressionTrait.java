package test.gui.trait;

import com.google.common.collect.ImmutableList;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.testfx.api.FxRobotInterface;
import org.testfx.api.FxToolkit;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.grammar.ExpressionLexer;
import records.gui.expressioneditor.AutoComplete;
import records.gui.expressioneditor.AutoComplete.AutoCompleteWindow;
import records.transformations.expression.*;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@SuppressWarnings("recorded")
public interface EnterExpressionTrait extends FxRobotInterface, EnterTypeTrait, FocusOwnerTrait
{
    static final int DELAY = 1;
    
    public static enum EntryBracketStatus
    {
        // e.g. when a function call argument
        DIRECTLY_ROUND_BRACKETED,
        // e.g. the top-level condition in @if ... @then
        SURROUNDED_BY_KEYWORDS,
        // e.g. an argument of plus
        SUB_EXPRESSION
    }
    
    @OnThread(Tag.Any)
    public default void enterExpression(TypeManager typeManager, Expression expression, EntryBracketStatus bracketedStatus, Random r) throws InternalException
    {
        Class<?> c = expression.getClass();
        if (c == TupleExpression.class)
        {
            TupleExpression t = (TupleExpression)expression;
            if (bracketedStatus != EntryBracketStatus.DIRECTLY_ROUND_BRACKETED)
                write("(");
            ImmutableList<Expression> members = t.getMembers();
            for (int i = 0; i < members.size(); i++)
            {
                if (i > 0)
                    write(",");
                enterExpression(typeManager, members.get(i), EntryBracketStatus.SUB_EXPRESSION, r);

            }
            if (bracketedStatus != EntryBracketStatus.DIRECTLY_ROUND_BRACKETED)
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
                enterExpression(typeManager, members.get(i), EntryBracketStatus.SUB_EXPRESSION, r);

            }
            write("]");
        }
        else if (c == StringLiteral.class)
        {
            write('"');
            write(((StringLiteral)expression).editString().replaceAll("\"", "^q"));
            /*
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
            */
            write('"');
        }
        else if (Literal.class.isAssignableFrom(c))
        {
            write(expression.toString(), DELAY);
        }
        else if (c == ColumnReference.class)
        {
            write(((ColumnReference)expression).getColumnId().getRaw(), DELAY);
            //push(KeyCode.ENTER);
        }
        else if (c == CallExpression.class)
        {
            CallExpression call = (CallExpression) expression;
            enterExpression(typeManager, call.getFunction(), EntryBracketStatus.SUB_EXPRESSION, r);
            write("(");
            enterExpression(typeManager, call.getParam(), EntryBracketStatus.DIRECTLY_ROUND_BRACKETED, r);
            write(")");
        }
        else if (c == StandardFunction.class)
        {
            StandardFunction function = (StandardFunction) expression;
            String name = function.getName();
            if (r.nextBoolean())
            {
                write(name, DELAY);
                if (r.nextBoolean())
                {
                    scrollAutoCompleteToOption(name + "(...)");
                    push(KeyCode.ENTER);
                    // Get rid of brackets; if in a call expression, we will add them again:
                    push(KeyCode.BACK_SPACE);
                    push(KeyCode.DELETE);
                }
            }
            else
            {
                write(name.substring(0, 1 + r.nextInt(name.length() - 1)));
                scrollAutoCompleteToOption(name + "(...)");
                push(KeyCode.ENTER);
                // Get rid of brackets; if in a call expression, we will add them again:
                push(KeyCode.BACK_SPACE);
                push(KeyCode.DELETE);
            }
        }
        else if (c == ConstructorExpression.class)
        {
            ConstructorExpression tag = (ConstructorExpression) expression;
            String tagName = tag.getName();
            boolean multipleTagsOfThatName = typeManager.ambiguousTagName(tagName);
            
            if (multipleTagsOfThatName && tag.getTypeName() != null)
                write(tag.getTypeName().getRaw() + ":");
            write(tagName, DELAY);
            if (r.nextBoolean())
            {
                push(KeyCode.ENTER);
                if (tag._test_hasInner())
                {
                    // Get rid of brackets; if in a call expression, we will add them again:
                    push(KeyCode.BACK_SPACE);
                    push(KeyCode.DELETE);
                }
            }
        }
        else if (c == MatchExpression.class)
        {
            MatchExpression match = (MatchExpression)expression;
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.MATCH), DELAY);
            enterExpression(typeManager, match.getExpression(), EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
            for (MatchClause matchClause : match.getClauses())
            {
                write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.CASE), DELAY);
                for (int i = 0; i < matchClause.getPatterns().size(); i++)
                {
                    if (i > 0)
                        write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ORCASE), DELAY);
                    Pattern pattern = matchClause.getPatterns().get(i);
                    enterExpression(typeManager, pattern.getPattern(), EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
                    @Nullable Expression guard = pattern.getGuard();
                    if (guard != null)
                    {
                        write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.CASEGUARD), DELAY);
                        enterExpression(typeManager, guard, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
                    }
                }
                write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.THEN), DELAY);
                enterExpression(typeManager, matchClause.getOutcome(), EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
            }
            // To finish whole match expression:
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ENDMATCH), DELAY);
        }
        else if (c == IfThenElseExpression.class)
        {
            IfThenElseExpression ite = (IfThenElseExpression) expression;
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.IF), DELAY);
            enterExpression(typeManager, ite._test_getCondition(), EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.THEN), DELAY);
            enterExpression(typeManager, ite._test_getThen(), EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ELSE), DELAY);
            enterExpression(typeManager, ite._test_getElse(), EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
            write(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ENDIF), DELAY);
            
        }
        else if (NaryOpExpression.class.isAssignableFrom(c))
        {
            NaryOpExpression n = (NaryOpExpression)expression;
            if (bracketedStatus == EntryBracketStatus.SUB_EXPRESSION)
                write("(");
            for (int i = 0; i < n.getChildren().size(); i++)
            {
                enterExpression(typeManager, n.getChildren().get(i), EntryBracketStatus.SUB_EXPRESSION, r);
                if (i < n.getChildren().size() - 1)
                {
                    write(n._test_getOperatorEntry(i));
                    if (n._test_getOperatorEntry(i).equals("-") || n._test_getOperatorEntry(i).equals("+") || r.nextBoolean())
                        write(" ");
                }
            }
            if (bracketedStatus == EntryBracketStatus.SUB_EXPRESSION)
                write(")");
        }
        else if (BinaryOpExpression.class.isAssignableFrom(c))
        {
            BinaryOpExpression b = (BinaryOpExpression)expression;
            if (bracketedStatus == EntryBracketStatus.SUB_EXPRESSION)
                write("(");
            enterExpression(typeManager, b.getLHS(), EntryBracketStatus.SUB_EXPRESSION, r);
            write(b._test_getOperatorEntry());
            if (b._test_getOperatorEntry().equals("-") || b._test_getOperatorEntry().equals("+") || r.nextBoolean())
                write(" ");
            enterExpression(typeManager, b.getRHS(), EntryBracketStatus.SUB_EXPRESSION, r);
            if (bracketedStatus == EntryBracketStatus.SUB_EXPRESSION)
                write(")");
        }
        else if (c == VarDeclExpression.class)
        {
            write("$" + ((VarDeclExpression)expression).getName(), DELAY);
            // Have to manually move on because it won't auto-complete:
            push(KeyCode.ENTER);
        }
        else if (c == IdentExpression.class)
        {
            String ident = ((IdentExpression) expression).getText();
            write(ident, DELAY);
            scrollAutoCompleteToOption(ident);
            push(KeyCode.ENTER);
        }
        else if (c == TypeLiteralExpression.class)
        {
            write("type{", DELAY);
            TypeLiteralExpression f = (TypeLiteralExpression)expression; 
            enterType(f.getType(), r);
            write("}");
        }
        else if (c == MatchAnythingExpression.class)
        {
            write("@anything", DELAY);
        }
        else if (c == InvalidOperatorExpression.class)
        {
            InvalidOperatorExpression invalid = (InvalidOperatorExpression) expression;
            for (Expression e : invalid._test_getItems())
            {
                enterExpression(typeManager, e, EntryBracketStatus.SUB_EXPRESSION, r);
            }
        }
        else if (c == InvalidIdentExpression.class)
        {
            InvalidIdentExpression invalid = (InvalidIdentExpression) expression;
            write(invalid.getText(), DELAY);
        }
        else
        {
            fail("Not yet supported: " + c);
        }
        // TODO add randomness to entry methods (e.g. selection from auto-complete
        // TODO check position of auto-complete
    }

    @OnThread(Tag.Any)
    default void scrollAutoCompleteToOption(String content)
    {
        List<Window> autos = listTargetWindows().stream().filter(w -> TestUtil.fx(() -> w.isShowing())).filter(w -> w instanceof AutoComplete.AutoCompleteWindow).collect(Collectors.toList());
        assertEquals(autos.stream().map(Object::toString).collect(Collectors.joining(";")), 1, autos.size());
        
        AutoCompleteWindow autoComplete = ((AutoCompleteWindow)window(w -> w instanceof AutoCompleteWindow && TestUtil.fx(() -> w.isShowing())));
        
        // Move to top:
        String prev = "\u0000";
        while (!Objects.equals(prev, TestUtil.<@Nullable String>fx(() -> autoComplete._test_getSelectedContent())))
        {
            prev = TestUtil.<@Nullable String>fx(() -> autoComplete._test_getSelectedContent());
            push(KeyCode.PAGE_UP);
        }
        
        List<String> all = new ArrayList<>();
        
        // Now scroll down until we find it, or reach end:
        prev = "\u0000";
        while (!Objects.equals(prev, TestUtil.<@Nullable String>fx(() -> autoComplete._test_getSelectedContent()))
                && !Objects.equals(content, TestUtil.<@Nullable String>fx(() -> autoComplete._test_getSelectedContent())))
        {
            prev = TestUtil.<@Nullable String>fx(() -> autoComplete._test_getSelectedContent());
            if (prev != null)
                all.add(prev);
            push(KeyCode.DOWN);
        }
        
        assertEquals("Options: " + all.stream().collect(Collectors.joining(";")), content, TestUtil.<@Nullable String>fx(() -> autoComplete._test_getSelectedContent()));
    }
}
