package utility.gui;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import utility.Utility;

import java.util.ArrayList;
import java.util.Iterator;

// Allows tracking of focus
public class FocusTracker
{
    private final ArrayList<TimedFocusable> items = new ArrayList<>();
    
    public void addNode(Node... nodes)
    {
        for (Node node : nodes)
        {
            if (node instanceof TimedFocusable)
                items.add((TimedFocusable) node);
            else
                items.add(new Wrapper(node));
        }
    }
    
    public void removeNode(Node... nodes)
    {
        for (Node node : nodes)
        {
            for (Iterator<TimedFocusable> it = items.iterator(); it.hasNext();)
            {
                TimedFocusable t = it.next();
                if (t == node)
                    it.remove();
                else if (t instanceof Wrapper && ((Wrapper)t).wrapped == node)
                {
                    node.focusedProperty().removeListener((Wrapper)t);
                    it.remove();
                }
            }
        }
    }

    public @Nullable Node getRecentlyFocused()
    {
        TimedFocusable timedFocusable = TimedFocusable.getRecentlyFocused(items.toArray(new TimedFocusable[0]));
        if (timedFocusable instanceof Node)
            return (Node) timedFocusable;
        else if (timedFocusable instanceof Wrapper)
            return ((Wrapper) timedFocusable).wrapped;
        else
            return null;
    }

    private static class Wrapper implements TimedFocusable, ChangeListener<Boolean>
    {
        private final Node wrapped;
        private long lastFocusTime;

        public Wrapper(Node wrapped)
        {
            this.wrapped = wrapped;
            if (this.wrapped.isFocused())
                lastFocusTime = System.currentTimeMillis();
            else
                lastFocusTime = 0L;
            wrapped.focusedProperty().addListener(Utility.later(this));
        }

        @Override
        public void changed(ObservableValue<? extends Boolean> observable, Boolean wasFocused, Boolean newValue)
        {
            if (wasFocused)
                lastFocusTime = System.currentTimeMillis(); 
        }

        @Override
        public long lastFocusedTime()
        {
            return wrapped.isFocused() ? System.currentTimeMillis() : lastFocusTime;
        }
    }
}
