package records.data;

import com.google.common.collect.ImmutableList;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A column which shows only error values.
 */
public class ErrorColumn extends Column
{
    @OnThread(Tag.Any)
    private final DataTypeValue dataTypeValue;
    
    public ErrorColumn(RecordSet recordSet, TypeManager typeManager, ColumnId columnId, StyledString errorText) throws InternalException
    {
        super(recordSet, columnId);
        try
        {
            dataTypeValue = typeManager.getVoidType().instantiate(ImmutableList.of(), typeManager).fromCollapsed((i, prog) -> {
                throw new UserException(errorText);
            });
        }
        catch (UserException e)
        {
            throw new InternalException("Error accessing void type", e);
        }
    }
    
    @Override
    public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
    {
        return dataTypeValue;
    }

    @Override
    public @OnThread(Tag.Any) AlteredState getAlteredState()
    {
        // We are an error column because we failed to be a calculation, so count as overwriting:
        return AlteredState.OVERWRITTEN;
    }

    @OnThread(Tag.Any)
    public EditableStatus getEditableStatus()
    {
        return new EditableStatus(false, null);
    }
}
