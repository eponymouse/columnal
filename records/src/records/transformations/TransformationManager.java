package records.transformations;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 02/11/2016.
 */
public class TransformationManager
{
    @MonotonicNonNull
    private static TransformationManager instance;

    @OnThread(Tag.FXPlatform)
    public static TransformationManager getInstance()
    {
        if (instance == null)
            instance = new TransformationManager();
        return instance;
    }

    @OnThread(Tag.FXPlatform)
    public List<TransformationInfo> getTransformations()
    {
        return Arrays.asList(
            new SummaryStatistics.Info()
        );
    }
}
