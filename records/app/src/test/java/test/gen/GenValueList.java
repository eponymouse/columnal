package test.gen;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenValueList.ListAndType;
import utility.Utility.ListEx;

/**
 * Created by neil on 22/01/2017.
 */
public class GenValueList extends GenValueBase<ListAndType>
{
    public static class ListAndType
    {
        public final ListEx list;
        // Type of the whole array, not the item inside:
        public final DataType type;

        public ListAndType(ListEx list, DataType type)
        {
            this.list = list;
            this.type = type;
        }

        @Override
        public String toString()
        {
            return super.toString() + "{Type: " + type + "}";
        }
    }

    public GenValueList()
    {
        super(ListAndType.class);
    }


    @Override
    public ListAndType generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        this.r = sourceOfRandomness;
        this.gs = generationStatus;
        DataType t = makeType(r);
        int length = r.nextInt(0, 100);
        @Value Object[] values = new @Value Object[length];
        for (int i = 0; i < length; i++)
        {
            try
            {
                values[i] = makeValue(t);
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }
        return new ListAndType(new ListEx()
        {
            @Override
            public int size() throws InternalException, UserException
            {
                return length;
            }

            @Override
            public @Value Object get(int index) throws InternalException, UserException
            {
                return values[index];
            }
        }, length == 0 && sourceOfRandomness.nextBoolean() ? DataType.array() : DataType.array(t));
    }
}
