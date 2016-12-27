package records.data;

import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.util.ArrayList;
import java.util.function.UnaryOperator;

/**
 * Created by neil on 20/10/2016.
 */
public class TextFileNumericColumn extends TextFileColumn<Number>
{
    @OnThread(Tag.Any)
    private final NumberInfo  numberInfo;
    private final @Nullable UnaryOperator<String> processString;

    public TextFileNumericColumn(RecordSet recordSet, File textFile, long fileStartPosition, byte sep, ColumnId columnName, int columnIndex, NumberInfo numberInfo, @Nullable UnaryOperator<String> processString) throws InternalException, UserException
    {
        super(recordSet, textFile, fileStartPosition, sep, columnName, columnIndex, new NumericColumnStorage(numberInfo));
        this.numberInfo = numberInfo;
        this.processString = processString;
    }

    @Override
    @OnThread(Tag.Any)
    protected DataTypeValue makeDataType() throws InternalException, UserException
    {
        return DataTypeValue.number(numberInfo, (i, prog) -> {
            fillUpTo(i);
            return storage.get(i);
        });
    }

    @Override
    protected void addValues(ArrayList<String> values) throws InternalException, UserException
    {
        for (String value : values)
        {
            String processed = value;
            if (processString != null)
                processed = processString.apply(processed);
            ((NumericColumnStorage)storage).addRead(processed);
        }
    }
}
