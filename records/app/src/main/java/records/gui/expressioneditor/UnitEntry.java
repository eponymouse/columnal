package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnitExpression;
import utility.FXPlatformConsumer;

import java.util.stream.Stream;

// Like GeneralExpressionEntry but for units only
public class UnitEntry extends GeneralOperandEntry<UnitExpression, UnitNodeParent> implements OperandNode<UnitExpression>, ErrorDisplayer
{
    public UnitEntry(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, String initialContent)
    {
        super(UnitExpression.class, parent);
        textField.setText(initialContent);
        updateNodes();
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.of(textField);
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
