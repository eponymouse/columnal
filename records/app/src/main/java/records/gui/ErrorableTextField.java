package records.gui;

import javafx.beans.Observable;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExSupplier;
import utility.FXPlatformFunction;


/**
 * A wrapper for a TextField which supports two extras:
 *
 * - A converter from text to a custom type, which supports giving an error if conversion fails
 * - Ability to display an error (either because of conversion failure, or any other reason)
 */
@OnThread(Tag.FXPlatform)
public class ErrorableTextField<T>
{
    private final TextField field = new TextField();
    private final FXPlatformFunction<String, ConversionResult<T>> converter;
    private final ObjectBinding<@Nullable T> value;

    public ErrorableTextField(FXPlatformFunction<String, ConversionResult<T>> converter, Observable... conversionDependencies)
    {
        this.converter = converter;
        this.value = new ValueBinding<T>(field, converter, conversionDependencies);
    }

    public BooleanProperty disableProperty()
    {
        return field.disableProperty();
    }

    public ObjectBinding<@Nullable T> valueProperty()
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
        private final @Nullable Object error;

        private ConversionResult(T value)
        {
            this.success = true;
            this.value = value;
            this.error = null;
        }

        private ConversionResult(String error)
        {
            this.success = false;
            this.value = null;
            this.error = error;
        }

        public @Nullable T getValue()
        {
            return value;
        }
    }

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

    private static class ValueBinding<T> extends ObjectBinding<@Nullable T>
    {
        private final TextField field;
        private final FXPlatformFunction<String, ConversionResult<T>> converter;

        public ValueBinding(TextField field, FXPlatformFunction<String, ConversionResult<T>> converter, Observable... conversionDependencies)
        {
            this.field = field;
            this.converter = converter;
            super.bind(field.textProperty());
            super.bind(conversionDependencies);
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected @Nullable T computeValue()
        {
            ConversionResult<T> result = converter.apply(field.getText());
            // TODO if result is an error, display it
            return result.getValue();
        }
    }
}
