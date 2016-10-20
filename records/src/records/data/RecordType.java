package records.data;

import java.util.Arrays;
import java.util.List;
import javafx.scene.control.TableColumn;

/**
 * Created by neil on 18/10/2016.
 */
public class RecordType
{
    public RecordType()
    {
    }
    
    public final List<TableColumn<Record, ?>> getColumns()
    {
        return Arrays.asList(new TableColumn<Record, String>() {
            
        });
    }
}
