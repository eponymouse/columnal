package records.data.datatype;

import javafx.scene.control.ListView;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.NumberDisplayInfo;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DisplayValue;
import threadchecker.OnThread;
import utility.Utility;

import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 22/11/2016.
 */
public class DataTypeUtility
{
    public static List<Object> generateExample(DataType type, int index) throws UserException, InternalException
    {
        return type.apply(new DataTypeVisitor<List<Object>>()
        {

            @Override
            public List<Object> number(NumberDisplayInfo displayInfo) throws InternalException, UserException
            {
                return Collections.singletonList((Long)(long)index);
            }

            @Override
            public List<Object> text() throws InternalException, UserException
            {
                return Collections.singletonList(Arrays.asList("Aardvark", "Bear", "Cat", "Dog", "Emu", "Fox").get(index));
            }

            @Override
            public List<Object> bool() throws InternalException, UserException
            {
                return Collections.singletonList((index % 2) == 1);
            }

            @Override
            public List<Object> date() throws InternalException, UserException
            {
                return Collections.singletonList(LocalDate.ofEpochDay(index));
            }

            @Override
            public List<Object> tagged(List<TagType<DataType>> tags) throws InternalException, UserException
            {
                int tag = index % tags.size();
                @Nullable DataType inner = tags.get(tag).getInner();
                if (inner != null)
                    return Utility.consList((Object)(Integer)tag, generateExample(inner, index - tag));
                else
                    return Collections.singletonList((Object)(Integer)tag);
            }
        });
    }

    public static int compare(DataType dataType, List<Object> as, List<Object> bs) throws InternalException
    {
        return Utility.compareLists(as, bs);
        /*
        return dataType.apply(new DataTypeVisitor<Integer>()
        {
            int index = 0;

            @Override
            public Integer number(NumberDisplayInfo displayInfo) throws InternalException, UserException
            {
                return Utility.compareNumbers(as.get(index), bs.get(index));
            }

            @Override
            public Integer text() throws InternalException, UserException
            {
                return ((String)as.get(index)).compareTo((String)bs.get(index));
            }

            @Override
            public Integer tagged(List<TagType> tags) throws InternalException, UserException
            {
                return null;
            }
        })
        */
    }

    public static List<List<DisplayValue>> displayAll(List<DataType> columnTypes, List<List<List<Object>>> dataRows) throws InternalException, UserException
    {
        List<List<DisplayValue>> r = new ArrayList<>();
        for (List<List<Object>> dataRow : dataRows)
        {
            List<DisplayValue> row = new ArrayList<>();
            for (int i = 0; i < columnTypes.size(); i++)
                row.add(display(columnTypes.get(i), dataRow.get(i)));
            r.add(row);
        }
        return r;
    }

    public static DisplayValue display(DataType dataType, List<Object> objects) throws UserException, InternalException
    {
        return dataType.apply(new DataTypeVisitor<DisplayValue>()
        {
            int index = 0;
            @Override
            public DisplayValue number(NumberDisplayInfo displayInfo) throws InternalException, UserException
            {
                return new DisplayValue((Number)objects.get(index), displayInfo.getDisplayPrefix(), displayInfo.getMinimumDP());
            }

            @Override
            public DisplayValue text() throws InternalException, UserException
            {
                return new DisplayValue((String)objects.get(index));
            }

            @Override
            public DisplayValue bool() throws InternalException, UserException
            {
                return new DisplayValue((Boolean)objects.get(index));
            }

            @Override
            public DisplayValue date() throws InternalException, UserException
            {
                return new DisplayValue((Temporal) objects.get(index));
            }

            @Override
            public DisplayValue tagged(List<TagType<DataType>> tagTypes) throws InternalException, UserException
            {
                int tag = (Integer)objects.get(index++);
                TagType tagType = tagTypes.get(tag);
                @Nullable DataType inner = tagType.getInner();
                if (DataType.canFitInOneNumeric(tagTypes))
                {
                    if (inner == null)
                        return new DisplayValue(tagType.getName());
                    else
                        return inner.apply(this);
                }
                else
                {
                    return new DisplayValue(tagType.getName() + (inner == null ? "" : (" " + inner.apply(this))));
                }
            }
        });
    }
}
