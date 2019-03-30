package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.TypeManager;
import records.transformations.expression.UnitExpression;
import utility.FXPlatformConsumer;

public class UnitEditor extends TopLevelEditor<UnitExpression, UnitLexer, CodeCompletionContext>
{
    public UnitEditor(TypeManager typeManager, @Nullable UnitExpression originalContent, FXPlatformConsumer<@NonNull @Recorded UnitExpression> onChange)
    {
        super(originalContent == null ? "" : originalContent.save(false, true), new UnitLexer(), onChange);
    }
}
