package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.dataflow.qual.Pure;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PopOver.ArrowLocation;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExSupplier;
import utility.FXPlatformFunction;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.TranslationUtility;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;


/**
 * A wrapper for a TextField which supports two extras:
 *
 * - A converter from text to a custom type, which supports giving an error if conversion fails, or warnings with a success
 * - Ability to display an error/warning (either because of conversion failure, or any other reason)
 */
@OnThread(Tag.FXPlatform)
public class ErrorableTextField<T>
{
    private final TextField field = new TextField();
    private final ObjectProperty<ConversionResult<T>> converted;
    private final ObjectProperty<@Nullable T> value;
    private final PopOver popOver = new PopOver();

    @SuppressWarnings("initialization") // Due to updateState
    public ErrorableTextField(FXPlatformFunction<String, ConversionResult<T>> converter, ObservableValue... conversionDependencies)
    {
        field.getStyleClass().add("errorable-text-field");
        popOver.getStyleClass().add("errorable-text-field-popup");
        this.converted = new SimpleObjectProperty<>(converter.apply(""));
        FXUtility.addChangeListenerPlatformNN(field.textProperty(), s -> converted.setValue(converter.apply(s)));
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
        FXUtility.addChangeListenerPlatformNN(field.focusedProperty(), b -> updateState());
    }

    private void updateState()
    {
        ConversionResult<T> result = converted.get();
        boolean hasError = result.getError() != null;
        boolean hasWarnings = !result.getWarnings().isEmpty();
        FXUtility.setPseudoclass(field, "has-error", hasError);
        FXUtility.setPseudoclass(field, "has-warnings", hasWarnings);

        boolean shouldShow = (hasError || hasWarnings) && field.isFocused();
        if (shouldShow)
        {
            popOver.setTitle(TranslationUtility.getString(hasError ? "error.popup.title.error" : "error.popup.title.warnings"));
            popOver.setHeaderAlwaysVisible(true);
            popOver.setContentNode(GUI.vbox("popup-error-pane", new TextFlow(Stream.concat(
                Utility.streamNullable(result.getError()),
                result.getWarnings().stream()
            ).map(Text::new).toArray(Node[]::new)), new FixList(result.getFixes())));

            if (!popOver.isShowing())
            {
                popOver.show(field);
                //org.scenicview.ScenicView.show(popOver.getScene());
            }
        }
        else if (!shouldShow && popOver.isShowing())
        {
            popOver.hide();
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

    public Node getNode()
    {
        return field;
    }

    public void requestFocusWhenInScene()
    {
        FXUtility.onceNotNull(field.sceneProperty(), sc -> {
            field.requestFocus();
        });
    }

    public void setText(String text)
    {
        field.setText(text);
    }


    protected void setPromptText(@UnknownInitialization(ErrorableTextField.class) ErrorableTextField<T> this, @Localized String prompt)
    {
        field.setPromptText(prompt);
    }


    public ErrorableTextField<T> withArrowLocation(ArrowLocation location)
    {
        popOver.setArrowLocation(location);
        return this;
    }

    public static class ConversionResult<T>
    {
        private final boolean success;
        private final @Nullable T value;
        private final @Nullable @Localized String error;
        private final ImmutableList<QuickFix> fixes;
        private final List<@Localized String> warnings;

        private ConversionResult(T value, @Localized String... warnings)
        {
            this.success = true;
            this.value = value;
            this.error = null;
            this.warnings = Arrays.asList(warnings);
            this.fixes = ImmutableList.of();
        }

        private ConversionResult(@Localized String error, QuickFix... fixes)
        {
            this.success = false;
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
        public @Nullable @Localized String getError()
        {
            return error;
        }

        public List<@Localized String> getWarnings()
        {
            return warnings;
        }

        public static <T> ConversionResult<T> success(T value, @Localized String... warnings)
        {
            return new ConversionResult<T>(value, warnings);
        }

        public static <T> ConversionResult<T> error(@Localized String error, QuickFix... fixes)
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
        private final @Localized String fixDescription;
        private final String fixedValue;

        public QuickFix(@Localized String fixDescription, String fixedValue)
        {
            this.fixDescription = fixDescription;
            this.fixedValue = fixedValue;
        }
    }

    @SuppressWarnings("i18n")
    public static <T> ConversionResult<T> validate(ExSupplier<T> getValue)
    {
        try
        {
            return new ConversionResult<T>(getValue.get());
        }
        catch (InternalException e)
        {
            return new ConversionResult<T>("Internal Error: " + e.getLocalizedMessage());
        }
        catch (UserException e)
        {
            return new ConversionResult<T>(e.getLocalizedMessage());
        }
    }

    /**
     * Checks if given string has valid alphabet.  If yes, returns success.  If no, returns illegal-character error
     * @param src The source String to examine
     * @param validCodepoint Is it a valid codepoint? First param is codepoint, second param is true at first position, false otherwise
     * @param makeResult If successful, turn the string into a result
     * @param <T> Type of result
     */
    protected static <T> ConversionResult<T> checkAlphabet(String src, BiPredicate<Integer, Boolean> validCodepoint, FXPlatformFunction<String, T> makeResult)
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
                    return ConversionResult.error(TranslationUtility.getString("error.illegalCharacter.start", Utility.codePointToString(codepoint) + " [\\u" + Integer.toHexString(codepoint) + "]"));
                }
                // Not valid anywhere; offer to remove in case it's an unprintable or awkward character:
                return ConversionResult.error(
                    TranslationUtility.getString("error.illegalCharacter", Utility.codePointToString(codepoint) + " [\\u" + Integer.toHexString(codepoint) + "]"),
                    new QuickFix(TranslationUtility.getString("error.illegalCharacter.remove"), new String(codePoints, 0, i) + new String(codePoints, i + 1, codePoints.length - i - 1))
                );
            }
        }
        return ConversionResult.success(makeResult.apply(src));
    }

    private class FixList extends VBox
    {
        private final ImmutableList<FixRow> fixes;
        private int selectedIndex; // TODO add support for keyboard selection

        @OnThread(Tag.FXPlatform)
        public FixList(ImmutableList<QuickFix> fixes)
        {
            selectedIndex = -1;

            getStyleClass().add("errorable-text-field-quick-fix-list");

            if (!fixes.isEmpty())
            {
                getChildren().add(GUI.label("error.fixes"));
            }

            ImmutableList.Builder<FixRow> rows = ImmutableList.builder();
            for (QuickFix fix : fixes)
            {
                getChildren().add(new FixRow(fix));
            }
            this.fixes = rows.build();
            getChildren().addAll(this.fixes);
        }

        private class FixRow extends BorderPane
        {
            private final QuickFix fix;

            public FixRow(QuickFix fix)
            {
                this.fix = fix;
                getStyleClass().add("errorable-text-field-quick-fix-row");

                setOnMouseClicked(e -> {
                    doFix();
                });
            }

            @OnThread(Tag.FXPlatform)
            @RequiresNonNull("fix")
            private void doFix(@UnknownInitialization(BorderPane.class) FixRow this)
            {
                ErrorableTextField<T> textField = ErrorableTextField.this;
                textField.setText(fix.fixedValue);
            }
        }
    }
}
