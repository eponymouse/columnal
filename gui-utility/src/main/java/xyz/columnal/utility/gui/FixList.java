package utility.gui;

import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.FXPlatformRunnable;

/**
 * A GUI list of quick-fix suggestions.  Like a ListView, but sizes to fit properly
 * and allows for key events on other items (e.g. a text field) to control it and trigger it.
 */
@OnThread(Tag.FXPlatform)
public class FixList extends VBox
{
    private int selectedIndex; // TODO add support for keyboard selection
    private ImmutableList<FixInfo> fixes;

    @OnThread(Tag.FXPlatform)
    public FixList(ImmutableList<FixInfo> fixes)
    {
        selectedIndex = -1;

        getStyleClass().add("quick-fix-list");

        setFixes(fixes);
    }

    @EnsuresNonNull("this.fixes")
    public void setFixes(@UnknownInitialization(VBox.class) FixList this, ImmutableList<FixInfo> fixes)
    {
        this.fixes = fixes;
        getChildren().clear();
        if (!fixes.isEmpty())
        {
            getChildren().add(GUI.label("error.fixes", "fix-list-heading"));
        }
        for (int i = 0; i < fixes.size(); i++)
        {
            FixRow fixRow = new FixRow(fixes.get(i));
            // CSS class helps in testing:
            fixRow.getStyleClass().add("key-F" + (i + 1));
            getChildren().add(fixRow);
        }
    }

    public ImmutableList<FixInfo> getFixes()
    {
        return fixes;
    }

    public static class FixInfo
    {
        private final StyledString label;
        private final ImmutableList<String> cssClasses;
        public final FXPlatformRunnable executeFix;

        public FixInfo(StyledString label, ImmutableList<String> cssClasses, FXPlatformRunnable executeFix)
        {
            this.label = label;
            this.cssClasses = cssClasses;
            this.executeFix = executeFix;
        }
        
        public String _debug_getName()
        {
            return label.toPlain();
        }
    }

    private class FixRow extends BorderPane
    {
        private final FXPlatformRunnable execute;

        @OnThread(Tag.FXPlatform)
        public FixRow(FixInfo fixInfo)
        {
            this.execute = fixInfo.executeFix;
            getStyleClass().add("quick-fix-row");
            getStyleClass().addAll(fixInfo.cssClasses);
            
            setCenter(new TextFlow(fixInfo.label.toGUI().toArray(new Node[0])));

            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY)
                    doFix();
            });
        }

        @OnThread(Tag.FXPlatform)
        @RequiresNonNull("execute")
        private void doFix(@UnknownInitialization(BorderPane.class) FixRow this)
        {
            execute.run();
        }
    }
}
