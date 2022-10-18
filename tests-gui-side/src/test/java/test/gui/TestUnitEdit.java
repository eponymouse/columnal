/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.gui;

import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import xyz.columnal.log.Log;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitDeclaration;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.grammar.MainLexer2;
import xyz.columnal.grammar.MainParser2;
import xyz.columnal.grammar.MainParser2.FileContext;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import test.DummyManager;
import test.gen.GenUnitDefinition;
import test.gui.trait.PopupTrait;
import test.gui.trait.TextFieldTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class TestUnitEdit extends FXApplicationTest implements TextFieldTrait, PopupTrait
{
    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testNewUnit(@From(GenUnitDefinition.class) GenUnitDefinition.UnitDetails unitDetails) throws Exception
    {
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, new DummyManager()).get();
        TFXUtil.sleep(1000);
        
        clickOn("#id-menu-view").clickOn(".id-menu-view-units");
        TFXUtil.sleep(200);
        clickOn(".id-units-userDeclared-add");
        TFXUtil.sleep(500);
        enterUnitDetails(unitDetails);
        TFXUtil.doubleOk(this);
        TFXUtil.sleep(500);
        clickOn(".close-button");
        TFXUtil.sleep(500);

        // Check that saved units in file match our new unit:
        String fileContent = FileUtils.readFileToString(TFXUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
        Log.debug("Saved:\n" + fileContent);
        String unitSrc = getUnitSrcFromFile(fileContent);
        UnitManager tmpUnits = new UnitManager();
        
        tmpUnits.loadUserUnits(unitSrc);
        assertEquals(ImmutableMap.of(unitDetails.name, unitDetails.aliasOrDeclaration), tmpUnits.getAllUserDeclared());
    }

    @OnThread(Tag.Simulation)
    public String getUnitSrcFromFile(String fileContent) throws InternalException, xyz.columnal.error.UserException
    {
        FileContext file = Utility.parseAsOne(fileContent, MainLexer2::new, MainParser2::new, p -> p.file());
        return Utility.getDetail(file.content().stream().filter(c -> c.ATOM(0).getText().equals("UNITS")).findFirst().orElseThrow(() -> new AssertionError("No UNITS section")).detail());
    }

    @OnThread(Tag.Simulation)
    private void enterUnitDetails(GenUnitDefinition.UnitDetails unitDetails) throws InternalException
    {
        correctTargetWindow();
        TextInputControl input = selectAllCurrentTextField();
        write(unitDetails.name);
        assertEquals(unitDetails.name, TFXUtil.fx(input::getText));
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
                if (!TFXUtil.fx(() -> equivCheck.isSelected()))
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
                if (TFXUtil.fx(() -> equivCheck.isSelected()))
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
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, prevManager).get();
        TFXUtil.sleep(1000);

        clickOn("#id-menu-view").clickOn(".id-menu-view-units");
        TFXUtil.sleep(200);
        clickOn(".user-unit-list");
        push(KeyCode.HOME);
        clickOn(".id-units-userDeclared-edit");
        TFXUtil.sleep(500);

        // No edit
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        TFXUtil.sleep(500);
        clickOn(".close-button");
        TFXUtil.sleep(500);

        // Check that saved units in file match our new unit:
        String fileContent = FileUtils.readFileToString(TFXUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
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
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, prevManager).get();
        TFXUtil.sleep(1000);

        clickOn("#id-menu-view").clickOn(".id-menu-view-units");
        TFXUtil.sleep(200);
        clickOn(".user-unit-list");
        push(KeyCode.HOME);
        clickOn(".id-units-userDeclared-edit");
        TFXUtil.sleep(500);

        enterUnitDetails(after);

        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        TFXUtil.sleep(500);
        clickOn(".close-button");
        TFXUtil.sleep(500);

        // Check that saved units in file match our new unit:
        String fileContent = FileUtils.readFileToString(TFXUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
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
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, prevManager).get();
        TFXUtil.sleep(1000);

        clickOn("#id-menu-view").clickOn(".id-menu-view-units");
        TFXUtil.sleep(200);
        clickOn(".user-unit-list");
        push(KeyCode.HOME);
        int count = 0;
        while (!existsSelectedCell(unitDetailsA.name) && count++ < 3)
            push(KeyCode.DOWN);
        assertTrue(unitDetailsA.name, existsSelectedCell(unitDetailsA.name));
        clickOn(".id-units-userDeclared-remove");
        TFXUtil.sleep(500);
        clickOn(".close-button");
        TFXUtil.sleep(500);

        // Check that saved units in file match our new unit:
        String fileContent = FileUtils.readFileToString(TFXUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
        Log.debug("Saved:\n" + fileContent);
        UnitManager tmpUnits = new UnitManager();
        tmpUnits.loadUserUnits(getUnitSrcFromFile(fileContent));
        assertEquals(ImmutableMap.of(unitDetailsB.name, unitDetailsB.aliasOrDeclaration, unitDetailsC.name, unitDetailsC.aliasOrDeclaration), tmpUnits.getAllUserDeclared());
    }

    @OnThread(Tag.Simulation)
    private boolean existsSelectedCell(String content)
    {
        return TFXUtil.fx(() -> lookup(".table-cell").match(t -> {
            if (t instanceof TableCell)
            {
                TableCell tableCell = (TableCell) t;
                return TFXUtil.fx(() -> tableCell.getTableRow().isSelected() && content.equals(tableCell.getText()));
            }
            return false;
        }).tryQuery().isPresent());
    }

}
