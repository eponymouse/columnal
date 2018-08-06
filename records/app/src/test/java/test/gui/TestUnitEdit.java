package test.gui;

import annotation.identifier.qual.UnitIdentifier;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import org.testfx.framework.junit.ApplicationTest;
import records.data.EditableRecordSet;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.data.unit.UnitDeclaration;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import records.grammar.MainParser;
import records.grammar.MainParser.FileContext;
import records.gui.MainWindow.MainWindowActions;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenUnitDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestUnitEdit extends ApplicationTest
{
    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
    }
    
    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testNewUnit(@When(seed=1L) @From(GenUnitDefinition.class) GenUnitDefinition.UnitDetails unitDetails) throws Exception
    {
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, new DummyManager()).get();
        TestUtil.sleep(1000);
        
        clickOn("#id-menu-view").clickOn(".id-menu-view-units");
        TestUtil.delay(200);
        clickOn(".id-units-userDeclared-add");
        TestUtil.delay(200);
        write(unitDetails.name);
        // Focus the alias radio:
        push(KeyCode.TAB);
        if (unitDetails.aliasOrDeclaration.isLeft())
        {
            // Select and move to alias field:
            push(KeyCode.SPACE);
            push(KeyCode.TAB);
            write(unitDetails.aliasOrDeclaration.getLeft(""));
        }
        else
        {
            // Move to other radio, select, and then move to units:
            push(KeyCode.TAB);
            push(KeyCode.SPACE);
            push(KeyCode.TAB);
            UnitDeclaration declaration = unitDetails.aliasOrDeclaration.getRight("");
            write(declaration.getDefined().getDescription());
            push(KeyCode.TAB);
            @Nullable Pair<Rational, Unit> equiv = declaration.getEquivalentTo();
            if (equiv != null)
                write(equiv.getFirst().toString());
            push(KeyCode.TAB);
            if (equiv != null)
                write(equiv.getSecond().toString());
        }
        clickOn(".ok-button");
        TestUtil.sleep(500);

        // Check that saved units in file match our new unit:
        FileContext file = Utility.parseAsOne(FileUtils.readFileToString(TestUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8"), MainLexer::new, MainParser::new, p -> p.file());
        UnitManager tmpUnits = new UnitManager();
        tmpUnits.loadUserUnits(file.units());
        assertEquals(ImmutableMap.of(unitDetails.name, unitDetails.aliasOrDeclaration), tmpUnits.getAllUserDeclared());
    }
}
