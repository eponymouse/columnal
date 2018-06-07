package utility.gui;

import org.checkerframework.checker.nullness.qual.NonNull;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Optional;

// Now this is a proper Java class name...
@OnThread(Tag.FXPlatform)
public class InsertableReorderableDeletableListView<@NonNull T> extends ReorderableDeletableListView<Optional<@NonNull T>>
{
    /*
    private final FXPlatformSupplier<@Nullable T> makeNew;

    public InsertableReorderableDeletableListView(List<T> original, FXPlatformSupplier<@Nullable T> makeNew)
    {
        super(FXCollections.observableArrayList(Stream.concat(original.stream().map(Optional::of), Stream.of(Optional.<T>empty())).collect(ImmutableList.toImmutableList())));
        this.makeNew = makeNew;
        FXUtility.listen(getItems(), c -> {
            if (getItems().isEmpty() || getItems().get(getItems().size() - 1).isPresent())
                getItems().add(Optional.empty());
        });
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

    @OnThread(Tag.FXPlatform)
    protected class IRDListCell extends RDListCell
    {
        private final Button addButton = GUI.button("irdListView.add", () -> addAtEnd());

        @SuppressWarnings("nullness") // Checker getc confused here by optional
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void updateItem(@Nullable Optional<@NonNull T> item, boolean empty)
        {
            if (empty || item == null)
            {
                setEditable(false);
                contentPane.getChildren().clear();
            }
            else if (item != null && !item.isPresent())
            {
                contentPane.setCenter(addButton);
                contentPane.setLeft(null);
                contentPane.setRight(null);
                contentPane.setTop(null);
                contentPane.setBottom(null);
                setEditable(false);
            }
            else
            {
                setEditable(true);
                setNormalContent();
            }
            super.updateItem(item, empty);
            
        }
        
        // Can be overridden by subclasses.
        protected void setNormalContent()
        {
            super.setContentLabelAndDelete();
        }
    }

    protected void addAtEnd()
    {
        @Nullable T newItem = makeNew.get();
        if (newItem != null)
            getItems().set(getItems().size() - 1, Optional.of(newItem));
    }
    */
}
