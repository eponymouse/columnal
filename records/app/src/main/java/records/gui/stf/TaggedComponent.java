package records.gui.stf;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.gui.TableDisplayUtility;
import records.gui.stf.StructuredTextField.Component;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import utility.Either;
import utility.TaggedValue;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Created by neil on 03/07/2017.
 */
public class TaggedComponent extends Component<TaggedValue>
{
    private final ImmutableList<TagType<DataType>> tagTypes;
    private final @Nullable TaggedValue initialValue;

    public <DT extends DataType> TaggedComponent(ImmutableList<Component<?>> parents, ImmutableList<TagType<DT>> tagTypes, @Nullable TaggedValue initialValue)
    {
        super(parents);
        this.tagTypes = (ImmutableList)tagTypes;
        this.initialValue = initialValue;
    }

    @Override
    public List<Item> getInitialItems()
    {
        return Arrays.asList(new Item(getItemParents(), initialValue == null ? "" : tagTypes.get(initialValue.getTagIndex()).getName(), ItemVariant.TAG_NAME, "Tag"));

    }

    @Override
    public Optional<List<Item>> valueChanged(Item oldVal, Item item)
    {
        if (item.getType() == ItemVariant.TAG_NAME)
        {
            @Nullable TagType<? extends DataType> matchingTag = tagTypes.stream().filter(tt -> tt.getName().toLowerCase().equals(item.getValue().toLowerCase())).findFirst().orElse(null);
            if (matchingTag != null && !matchingTag.getName().equals(oldVal.getValue()))
            {
                List<Item> r = new ArrayList<>();
                r.add(new Item(getItemParents(), matchingTag.getName(), ItemVariant.TAG_NAME, "Tag"));
                if (matchingTag.getInner() != null)
                {
                    @MonotonicNonNull Component<?> innerComponent = null;
                    try
                    {
                        innerComponent = TableDisplayUtility.component(getItemParents(), matchingTag.getInner());
                    }
                    catch (InternalException e)
                    {
                        Utility.log(e);
                        // Just return as is:
                        return Optional.of(r);
                    }
                    if (!innerComponent.hasOuterBrackets())
                        r.add(new Item(getItemParents(), "("));
                    r.addAll(innerComponent.getInitialItems());
                    if (!innerComponent.hasOuterBrackets())
                        r.add(new Item(getItemParents(), ")"));
                }
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    @Override
    public Either<List<ErrorFix>, TaggedValue> endEdit(StructuredTextField<?> field, List<Item> endResult)
    {
        String tagName = getItem(endResult, ItemVariant.TAG_NAME);
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
}
