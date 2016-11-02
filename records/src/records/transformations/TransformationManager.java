package records.transformations;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 02/11/2016.
 */
public class TransformationManager
{
    @MonotonicNonNull
    private static TransformationManager instance;

    public static TransformationManager getInstance()
    {
        if (instance == null)
            instance = new TransformationManager();
        return instance;
    }

    public List<TransformationInfo> getTransformations()
    {
        return Arrays.asList(
            new SummaryStatistics.Info()
        );
    }
}
