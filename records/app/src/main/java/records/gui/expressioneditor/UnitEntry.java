package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.Expression;
import records.transformations.expression.UnitExpression;
import utility.gui.FXUtility;

// Like GeneralEntry but for units only
public class UnitEntry extends TextFieldEntry<UnitExpression> implements OperandNode<UnitExpression>, ErrorDisplayer
{
    public UnitEntry(ConsecutiveBase<UnitExpression> parent)
    {
        super(UnitExpression.class, parent);
        this.nodes.setAll(FXCollections.observableArrayList(textField));
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return null;
    }

    public UnitEntry focusWhenShown()
    {
        FXUtility.onceNotNull(textField.sceneProperty(), scene -> focus(Focus.RIGHT));
        return this;
    }
}
