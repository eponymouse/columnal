package records.gui.dtf;

import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.error.InternalException;
import records.gui.dtf.Recogniser.ErrorDetails;
import records.gui.dtf.Recogniser.ParseProgress;
import records.gui.dtf.Recogniser.SuccessDetails;
import utility.Either;
import utility.FXPlatformBiConsumer;
import utility.FXPlatformRunnable;
import utility.FXPlatformSupplier;
import utility.FunctionInt;
import utility.Pair;
import utility.SimulationSupplier;
import utility.SimulationSupplierInt;
import utility.Workers;
import utility.Workers.Priority;

import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

public final class RecogniserDocument<V> extends DisplayDocument
{
    private final Recogniser<V> recogniser;
    private final FXPlatformBiConsumer<String, @Nullable V> saveChange;
    private final FXPlatformRunnable relinquishFocus;
    private final Class<V> itemClass;
    private final @Nullable SimulationSupplierInt<Boolean> checkEditable;
    private OptionalInt curErrorPosition = OptionalInt.empty();
    private String valueOnFocusGain;
    private Either<ErrorDetails, SuccessDetails<V>> latestValue;
    private @Nullable FXPlatformRunnable onFocusLost;
    private Pair<ImmutableList<Pair<Set<String>, String>>, CaretPositionMapper> unfocusedDocument;

    public RecogniserDocument(String initialContent, Class<V> valueClass, Recogniser<V> recogniser, @Nullable SimulationSupplierInt<Boolean> checkStartEdit, FXPlatformBiConsumer<String, @Nullable V> saveChange, FXPlatformRunnable relinquishFocus)
    {
        super(initialContent);
        this.itemClass = valueClass;
        this.recogniser = recogniser;
        this.saveChange = saveChange;
        this.relinquishFocus = relinquishFocus;
        this.checkEditable = checkStartEdit;
        recognise(false);
        valueOnFocusGain = initialContent;
    }

    @Override
    void focusChanged(boolean focused)
    {
        if (focused && checkEditable != null)
        {
            @Nullable SimulationSupplierInt<Boolean> checkEditableFinal = checkEditable;
            Workers.onWorkerThread("Check editable", Priority.FETCH, () -> {
                try
                {
                    if (!checkEditableFinal.get())
                    {
                        // Cancel the edit:
                        Platform.runLater(() -> {
                            replaceText(0, getLength(), valueOnFocusGain);
                            defocus();
                            Alert alert = new Alert(AlertType.ERROR, "Cannot edit value on a row with an error in the identifier column.", ButtonType.OK);
                            alert.getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
                            alert.showAndWait();
                            // Restore focus to reasonable position:
                            defocus();
                        });
                    }
                }
                catch (InternalException e)
                {
                    Log.log(e);
                    // Just have to ignore...
                }
            });
        }
        
        super.focusChanged(focused);
        if (!focused)
        {
            recognise(!getText().equals(valueOnFocusGain));
            if (onFocusLost != null)
                onFocusLost.run();
        }
        else
        {
            valueOnFocusGain = getText();
        }
    }

    @EnsuresNonNull({"latestValue", "unfocusedDocument"})
    @RequiresNonNull({"recogniser", "saveChange", "curErrorPosition"})
    private void recognise(@UnknownInitialization(DisplayDocument.class) RecogniserDocument<V> this, boolean save)
    {
        String text = getText();
        latestValue = recogniser.process(ParseProgress.fromStart(text), false)
                        .flatMap(SuccessDetails::requireEnd);
        latestValue.ifLeft(err -> {
            curErrorPosition = OptionalInt.of(err.errorPosition);
            if (save)
                saveChange.consume(text, null);
        });
        latestValue.ifRight(x -> {
            curErrorPosition = OptionalInt.empty();
            if (save)
                saveChange.consume(text, x.value);
        });
        this.unfocusedDocument = new Pair<>(makeStyledSpans(curErrorPosition, text).collect(ImmutableList.<Pair<Set<String>, String>>toImmutableList()), n -> n);
    }

    @Override
    Stream<Pair<Set<String>, String>> getStyledSpans(boolean focused)
    {
        if (!focused && unfocusedDocument != null)
            return unfocusedDocument.getFirst().stream();
            
        return makeStyledSpans(curErrorPosition, getText());
    }

    private static Stream<Pair<Set<String>, String>> makeStyledSpans(OptionalInt curErrorPosition, String text)
    {
        if (!curErrorPosition.isPresent() || curErrorPosition.getAsInt() >= text.length())
        {
            return Stream.of(new Pair<>(ImmutableSet.of(), text));
        }
        else
        {
            return ImmutableList.<Pair<Set<String>, String>>of(
                    new Pair<Set<String>, String>(ImmutableSet.of(), text.substring(0, curErrorPosition.getAsInt())),
                    new Pair<Set<String>, String>(ImmutableSet.of("input-error"), text.substring(curErrorPosition.getAsInt(), curErrorPosition.getAsInt() + 1)),
                    new Pair<Set<String>, String>(ImmutableSet.of(), text.substring(curErrorPosition.getAsInt() + 1))
            ).stream();
        }
    }

    @Override
    boolean hasError()
    {
        return curErrorPosition.isPresent();
    }

    @Override
    void defocus()
    {
        relinquishFocus.run();
    }

    public Either<ErrorDetails, V> getLatestValue()
    {
        return latestValue.map(x -> x.value);
    }

    // Note: this is not a list of listeners, there's only one.
    public void setOnFocusLost(FXPlatformRunnable onFocusLost)
    {
        this.onFocusLost = onFocusLost;
    }

    public Class<V> getItemClass()
    {
        return itemClass;
    }

    public void setUnfocusedDocument(ImmutableList<Pair<Set<String>, String>> unfocusedDocument, CaretPositionMapper positionMapper)
    {
        this.unfocusedDocument = new Pair<>(unfocusedDocument, positionMapper);
    }

    @Override
    int mapCaretPos(int pos)
    {
        if (unfocusedDocument != null)
            return unfocusedDocument.getSecond().mapCaretPosition(pos);
        else
            return pos;
    }
}
