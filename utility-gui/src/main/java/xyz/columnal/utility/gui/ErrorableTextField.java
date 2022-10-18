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

package xyz.columnal.utility.gui;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PopOver.ArrowLocation;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.gui.FixList.FixInfo;
import xyz.columnal.styled.StyledCSS;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.ExSupplier;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformFunction;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.TranslationUtility;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;


/**
 * A wrapper for a TextField which supports two extras:
 *
 * - A converter from text to a custom type, which supports giving an error if conversion fails, or warnings with a success
 * - Ability to display an error/warning (either because of conversion failure, or any other reason)
 */
@OnThread(Tag.FXPlatform)
public class ErrorableTextField<T> implements TimedFocusable
{
    private final TextField field = new TextField();
    private final ObjectProperty<ConversionResult<T>> converted;
    private final ObjectProperty<@Nullable T> value;
    private final PopOver popOver = new PopOver();
    private long lastFocusLeft;
    
    // Default is to suppress errors if blank and never been unfocused.
    private boolean suppressingErrors = true;
    
    public ErrorableTextField(FXPlatformFunction<String, ConversionResult<T>> converter, ObservableValue... conversionDependencies)
    {
        field.getStyleClass().add("errorable-text-field");
        popOver.getStyleClass().add("errorable-text-field-popup");
        popOver.setArrowLocation(ArrowLocation.BOTTOM_CENTER);
        popOver.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.MIDDLE)
            {
                popOver.hide(Duration.ZERO);
            }
        });
        FXUtility.onceNotNull(popOver.skinProperty(), skin -> {
            // Override default skin behaviour:
            popOver.getRoot().minHeightProperty().unbind();
            popOver.getRoot().setMinHeight(10);
        });
        
        this.converted = new SimpleObjectProperty<>(converter.apply(""));
        FXUtility.addChangeListenerPlatformNN(field.textProperty(), s -> {
            // Once changed from blank, stop suppressing:
            suppressingErrors = false;
            converted.setValue(converter.apply(s));
        });
        for (ObservableValue<?> dependency : conversionDependencies)
        {
            FXUtility.addChangeListenerPlatform(dependency, o -> converted.setValue(converter.apply(field.getText())));
        }
        this.value = new SimpleObjectProperty<>(converted.get().getValue());
        updateState();
        FXUtility.addChangeListenerPlatformNN(converted, conv -> {
            value.setValue(conv.getValue());
            updateState();
        });
        FXUtility.addChangeListenerPlatformNN(field.focusedProperty(), focused -> {
            // Once unfocused, stop suppressing errors;
            if (!focused)
            {
                suppressingErrors = false;
                lastFocusLeft = System.currentTimeMillis();
            }
            updateState();
        });
    }

    @Override
    public long lastFocusedTime()
    {
        return isFocused() ? System.currentTimeMillis() : lastFocusLeft;
    }

    private void updateState(@UnknownInitialization(ErrorableTextField.class) ErrorableTextField<T> this)
    {
        // I don't understand why this can be null if ErrorableTextField is initialised?
        @SuppressWarnings("nullness")
        ConversionResult<T> result = converted.get();
        boolean hasError = result.getError() != null && !suppressingErrors;
        boolean hasWarnings = !result.getWarnings().isEmpty() && !suppressingErrors;
        FXUtility.setPseudoclass(field, "has-error", hasError);
        FXUtility.setPseudoclass(field, "has-warnings", hasWarnings);

        boolean shouldShow = (hasError || hasWarnings) && field.isFocused();
        if (shouldShow)
        {
            popOver.setTitle(TranslationUtility.getString(hasError ? "error.popup.title.error" : "error.popup.title.warnings"));
            //popOver.setHeaderAlwaysVisible(true);
            popOver.setContentNode(GUI.vbox("popup-error-pane", new TextFlow(Stream.<Text>concat(
                Optional.ofNullable(result.getError()).map(e -> e.toGUI().stream()).orElse(Stream.of()),
                    result.getWarnings().stream().<Text>map(Text::new)
            ).toArray(Node[]::new)), new FixList(ImmutableList.<FixInfo>copyOf(Utility.<QuickFix, FixInfo>mapList(result.getFixes(), f -> new FixInfo(f.fixDescription, ImmutableList.of(), () -> setText(f.fixedValue)))))));

            if (!popOver.isShowing())
            {
                popOver.show(field);
                //org.scenicview.ScenicView.show(popOver.getScene());
            }
        }
        else if (!shouldShow && popOver.isShowing())
        {
            popOver.hide(Duration.ZERO);
        }

    }

    public BooleanProperty disableProperty()
    {
        return field.disableProperty();
    }

    public ObjectExpression<@Nullable T> valueProperty()
    {
        return value;
    }

    public Node getNode(@UnknownInitialization(ErrorableTextField.class) ErrorableTextField<T> this)
    {
        return field;
    }

    public void requestFocusWhenInScene()
    {
        FXUtility.onceNotNull(field.sceneProperty(), sc -> {
            FXUtility.runAfter(() -> field.requestFocus());
        });
    }

    public void setText(@UnknownInitialization(ErrorableTextField.class) ErrorableTextField<T> this, String text)
    {
        field.setText(text);
    }


    public void setPromptText(@UnknownInitialization(ErrorableTextField.class) ErrorableTextField<T> this, @Localized String prompt)
    {
        field.setPromptText(prompt);
    }


    public ErrorableTextField<T> withArrowLocation(ArrowLocation location)
    {
        popOver.setArrowLocation(location);
        return this;
    }

    public List<String> getStyleClass(@UnknownInitialization(ErrorableTextField.class) ErrorableTextField<T> this)
    {
        return field.getStyleClass();
    }

    public void sizeToFit(@Nullable Double minSizeFocused, @Nullable Double minSizeUnfocused, @Nullable DoubleExpression maxSize)
    {
        FXUtility.sizeToFit(field, minSizeFocused, minSizeUnfocused, maxSize);
    }

    public void setEditable(boolean editable)
    {
        field.setEditable(editable);
    }

    // When focus is lost from field, the given item is called with the new value 
    public void addOnFocusLoss(FXPlatformConsumer<@Nullable T> updateValue)
    {
        FXUtility.addChangeListenerPlatformNN(field.focusedProperty(), focused -> {
            if (!focused)
            {
                updateValue.consume(value.get());
            }
        });
    }

    public void setContextMenu(ContextMenu contextMenu)
    {
        field.setContextMenu(contextMenu);
    }

    public boolean isFocused()
    {
        return field.isFocused();
    }

    public void onTextChange(FXPlatformRunnable onChange)
    {
        FXUtility.addChangeListenerPlatform(field.textProperty(), t -> onChange.run());
    }

    public TextField getFieldForComplete()
    {
        return field;
    }

    public ReadOnlyBooleanProperty focusedProperty()
    {
        return field.focusedProperty();
    }

    public static class ConversionResult<T>
    {
        private final @Nullable T value;
        private final @Nullable StyledString error;
        private final ImmutableList<QuickFix> fixes;
        private final List<@Localized String> warnings;

        private ConversionResult(@NonNull T value, @Localized String... warnings)
        {
            this.value = value;
            this.error = null;
            this.warnings = Arrays.asList(warnings);
            this.fixes = ImmutableList.of();
        }

        private ConversionResult(StyledString error, QuickFix... fixes)
        {
            this.value = null;
            this.error = error;
            this.warnings = Collections.emptyList();
            this.fixes = ImmutableList.copyOf(fixes);
        }

        public @Nullable T getValue()
        {
            return value;
        }

        @Pure
        public @Nullable StyledString getError()
        {
            return error;
        }

        public List<@Localized String> getWarnings()
        {
            return warnings;
        }

        public static <T> ConversionResult<T> success(@NonNull T value, @Localized String... warnings)
        {
            return new ConversionResult<T>(value, warnings);
        }

        public static <T> ConversionResult<T> error(StyledString error, QuickFix... fixes)
        {
            return new ConversionResult<T>(error, fixes);
        }

        public ImmutableList<QuickFix> getFixes()
        {
            return fixes;
        }
    }

    public static class QuickFix
    {
        private final StyledString fixDescription;
        private final String fixedValue;

        public QuickFix(StyledString fixDescription, String fixedValue)
        {
            this.fixDescription = fixDescription;
            this.fixedValue = fixedValue;
        }
    }

    @SuppressWarnings("i18n")
    public static <T> ConversionResult<T> validate(ExSupplier<@NonNull T> getValue)
    {
        try
        {
            return new ConversionResult<T>(getValue.get());
        }
        catch (InternalException e)
        {
            return new ConversionResult<T>(StyledString.concat(StyledString.s("Internal Error: "), e.getStyledMessage()));
        }
        catch (UserException e)
        {
            return new ConversionResult<T>(e.getStyledMessage());
        }
    }

    /**
     * Checks if given string has valid alphabet.  If yes, returns success.  If no, returns illegal-character error
     * @param src The source String to examine
     * @param validCodepoint Is it a valid codepoint? First param is codepoint, second param is true at first position, false otherwise
     * @param makeResult If successful, turn the string into a result
     * @param <T> Type of result
     */
    protected static <T> ConversionResult<T> checkAlphabet(String src, BiPredicate<Integer, Boolean> validCodepoint, FXPlatformFunction<String, @NonNull T> makeResult)
    {
        int[] codePoints = src.codePoints().toArray();
        for (int i = 0; i < codePoints.length; i++)
        {
            int codepoint = codePoints[i];
            if (!validCodepoint.test(codepoint, i == 0))
            {
                if (i == 0 && validCodepoint.test(codepoint, false))
                {
                    // Would be valid, just not at the start:
                    return ConversionResult.error(StyledString.concat(TranslationUtility.getStyledString("error.illegalCharacter.start", Utility.codePointToString(codepoint)), StyledString.s("\n  "), StyledString.s("Character code: \\u" + Integer.toHexString(codepoint)).withStyle(new StyledCSS("errorable-sub-explanation"))));
                }
                // Not valid anywhere; offer to remove in case it's an unprintable or awkward character:
                return ConversionResult.error(
                    StyledString.concat(TranslationUtility.getStyledString("error.illegalCharacter", Utility.codePointToString(codepoint)), StyledString.s("\n  "), StyledString.s("Character code: \\u" + Integer.toHexString(codepoint)).withStyle(new StyledCSS("errorable-sub-explanation"))),
                    new QuickFix(StyledString.s(TranslationUtility.getString("error.illegalCharacter.remove")), new String(codePoints, 0, i) + new String(codePoints, i + 1, codePoints.length - i - 1))
                );
            }
        }
        return ConversionResult.success(makeResult.apply(src));
    }

}
