package records.transformations;

import records.data.TableManager;
import records.data.Transformation;
import threadchecker.OnThread;
import threadchecker.Tag;

public abstract class VisitableTransformation extends Transformation 
{
    public VisitableTransformation(TableManager mgr, InitialLoadDetails initialLoadDetails)
    {
        super(mgr, initialLoadDetails);
    }
    
    @OnThread(Tag.Any)
    public abstract <T> T visit(TransformationVisitor<T> visitor);
}
