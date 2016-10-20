package records.data;

/**
 * Created by neil on 18/10/2016.
 */
public class Record
{
    private RecordType recordType;
    
    public Record(RecordType type)
    {
        this.recordType = type;
    }

    public final RecordType getType()
    {
        return recordType;
    }
}
