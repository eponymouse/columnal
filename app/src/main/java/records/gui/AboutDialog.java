package records.gui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.gui.DimmableParent;
import utility.gui.FXUtility;
import utility.gui.GUI;

@OnThread(Tag.FXPlatform)
public class AboutDialog extends Dialog<Void>
{
    public AboutDialog(DimmableParent owner)
    {
        initOwner(owner.dimWhileShowing(this));
        initModality(Modality.APPLICATION_MODAL);
        getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);

        ImageView title = FXUtility.makeImageView("columnal.png", null, 90);
        ImageView logo = FXUtility.makeImageView("logo.png", null, 100);
        HBox header = new HBox(Utility.streamNullable(title, logo).toArray(Node[]::new));
        header.getStyleClass().add("logo-container");
        header.setAlignment(Pos.BOTTOM_CENTER);

        TextArea info = new TextArea(
            "Version: " + System.getProperty("columnal.version") + "\n" +
            "Operating system: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")\n" +
            "Location: " + System.getProperty("user.dir") + "\n"
        );
        info.setEditable(false);
        getDialogPane().setContent(GUI.borderTopCenter(header, info));
    }
}
