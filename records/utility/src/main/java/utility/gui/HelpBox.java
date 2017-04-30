package utility.gui;

import annotation.help.qual.HelpKey;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PopOver.ArrowLocation;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.gui.Help.HelpInfo;

import java.util.List;

/**
 * Created by neil on 30/04/2017.
 */
@OnThread(Tag.FXPlatform)
class HelpBox extends StackPane
{
    private @MonotonicNonNull PopOver popOver;
    private Help.@MonotonicNonNull HelpInfo helpInfo;
    // Showing full is also equivalent to whether it is pinned.
    private final BooleanProperty showingFull = new SimpleBooleanProperty(false);

    @SuppressWarnings("initialization") // For the call of showHidePopOver
    public HelpBox(@HelpKey String helpId)
    {
        getStyleClass().add("help-box");
        Circle circle = new Circle(10.0);
        circle.getStyleClass().add("circle");
        Text text = new Text("?");
        text.getStyleClass().add("question");
        getChildren().setAll(circle, text);

        setOnMouseEntered(e -> {
            if (!popupShowing())
                showPopOver(helpId);
        });
        setOnMouseExited(e -> {
            if (popupShowing() && !showingFull.get())
            {
                popOver.hide();
            }
        });
        setOnMouseClicked(e -> {
            boolean wasPinned = showingFull.get();
            showingFull.set(true);
            if (!popupShowing())
            {
                showPopOver(helpId);
            }
            else
            {
                if (wasPinned)
                    popOver.hide();
            }
        });
    }


    @EnsuresNonNullIf(expression = "popOver", result = true)
    private boolean popupShowing()
    {
        return popOver != null && popOver.isShowing();
    }

    private Help.@Nullable HelpInfo getHelpInfo(@HelpKey String helpId)
    {
        if (helpInfo == null)
        {
            @Nullable HelpInfo loaded = Help.getHelpInfo(helpId);
            if (loaded != null)
                this.helpInfo = loaded;
        }
        return helpInfo;
    }

    @OnThread(Tag.FXPlatform)
    private void showPopOver(@HelpKey String helpId)
    {
        if (popOver == null)
        {
            @Nullable HelpInfo helpInfo = getHelpInfo(helpId);
            if (helpInfo != null)
            {
                Text shortText = new Text(helpInfo.shortText);
                shortText.getStyleClass().add("short");
                TextFlow textFlow = new TextFlow(shortText);
                Text more = new Text("\nClick ? icon to show more");
                more.visibleProperty().bind(showingFull.not());
                more.managedProperty().bind(more.visibleProperty());
                textFlow.getChildren().add(more);

                textFlow.getChildren().addAll(Utility.mapList(helpInfo.fullParas, p ->
                {
                    // Blank line between paragraphs:
                    Text t = new Text("\n\n" + p);
                    t.getStyleClass().add("full");
                    t.visibleProperty().bind(showingFull);
                    t.managedProperty().bind(t.visibleProperty());
                    return t;
                }));

                BorderPane pane = new BorderPane(textFlow);
                pane.getStyleClass().add("help-content");

                popOver = new PopOver(pane);
                popOver.setTitle(helpInfo.title);
                popOver.setArrowLocation(ArrowLocation.TOP_LEFT);
                popOver.getStyleClass().add("help-popup");
                popOver.setOnHidden(e -> {showingFull.set(false);});
            }
            //org.scenicview.ScenicView.show(popOver.getRoot().getScene());
        }
        // Not guaranteed to have been created, if we can't find the hint:
        if (popOver != null)
        {
            popOver.show(this);
        }
    }
}
