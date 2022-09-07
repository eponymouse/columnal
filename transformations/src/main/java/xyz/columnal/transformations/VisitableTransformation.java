package xyz.columnal.transformations;

import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;
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
