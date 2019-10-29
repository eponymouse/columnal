package records.transformations;

import records.data.TableManager;
import records.data.Transformation;

public abstract class VisitableTransformation extends Transformation 
{
    public VisitableTransformation(TableManager mgr, InitialLoadDetails initialLoadDetails)
    {
        super(mgr, initialLoadDetails);
    }
    
    public abstract <T> T visit(TransformationVisitor<T> visitor);
}
