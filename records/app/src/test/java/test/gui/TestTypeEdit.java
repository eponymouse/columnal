package test.gui;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.Orientation;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableCell;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import log.Log;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.assertj.core.internal.bytebuddy.description.type.TypeDefinition;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.NodeQueryUtils;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
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
import records.jellytype.JellyType;
import records.transformations.expression.type.TypeExpression;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenRandom;
import test.gen.GenTaggedTypeDefinition;
import test.gen.GenUnitDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class TestTypeEdit extends ApplicationTest implements TextFieldTrait, EnterTypeTrait, CheckWindowBounds
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
    
    // TODO restore
    //@Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testNewType(@When(seed=2L) @From(GenTaggedTypeDefinition.class) TaggedTypeDefinition typeDefinition, @When(seed=2L) @From(GenRandom.class) Random random) throws Exception
    {
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, new DummyManager()).get();
        TestUtil.sleep(1000);
        
        clickOn("#id-menu-view").clickOn(".id-menu-view-types");
        TestUtil.delay(200);
        clickOn(".id-types-add");
        TestUtil.delay(200);
        enterTypeDetails(typeDefinition, random, mainWindowActions._test_getTableManager().getTypeManager());
        clickOn(".ok-button");
        TestUtil.sleep(500);
        clickOn(".close-button");
        TestUtil.sleep(500);

        // Check that saved types in file match our new unit:
        String fileContent = FileUtils.readFileToString(TestUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
        Log.debug("Saved:\n" + fileContent);
        FileContext file = Utility.parseAsOne(fileContent, MainLexer::new, MainParser::new, p -> p.file());
        TypeManager  tmpTypes = new TypeManager(new UnitManager());
        tmpTypes.loadTypeDecls(file.types());
        assertEquals(ImmutableMap.of(typeDefinition.getTaggedTypeName(), typeDefinition), tmpTypes.getUserTaggedTypes());
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
            // Select and move to alias field:
            clickOn(".type-entry-tab-plain");
            clickOn(".type-entry-plain-tags-textarea");
            selectAllCurrentTextField();
            write(typeDefinition.getTags().stream().map(t -> t.getName()).collect(Collectors.joining(" | ")), 1);
        }
        else
        {
            clickOn(".type-entry-tab-standard");
            clickOn(".type-entry-inner-type-args");
            selectAllCurrentTextField();
            push(KeyCode.DELETE);
            write(typeDefinition.getTypeArguments().stream().map(p -> p.getSecond()).collect(Collectors.joining(", ")), 1);
            int count = 0;
            while (lookup(".small-delete").tryQuery().isPresent() && ++count < 30)
            {
                // Click highest one as likely to not be off the screen:
                Node node = lookup(".small-delete-circle").match(NodeQueryUtils.isVisible()).<Node>queryAll().stream().sorted(Comparator.comparing(n -> TestUtil.fx(() -> n.localToScene(0, 0).getY()))).findFirst().orElse(null);
                if (node != null)
                {
                    clickOn(node);
                    TestUtil.sleep(800);
                }
            }
            assertTrue(!lookup(".small-delete").tryQuery().isPresent());
            
            for (TagType<JellyType> tagType : typeDefinition.getTags())
            {
                Optional<ScrollBar> visibleScroll = lookup(".fancy-list > .scroll-bar").match(NodeQueryUtils.isVisible()).match((ScrollBar s) -> TestUtil.fx(() -> s.getOrientation()).equals(Orientation.VERTICAL)).tryQuery();
                if (visibleScroll.isPresent())
                {
                    moveTo(visibleScroll.get());
                    count = 0;
                    while (TestUtil.fx(() -> visibleScroll.get().getValue()) < 0.99 && ++count < 100)
                        scroll(SystemUtils.IS_OS_MAC_OSX ? VerticalDirection.UP : VerticalDirection.DOWN);
                }
                TestUtil.delay(200);
                Log.debug("Entering: " + tagType.getName());
                clickOn(".id-fancylist-add");
                TestUtil.delay(500);
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
        }
    }

    // TODO restore
    //@Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testNoOpEditType(@When(seed=2L) @From(GenTaggedTypeDefinition.class) TaggedTypeDefinition typeDefinition, @When(seed=2L) @From(GenRandom.class) Random random) throws Exception
    {
        DummyManager initial = new DummyManager();
        initial.getTypeManager().registerTaggedType(typeDefinition.getTaggedTypeName().getRaw(), typeDefinition.getTypeArguments(), typeDefinition.getTags());
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, initial).get();
        TestUtil.sleep(1000);

        clickOn("#id-menu-view").clickOn(".id-menu-view-types");
        TestUtil.delay(200);
        clickOn(".types-list");
        push(KeyCode.DOWN);
        push(KeyCode.HOME);
        clickOn(".id-types-edit");
        TestUtil.delay(500);
        clickOn(".ok-button");
        TestUtil.sleep(500);
        clickOn(".close-button");
        TestUtil.sleep(500);

        // Check that saved types in file match our new unit:
        String fileContent = FileUtils.readFileToString(TestUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
        Log.debug("Saved:\n" + fileContent);
        FileContext file = Utility.parseAsOne(fileContent, MainLexer::new, MainParser::new, p -> p.file());
        TypeManager  tmpTypes = new TypeManager(new UnitManager());
        tmpTypes.loadTypeDecls(file.types());
        assertEquals(ImmutableMap.of(typeDefinition.getTaggedTypeName(), typeDefinition), tmpTypes.getUserTaggedTypes());
    }

    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testEditType(@When(seed=2L) @From(GenTaggedTypeDefinition.class) TaggedTypeDefinition before, @When(seed=3L) @From(GenTaggedTypeDefinition.class) TaggedTypeDefinition after, @When(seed=2L) @From(GenRandom.class) Random random) throws Exception
    {
        DummyManager initial = new DummyManager();
        initial.getTypeManager().registerTaggedType(before.getTaggedTypeName().getRaw(), before.getTypeArguments(), before.getTags());
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, initial).get();
        TestUtil.sleep(1000);

        clickOn("#id-menu-view").clickOn(".id-menu-view-types");
        TestUtil.delay(200);
        clickOn(".types-list");
        push(KeyCode.DOWN);
        push(KeyCode.HOME);
        clickOn(".id-types-edit");
        enterTypeDetails(after, random, mainWindowActions._test_getTableManager().getTypeManager());
        TestUtil.delay(500);
        clickOn(".ok-button");
        TestUtil.sleep(500);
        clickOn(".close-button");
        TestUtil.sleep(500);

        // Check that saved types in file match our new unit:
        String fileContent = FileUtils.readFileToString(TestUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
        Log.debug("Saved:\n" + fileContent);
        FileContext file = Utility.parseAsOne(fileContent, MainLexer::new, MainParser::new, p -> p.file());
        TypeManager  tmpTypes = new TypeManager(new UnitManager());
        tmpTypes.loadTypeDecls(file.types());
        assertEquals(ImmutableMap.of(after.getTaggedTypeName(), after), tmpTypes.getUserTaggedTypes());
    }

    //@Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testDeleteType(@When(seed=2L) @From(GenTaggedTypeDefinition.class) TaggedTypeDefinition typeDefinitionA, @When(seed=3L) @From(GenTaggedTypeDefinition.class) TaggedTypeDefinition typeDefinitionB, @When(seed=4L) @From(GenTaggedTypeDefinition.class) TaggedTypeDefinition typeDefinitionC, int whichToDelete) throws Exception
    {
        DummyManager prevManager = new DummyManager();
        prevManager.getTypeManager().registerTaggedType(typeDefinitionA.getTaggedTypeName().getRaw(), typeDefinitionA.getTypeArguments(), typeDefinitionA.getTags());
        prevManager.getTypeManager().registerTaggedType(typeDefinitionB.getTaggedTypeName().getRaw(), typeDefinitionB.getTypeArguments(), typeDefinitionB.getTags());
        prevManager.getTypeManager().registerTaggedType(typeDefinitionC.getTaggedTypeName().getRaw(), typeDefinitionC.getTypeArguments(), typeDefinitionC.getTags());
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, prevManager).get();
        TestUtil.sleep(1000);

        clickOn("#id-menu-view").clickOn(".id-menu-view-types");
        TestUtil.delay(200);
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
        TestUtil.sleep(500);
        clickOn(".close-button");
        TestUtil.sleep(500);

        // Check that saved units in file match our new unit:
        String fileContent = FileUtils.readFileToString(TestUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
        Log.debug("Saved:\n" + fileContent);
        FileContext file = Utility.parseAsOne(fileContent, MainLexer::new, MainParser::new, p -> p.file());
        TypeManager  tmpTypes = new TypeManager(new UnitManager());
        HashMap<TypeId, TaggedTypeDefinition> remaining = new HashMap<>();
        remaining.put(typeDefinitionA.getTaggedTypeName(), typeDefinitionA);
        remaining.put(typeDefinitionB.getTaggedTypeName(), typeDefinitionB);
        remaining.put(typeDefinitionC.getTaggedTypeName(), typeDefinitionC);
        remaining.remove(toDelete.getTaggedTypeName());
        tmpTypes.loadTypeDecls(file.types());
        assertEquals(remaining, tmpTypes.getUserTaggedTypes());
    }

    @OnThread(Tag.Simulation)
    private boolean existsSelectedCell(String content)
    {
        return lookup(".list-cell").match(t -> {
            if (t instanceof ListCell)
            {
                ListCell<?> listCell = (ListCell<?>) t;
                return TestUtil.fx(() -> {
                    Log.debug("Cell text: \"" + listCell.getText() + "\" sel: " + listCell.isSelected());
                    return listCell.isSelected() && content.equals(listCell.getText());
                });
            }
            return false;
        }).tryQuery().isPresent();
    }
}
