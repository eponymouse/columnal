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

import org.testjavafx.Motion;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.Orientation;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;
import org.junit.Assert;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.log.Log;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import org.testfx.util.NodeQueryUtils;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.MainLexer2;
import xyz.columnal.grammar.MainParser2;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.transformations.expression.type.TypeExpression;
import test.DummyManager;
import test.gen.GenRandom;
import test.gen.GenTaggedTypeDefinition;
import test.gui.trait.CheckWindowBoundsTrait;
import test.gui.trait.EnterTypeTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.trait.TextFieldTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class TestTypeEdit extends FXApplicationTest implements TextFieldTrait, EnterTypeTrait, CheckWindowBoundsTrait, ScrollToTrait, PopupTrait
{    
    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testNewType(@From(GenTaggedTypeDefinition.class) TaggedTypeDefinition typeDefinition, @From(GenRandom.class) Random random) throws Exception
    {
        TBasicUtil.printSeedOnFail(() -> {
            MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, new DummyManager()).get();
            TFXUtil.sleep(1000);

            clickOn("#id-menu-view").moveTo(".id-menu-view-types", Motion.VERTICAL_FIRST).clickOn();
            TFXUtil.sleep(200);
            Window typeWindow = fxGetRealFocusedWindow();
            clickOn(".id-types-add");
            TFXUtil.sleep(200);
            Window dialog = fxGetRealFocusedWindow();
            enterTypeDetails(typeDefinition, random, mainWindowActions._test_getTableManager().getTypeManager());
            moveAndDismissPopupsAtPos(point(".ok-button"));
            clickOn(".ok-button");
            TFXUtil.sleep(500);
            Assert.assertNotEquals(dialog, fxGetRealFocusedWindow());
            clickOn(".close-button");
            TFXUtil.sleep(500);
            Assert.assertNotEquals(typeWindow, fxGetRealFocusedWindow());

            // Check that saved types in file match our new unit:
            String fileContent = FileUtils.readFileToString(TFXUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
            Log.debug("Saved:\n" + fileContent);
            TypeManager tmpTypes = new TypeManager(new UnitManager());
            tmpTypes.loadTypeDecls(getTypeSrcFromFile(fileContent));
            Assert.assertEquals(ImmutableMap.of(typeDefinition.getTaggedTypeName(), typeDefinition), tmpTypes.getUserTaggedTypes());
        });
    }

    @OnThread(Tag.Simulation)
    public String getTypeSrcFromFile(String fileContent) throws InternalException, UserException
    {
        MainParser2.FileContext file = Utility.parseAsOne(fileContent, MainLexer2::new, MainParser2::new, p -> p.file());
        return Utility.getDetail(file.content().stream().filter(c -> c.ATOM(0).getText().equals("TYPES")).findFirst().orElseThrow(() -> new AssertionError("No TYPES section")).detail());
    }

    @OnThread(Tag.Simulation)
    private void enterTypeDetails(TaggedTypeDefinition typeDefinition, Random r, TypeManager typeManager) throws InternalException, UserException
    {
        checkWindowWithinScreen();
        selectAllCurrentTextField();
        write(typeDefinition.getTaggedTypeName().getRaw());
        
        boolean canUsePlainEntry = typeDefinition.getTags().stream().allMatch(t -> t.getInner() == null) && typeDefinition.getTypeArguments().isEmpty();
        
        if (canUsePlainEntry && r.nextInt(3) != 1)
        {
            int numTags = typeDefinition.getTags().size();
            // Chance of beginning in the inner-values side:
            if (numTags >= 2 && r.nextInt(3) == 1)
            {
                int split = 1 + r.nextInt(numTags - 1);

                clickOn(".type-entry-tab-standard");
                clickOn(".type-entry-inner-type-args");
                selectAllCurrentTextField();
                push(KeyCode.DELETE);
                deleteExistingInnerValueTags();
                
                for (TagType<JellyType> tagType : Utility.iterableStream(typeDefinition.getTags().stream().limit(split)))
                {
                    enterNewInnerValueTag(r, typeManager, tagType);
                }

                assertNoErrors();
                clickOn(".type-entry-tab-plain");
                clickOn(".type-entry-plain-tags-textarea");
                push(KeyCode.END);
                write(typeDefinition.getTags().stream().skip(split).map(t -> tagDivider(r) + t.getName()).collect(Collectors.joining()), 1);
            }
            else
            {
                // Select and move to alias field:
                assertNoErrors();
                clickOn(".type-entry-tab-plain");
                clickOn(".type-entry-plain-tags-textarea");
                selectAllCurrentTextField();
                write(typeDefinition.getTags().stream().map(t -> t.getName()).collect(Collectors.joining(tagDivider(r))), 1);
            }
        }
        else
        {
            // If we can start in plain side, then potentially do so:
            int firstWithInner = Utility.findFirstIndex(typeDefinition.getTags(), t -> t.getInner() != null).orElse(typeDefinition.getTags().size());
            int alreadyEntered = 0;
            if (firstWithInner > 0 && r.nextInt(3) == 1)
            {
                assertNoErrors();
                clickOn(".type-entry-tab-plain");
                clickOn(".type-entry-plain-tags-textarea");
                selectAllCurrentTextField();
                write(typeDefinition.getTags().stream().limit(firstWithInner).map(t -> t.getName()).collect(Collectors.joining(tagDivider(r))), 1);
                alreadyEntered = firstWithInner;
            }
            
            clickOn(".type-entry-tab-standard");
            clickOn(".type-entry-inner-type-args");
            selectAllCurrentTextField();
            push(KeyCode.DELETE);
            write(typeDefinition.getTypeArguments().stream()
                .map(p -> p.getFirst() == TypeVariableKind.UNIT ? "{" + p.getSecond() + "}" : p.getSecond())
                .collect(Collectors.joining(", ")), 1);
            if (alreadyEntered == 0)
                deleteExistingInnerValueTags();
            
            for (TagType<JellyType> tagType : Utility.iterableStream(typeDefinition.getTags().stream().skip(alreadyEntered)))
            {
                enterNewInnerValueTag(r, typeManager, tagType);
            }
        }
    }

    @OnThread(Tag.Any)
    private void assertNoErrors()
    {
        Assert.assertEquals(0, count(".error-underline"));
    }

    @OnThread(Tag.Any)
    private String tagDivider(Random r)
    {
        // Blank items between dividers should get ignored:
        if (r.nextInt(5) == 1)
            return r.nextBoolean() ? " , , " : " \n , \n ";
        else
            return r.nextBoolean() ? " , " : " \n ";
    }

    @OnThread(Tag.Any)
    private void deleteExistingInnerValueTags()
    {
        int count = 0;
        while (TFXUtil.fx(() -> lookup(".small-delete").tryQuery().isPresent()) && ++count < 30)
        {
            // Click highest one as likely to not be off the screen:
            Node node = TFXUtil.<@Nullable Node>fx(() -> lookup(".small-delete-circle").match(NodeQueryUtils.isVisible()).<Node>queryAll().stream().sorted(Comparator.comparing(n -> n.localToScene(0, 0).getY())).findFirst().orElse(null));
            if (node != null)
            {
                clickOn(node);
                TFXUtil.sleep(800);
            }
        }
        assertNotShowing("Small delete", ".small-delete");
    }

    @OnThread(Tag.Any)
    private void enterNewInnerValueTag(Random r, TypeManager typeManager, TagType<JellyType> tagType) throws InternalException, UserException
    {
        int count;Optional<ScrollBar> visibleScroll = TFXUtil.fx(() -> lookup(".fancy-list > .scroll-bar").match(NodeQueryUtils.isVisible()).match((ScrollBar s) -> s.getOrientation().equals(Orientation.VERTICAL)).tryQuery());
        if (visibleScroll.isPresent())
        {
            moveTo(point(visibleScroll.get()));
            count = 0;
            while (TFXUtil.fx(() -> visibleScroll.get().getValue()) < 0.99 && ++count < 100)
                scroll(SystemUtils.IS_OS_MAC_OSX ? VerticalDirection.UP : VerticalDirection.DOWN);
        }
        TFXUtil.sleep(200);
        Log.debug("Entering: " + tagType.getName());
        //TFXUtil.fx_(() -> dumpScreenshot(getRealFocusedWindow()));
        scrollTo(".id-fancylist-add");
        moveAndDismissPopupsAtPos(point(".id-fancylist-add"));
        clickOn(".id-fancylist-add");
        TFXUtil.sleep(500);
        write(tagType.getName(), 1);
        @Nullable JellyType inner = tagType.getInner();
        if (inner != null)
        {
            push(KeyCode.TAB);
            enterType(TypeExpression.fromJellyType(inner, typeManager), r);
            // Cancel auto-complete:
            push(KeyCode.ESCAPE);
        }
    }

    @Property(trials = 10)
    @OnThread(Tag.Simulation)
    public void testNoOpEditType(@From(GenTaggedTypeDefinition.class) TaggedTypeDefinition typeDefinition, @From(GenRandom.class) Random random) throws Exception
    {
        TBasicUtil.printSeedOnFail(() -> {

            DummyManager initial = new DummyManager();
            initial.getTypeManager().registerTaggedType(typeDefinition.getTaggedTypeName().getRaw(), typeDefinition.getTypeArguments(), typeDefinition.getTags());
            MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, initial).get();
            TFXUtil.sleep(1000);

            clickOn("#id-menu-view").moveTo(".id-menu-view-types", Motion.VERTICAL_FIRST).clickOn();
            TFXUtil.sleep(200);
            clickOn(".types-list");
            push(KeyCode.DOWN);
            push(KeyCode.HOME);
            clickOn(".id-types-edit");
            TFXUtil.sleep(2000);
            clickOn(".ok-button");
            TFXUtil.sleep(500);
            clickOn(".close-button");
            TFXUtil.sleep(500);

            // Check that saved types in file match our new unit:
            String fileContent = FileUtils.readFileToString(TFXUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
            Log.debug("Saved:\n" + fileContent);
            TypeManager tmpTypes = new TypeManager(new UnitManager());
            tmpTypes.loadTypeDecls(getTypeSrcFromFile(fileContent));
            Assert.assertEquals(ImmutableMap.of(typeDefinition.getTaggedTypeName(), typeDefinition), tmpTypes.getUserTaggedTypes());
        });
    }

    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testEditType(@From(GenTaggedTypeDefinition.class) TaggedTypeDefinition before, @From(GenTaggedTypeDefinition.class) TaggedTypeDefinition after, @From(GenRandom.class) Random random) throws Exception
    {
        TBasicUtil.printSeedOnFail(() -> {
            DummyManager initial = new DummyManager();
            initial.getTypeManager().registerTaggedType(before.getTaggedTypeName().getRaw(), before.getTypeArguments(), before.getTags());
            MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, initial).get();
            TFXUtil.sleep(1000);

            clickOn("#id-menu-view").moveTo(".id-menu-view-types", Motion.VERTICAL_FIRST).clickOn();
            TFXUtil.sleep(200);
            clickOn(".types-list");
            push(KeyCode.DOWN);
            push(KeyCode.HOME);
            clickOn(".id-types-edit");
            enterTypeDetails(after, random, mainWindowActions._test_getTableManager().getTypeManager());
            TFXUtil.sleep(500);
            moveAndDismissPopupsAtPos(point(".ok-button"));
            clickOn(".ok-button");
            TFXUtil.sleep(500);
            clickOn(".close-button");
            TFXUtil.sleep(500);

            // Check that saved types in file match our new unit:
            String fileContent = FileUtils.readFileToString(TFXUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
            Log.debug("Saved:\n" + fileContent);
            TypeManager tmpTypes = new TypeManager(new UnitManager());
            tmpTypes.loadTypeDecls(getTypeSrcFromFile(fileContent));
            Assert.assertEquals(ImmutableMap.of(after.getTaggedTypeName(), after), tmpTypes.getUserTaggedTypes());
        });
    }

    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testDeleteType(@From(GenTaggedTypeDefinition.class) TaggedTypeDefinition typeDefinitionA, @From(GenTaggedTypeDefinition.class) TaggedTypeDefinition typeDefinitionB, @From(GenTaggedTypeDefinition.class) TaggedTypeDefinition typeDefinitionC, int whichToDelete) throws Exception
    {
        TBasicUtil.printSeedOnFail(() -> {
            DummyManager prevManager = new DummyManager();
            prevManager.getTypeManager().registerTaggedType(typeDefinitionA.getTaggedTypeName().getRaw(), typeDefinitionA.getTypeArguments(), typeDefinitionA.getTags());
            prevManager.getTypeManager().registerTaggedType(typeDefinitionB.getTaggedTypeName().getRaw(), typeDefinitionB.getTypeArguments(), typeDefinitionB.getTags());
            prevManager.getTypeManager().registerTaggedType(typeDefinitionC.getTaggedTypeName().getRaw(), typeDefinitionC.getTypeArguments(), typeDefinitionC.getTags());
            MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, prevManager).get();
            TFXUtil.sleep(1000);

            clickOn("#id-menu-view").moveTo(".id-menu-view-types", Motion.VERTICAL_FIRST).clickOn();
            TFXUtil.sleep(200);
            clickOn(".types-list");
            push(KeyCode.DOWN);
            push(KeyCode.HOME);
            TaggedTypeDefinition toDelete;
            if ((whichToDelete % 3) == 0)
                toDelete = typeDefinitionA;
            else if ((whichToDelete % 3) == 1)
                toDelete = typeDefinitionB;
            else
                toDelete = typeDefinitionC;
            int count = 0;
            String toDeleteCellText = toDelete.getTaggedTypeName().getRaw() + toDelete.getTypeArguments().stream().map(t -> "(" + t.getSecond() + ")").collect(Collectors.joining(""));
            while (!existsSelectedCell(toDeleteCellText) && count++ < 3)
                push(KeyCode.DOWN);
            assertTrue(toDeleteCellText, existsSelectedCell(toDeleteCellText));
            clickOn(".id-types-remove");
            TFXUtil.sleep(500);
            clickOn(".close-button");
            TFXUtil.sleep(500);

            // Check that saved units in file match our new unit:
            String fileContent = FileUtils.readFileToString(TFXUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
            Log.debug("Saved:\n" + fileContent);
            TypeManager tmpTypes = new TypeManager(new UnitManager());
            HashMap<TypeId, TaggedTypeDefinition> remaining = new HashMap<>();
            remaining.put(typeDefinitionA.getTaggedTypeName(), typeDefinitionA);
            remaining.put(typeDefinitionB.getTaggedTypeName(), typeDefinitionB);
            remaining.put(typeDefinitionC.getTaggedTypeName(), typeDefinitionC);
            remaining.remove(toDelete.getTaggedTypeName());
            tmpTypes.loadTypeDecls(getTypeSrcFromFile(fileContent));
            Assert.assertEquals(remaining, tmpTypes.getUserTaggedTypes());
        });
    }

    @OnThread(Tag.Simulation)
    private boolean existsSelectedCell(String content)
    {
        return TFXUtil.fx(() -> lookup(".list-cell").match(t -> {
            if (t instanceof ListCell)
            {
                ListCell<?> listCell = (ListCell<?>) t;
                return TFXUtil.fx(() -> {
                    Log.debug("Cell text: \"" + listCell.getText() + "\" sel: " + listCell.isSelected());
                    return listCell.isSelected() && content.equals(listCell.getText());
                });
            }
            return false;
        }).tryQuery().isPresent());
    }
}
