package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.TypeManager;
import records.transformations.expression.Expression.SaveDestination;
import records.transformations.expression.UnitExpression;
import utility.FXPlatformConsumer;

public class UnitEditor extends TopLevelEditor<UnitExpression, UnitLexer, CodeCompletionContext>
{
    public UnitEditor(TypeManager typeManager, @Nullable UnitExpression originalContent, FXPlatformConsumer<@NonNull @Recorded UnitExpression> onChange)
    {
        super(originalContent == null ? null : originalContent.save(SaveDestination.EDITOR, true), new UnitLexer(typeManager.getUnitManager(), false), typeManager, onChange, "unit-editor");
    }
}
