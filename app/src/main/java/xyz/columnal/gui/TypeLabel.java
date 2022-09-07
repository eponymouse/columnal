package records.gui;

import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.gui.FXUtility;

/**
 * Displays the type of the given expression, dynamically
 */
@OnThread(Tag.FXPlatform)
public class TypeLabel extends Label
{
    public TypeLabel(ObjectExpression<@Nullable DataType> typeProperty)
    {
        getStyleClass().add("type-label");
        FXUtility.addChangeListenerPlatform(typeProperty, this::updateType);
        updateType(typeProperty.getValue());
    }

    private void updateType(@UnknownInitialization(Label.class) TypeLabel this, @Nullable DataType type)
    {
        if (type != null)
        {
            try
            {
                setText(type.toDisplay(false));
            }
            catch(UserException | InternalException e)
            {
                setText("Error: " + e.getLocalizedMessage());
            }
        }
        else
            setText("Invalid expression");
    }
}
