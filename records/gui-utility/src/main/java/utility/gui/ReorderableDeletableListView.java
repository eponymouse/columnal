package utility.gui;

import javafx.collections.ObservableList;

public class ReorderableDeletableListView<T> extends DeletableListView<T>
{
    public ReorderableDeletableListView(ObservableList<T> items)
    {
        super(items);
    }

    public ReorderableDeletableListView()
    {
    }
}
