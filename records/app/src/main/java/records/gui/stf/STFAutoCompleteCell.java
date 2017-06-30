package records.gui.stf;

import javafx.scene.text.TextFlow;
import org.fxmisc.flowless.Cell;

/**
 * Created by neil on 30/06/2017.
 */
public class STFAutoCompleteCell extends TextFlow implements Cell<Object, STFAutoCompleteCell>
{
    private Object value;

    public STFAutoCompleteCell(Object value)
    {
        this.value = value;
    }

    public Object getValue()
    {
        return value;
    }

    @Override
    public STFAutoCompleteCell getNode()
    {
        return this;
    }
}
