package records.data;

import records.error.InternalException;
import records.error.UserException;
import utility.Utility;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Created by neil on 21/10/2016.
 */
public class SummaryStatistics extends Transformation
{
    public static enum SummaryType
    {
        MEAN, MEDIAN, MIN, MAX;
    }

    private final RecordSet result;

    public SummaryStatistics(RecordSet src, Map<String, Set<SummaryType>> summaries, List<String> splitBy) throws InternalException, UserException
    {
        List<Column> columns = new ArrayList<>();
        for (Entry<String, Set<SummaryType>> e : summaries.entrySet())
        {
            for (SummaryType summaryType : e.getValue())
            {
                Column srcCol = src.getColumn(e.getKey());
                boolean srcColIsNumber = Number.class.equals(srcCol.getType());
                switch (summaryType)
                {
                    case MIN:case MAX:
                        if (!Comparable.class.isAssignableFrom(srcCol.getType()) && !srcColIsNumber)
                            throw new UserException("Summary column not comparable for " + summaryType + ": " + srcCol.getType());
                        break;
                }

                columns.add(new CalculatedColumn<Object>(e.getKey() + "." + summaryType, srcCol)
                {
                    @Override
                    protected Object calculate(int index) throws UserException, InternalException
                    {
                        // TODO implement split by
                        if (index > 0)
                            throw new InternalException("Looking for item beyond end");
                        switch (summaryType)
                        {
                            case MIN:
                            case MAX:
                                Comparable<Object> cur = (Comparable<Object>) srcCol.get(0);
                                for (int i = 1; srcCol.indexValid(i); i++)
                                {
                                    Comparable<Object> x = (Comparable<Object>) srcCol.get(i);
                                    int comparison;
                                    if (srcColIsNumber)
                                        comparison = Utility.compareNumbers(cur, x);
                                    else
                                        comparison = cur.compareTo(x);
                                    if ((summaryType == SummaryType.MIN && comparison > 0)
                                         || (summaryType == SummaryType.MAX && comparison < 0))
                                        cur = x;
                                }
                                return cur;
                        }
                        throw new UserException("Unsupported summary type");
                    }

                    @Override
                    public boolean indexValid(int index)
                    {
                        return index == 0; // TODO add split by
                    }

                    @Override
                    public Class<?> getType()
                    {
                        return srcCol.getType();
                    }
                });
            }
        }
        result = new RecordSet("Summary", columns, 1);
    }

    public static SummaryStatistics guiCreate(RecordSet src) throws InternalException, UserException
    {
        // TODO actually show GUI
        Map<String, Set<SummaryType>> summaries = new HashMap<>();
        for (Column c : src.getColumns())
        {
            summaries.put(c.getName(), new HashSet(Arrays.asList(SummaryType.MIN, SummaryType.MAX)));
        }

        return new SummaryStatistics(src, summaries, Collections.emptyList());
    }

    @Override
    @NotNull
    public RecordSet getResult()
    {
        return result;
    }
}
