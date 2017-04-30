package records.gui;

import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.dataflow.qual.Pure;
import org.controlsfx.control.PopOver;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExSupplier;
import utility.FXPlatformFunction;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
        this.converted = new SimpleObjectProperty<>(converter.apply(""));
        FXUtility.addChangeListenerPlatformNN(field.textProperty(), s -> converted.setValue(converter.apply(s)));
        for (ObservableValue dependency : conversionDependencies)
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
            popOver.setContentNode(GUI.wrap(new TextFlow(Stream.concat(
                Utility.streamNullable(result.getError()),
                result.getWarnings().stream()
            ).map(Text::new).toArray(Node[]::new)), "popup-error-pane"));

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

    public static class ConversionResult<T>
    {
        private final boolean success;
        private final @Nullable T value;
        private final @Nullable @Localized String error;
        private final List<@Localized String> warnings;

        private ConversionResult(T value, @Localized String... warnings)
        {
            this.success = true;
            this.value = value;
            this.error = null;
            this.warnings = Arrays.asList(warnings);
        }

        private ConversionResult(@Localized String error)
        {
            this.success = false;
            this.value = null;
            this.error = error;
            this.warnings = Collections.emptyList();
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

        public static <T> ConversionResult<T> error(@Localized String error)
        {
            return new ConversionResult<T>(error);
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
}
