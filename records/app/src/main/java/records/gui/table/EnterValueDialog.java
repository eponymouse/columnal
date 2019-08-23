package records.gui.table;

import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.dtf.DocumentTextField;
import records.gui.dtf.RecogniserDocument;
import records.gui.dtf.TableDisplayUtility;
import records.gui.dtf.TableDisplayUtility.RecogniserAndType;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.gui.DimmableParent;
import utility.gui.ErrorableLightDialog;
import utility.gui.FXUtility;
import utility.gui.GUI;

@OnThread(Tag.FXPlatform)
public class EnterValueDialog<@NonNull V> extends ErrorableLightDialog<V>
{
    private final RecogniserDocument<@NonNull V> document;

    public EnterValueDialog(DimmableParent parent, DataType dataType, RecogniserAndType<@NonNull V> recogniserAndType) throws InternalException
    {
        super(parent, true);
        initModality(Modality.APPLICATION_MODAL);
        String initialContent;
        try
        {
            initialContent = DataTypeUtility.valueToStringFX(dataType, DataTypeUtility.makeDefaultValue(dataType));
        }
        catch (UserException e)
        {
            // Shouldn't happen when converting default value
            initialContent = "";
        }
        document = new RecogniserDocument<@NonNull V>(initialContent, recogniserAndType.itemClass, recogniserAndType.recogniser, null, (a, b, c) -> {}, k -> getDialogPane().lookupButton(ButtonType.OK).requestFocus());
        DocumentTextField textField = new DocumentTextField(null);
        textField.setDocument(document);
        getDialogPane().setContent(GUI.borderTopCenterBottom(null, textField, getErrorLabel()));
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        setOnShown(e -> FXUtility.runAfter(() -> {
            textField.requestFocus();
            textField.selectAll();
        }));
    }


    @Override
    protected @OnThread(Tag.FXPlatform) Either<@Localized String, V> calculateResult()
    {
        return document.getLatestValue().mapBoth(err -> err.error.toPlain(), v -> v);
    }
}
