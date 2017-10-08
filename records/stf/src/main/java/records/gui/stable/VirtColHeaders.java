package records.gui.stable;

import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import utility.FXPlatformFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VirtColHeaders implements ScrollBindable
{
    private final Map<Integer, VBox> visibleCells = new HashMap<>();
    private final List<VBox> spareCells = new ArrayList<>();
    private final Region container;
    private final VirtScrollStrTextGrid grid;
    private final FXPlatformFunction<Integer, List<MenuItem>> makeContextMenuItems;
    private int firstVisibleColIndex;
    private double firstVisibleColOffset;
}
