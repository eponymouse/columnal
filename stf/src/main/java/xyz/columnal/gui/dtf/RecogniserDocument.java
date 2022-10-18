/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui.dtf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import xyz.columnal.log.Log;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import xyz.columnal.error.InternalException;
import xyz.columnal.gui.dtf.Recogniser.ErrorDetails;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformFunction;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.ParseProgress;
import xyz.columnal.gui.dtf.Recogniser.SuccessDetails;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.simulation.SimulationSupplierInt;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;

import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A document which can display differently when unfocused.
 * When focused, behaves like plain text, but also uses a Recogniser
 * to potentially show errors.
 */
public final class RecogniserDocument<V> extends DisplayDocument
{
    private final Recogniser<V> recogniser;
    private final Saver<V> saveChange;
    private final FXPlatformConsumer<KeyCode> relinquishFocus;
    private final Class<V> itemClass;
    private final @Nullable SimulationSupplierInt<Boolean> checkEditable;
    private final @Nullable FXPlatformFunction<Boolean, ImmutableList<MenuItem>> getAdditionalMenuItems;
    private OptionalInt curErrorPosition = OptionalInt.empty();
    private String valueOnFocusGain;
    private Either<ErrorDetails, SuccessDetails<V>> latestValue;
    private @Nullable FXPlatformRunnable onFocusLost;
    private @MonotonicNonNull Pair<ImmutableList<Pair<Set<String>, String>>, CaretPositionMapper> unfocusedDocument;

    public RecogniserDocument(String initialContent, Class<V> valueClass, Recogniser<V> recogniser, @Nullable SimulationSupplierInt<Boolean> checkStartEdit, Saver<V> saveChange, FXPlatformConsumer<KeyCode> relinquishFocus, @Nullable FXPlatformFunction<Boolean, ImmutableList<MenuItem>> getAdditionalMenuItems)
    {
        super(initialContent);
        this.itemClass = valueClass;
        this.recogniser = recogniser;
        this.saveChange = saveChange;
        this.relinquishFocus = relinquishFocus;
        this.checkEditable = checkStartEdit;
        this.getAdditionalMenuItems = getAdditionalMenuItems;
        this.valueOnFocusGain = initialContent;
        recognise(false);
        
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
                            defocus(KeyCode.ESCAPE);
                            Alert alert = new Alert(AlertType.ERROR, "Cannot edit value on a row with an error in the identifier column.", ButtonType.OK);
                            alert.getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
                            alert.showAndWait();
                            // Restore focus to reasonable position:
                            defocus(KeyCode.ESCAPE);
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
            notifyListeners();
            if (onFocusLost != null)
                onFocusLost.run();
        }
        else
        {
            valueOnFocusGain = getText();
        }
    }

    @EnsuresNonNull({"latestValue", "unfocusedDocument"})
    @RequiresNonNull({"recogniser", "saveChange", "curErrorPosition", "valueOnFocusGain"})
    private void recognise(@UnknownInitialization(DisplayDocument.class) RecogniserDocument<V> this, boolean save)
    {
        String text = getText();
        latestValue = recogniser.process(ParseProgress.fromStart(text), false)
                        .flatMap(SuccessDetails::requireEnd);
        // Once latestValue is set, we're fully initialised but checker doesn't know this:
        @SuppressWarnings("assignment")
        @Initialized RecogniserDocument<V> initialized = this;
        FXPlatformRunnable reset = () -> {
            Utility.later(this).replaceText(0, Utility.later(this).getLength(), valueOnFocusGain);
            recognise(false);
            Utility.later(this).notifyListeners();
            if (onFocusLost != null)
                onFocusLost.run();
        };
        Pair<String, @Nullable V> saveDetails;
        saveDetails = latestValue.<Pair<String, @Nullable V>>either(err -> {
            curErrorPosition = OptionalInt.of(err.errorPosition);
            return new Pair<String, @Nullable V>(text, null);
        }, (SuccessDetails<V> x) -> {
            curErrorPosition = OptionalInt.empty();
            @SuppressWarnings("valuetype")
            V value = x.value;
            return new Pair<String, @Nullable V>(x.immediateReplacementText != null ? x.immediateReplacementText : text, value);
        });
        this.unfocusedDocument = new Pair<>(makeStyledSpans(curErrorPosition, saveDetails.getFirst()).collect(ImmutableList.<Pair<Set<String>, String>>toImmutableList()), n -> n);
        if (save)
        {
            if (!saveDetails.getFirst().equals(text))
                initialized.replaceText(0, text.length(), saveDetails.getFirst());
            saveChange.save(saveDetails.getFirst(), saveDetails.getSecond(), reset);
        }
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
            return Stream.of(new Pair<>(ImmutableSet.<String>of(), text));
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
    void defocus(KeyCode defocusCause)
    {
        relinquishFocus.consume(defocusCause);
    }

    @SuppressWarnings("valuetype")
    public Either<ErrorDetails, V> getLatestValue()
    {
        return latestValue.map((SuccessDetails<V> x) -> x.value);
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

    @Override
    public void setAndSave(String content)
    {
        replaceText(0, getText().length(), content);
        recognise(true);
        // Must notify listeners again after recognition to update display correctly:
        notifyListeners();
    }

    @Override
    public @Nullable String getUndo()
    {
        return valueOnFocusGain;
    }

    @Override
    public ImmutableList<MenuItem> getAdditionalMenuItems(boolean focused)
    {
        return getAdditionalMenuItems == null ? ImmutableList.of() : getAdditionalMenuItems.apply(focused);
    }

    public static interface Saver<V>
    {
        @OnThread(Tag.FXPlatform)
        public void save(String text, @Nullable V recognisedValue, FXPlatformRunnable resetGraphics);
    }
}
