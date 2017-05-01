package test;

import records.data.DataSource;
import records.data.Table;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.TransformationManager;

/**
 * Created by neil on 16/11/2016.
 */
public class DummyManager extends TableManager
{
    // TODO eliminate the use of this; too hacky
    public static final DummyManager INSTANCE;

    static
    {
        try
        {
            INSTANCE = new DummyManager();
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    public DummyManager() throws InternalException, UserException
    {
        super(TransformationManager.getInstance(), new TableManagerListener()
        {
            @Override
            public void removeTable(Table t)
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
        });
    };
}
