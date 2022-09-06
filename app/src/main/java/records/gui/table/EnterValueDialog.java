package records.gui.table;

import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.gui.dtf.DocumentTextField;
import records.gui.dtf.RecogniserDocument;
import records.gui.dtf.TableDisplayUtility;
import records.gui.dtf.TableDisplayUtility.RecogniserAndType;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.gui.DimmableParent;
import xyz.columnal.utility.gui.ErrorableLightDialog;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;

@OnThread(Tag.FXPlatform)
public class EnterValueDialog<V extends @NonNull @ImmediateValue Object> extends ErrorableLightDialog<V>
{
    private final RecogniserDocument<V> document;

    public EnterValueDialog(DimmableParent parent, DataType dataType, RecogniserAndType<V> recogniserAndType) throws InternalException
    {
        super(parent, true);
        initModality(Modality.APPLICATION_MODAL);
        String initialContent;
        try
        {
            initialContent = DataTypeUtility.valueToStringFX(DataTypeUtility.makeDefaultValue(dataType));
        }
        catch (UserException e)
        {
            // Shouldn't happen when converting default value
            initialContent = "";
        }
        document = new RecogniserDocument<V>(initialContent, recogniserAndType.itemClass, recogniserAndType.recogniser, null, (a, b, c) -> {}, k -> getDialogPane().lookupButton(ButtonType.OK).requestFocus(), null);
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
        return document.getLatestValue().<@Localized String, V>mapBoth(err -> err.error.toPlain(), v -> v);
    }
}
