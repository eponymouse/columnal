package utility.gui;

import annotation.help.qual.HelpKey;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.web.WebView;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PopOver.ArrowLocation;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.gui.Help.HelpInfo;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Created by neil on 30/04/2017.
 */
class HelpBox extends StackPane
{
    private @MonotonicNonNull PopOver popOver;
    private Help.@MonotonicNonNull HelpInfo helpInfo;

    @SuppressWarnings("initialization") // For the call of showHidePopOver
    public HelpBox(@HelpKey String helpId)
    {
        getStyleClass().add("help-box");
        Circle circle = new Circle(10.0);
        circle.getStyleClass().add("circle");
        Text text = new Text("?");
        text.getStyleClass().add("question");
        getChildren().setAll(circle, text);

        setOnMouseClicked(e -> showHidePopOver(helpId, true));
    }

    @OnThread(Tag.FXPlatform)
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
    private void showHidePopOver(@HelpKey String helpId, boolean full)
    {
        if (popOver != null && popOver.isShowing())
        {
            popOver.hide();
        }
        else
        {
            if (popOver == null)
            {
                @Nullable HelpInfo helpInfo = getHelpInfo(helpId);
                if (helpInfo != null)
                {
                    Text shortText = new Text(helpInfo.shortText + "\n");
                    shortText.getStyleClass().add("short");
                    TextFlow textFlow = new TextFlow(shortText);
                    if (full)
                    {
                        textFlow.getChildren().addAll(Utility.mapList(helpInfo.fullParas, p ->
                        {
                            Text t = new Text("\n" + p);
                            t.getStyleClass().add("full");
                            return t;
                        }));
                    }

                    BorderPane pane = new BorderPane(textFlow);
                    pane.getStyleClass().add("help-content");

                    popOver = new PopOver(pane);
                    popOver.setTitle(helpInfo.title);
                    popOver.setArrowLocation(ArrowLocation.TOP_LEFT);
                    popOver.getStyleClass().add("help-popup");
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
}
