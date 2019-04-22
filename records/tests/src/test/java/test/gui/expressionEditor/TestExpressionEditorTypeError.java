package test.gui.expressionEditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.shape.Path;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.SubstringMatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.gui.lexeditor.EditorDisplay;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.CanonicalSpan;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import test.TestUtil;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationFunction;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditorTypeError extends BaseTestExpressionEditorError
{
    @Test
    public void testEmptyList()
    {
        // Should be no error:
        testError("[]", e(0, 2, "ambig"));
    }

    @Test
    public void testLengthEmptyList()
    {
        // Should be no error:
        testError("list length([])");
    }
    
    @Test
    public void testTypeError1()
    {
        testError("2+true", e(2, 6, "number"));
    }

    @Test
    public void testTypeError1b()
    {
        testError("2+true+3", e(2, 6, "number"));
    }

    @Test
    public void testTypeError2()
    {
        testError("[2,true]", e(0, 8, "type"));
    }

    @Test
    public void testTypeError3()
    {
        testError("@if0@then1@else2@endif", e(3, 4, "boolean"));
    }

    @Test
    public void testFunctionError1()
    {
        testError("element([1],false)", e(0, 18, "type"));
    }

    @Test
    public void testUnitError1()
    {
        testError("2{s}+3{m}", e(5, 9, "number"));
    }

    @Test
    public void testUnitError1b()
    {
        testError("2{s}+3{m}+4{s}", e(5, 9, "number"));
    }

    @Test
    public void testAmbiguousTypeError1()
    {
        testError("from text(\"\")", e(0, 13, "type"));
    }

    @Test
    public void testAmbiguousTypeError1b()
    {
        testError("[from text(\"\")]", e(0, 15, "type"));
    }

    @Test
    public void testAmbiguousTypeError1c()
    {
        testError("@iftrue@thenfrom text(\"\")@else[]@endif", e(0, 38, "type"));
    }

    @Test
    public void testAmbiguousTypeError2()
    {
        testError("element([],1)", e(0, 13, "type"));
    }
}
