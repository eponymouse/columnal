package test.gui;

import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import log.Log;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import records.data.unit.Unit;
import records.data.unit.UnitDeclaration;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.grammar.MainLexer2;
import records.grammar.MainParser2;
import records.grammar.MainParser2.FileContext;
import records.gui.MainWindow.MainWindowActions;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenUnitDefinition;
import test.gui.trait.PopupTrait;
import test.gui.trait.TextFieldTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class TestUnitEdit extends FXApplicationTest implements TextFieldTrait, PopupTrait
{
    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testNewUnit(@From(GenUnitDefinition.class) GenUnitDefinition.UnitDetails unitDetails) throws Exception
    {
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, new DummyManager()).get();
        TestUtil.sleep(1000);
        
        clickOn("#id-menu-view").clickOn(".id-menu-view-units");
        TestUtil.delay(200);
        clickOn(".id-units-userDeclared-add");
        TestUtil.delay(200);
        enterUnitDetails(unitDetails);
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        TestUtil.sleep(500);
        clickOn(".close-button");
        TestUtil.sleep(500);

        // Check that saved units in file match our new unit:
        String fileContent = FileUtils.readFileToString(TestUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
        Log.debug("Saved:\n" + fileContent);
        String unitSrc = getUnitSrcFromFile(fileContent);
        UnitManager tmpUnits = new UnitManager();
        
        tmpUnits.loadUserUnits(unitSrc);
        assertEquals(ImmutableMap.of(unitDetails.name, unitDetails.aliasOrDeclaration), tmpUnits.getAllUserDeclared());
    }

    @OnThread(Tag.Simulation)
    public String getUnitSrcFromFile(String fileContent) throws InternalException, records.error.UserException
    {
        FileContext file = Utility.parseAsOne(fileContent, MainLexer2::new, MainParser2::new, p -> p.file());
        return Utility.getDetail(file.content(0).detail());
    }

    @OnThread(Tag.Simulation)
    private void enterUnitDetails(GenUnitDefinition.UnitDetails unitDetails) throws InternalException
    {
        correctTargetWindow();
        TextInputControl input = selectAllCurrentTextField();
        write(unitDetails.name);
        assertEquals(unitDetails.name, TestUtil.fx(input::getText));
        if (unitDetails.aliasOrDeclaration.isLeft())
        {
            // Select and move to alias field:
            clickOn(".id-unit-alias");
            
            push(KeyCode.TAB);
            selectAllCurrentTextField();
            write(unitDetails.aliasOrDeclaration.getLeft(""));
        }
        else
        {
            // Select and move to units:
            clickOn(".id-unit-full");
            push(KeyCode.TAB);
            UnitDeclaration declaration = unitDetails.aliasOrDeclaration.getRight("");
            selectAllCurrentTextField();
            write(declaration.getDefined().getDescription());
            push(KeyCode.TAB);
            CheckBox equivCheck = getFocusOwner(CheckBox.class);
            @Nullable Pair<Rational, Unit> equiv = declaration.getEquivalentTo();
            if (equiv != null)
            {
                // Tick the box:
                if (!TestUtil.fx(() -> equivCheck.isSelected()))
                    push(KeyCode.SPACE);
                push(KeyCode.TAB);
                selectAllCurrentTextField();
                write(equiv.getFirst().toString());
                push(KeyCode.TAB);
                push(KeyCode.SHORTCUT, KeyCode.A);
                push(KeyCode.BACK_SPACE);
                write(equiv.getSecond().toString());
            }
            else
            {
                // Untick the box:
                if (TestUtil.fx(() -> equivCheck.isSelected()))
                    push(KeyCode.SPACE);
            }
        }
    }

    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testNoOpEditUnit(@From(GenUnitDefinition.class) GenUnitDefinition.UnitDetails details) throws Exception
    {
        DummyManager prevManager = new DummyManager();
        prevManager.getUnitManager().addUserUnit(new Pair<>(details.name, details.aliasOrDeclaration));
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, prevManager).get();
        TestUtil.sleep(1000);

        clickOn("#id-menu-view").clickOn(".id-menu-view-units");
        TestUtil.delay(200);
        clickOn(".user-unit-list");
        push(KeyCode.HOME);
        clickOn(".id-units-userDeclared-edit");
        TestUtil.delay(500);
        
        // No edit
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        TestUtil.sleep(500);
        clickOn(".close-button");
        TestUtil.sleep(500);

        // Check that saved units in file match our new unit:
        String fileContent = FileUtils.readFileToString(TestUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
        Log.debug("Saved:\n" + fileContent);
        UnitManager tmpUnits = new UnitManager();
        tmpUnits.loadUserUnits(getUnitSrcFromFile(fileContent));
        assertEquals(ImmutableMap.of(details.name, details.aliasOrDeclaration), tmpUnits.getAllUserDeclared());
    }



    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testEditUnit(@From(GenUnitDefinition.class) GenUnitDefinition.UnitDetails before, @From(GenUnitDefinition.class) GenUnitDefinition.UnitDetails after) throws Exception
    {
        DummyManager prevManager = new DummyManager();
        prevManager.getUnitManager().addUserUnit(new Pair<>(before.name, before.aliasOrDeclaration));
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, prevManager).get();
        TestUtil.sleep(1000);

        clickOn("#id-menu-view").clickOn(".id-menu-view-units");
        TestUtil.delay(200);
        clickOn(".user-unit-list");
        push(KeyCode.HOME);
        clickOn(".id-units-userDeclared-edit");
        TestUtil.delay(500);

        enterUnitDetails(after);

        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        TestUtil.sleep(500);
        clickOn(".close-button");
        TestUtil.sleep(500);

        // Check that saved units in file match our new unit:
        String fileContent = FileUtils.readFileToString(TestUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
        Log.debug("Saved:\n" + fileContent);
        UnitManager tmpUnits = new UnitManager();
        tmpUnits.loadUserUnits(getUnitSrcFromFile(fileContent));
        assertEquals(ImmutableMap.of(after.name, after.aliasOrDeclaration), tmpUnits.getAllUserDeclared());
    }

    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testDeleteUnit(@From(GenUnitDefinition.class) GenUnitDefinition.UnitDetails unitDetailsA, @From(GenUnitDefinition.class) GenUnitDefinition.UnitDetails unitDetailsB, @From(GenUnitDefinition.class) GenUnitDefinition.UnitDetails unitDetailsC, int whichToDelete) throws Exception
    {
        DummyManager prevManager = new DummyManager();
        prevManager.getUnitManager().addUserUnit(new Pair<>(unitDetailsA.name, unitDetailsA.aliasOrDeclaration));
        prevManager.getUnitManager().addUserUnit(new Pair<>(unitDetailsB.name, unitDetailsB.aliasOrDeclaration));
        prevManager.getUnitManager().addUserUnit(new Pair<>(unitDetailsC.name, unitDetailsC.aliasOrDeclaration));
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, prevManager).get();
        TestUtil.sleep(1000);

        clickOn("#id-menu-view").clickOn(".id-menu-view-units");
        TestUtil.delay(200);
        clickOn(".user-unit-list");
        push(KeyCode.HOME);
        int count = 0;
        while (!existsSelectedCell(unitDetailsA.name) && count++ < 3)
            push(KeyCode.DOWN);
        assertTrue(unitDetailsA.name, existsSelectedCell(unitDetailsA.name));
        clickOn(".id-units-userDeclared-remove");
        TestUtil.sleep(500);
        clickOn(".close-button");
        TestUtil.sleep(500);

        // Check that saved units in file match our new unit:
        String fileContent = FileUtils.readFileToString(TestUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
        Log.debug("Saved:\n" + fileContent);
        UnitManager tmpUnits = new UnitManager();
        tmpUnits.loadUserUnits(getUnitSrcFromFile(fileContent));
        assertEquals(ImmutableMap.of(unitDetailsB.name, unitDetailsB.aliasOrDeclaration, unitDetailsC.name, unitDetailsC.aliasOrDeclaration), tmpUnits.getAllUserDeclared());
    }

    @OnThread(Tag.Simulation)
    private boolean existsSelectedCell(String content)
    {
        return lookup(".table-cell").match(t -> {
            if (t instanceof TableCell)
            {
                TableCell tableCell = (TableCell) t;
                return TestUtil.fx(() -> tableCell.getTableRow().isSelected() && content.equals(tableCell.getText()));
            }
            return false;
        }).tryQuery().isPresent();
    }

}
