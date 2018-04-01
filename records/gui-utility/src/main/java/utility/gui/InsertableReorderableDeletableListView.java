package utility.gui;

import com.google.common.collect.ImmutableList;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;
import utility.Utility;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Now this is a proper Java class name...
@OnThread(Tag.FXPlatform)
public class InsertableReorderableDeletableListView<@NonNull T> extends ReorderableDeletableListView<Optional<@NonNull T>>
{
    private final FXPlatformSupplier<@Nullable T> makeNew;

    public InsertableReorderableDeletableListView(List<T> original, FXPlatformSupplier<@Nullable T> makeNew)
    {
        super(FXCollections.observableArrayList(Stream.concat(original.stream().map(Optional::of), Stream.of(Optional.<T>empty())).collect(ImmutableList.toImmutableList())));
        this.makeNew = makeNew;
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

    @Override
    protected DeletableListCell makeCell()
    {
        return new IRDListCell();
    }

    @Override
    protected boolean validTargetPosition(int index)
    {
        // It's ok to insert before last item, but not after it:
        return index < getItems().size();
    }

    private class IRDListCell extends RDListCell
    {
        private final Button addButton = GUI.button("irdListView.add", () -> addAtEnd());

        @SuppressWarnings("nullness") // Checker getc confused here by optional
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void updateItem(@Nullable Optional<@NonNull T> item, boolean empty)
        {
            if (item != null && !item.isPresent())
            {
                contentPane.getChildren().clear();
                contentPane.setCenter(addButton);
            }
            else
            {
                resetContentPane();
            }
            super.updateItem(item, empty);
            
        }
    }

    private void addAtEnd()
    {
        @Nullable T newItem = makeNew.get();
        if (newItem != null)
            getItems().add(getItems().size() - 1, Optional.of(newItem));
    }
}
