package records.data;

import org.checkerframework.dataflow.qual.Pure;
import records.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface SingleSourceTransformation
{
    @OnThread(Tag.Any)
    @Pure
    TableId getSrcTableId();
    
    @OnThread(Tag.Simulation)
    Transformation withNewSource(TableId newSrcTableId) throws InternalException;
}
