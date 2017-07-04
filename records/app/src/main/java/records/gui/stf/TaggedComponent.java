package records.gui.stf;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
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
public class TaggedComponent implements Component<TaggedValue>
{
    private final ImmutableList<TagType<DataType>> tagTypes;
    private final TaggedValue initialValue;

    public <DT extends DataType> TaggedComponent(ImmutableList<TagType<DT>> tagTypes, TaggedValue initialValue)
    {
        this.tagTypes = (ImmutableList)tagTypes;
        this.initialValue = initialValue;
    }

    @Override
    public List<Item> getItems()
    {
        return Arrays.asList(new Item(this, tagTypes.get(initialValue.getTagIndex()).getName(), ItemVariant.TAG_NAME, "Tag"));

    }

    @Override
    public Optional<List<Item>> valueIsNow(Item item)
    {
        if (item.getType() == ItemVariant.TAG_NAME)
        {
            @Nullable TagType<? extends DataType> matchingTag = tagTypes.stream().filter(tt -> tt.getName().toLowerCase().equals(item.getValue().toLowerCase())).findFirst().orElse(null);
            if (matchingTag != null)
            {
                List<Item> r = new ArrayList<>();
                r.add(new Item(this, matchingTag.getName(), ItemVariant.TAG_NAME, "Tag"));
                if (matchingTag.getInner() != null)
                {
                    @MonotonicNonNull Component<?> innerComponent = null;
                    try
                    {
                        innerComponent = TableDisplayUtility.component(matchingTag.getInner());
                    }
                    catch (InternalException e)
                    {
                        Utility.log(e);
                        // Just return as is:
                        return Optional.of(r);
                    }
                    r.add(new Item(this, "("));
                    r.addAll(innerComponent.getItems());
                    r.add(new Item(this, ")"));
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
