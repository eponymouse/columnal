package test.gui;

import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.Orientation;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableCell;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import log.Log;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.NodeQueryUtils;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TaggedTypeDefinition;
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

import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class TestTypeEdit extends ApplicationTest implements TextFieldTrait, EnterTypeTrait
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
            write(typeDefinition.getTypeArguments().stream().map(p -> p.getSecond()).collect(Collectors.joining(", ")), 1);
            for (TagType<JellyType> tagType : typeDefinition.getTags())
            {
                Optional<ScrollBar> visibleScroll = lookup(".fancy-list > .scroll-bar").match(NodeQueryUtils.isVisible()).match((ScrollBar s) -> TestUtil.fx(() -> s.getOrientation()).equals(Orientation.VERTICAL)).tryQuery();
                if (visibleScroll.isPresent())
                {
                    moveTo(visibleScroll.get());
                    while (TestUtil.fx(() -> visibleScroll.get().getValue()) < 0.99)
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

    /*
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
        
        clickOn(".ok-button");
        TestUtil.sleep(500);
        clickOn(".close-button");
        TestUtil.sleep(500);

        // Check that saved units in file match our new unit:
        String fileContent = FileUtils.readFileToString(TestUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
        Log.debug("Saved:\n" + fileContent);
        FileContext file = Utility.parseAsOne(fileContent, MainLexer::new, MainParser::new, p -> p.file());
        UnitManager tmpUnits = new UnitManager();
        tmpUnits.loadUserUnits(file.units());
        assertEquals(ImmutableMap.of(details.name, details.aliasOrDeclaration), tmpUnits.getAllUserDeclared());
    }



    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testEditUnit(@When(seed=2L) @From(GenUnitDefinition.class) GenUnitDefinition.UnitDetails before, @When(seed=2L) @From(GenUnitDefinition.class) GenUnitDefinition.UnitDetails after) throws Exception
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

        clickOn(".ok-button");
        TestUtil.sleep(500);
        clickOn(".close-button");
        TestUtil.sleep(500);

        // Check that saved units in file match our new unit:
        String fileContent = FileUtils.readFileToString(TestUtil.fx(() -> mainWindowActions._test_getCurFile()), "UTF-8");
        Log.debug("Saved:\n" + fileContent);
        FileContext file = Utility.parseAsOne(fileContent, MainLexer::new, MainParser::new, p -> p.file());
        UnitManager tmpUnits = new UnitManager();
        tmpUnits.loadUserUnits(file.units());
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
        FileContext file = Utility.parseAsOne(fileContent, MainLexer::new, MainParser::new, p -> p.file());
        UnitManager tmpUnits = new UnitManager();
        tmpUnits.loadUserUnits(file.units());
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
    */
}
