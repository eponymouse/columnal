package records.transformations.expression.type;

import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.TypeEntry.TypeValue;
import records.transformations.expression.BracketedStatus;
import records.gui.expressioneditor.TypeEntry;
import records.loadsave.OutputBuilder;
import styled.StyledString;

import java.util.Objects;
import java.util.stream.Stream;

// For a named, fully-formed type (not a tagged type name)
public class TypePrimitiveLiteral extends TypeExpression
{
    private final DataType dataType;

    public TypePrimitiveLiteral(DataType dataType)
    {
        this.dataType = dataType;
    }

    @Override
    public Stream<SingleLoader<TypeExpression, TypeParent>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(p -> new TypeEntry(p, new TypeValue(toDisplay())));
    }

    public String toDisplay()
    {
        try
        {
            return dataType.toDisplay(false);
        }
        catch (UserException | InternalException e)
        {
            Log.log(e);
            return "";
        }
    }

    @Override
    public StyledString toStyledString()
    {
        return dataType.toStyledString();
    }

    @Override
    public String save(TableAndColumnRenames renames)
    {
        try
        {
            return dataType.save(new OutputBuilder()).toString();
        }
        catch (InternalException e)
        {
            Log.log(e);
            return new UnfinishedTypeExpression("").save(renames);
        }
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        return dataType;
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    public DataType _test_getType()
    {
        return dataType;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypePrimitiveLiteral that = (TypePrimitiveLiteral) o;
        return Objects.equals(dataType, that.dataType);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(dataType);
    }
}
