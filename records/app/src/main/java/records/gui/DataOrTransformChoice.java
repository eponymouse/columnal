package records.gui;

import javafx.scene.control.Dialog;
import records.gui.DataOrTransformChoice.DataOrTransform;

public class DataOrTransformChoice extends Dialog<DataOrTransform>
{
    public static enum DataOrTransform {DATA, TRANSFORM};
}
