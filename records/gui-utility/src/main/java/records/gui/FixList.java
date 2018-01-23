package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.gui.GUI;

/**
 * A GUI list of quick-fix suggestions.  Like a ListView, but sizes to fit properly
 * and allows for key events on other items (e.g. a text field) to control it and trigger it.
 */
@OnThread(Tag.FXPlatform)
public class FixList extends VBox
{
    private int selectedIndex; // TODO add support for keyboard selection

    @OnThread(Tag.FXPlatform)
    public FixList(ImmutableList<FixInfo> fixes)
    {
        selectedIndex = -1;

        getStyleClass().add("quick-fix-list");

        setFixes(fixes);
    }

    public void setFixes(@UnknownInitialization(Object.class) FixList this, ImmutableList<FixInfo> fixes)
    {
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
    
    public static class FixInfo
    {
        private final @Localized String label;
        private final ImmutableList<String> cssClasses;
        public final FXPlatformRunnable executeFix;

        public FixInfo(@Localized String label, ImmutableList<String> cssClasses, FXPlatformRunnable executeFix)
        {
            this.label = label;
            this.cssClasses = cssClasses;
            this.executeFix = executeFix;
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
            
            setCenter(new Label(fixInfo.label));

            setOnMouseClicked(e -> {
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
