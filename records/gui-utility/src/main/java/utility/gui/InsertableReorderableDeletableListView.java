package utility.gui;

import com.google.common.collect.ImmutableList;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Now this is a proper Java class name...
@OnThread(Tag.FXPlatform)
public class InsertableReorderableDeletableListView<T> extends ReorderableDeletableListView<Optional<@NonNull T>>
{
    public InsertableReorderableDeletableListView(List<T> original)
    {
        super(FXCollections.observableArrayList(Stream.concat(original.stream().map(Optional::of), Stream.of(Optional.<T>empty())).collect(ImmutableList.toImmutableList())));
    }

    public ImmutableList<T> getRealItems()
    {
        return getItems().stream().flatMap(x -> Utility.streamNullable(x.orElse(null))).collect(ImmutableList.toImmutableList());
    }

    @Override
    @OnThread(Tag.FXPlatform)
    protected boolean canDeleteValue(@UnknownInitialization(ListView.class) InsertableReorderableDeletableListView<T> this, Optional<T> value)
    {
        return value.isPresent();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    protected String valueToString(@NonNull Optional<@NonNull T> item)
    {
        return item.map(x -> x.toString()).orElse("!!!");
    }
}
