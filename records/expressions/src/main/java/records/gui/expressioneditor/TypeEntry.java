package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import javafx.beans.value.ObservableObjectValue;
import javafx.scene.Node;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;
import records.loadsave.OutputBuilder;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.UnfinishedExpression;
import records.transformations.expression.type.TaggedTypeNameExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeParent;
import records.transformations.expression.type.TypePrimitiveLiteral;
import records.transformations.expression.type.UnfinishedTypeExpression;

import java.util.jar.JarFile;
import java.util.stream.Stream;

public class TypeEntry extends GeneralOperandEntry<TypeExpression, TypeParent>
{
    // Number is not included as that is done separately:
    private static final ImmutableList<DataType> PRIMITIVE_TYPES = ImmutableList.of(
        DataType.BOOLEAN,
        DataType.TEXT,
        DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)),
        DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)),
        DataType.date(new DateTimeInfo(DateTimeType.DATETIME)),
        DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)),
        DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)),
        DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED))
    );
    private final TypeParent semanticParent;

    public TypeEntry(ConsecutiveBase<TypeExpression, TypeParent> parent, TypeParent typeParent, String initialContent)
    {
        super(TypeExpression.class, parent);
        this.semanticParent = typeParent;
        textField.setText(initialContent);
    }


    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.of(textField);
    }

    @Override
    public TypeExpression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        return Stream.<TypeExpression>concat(
            PRIMITIVE_TYPES.stream()
                .filter(t -> t.toString().equals(textField.getText()))
                .map(t -> new TypePrimitiveLiteral(t)), 
            parent.getEditor().getTypeManager().getKnownTaggedTypes().keySet().stream()
                .filter(t -> t.getRaw().equals(textField.getText()))
                .map(t -> new TaggedTypeNameExpression(t))
        ).findFirst().orElseGet(() -> new UnfinishedTypeExpression(textField.getText()));
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return null;
    }

    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return child == this;
    }
}
