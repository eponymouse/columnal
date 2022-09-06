package utility.gui;

import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.i18n.qual.Localized;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 11/06/2017.
 */
@OnThread(Tag.FXPlatform)
public class ErrorLabel extends TextFlow
{
    private final Text text = new Text();

    public ErrorLabel()
    {
        getChildren().setAll(text);
        text.getStyleClass().add("error-label");
    }

    public void setText(@Localized String content)
    {
        text.setText(content);
    }
}
