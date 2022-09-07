package test.gui.expressionEditor;

import annotation.recorded.qual.Recorded;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableId;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.gui.lexeditor.ExpressionEditor;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenInvalidExpressionSource;
import test.gui.trait.EnterTypeTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestExpressionEditorInvalid extends FXApplicationTest implements EnterTypeTrait
{
    @OnThread(Tag.Simulation)
    @Property(trials=20)
    public void testLoadSaveInvalid(@From(GenInvalidExpressionSource.class) String invalid) throws UserException, InternalException
    {
        DummyManager dummyManager = new DummyManager();
        ExpressionEditor expressionEditorA = makeExpressionEditor(dummyManager, null);
        TestUtil.fx_(() -> {
            windowToUse.setScene(new Scene(new StackPane(expressionEditorA.getContainer())));
            windowToUse.show();
        });
        clickOn(".top-level-editor");
        enterAndDeleteSmartBrackets(invalid);
        @Recorded @NonNull Expression savedInvalid = TestUtil.fx(() -> expressionEditorA.save(false));
        ExpressionEditor expressionEditorB = makeExpressionEditor(dummyManager, savedInvalid);
        assertEquals(savedInvalid.toString(), invalid.replaceAll("[ ()]", ""), TestUtil.fx(() -> expressionEditorB._test_getRawText()).replaceAll("[ ()]", ""));
    }

    @OnThread(Tag.Any)
    private ExpressionEditor makeExpressionEditor(DummyManager dummyManager, @Nullable Expression initial)
    {
        return TestUtil.fx(() -> new ExpressionEditor(initial, new ReadOnlyObjectWrapper<@Nullable Table>(null), new ReadOnlyObjectWrapper<ColumnLookup>(TestUtil.dummyColumnLookup()), null, null, dummyManager.getTypeManager(), () -> TestUtil.createTypeState(dummyManager.getTypeManager()), FunctionList.getFunctionLookup(dummyManager.getUnitManager()), e -> {}));
    }
}
