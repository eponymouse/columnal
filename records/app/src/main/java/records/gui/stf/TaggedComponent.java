package records.gui.stf;

import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.gui.stf.StructuredTextField.Component;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import utility.Either;
import utility.TaggedValue;
import utility.Utility;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

/**
 * Created by neil on 03/07/2017.
 */
public class TaggedComponent implements Component<TaggedValue>
{
    private final List<TagType<DataTypeValue>> tagTypes;
    private final TaggedValue initialValue;

    public TaggedComponent(List<TagType<DataTypeValue>> tagTypes, TaggedValue initialValue)
    {
        this.tagTypes = tagTypes;
        this.initialValue = initialValue;
    }

    @Override
    public List<Item> getItems()
    {
        return Arrays.asList(new Item(tagTypes.get(initialValue.getTagIndex()).getName(), ItemVariant.TAG_NAME, "Tag"));
        // TODO add value
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
