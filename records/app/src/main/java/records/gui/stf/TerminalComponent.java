package records.gui.stf;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import utility.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A parent class for Component subclasses that have no sub-components, just a plain list of items
 * with no special behaviour.
 */
public abstract class TerminalComponent<T> extends Component<T>
{
    protected final ArrayList<Item> items = new ArrayList<>();

    public TerminalComponent(ImmutableList<Component<?>> componentParents)
    {
        super(componentParents);
    }

    @Override
    public final List<Item> getItems()
    {
        return Collections.unmodifiableList(items);
    }

    @Override
    public final Pair<List<Item>, Integer> delete(int startIncl, int endExcl)
    {
        int delta = 0;
        for (int i = 0; i < items.size(); i++)
        {
            Item item = items.get(i);
            //startIncl (and possible endExcl) may be negative here
            int itemScreenLength = item.getScreenLength();
            if (startIncl < itemScreenLength && endExcl >= 0)
            {
                // Deletion involves us.  Are we a divider?  (Shouldn't be any special dividers in this component, so ignore them)
                // If divider, we stay as-is.  Only delete content if not.
                if (item.getType() != ItemVariant.DIVIDER)
                {
                    items.set(i, item.withTrimmedContent(Math.max(0, startIncl), Math.min(itemScreenLength, endExcl)));
                    if (startIncl >= 0)
                    {
                        delta -= (itemScreenLength - items.get(i).getScreenLength());
                    }
                }
            }
            startIncl -= itemScreenLength;
            endExcl -= itemScreenLength;
        }
        return new Pair<>(items, delta);
    }

    @Override
    public final InsertState insert(int cursorPos, ImmutableList<Integer> codepoints)
    {
        for (int i = 0; i < items.size(); i++)
        {
            Item item = items.get(i);
            //cursorPos may be negative here
            int itemScreenLength = item.getScreenLength();
            if (cursorPos <= itemScreenLength)
            {
                // We've found the right spot
                ArrayList<Integer> insertion = new ArrayList<>();
                while (!codepoints.isEmpty())
                {
                    if (item.getType().validCharacterForItem(item.getValue().substring(0, Math.min(item.getLength(), cursorPos)), codepoints.get(0)))
                    {
                        // Insert the character
                        insertion.add(codepoints.get(0));
                        codepoints = codepoints.subList(1, codepoints.size());
                    }
                    else
                    {
                        // If we're in the middle, just drop it.  If we're at the end, move on to next item
                        if (cursorPos >= item.getLength())
                        {
                            break;
                        }
                        else
                        {
                            codepoints = codepoints.subList(1, codepoints.size());
                        }
                    }
                }
                items.set(i, item.withInsertAt(cursorPos, new String(Ints.toArray(insertion), 0, insertion.size())));
            }
            cursorPos -= itemScreenLength;
        }

        return new InsertState(items, cursorPos, codepoints);
    }
}
