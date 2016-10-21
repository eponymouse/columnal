package records.data;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
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

    public SummaryStatistics(RecordSet src, Map<String, Set<SummaryType>> summaries, List<String> splitBy) throws Exception
    {
        List<Column<?>> columns = new ArrayList<>();
        for (Entry<String, Set<SummaryType>> e : summaries.entrySet())
        {
            for (SummaryType summaryType : e.getValue())
            {
                Column srcCol = src.getColumn(e.getKey());
                switch (summaryType)
                {
                    case MIN:case MAX:
                        if (!Comparable.class.isAssignableFrom(srcCol.getType()))
                            throw new Exception("Summary column not comparable for " + summaryType);
                        break;
                }

                columns.add(new CalculatedColumn<Object>(e.getKey() + "." + summaryType, srcCol)
                {
                    @Override
                    protected Object calculate(int index) throws Exception
                    {
                        // TODO implement split by
                        if (index > 0)
                            throw new Exception("Looking for item beyond end");
                        switch (summaryType)
                        {
                            case MIN:
                                Column<Comparable> srcColComp = srcCol;
                                Comparable min = srcColComp.get(0);
                                for (int i = 1; srcColComp.indexValid(i); i++)
                                {
                                    Comparable x = srcColComp.get(index);
                                    if (min.compareTo(x) > 0)
                                        min = x;
                                }
                                return min;
                        }
                        return null; // TODO intellij should flag this
                    }

                    @Override
                    public boolean indexValid(int index)
                    {
                        return index == 0; // TODO add split by
                    }

                    @Override
                    public Class<Object> getType()
                    {
                        return srcCol.getType();
                    }
                });
            }
        }
        result = new RecordSet("Summary", columns, 1);
    }

    @NotNull
    public static SummaryStatistics guiCreate(RecordSet src) throws Exception
    {
        // TODO actually show GUI
        return new SummaryStatistics(src, Collections.emptyMap(), Collections.emptyList());
    }

    @Override
    public RecordSet getResult()
    {
        return result;
    }
}
