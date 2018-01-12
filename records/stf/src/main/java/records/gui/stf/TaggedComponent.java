package records.gui.stf;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import utility.Either;
import utility.TaggedValue;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
        @SuppressWarnings("unchecked")
        ImmutableList<TagType<DataType>> tagTypesRaw = (ImmutableList)tagTypes;
        this.tagTypes = tagTypesRaw;
        openBracket = new DividerComponent(getItemParents(), "(");
        closeBracket = new DividerComponent(getItemParents(), ")");
        // Important to do TagComponent last as it uses the other fields:
        this.tagComponent = new TagComponent(getItemParents(), initialValue == null ? "" : tagTypes.get(initialValue.getTagIndex()).getName());
        @Nullable DT innerType = initialValue == null ? null : tagTypes.get(initialValue.getTagIndex()).getInner();
        if (initialValue != null && initialValue.getInner() != null && innerType != null)
        {
            try
            {
                currentChild = TableDisplayUtility.component(getItemParents(), innerType, initialValue.getInner());
            }
            catch (InternalException e)
            {
                Log.log(e);
                // Just leave blank:
                currentChild = null;
            }
        }
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
                    currentChild = TableDisplayUtility.component(getItemParents(), matchingTag.getInner(), null);
                }
                catch (InternalException e)
                {
                    Log.log(e);
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
    public Either<List<ErrorFix>, TaggedValue> endEdit(StructuredTextField field)
    {
        String tagName = getItem(ItemVariant.TAG_NAME);
        OptionalInt tagIndex = Utility.findFirstIndex(tagTypes, tt -> tt.getName().equals(tagName));
        if (tagIndex.isPresent())
        {
            if (currentChild != null)
            {
                Either<List<ErrorFix>, ? extends @Value Object> inner = currentChild.endEdit(field);
                return inner.either(x -> Either.left(x), (@Value Object v) -> Either.right(new TaggedValue(tagIndex.getAsInt(), v)));
            }
            else
            {
                return Either.right(new TaggedValue(tagIndex.getAsInt(), null));
            }
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

            items.add(new Item(getItemParents(), initialContent, ItemVariant.TAG_NAME, TranslationUtility.getString("entry.prompt.tag")));
        }



        @Override
        public Either<List<ErrorFix>, String> endEdit(StructuredTextField field)
        {
            return Either.right(getItem(ItemVariant.TAG_NAME));
        }
    }
}
