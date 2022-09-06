package records.gui.settings;

import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Settings;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.TranslationUtility;
import utility.gui.ErrorableDialog;
import utility.gui.GUI;
import utility.gui.LabelledGrid;

import java.io.File;

@OnThread(Tag.FXPlatform)
public class EditSettingsDialog extends ErrorableDialog<Settings>
{
    private final TextField rLocation;
    private final CheckBox useRLocalLibs;

    public EditSettingsDialog(Window parent, Settings initialSettings)
    {
        initOwner(parent);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(TranslationUtility.getString("settings.title"));
        rLocation = new TextField(initialSettings.pathToRExecutable == null ? "" : initialSettings.pathToRExecutable.getAbsolutePath());
        rLocation.setPromptText(TranslationUtility.getString("settings.rexe.blank"));

        this.useRLocalLibs = new CheckBox(TranslationUtility.getString("settings.rlocallibs.checkbox"));
        this.useRLocalLibs.setSelected(initialSettings.useColumnalRLibs);
        
        getDialogPane().setContent(new LabelledGrid(
            LabelledGrid.labelledGridRow("settings.rexe", "edit-settings/rexe", GUI.borderLeftCenterRight(null, rLocation, GUI.button("settings.rexe.choose", () -> {
                FileChooser fileChooser = new FileChooser();
                File file = new File(rLocation.getText().trim());
                if (file.exists())
                {
                    if (file.isFile())
                    {
                        File parentFile = file.getParentFile();
                        if (parentFile != null)
                            fileChooser.setInitialDirectory(parentFile);
                    }
                    else if (file.isDirectory())
                        fileChooser.setInitialDirectory(file);
                }
                Scene scene = getDialogPane().getScene();
                File newFile = fileChooser.showOpenDialog(scene == null ? null : scene.getWindow());
                if (newFile != null)
                {
                    rLocation.setText(newFile.getAbsolutePath());
                }
            }))),
            LabelledGrid.labelledGridRow("settings.rlocallibs.label", "edit-settings/rlocallibs", this.useRLocalLibs)
        ));
    }

    @Override
    protected Either<@Localized String, Settings> calculateResult()
    {
        @Nullable File rexe;
        if (rLocation.getText().trim().isEmpty())
            rexe = null;
        else
        {
            rexe = new File(rLocation.getText().trim());
            if (!rexe.isFile())
            {
                return Either.left(TranslationUtility.getString("settings.error.rexe.invalid"));
            }
        }
        
        return Either.right(new Settings(rexe, useRLocalLibs.isSelected()));
    }
}
