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
import org.junit.Before;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.EditableRecordSet;
import records.gui.MainWindow.MainWindowActions;
import records.gui.expressioneditor.ConsecutiveChild;
import records.gui.expressioneditor.EEDisplayNode.Focus;
import records.gui.expressioneditor.ExpressionEditor;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.TopLevelEditor.TopLevelEditorFlowPane;
import records.gui.grid.RectangleBounds;
import records.transformations.expression.Expression;
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
    private final CellPosition targetPos = new CellPosition(CellPosition.row(1), CellPosition.col(1));

    @Before
    public void setupWindow() throws Exception
    {
        mainWindowActions = TestUtil.openDataAsTable(windowToUse,null, new EditableRecordSet(ImmutableList.of(), () -> 0));

    }
    
    @Property(trials = 3)
    public void testDeleteAfterOperand(@When(seed=1L) @From(GenRandom.class) Random r) throws Exception
    {
        testBackspace("true & false", 3, "true & fals", r);
    }

    @Property(trials = 3)
    public void testDeleteAfterInvalidOperator(@From(GenRandom.class) Random r) throws Exception
    {
        testDeleteBackspace("@invalidops(true, \"&\")", 0, 2, "true", r);
    }

    @Property(trials = 3)
    public void testDeleteAfterSpareKeyword(@From(GenRandom.class) Random r) throws Exception
    {
        testDeleteBackspace("@invalidops(1, @unfinished \"+\", 2, @invalidops(@unfinished \"^aif\", @unfinished \"\"))", 2, 4, "1 + 2", r);
    }

    @Property(trials = 3)
    public void testDeleteAfterInfixOperator(@From(GenRandom.class) Random r) throws Exception
    {
        testDeleteBackspace("1 + 2", 0, 2, "12", r);
    }
    
    @Property(trials = 3)
    public void testDeleteAfterInfixOperator2(@From(GenRandom.class) Random r) throws Exception
    {
        testDeleteBackspace("a < b <= c", 0, 2, "ab <= c", r);
    }
    
    @Property(trials = 3)
    public void testDeleteAfterInfixOperator3(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspace("\"a\" ; b", 2, "@invalidops(\"a\", b)", r);
    }
    
    @Property(trials = 2)
    public void testRetypeInfix(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("1 + 2", 2, 1, "+", r);
    }

    @Property(trials = 2)
    public void testRetypeInfix2(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("1 <= 2", 2, 1, "<=", r);
    }

    @Property(trials = 2)
    public void testRetypeLeadingOperand(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("1 + 2", 1, 1, "1", r);
    }

    @Property(trials = 2)
    public void testRetypeLeadingOperand2(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("1234 + 5678", 1, 4, "1234", r);
    }

    @Property(trials = 2)
    public void testRetypeTrailingOperand(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("1 + 2", 3, 1, "2", r);
    }

    @Property(trials = 2)
    public void testRetypeTrailingOperand2(@From(GenRandom.class) Random r) throws Exception
    {
        testBackspaceRetype("123 + 456", 3, 3,"456", r);
    }
    
    // TODO more retype tests

    @Property(trials = 2)
    public void testPasteSeveral(@From(GenRandom.class) Random r) throws Exception
    {
        testPaste("12", 0, 1, "+3+4-", "1+3+4-2", r);
    }

    private void testPaste(String original, int slotIndex, int subIndex, String paste, String expected, Random r) throws Exception
    {
        DummyManager dummyManager = new DummyManager();
        Expression originalExp = Expression.parse(null, original, dummyManager.getTypeManager());
        ExpressionEditor expressionEditor = enter(originalExp, r);

        ImmutableList<ConsecutiveChild<@NonNull Expression, ExpressionSaver>> children = TestUtil.fx(() -> expressionEditor._test_getAllChildren());

        TestUtil.fx_(() -> {
            children.get(slotIndex).focus(Focus.LEFT);
            Clipboard.getSystemClipboard().setContent(ImmutableMap.of(DataFormat.PLAIN_TEXT, paste));
        });
        for (int i = 0; i < subIndex; i++)
        {
            push(KeyCode.RIGHT);
        }
        push(KeyCode.SHORTCUT, KeyCode.V);

        Expression after = TestUtil.fx(() -> expressionEditor.save());

        assertEquals(Expression.parse(null, expected, dummyManager.getTypeManager()), after);

        clickOn(".cancel-button");
    }
    
    private void testBackspaceRetype(String original, int deleteBefore, int deleteCount, String retype, Random r) throws Exception
    {
        DummyManager dummyManager = new DummyManager();
        Expression originalExp = Expression.parse(null, original, dummyManager.getTypeManager());
        ExpressionEditor expressionEditor = enter(originalExp, r);

        ImmutableList<ConsecutiveChild<@NonNull Expression, ExpressionSaver>> children = TestUtil.fx(() -> expressionEditor._test_getAllChildren());

        if (deleteBefore == children.size())
        {
            TestUtil.fx_(() -> expressionEditor.focus(Focus.RIGHT));
            if (r.nextBoolean())
                push(KeyCode.RIGHT); // Move into empty slot.
        }
        else
            TestUtil.fx_(() -> children.get(deleteBefore).focus(Focus.LEFT));

        for (int i = 0; i < deleteCount; i++)
        {
            push(KeyCode.BACK_SPACE);
        }
        write(retype);

        Expression after = TestUtil.fx(() -> expressionEditor.save());

        assertEquals(originalExp, after);

        clickOn(".cancel-button");
    }

    private void testDeleteBackspace(String original, int deleteAfter, int deleteBefore, String expectedStr, Random r) throws Exception
    {
        assertEquals(1, mainWindowActions._test_getTableManager().getAllTables().size());
        testBackspace(original, deleteBefore, expectedStr, r);
        assertEquals(2, mainWindowActions._test_getTableManager().getAllTables().size());
        triggerTableHeaderContextMenu(mainWindowActions._test_getVirtualGrid(), targetPos);
        clickOn(".id-tableDisplay-menu-delete");
        TestUtil.sleep(300);
        assertEquals(1, mainWindowActions._test_getTableManager().getAllTables().size());
        testDelete(original, deleteAfter, expectedStr, r);
    }

    private void testBackspace(String original, int deleteBefore, String expectedStr, Random r) throws Exception
    {
        DummyManager dummyManager = new DummyManager();
        Expression expectedExp = Expression.parse(null, expectedStr, dummyManager.getTypeManager());
        Expression originalExp = Expression.parse(null, original, dummyManager.getTypeManager());
        ExpressionEditor expressionEditor = enter(originalExp, r);
        
        assertEquals(originalExp, TestUtil.fx(() -> expressionEditor.save()));

        ImmutableList<ConsecutiveChild<@NonNull Expression, ExpressionSaver>> children = TestUtil.fx(() -> expressionEditor._test_getAllChildren());

        if (deleteBefore > children.size())
            throw new RuntimeException("Target " + deleteBefore + " out of bounds: " + children.stream().map(c -> "  " + c.toString()).collect(Collectors.joining("\n")));
        
        if (deleteBefore == children.size())
        {
            TestUtil.fx_(() -> expressionEditor.focus(Focus.RIGHT));
            if (r.nextBoolean())
                push(KeyCode.RIGHT); // Move into empty slot.
        }
        else
            TestUtil.fx_(() -> children.get(deleteBefore).focus(Focus.LEFT));

        push(KeyCode.BACK_SPACE);
        
        TestUtil.sleep(1000);

        Expression after = TestUtil.fx(() -> expressionEditor.save());

        assertEquals(expectedExp, after);
        
        moveAndDismissPopupsAtPos(point(".cancel-button"));
        clickOn();
    }

    private void testDelete(String original, int deleteAfter, String expectedStr, Random r) throws Exception
    {
        DummyManager dummyManager = new DummyManager();
        Expression expectedExp = Expression.parse(null, expectedStr, dummyManager.getTypeManager());
        ExpressionEditor expressionEditor = enter(Expression.parse(null, original, dummyManager.getTypeManager()), r);

        ImmutableList<ConsecutiveChild<@NonNull Expression, ExpressionSaver>> children = TestUtil.fx(() -> expressionEditor._test_getAllChildren());

        if (deleteAfter == -1)
        {
            TestUtil.fx_(() -> expressionEditor.focus(Focus.LEFT));
            if (r.nextBoolean())
                push(KeyCode.LEFT); // Move into empty slot.
        }
        else
            TestUtil.fx_(() -> children.get(deleteAfter).focus(Focus.RIGHT));

        push(KeyCode.DELETE);

        Expression after = TestUtil.fx(() -> expressionEditor.save());

        assertEquals(expectedExp, after);

        clickOn(".cancel-button");
    }
    
    private ExpressionEditor enter(Expression expression, Random r) throws Exception
    {
        Region gridNode = TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
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

        ExpressionEditor expressionEditor = (ExpressionEditor)lookup(".expression-editor").<TopLevelEditorFlowPane>query()._test_getEditor();
        return expressionEditor;
    }
}
