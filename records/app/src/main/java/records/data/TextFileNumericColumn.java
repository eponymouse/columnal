package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.NumberInfo;
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
public class TextFileNumericColumn extends TextFileColumn<NumericColumnStorage>
{
    @OnThread(Tag.Any)
    private final NumberInfo  numberInfo;
    private final @Nullable UnaryOperator<String> processString;

    @SuppressWarnings("initialization")
    public TextFileNumericColumn(RecordSet recordSet, File textFile, long fileStartPosition, byte @Nullable [] sep, ColumnId columnName, int columnIndex, int totalColumns, NumberInfo numberInfo, @Nullable UnaryOperator<String> processString) throws InternalException, UserException
    {
        super(recordSet, textFile, fileStartPosition, sep, columnName, columnIndex, totalColumns);
        setStorage(new NumericColumnStorage(numberInfo, (index, prog) -> fillUpTo(index)));
        this.numberInfo = numberInfo;
        this.processString = processString;
    }

    @Override
    protected void addValues(ArrayList<String> values) throws InternalException, UserException
    {
        NumericColumnStorage store = getStorage();
        for (String value : values)
        {
            String processed = value;
            if (processString != null)
                processed = processString.apply(processed);
            store.addRead(processed);
        }
    }
}
