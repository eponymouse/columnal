package records.transformations.expression.type;

import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.gui.expressioneditor.TypeEntry;
import records.loadsave.OutputBuilder;
import styled.StyledString;
import threadchecker.OnThread;
import utility.Pair;

import java.util.List;

// For a named, fully-formed type (not a tagged type name)
public class TypePrimitiveLiteral extends TypeExpression
{
    private final DataType dataType;

    public TypePrimitiveLiteral(DataType dataType)
    {
        this.dataType = dataType;
    }

    @Override
    public SingleLoader<TypeExpression, TypeParent, OperandNode<TypeExpression, TypeParent>> loadAsSingle()
    {
        return (p, s) -> new TypeEntry(p, s, toDisplay());
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
    public @Nullable DataType toDataType()
    {
        return dataType;
    }
}
