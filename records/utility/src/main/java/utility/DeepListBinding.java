package utility;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

@OnThread(Tag.FXPlatform)
public class DeepListBinding<LIST_ELEMENT, RESULT> extends SimpleObjectProperty<RESULT> implements ListChangeListener<ObservableValue<LIST_ELEMENT>>, ChangeListener<LIST_ELEMENT>
{
    // Boolean only used when updating list:
    private final IdentityHashMap<ObservableValue<LIST_ELEMENT>, Boolean> listeningTo = new IdentityHashMap<>();
    private final FXPlatformFunction<ImmutableList<LIST_ELEMENT>, RESULT> calculate;
    private final ObservableList<? extends ObservableValue<LIST_ELEMENT>> srcList;

    @SuppressWarnings("initialization")
    @OnThread(Tag.FXPlatform)
    public DeepListBinding(ObservableList<? extends ObservableValue<LIST_ELEMENT>> srcList, FXPlatformFunction<ImmutableList<LIST_ELEMENT>, RESULT> calculate)
    {
        this.calculate = calculate;
        this.srcList = srcList;
        for (ObservableValue<LIST_ELEMENT> observableValue : srcList)
        {
            listenTo(observableValue);
        }
        srcList.addListener(this);
        recalculate();
    }

    @OnThread(Tag.FXPlatform)
    private void listenTo(ObservableValue<LIST_ELEMENT> observableValue)
    {
        observableValue.addListener(this);
        listeningTo.put(observableValue, true);
    }

    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    @Override
    public void changed(ObservableValue<? extends LIST_ELEMENT> observable, LIST_ELEMENT oldValue, LIST_ELEMENT newValue)
    {
        // If individual value changes, need to recalculate:
        recalculate();
    }

    @OnThread(Tag.FXPlatform)
    private void recalculate()
    {
        setValue(calculate.apply(srcList.stream().map(o -> o.getValue()).collect(ImmutableList.toImmutableList())));
    }

    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    @Override
    public void onChanged(Change<? extends ObservableValue<LIST_ELEMENT>> c)
    {
        // Set all old listeners to false:
        for (Entry<ObservableValue<LIST_ELEMENT>, Boolean> entry : listeningTo.entrySet())
        {
            entry.setValue(false);
        }
        // Listen to everything in list:
        for (ObservableValue<LIST_ELEMENT> observableValue : srcList)
        {
            listenTo(observableValue);
        }
        // Now stop listening to anything mapped to false:
        for (Iterator<Entry<ObservableValue<LIST_ELEMENT>, Boolean>> iterator = listeningTo.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<ObservableValue<LIST_ELEMENT>, Boolean> entry = iterator.next();
            if (entry.getValue() == false)
            {
                entry.getKey().removeListener(this);
                iterator.remove();
            }
        }
        recalculate();
    }
}
