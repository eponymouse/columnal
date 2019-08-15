package test.gui.expressionEditor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Before;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.EditableRecordSet;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.gui.lexeditor.EditorDisplay;
import records.transformations.expression.Expression;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenRandom;
import test.gui.trait.ClickOnTableHeaderTrait;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterExpressionTrait;
import test.gui.trait.PopupTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditorDelete extends FXApplicationTest
    implements ClickTableLocationTrait, EnterExpressionTrait, ClickOnTableHeaderTrait, PopupTrait
{
    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private MainWindowActions mainWindowActions;
    private final CellPosition targetPos = new CellPosition(CellPosition.row(2), CellPosition.col(2));

    @Before
    public void setupWindow() throws Exception
    {
        mainWindowActions = TestUtil.openDataAsTable(windowToUse,null, new EditableRecordSet(ImmutableList.of(), () -> 0));

    }
    
    @Property(trials = 3)
    public void testDeleteAfterOperand(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspace("true&false", 10, 1, "true & fals", r);
    }

    @Property(trials = 3)
    public void testDeleteAfterInvalidOperator(@From(GenRandom.class) Random r) throws Exception
    {
        testDeleteBackspace("@invalidops(true, @unfinished \"&\")", 4, 1, "true", r);
    }

    @Property(trials = 3)
    public void testDeleteAfterSpareKeyword(@From(GenRandom.class) Random r) throws Exception
    {
        testDeleteBackspace("@invalidops(1, @unfinished \"+\", 2, @invalidops(@unfinished \"^aif\", @invalidops ()))", 3, 3, "1+2", r);
    }

    @Property(trials = 3)
    public void testDeleteAfterInfixOperator(@From(GenRandom.class) Random r) throws Exception
    {
        testDeleteBackspace("1+2", 1, 1, "12", r);
    }
    
    @Property(trials = 3)
    public void testDeleteAfterInfixOperator2(@From(GenRandom.class) Random r) throws Exception
    {
        testDeleteBackspace("a<b<=c", 1, 1, "ab <= c", r);
    }
    
    @Property(trials = 3)
    public void testDeleteAfterInfixOperator2b(@From(GenRandom.class) Random r) throws Exception
    {
        testDeleteBackspace("a<b<=c", 3, 2, "a < bc", r, 1);
    }

    @Property(trials = 3)
    public void testDeleteAfterInfixOperator2c(@From(GenRandom.class) Random r) throws Exception
    {
        testDeleteBackspace("a<b<=c", 3, 1, "@invalidops(a, @unfinished \"<\", b, @unfinished \"=\", c)", r, -1);
    }
    
    @Property(trials = 3)
    public void testDeleteAfterInfixOperator3(@From(GenRandom.class) Random r) throws Exception
    {
        testDeleteBackspace("\"a\";b", 3, 1, "@invalidops(\"a\", b)", r);
    }
    
    @Property(trials = 2)
    public void testRetypeInfix(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("1+2", 2, 1, "+", r);
    }

    @Property(trials = 2)
    public void testRetypeInfix2(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("1<=2", 3, 2, "<=", r);
    }

    @Property(trials = 2)
    public void testRetypeLeadingOperand(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("1+2", 1, 1, "1", r);
    }

    @Property(trials = 2)
    public void testRetypeLeadingOperand2(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("1234 + 5678", 4, 4, "1234", r);
    }

    @Property(trials = 2)
    public void testRetypeTrailingOperand(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("1+2", 3, 1, "2", r);
    }

    @Property(trials = 2)
    public void testRetypeTrailingOperand2(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("123+456", 7, 3,"456", r);
    }
    
    @Property(trials = 2)
    public void testRetypeListTypeContent(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("type{[Text]}", 10, 4, "Text", r);
    }

    @Property(trials = 2)
    public void testRetypeParameter(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("@call @function sum([2])", 7, 3, "[2]", r);
    }

    @Property(trials = 2)
    public void testRetypeParameter2(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("@call @function sum([])", 6, 2, "[]", r);
    }

    @Property(trials = 2)
    public void testRetypeParameter3(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("@call @function convert unit(foo*unit{m}, 1{cm})", 10, 2, "fo", r);
    }

    @Property(trials = 2)
    public void testRetypeWordInIdent(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("the quick brown fox", 9, 5, "quick", r);
    }
    
    
    
    // TODO more retype tests

    @Property(trials = 2)
    public void testPasteSeveral(@From(GenRandom.class) Random r) throws Exception
    {
        testPaste("12", 1, "+3+4-", "1+3+4-2", r);
    }

    private void testPaste(String original, int caretPos, String paste, String expected, Random r) throws Exception
    {
        DummyManager dummyManager = new DummyManager();
        Expression originalExp = Expression.parse(null, original, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        EditorDisplay expressionEditor = enter(originalExp, r);

        TestUtil.fx_(() -> {
            expressionEditor._test_positionCaret(caretPos);
            Clipboard.getSystemClipboard().setContent(ImmutableMap.of(DataFormat.PLAIN_TEXT, paste));
        });
        push(KeyCode.SHORTCUT, KeyCode.V);

        Expression after = (Expression)TestUtil.fx(() -> expressionEditor._test_getEditor().save(false));

        assertEquals(Expression.parse(null, expected, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager())), after);

        moveAndDismissPopupsAtPos(point(".cancel-button"));
        clickOn();
    }
    
    private void testBackspaceRetype(String original, int deleteBefore, int deleteCount, String retype, Random r) throws Exception
    {
        DummyManager dummyManager = new DummyManager();
        Expression originalExp = Expression.parse(null, original, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        EditorDisplay expressionEditor = enter(originalExp, r);

        TestUtil.fx_(() -> expressionEditor._test_positionCaret(deleteBefore));
        sleep(200);

        for (int i = 0; i < deleteCount; i++)
        {
            push(KeyCode.BACK_SPACE);
        }
        if (r.nextBoolean())
            write(retype);
        else
        {
            TestUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(ImmutableMap.of(DataFormat.PLAIN_TEXT, retype)));
            push(KeyCode.SHORTCUT, KeyCode.V);
        }

        Expression after = (Expression)TestUtil.fx(() -> expressionEditor._test_getEditor().save(false));

        assertEquals(originalExp, after);

        moveAndDismissPopupsAtPos(point(".cancel-button"));
        clickOn();
    }

    private void testDeleteBackspace(String original, int deleteAfterPos, int deleteCount, String expectedStr, Random r, int... cutCount) throws Exception
    {
        assertEquals(1, mainWindowActions._test_getTableManager().getAllTables().size());
        testBackspace(original, deleteAfterPos + deleteCount, deleteCount, expectedStr, r);
        assertEquals(2, mainWindowActions._test_getTableManager().getAllTables().size());
        triggerTableHeaderContextMenu(mainWindowActions._test_getVirtualGrid(), targetPos);
        clickOn(".id-tableDisplay-menu-delete");
        TestUtil.sleep(300);
        assertEquals(1, mainWindowActions._test_getTableManager().getAllTables().size());
        testDelete(original, deleteAfterPos, deleteCount, expectedStr, r);
        assertEquals(2, mainWindowActions._test_getTableManager().getAllTables().size());
        triggerTableHeaderContextMenu(mainWindowActions._test_getVirtualGrid(), targetPos);
        clickOn(".id-tableDisplay-menu-delete");
        TestUtil.sleep(300);
        assertEquals(1, mainWindowActions._test_getTableManager().getAllTables().size());
        if (cutCount.length == 0 || cutCount[0] > 0)
            testCut(original, deleteAfterPos, cutCount.length > 0 ? cutCount[0] : deleteCount, expectedStr, r);
    }

    private void testBackspace(String original, int deleteBefore, int deleteCount, String expectedStr, Random r) throws Exception
    {
        DummyManager dummyManager = new DummyManager();
        Expression expectedExp = Expression.parse(null, expectedStr, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        Expression originalExp = Expression.parse(null, original, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        EditorDisplay expressionEditor = enter(originalExp, r);
        
        assertEquals(originalExp, TestUtil.fx(() -> expressionEditor._test_getEditor().save(false)));

        TestUtil.fx_(() -> expressionEditor._test_positionCaret(deleteBefore));

        for (int i = 0; i < deleteCount; i++)
        {
            push(KeyCode.BACK_SPACE);
        }
        
        TestUtil.sleep(1000);

        Expression after = (Expression)TestUtil.fx(() -> expressionEditor._test_getEditor().save(false));

        assertEquals(expectedExp, after);
        
        moveAndDismissPopupsAtPos(point(".cancel-button"));
        clickOn();
    }

    private void testDelete(String original, int deleteAfter, int deleteCount, String expectedStr, Random r) throws Exception
    {
        DummyManager dummyManager = new DummyManager();
        Expression expectedExp = Expression.parse(null, expectedStr, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        EditorDisplay expressionEditor = enter(Expression.parse(null, original, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager())), r);

        TestUtil.fx_(() -> expressionEditor._test_positionCaret(deleteAfter));

        for (int i = 0; i < deleteCount; i++)
        {
            push(KeyCode.DELETE);
        }

        Expression after = (Expression)TestUtil.fx(() -> expressionEditor._test_getEditor().save(false));

        assertEquals(expectedExp, after);

        moveAndDismissPopupsAtPos(point(".cancel-button"));
        clickOn();
    }

    private void testCut(String original, int deleteAfter, int deleteCount, String expectedStr, Random r) throws Exception
    {
        DummyManager dummyManager = new DummyManager();
        Expression expectedExp = Expression.parse(null, expectedStr, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        EditorDisplay expressionEditor = enter(Expression.parse(null, original, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager())), r);

        TestUtil.fx_(() -> expressionEditor._test_positionCaret(deleteAfter));

        press(KeyCode.SHIFT);
        for (int i = 0; i < deleteCount; i++)
        {
            push(KeyCode.RIGHT);
        }
        release(KeyCode.SHIFT);
        
        if (r.nextBoolean())
            push(KeyCode.DELETE);
        else
        {
            // Test copy does same as cut:
            TestUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(ImmutableMap.of(DataFormat.PLAIN_TEXT, "EMPTY")));
            push(KeyCode.SHORTCUT, KeyCode.C);
            String copied = TestUtil.<@Nullable String>fx(() -> Clipboard.getSystemClipboard().getString());
            TestUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(ImmutableMap.of(DataFormat.PLAIN_TEXT, "EMPTY")));
            push(KeyCode.SHORTCUT, KeyCode.X);
            assertEquals(copied, TestUtil.<@Nullable String>fx(() -> Clipboard.getSystemClipboard().getString()));
        }
        

        Expression after = (Expression)TestUtil.fx(() -> expressionEditor._test_getEditor().save(false));

        assertEquals(expectedExp, after);

        clickOn(".cancel-button");
    }
    
    private EditorDisplay enter(Expression expression, Random r) throws Exception
    {
        Region gridNode = TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
        push(KeyCode.SHORTCUT, KeyCode.HOME);
        for (int i = 0; i < 2; i++)
            clickOnItemInBounds(from(gridNode), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        clickOn(".id-new-transform");
        clickOn(".id-transform-calculate");
        write("Table1");
        push(KeyCode.ENTER);
        TestUtil.sleep(200);
        write("DestCol");
        // Focus expression editor:
        push(KeyCode.TAB);
        
        enterExpression(mainWindowActions._test_getTableManager().getTypeManager(), expression, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);

        return lookup(".editor-display").<EditorDisplay>query();
    }
}
