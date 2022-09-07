package test;

import com.google.common.collect.ImmutableMap;
import xyz.columnal.data.DataSource;
import xyz.columnal.data.GridComment;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.TransformationManager;

/**
 * A TableManager implementation useful for testing
 */
public final class DummyManager extends TableManager
{
    public DummyManager() throws InternalException, UserException
    {
        super(TransformationManager.getInstance(), onError -> ImmutableMap.of());
        addListener(new TableManagerListener()
        {
            @Override
            public void removeTable(Table t, int remainingCount)
            {
            }

            @Override
            public void addSource(DataSource dataSource)
            {
            }

            @Override
            public void addTransformation(Transformation transformation)
            {
            }

            @Override
            public void addComment(GridComment gridComment)
            {
            }

            @Override
            public void removeComment(GridComment gridComment)
            {
            }
        });
    };
    
    public static DummyManager make()
    {
        try
        {
            return new DummyManager();
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
