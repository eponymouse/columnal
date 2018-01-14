package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
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
    public FixList(ImmutableList<Pair<@Localized String, FXPlatformRunnable>> fixes)
    {
        selectedIndex = -1;

        getStyleClass().add("quick-fix-list");

        setFixes(fixes);
    }

    public void setFixes(@UnknownInitialization(Object.class) FixList this, ImmutableList<Pair<@Localized String, FXPlatformRunnable>> fixes)
    {
        getChildren().clear();
        if (!fixes.isEmpty())
        {
            getChildren().add(GUI.label("error.fixes"));
        }
        for (Pair<@Localized String, FXPlatformRunnable> fix : fixes)
        {
            getChildren().add(new FixRow(fix.getFirst(), fix.getSecond()));
        }
    }

    private class FixRow extends BorderPane
    {
        private final FXPlatformRunnable execute;

        @OnThread(Tag.FXPlatform)
        public FixRow(String text, FXPlatformRunnable execute)
        {
            this.execute = execute;
            FixRow.this.getStyleClass().add("quick-fix-row");
            
            setCenter(new Label(text));

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
