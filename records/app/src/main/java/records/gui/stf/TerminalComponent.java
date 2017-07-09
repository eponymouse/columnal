package records.gui.stf;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A parent class for Component subclasses that have no sub-components, just a plain list of items
 * with no special behaviour.
 */
public abstract class TerminalComponent<T> extends Component<T>
{
    protected final ObservableList<Item> items = FXCollections.observableArrayList();

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
    public final int delete(int startIncl, int endExcl)
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
                    items.set(i, item.withCutContent(Math.max(0, startIncl), Math.min(item.getLength(), endExcl)));
                    if (startIncl >= itemScreenLength)
                    {
                        delta -= (itemScreenLength - items.get(i).getScreenLength());
                    }
                    else if (startIncl >= 0)
                    {
                        delta -= startIncl;
                    }
                }
            }
            startIncl -= itemScreenLength;
            endExcl -= itemScreenLength;
        }
        return delta;
    }

    @Override
    public final InsertState insert(InsertState s)
    {
        int cursorPos = s.cursorPos;
        int lenSoFar = s.lenSoFar;
        ImmutableList<Integer> codepoints = s.remainingCodepointsToInsert;
        for (int i = 0; i < items.size(); i++)
        {
            if (codepoints.isEmpty())
                break; // Nothing more to do

            Item item = items.get(i);
            //cursorPos may be negative here
            int itemScreenLength = item.getScreenLength();
            if (cursorPos - lenSoFar <= itemScreenLength)
            {
                // We've found the right spot
                ArrayList<Integer> insertion = new ArrayList<>();
                while (!codepoints.isEmpty())
                {
                    if (item.getType() == ItemVariant.DIVIDER && cursorPos - lenSoFar == 0 && item.isValidDividerEntry(codepoints.get(0)))
                    {
                        // Overtype the divider:
                        codepoints = codepoints.subList(1, codepoints.size());
                        cursorPos += 1;
                        break;
                    }
                    else if (item.getType() != ItemVariant.DIVIDER && item.getType().validCharacterForItem(item.getValue().substring(0, Math.min(item.getLength(), cursorPos - lenSoFar)), codepoints.get(0)))
                    {
                        // Insert the character
                        insertion.add(codepoints.get(0));
                        codepoints = codepoints.subList(1, codepoints.size());
                    }
                    else
                    {
                        // If we're in the middle, just drop it.  If we're at the end, move on to next item
                        if (cursorPos - lenSoFar >= item.getLength())
                        {
                            break;
                        }
                        else
                        {
                            codepoints = codepoints.subList(1, codepoints.size());
                        }
                    }
                }
                if (!insertion.isEmpty())
                {
                    items.set(i, item.withInsertAt(cursorPos - lenSoFar, new String(Ints.toArray(insertion), 0, insertion.size())));
                    cursorPos += insertion.size();
                }
            }

            lenSoFar += items.get(i).getLength();
        }

        return new InsertState(lenSoFar, cursorPos, codepoints);
    }
}
