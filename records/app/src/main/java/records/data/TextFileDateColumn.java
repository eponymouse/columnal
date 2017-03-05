package records.data;

import records.data.datatype.DataType.DateTimeInfo;
import records.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.ArrayList;

/**
 * Created by neil on 26/12/2016.
 */
public class TextFileDateColumn extends TextFileColumn<TemporalColumnStorage>
{
    @OnThread(Tag.Any)
    private final DateTimeInfo dateTimeInfo;
    private final DateTimeFormatter dateTimeFormatter;
    private final TemporalQuery<? extends Temporal> query;

    @SuppressWarnings("initialization")
    public TextFileDateColumn(RecordSet recordSet, File textFile, long initialFilePosition, byte sep, ColumnId columnName, int columnIndex, DateTimeInfo dateTimeInfo, DateTimeFormatter dateTimeFormatter, TemporalQuery query) throws InternalException
    {
        super(recordSet, textFile, initialFilePosition, sep, columnName, columnIndex);
        setStorage(new TemporalColumnStorage(dateTimeInfo, (index, prog) -> fillUpTo(index)));
        this.dateTimeInfo = dateTimeInfo;
        this.dateTimeFormatter = dateTimeFormatter;
        this.query = query;
    }

    @Override
    protected void addValues(ArrayList<String> values) throws InternalException
    {
        getStorage().addAll(Utility.<String, TemporalAccessor>mapList(values, s -> dateTimeFormatter.parse(s, query)));
    }
}
