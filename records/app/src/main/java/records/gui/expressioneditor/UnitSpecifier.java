package records.gui.expressioneditor;

import javafx.scene.Node;
import javafx.scene.control.Label;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.UnitExpression;
import utility.FXPlatformFunction;
import utility.Pair;

import java.util.Collections;
import java.util.List;

public class UnitSpecifier extends Bracketed<UnitExpression>
{
    public UnitSpecifier(ExpressionParent parent, boolean topLevel)
    {
        super(parent, new Label(topLevel ? "{" : "("), new Label(topLevel ? "}" : ")"), new Pair<>(Collections.singletonList(p -> new UnitEntry(p)), Collections.emptyList()));
    }
}
