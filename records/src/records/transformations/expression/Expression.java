package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.GetValue;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataType.SpecificDataTypeVisitorGet;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 24/11/2016.
 */
public abstract class Expression
{

    @OnThread(Tag.Simulation)
    public boolean getBoolean(RecordSet data, int rowIndex, @Nullable ProgressListener prog) throws UserException, InternalException
    {
        return getType(data).apply(new SpecificDataTypeVisitorGet<Boolean>(new UserException("Type must be boolean")) {
            @Override
            @OnThread(Tag.Simulation)
            protected Boolean bool(GetValue<Boolean> g) throws InternalException, UserException
            {
                return g.getWithProgress(rowIndex, prog);
            }
        });
    }

    public abstract DataType getType(RecordSet data) throws UserException, InternalException;

    @OnThread(Tag.FXPlatform)
    public abstract String save();

    public static Expression parse(String src)
    {
        return new NumericLiteral(0); // TODO
    }
}
