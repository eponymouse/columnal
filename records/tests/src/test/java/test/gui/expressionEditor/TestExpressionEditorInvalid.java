package test.gui.expressionEditor;

import annotation.recorded.qual.Recorded;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import records.data.ColumnId;
import records.data.Table;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.lexeditor.ExpressionEditor;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenInvalidExpressionSource;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestExpressionEditorInvalid extends FXApplicationTest
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
        for (char c : invalid.toCharArray())
        {
            write(c);
            if ("({[".contains("" + c))
                push(KeyCode.DELETE);
        }
        @Recorded @NonNull Expression savedInvalid = TestUtil.fx(() -> expressionEditorA.save());
        ExpressionEditor expressionEditorB = makeExpressionEditor(dummyManager, savedInvalid);
        assertEquals(savedInvalid.toString(), invalid.replaceAll("[ ()]", ""), TestUtil.fx(() -> expressionEditorB._test_getRawText()).replaceAll("[ ()]", ""));
    }

    @OnThread(Tag.Any)
    private ExpressionEditor makeExpressionEditor(DummyManager dummyManager, @Nullable Expression initial)
    {
        return TestUtil.fx(() -> new ExpressionEditor(initial, new ReadOnlyObjectWrapper<@Nullable Table>(null), new ReadOnlyObjectWrapper<ColumnLookup>(new ColumnLookup()
        {
            @Override
            public @Nullable Pair<TableId, DataTypeValue> getColumn(@Nullable TableId tableId, ColumnId columnId, ColumnReferenceType columnReferenceType)
            {
                return null;
            }

            @Override
            public Stream<ColumnReference> getAvailableColumnReferences()
            {
                return Stream.of();
            }
        }), new ReadOnlyObjectWrapper<@Nullable DataType>(null), null, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()), e -> {}));
    }
}
