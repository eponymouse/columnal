package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.UnitExpression;
import xyz.columnal.utility.FXPlatformConsumer;

public class UnitEditor extends TopLevelEditor<UnitExpression, UnitLexer, CodeCompletionContext>
{
    public UnitEditor(TypeManager typeManager, @Nullable UnitExpression originalContent, FXPlatformConsumer<@NonNull @Recorded UnitExpression> onChange)
    {
        super(originalContent == null ? null : originalContent.save(SaveDestination.toUnitEditor(typeManager.getUnitManager()), true), new UnitLexer(typeManager, false), typeManager, onChange, "unit-editor");
    }
}
