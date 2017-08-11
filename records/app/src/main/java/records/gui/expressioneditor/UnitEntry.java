package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.collections.FXCollections;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnitExpression;
import utility.FXPlatformConsumer;

// Like GeneralExpressionEntry but for units only
public class UnitEntry extends GeneralOperandEntry<UnitExpression, Void> implements OperandNode<UnitExpression>, ErrorDisplayer
{
    public UnitEntry(ConsecutiveBase<UnitExpression, ExpressionNodeParent> parent, String initialContent)
    {
        super(UnitExpression.class, parent);
        this.nodes.setAll(FXCollections.observableArrayList(textField));
        textField.setText(initialContent);
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return null;
    }

    @Override
    public UnitExpression save(ErrorDisplayerRecord<UnitExpression> errorDisplayer, FXPlatformConsumer<Object> onError)
    {
        SingleUnitExpression singleUnitExpression = new SingleUnitExpression(textField.getText().trim());
        errorDisplayer.record(this, singleUnitExpression);
        return singleUnitExpression;
    }
}
