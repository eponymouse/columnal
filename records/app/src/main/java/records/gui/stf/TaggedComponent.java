package records.gui.stf;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.collections.ListChangeListener;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.gui.TableDisplayUtility;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import utility.Either;
import utility.TaggedValue;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Created by neil on 03/07/2017.
 */
public class TaggedComponent extends ParentComponent<TaggedValue>
{
    private final ImmutableList<TagType<DataType>> tagTypes;
    private final TagComponent tagComponent;
    private final DividerComponent openBracket;
    private final DividerComponent closeBracket;
    private @Nullable Component<? extends @Value Object> currentChild;

    public <DT extends DataType> TaggedComponent(ImmutableList<Component<?>> parents, ImmutableList<TagType<DT>> tagTypes, @Nullable TaggedValue initialValue)
    {
        super(parents);
        this.tagTypes = (ImmutableList)tagTypes;
        openBracket = new DividerComponent(getItemParents(), "(");
        closeBracket = new DividerComponent(getItemParents(), ")");
        // Important to do TagComponent last as it uses the other fields:
        this.tagComponent = new TagComponent(getItemParents(), initialValue == null ? "" : tagTypes.get(initialValue.getTagIndex()).getName());
    }

    @Override
    protected List<Component<?>> getChildComponents()
    {
        if (currentChild == null)
            return Collections.singletonList(tagComponent);
        else
        {
            if (currentChild.hasOuterBrackets())
                return Arrays.asList(tagComponent, currentChild);
            else
                return Arrays.asList(tagComponent, openBracket, currentChild, closeBracket);
        }
    }

    private void tagNameChanged(String newValue)
    {
        @Nullable TagType<? extends DataType> matchingTag = tagTypes.stream().filter(tt -> tt.getName().toLowerCase().equals(newValue.toLowerCase())).findFirst().orElse(null);
        if (matchingTag != null)
        {
            if (matchingTag.getInner() != null)
            {
                try
                {
                    currentChild = TableDisplayUtility.component(getItemParents(), matchingTag.getInner());
                }
                catch (InternalException e)
                {
                    Utility.log(e);
                    // Just leave blank:
                    currentChild = null;
                }
            }
            else
            {
                currentChild = null;
            }
        }
        else
        {
            currentChild = null;
        }
    }

    @Override
    public Either<List<ErrorFix>, TaggedValue> endEdit(StructuredTextField<?> field)
    {
        String tagName = getItem(ItemVariant.TAG_NAME);
        OptionalInt tagIndex = Utility.findFirstIndex(tagTypes, tt -> tt.getName().equals(tagName));
        if (tagIndex.isPresent())
        {
            return Either.right(new TaggedValue(tagIndex.getAsInt(), null)); // TODO do inner value
        }
        else
        {
            return Either.left(Collections.emptyList());
        }
    }

    private class TagComponent extends TerminalComponent<String>
    {
        public TagComponent(ImmutableList<Component<?>> componentParents, String initialContent)
        {
            super(componentParents);
            FXUtility.listen(items, c -> {
                // There's only ever one item, so if the list has changed, we know that it affects the single item in the list
                // We also know that the tag name will have changed
                tagNameChanged(items.get(0).getValue());
            });

            items.add(new Item(getItemParents(), initialContent, ItemVariant.TAG_NAME, "Tag"));
        }



        @Override
        public Either<List<ErrorFix>, String> endEdit(StructuredTextField<?> field)
        {
            return Either.right(getItem(ItemVariant.TAG_NAME));
        }
    }
}
