package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.TableAndColumnRenames;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.utility.FXPlatformConsumer;

public class TypeEditor extends TopLevelEditor<TypeExpression, TypeLexer, CodeCompletionContext>
{
    public TypeEditor(TypeManager typeManager, @Nullable TypeExpression originalContent, boolean requireConcreteType, boolean emptyAllowed, FXPlatformConsumer<@NonNull @Recorded TypeExpression> onChange)
    {
        super(originalContent == null ? null : originalContent.save(SaveDestination.toTypeEditor(typeManager), new TableAndColumnRenames(ImmutableMap.of())), new TypeLexer(typeManager, requireConcreteType, emptyAllowed), typeManager, onChange, "type-editor");
    }
}
