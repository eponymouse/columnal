package records.gui.stf;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.error.UserException;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import utility.Either;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 28/06/2017.
 */
public class NumberEntry extends TerminalComponent<@Value Number>
{
    // NumberEntry can have overrides while it is not focused for editing:
    private String actualIntegerPart;
    private String actualFracPart;

    private String displayIntegerPart;
    private String displayFracPart;
    
    private boolean focused;

    public NumberEntry(ImmutableList<Component<?>> parents, @Nullable Number initial)
    {
        super(parents);
        actualIntegerPart = initial == null ? "" : (initial instanceof BigDecimal ? ((BigDecimal) initial).toBigInteger().toString() : initial.toString());
        Item integerComponent = makeIntegerComponent(actualIntegerPart);
        items.add(integerComponent);
        actualFracPart = initial == null ? "" : Utility.getFracPartAsString(initial, 0, Integer.MAX_VALUE);
        Item dotComponent = makeDotComponent(actualFracPart.isEmpty() ? "" : ".");
        items.add(dotComponent);
        Item fracComponent = makeFracComponent(actualFracPart);
        items.add(fracComponent);

        displayIntegerPart = actualIntegerPart;
        displayFracPart = actualFracPart;
    }

    private Item makeFracComponent(@UnknownInitialization(TerminalComponent.class) NumberEntry this, String fracPart)
    {
        return new Item(getItemParents(), fracPart, ItemVariant.EDITABLE_NUMBER_FRAC, "").withStyleClasses("stf-number-frac");
    }

    public Item makeDotComponent(@UnknownInitialization(TerminalComponent.class) NumberEntry this, String dot)
    {
        return new Item(getItemParents(), dot, ItemVariant.NUMBER_DOT, "").withStyleClasses("stf-number-dot");
    }

    public Item makeIntegerComponent(@UnknownInitialization(TerminalComponent.class) NumberEntry this, String intPart)
    {
        return new Item(getItemParents(), intPart, ItemVariant.EDITABLE_NUMBER_INT, TranslationUtility.getString("entry.prompt.number")).withStyleClasses("stf-number-int");
    }

    @Override
    public Either<List<ErrorFix>, @Value Number> endEdit(StructuredTextField field)
    {
        try
        {
            actualIntegerPart = getItem(ItemVariant.EDITABLE_NUMBER_INT);
            actualFracPart = getItem(ItemVariant.EDITABLE_NUMBER_FRAC);
            return Either.right(DataTypeUtility.value(Utility.parseNumber(actualIntegerPart + "." + actualFracPart)));
        }
        catch (UserException e)
        {
            return Either.left(Collections.emptyList());
        }
    }

    /**
     * Sets the display integer and fractional parts (for when the field is NOT focused).
     * Returns true if this was a change from before
     */
    public boolean setDisplay(String displayIntegerPart, String displayFracPart)
    {
        if (this.displayIntegerPart.equals(displayIntegerPart) && this.displayFracPart.equals(displayFracPart))
            return false;
        this.displayIntegerPart = displayIntegerPart;
        this.displayFracPart = displayFracPart;
        updateComponentContent();
        return true;
    }

    @Override
    public @Nullable CaretPositionMapper focusChanged(boolean focused)
    {
        this.focused = focused;
        // We have to work out whereabouts the caret currently lies.
        CaretPositionMapper mapper;
        String integerComponent = getItem(ItemVariant.EDITABLE_NUMBER_INT);
        String dotComponent = getItem(ItemVariant.NUMBER_DOT);
        String fracComponent = getItem(ItemVariant.EDITABLE_NUMBER_FRAC);
        int prevInt = integerComponent.length();
        int prevDot = dotComponent.length();
        updateComponentContent();
        if (focused)
        {
            return n -> {
                if (n <= prevInt)
                    // Right-align the position:
                    return integerComponent.length() - (prevInt - n);
                else
                    // Left-align the position:
                    return integerComponent.length() + dotComponent.length() + (n - (prevInt + prevDot));
            };
        }
        else
            return null;
    }

    private void updateComponentContent()
    {
        ImmutableList<Item> prospectiveContent = ImmutableList.of(
            makeIntegerComponent(!focused ? displayIntegerPart : actualIntegerPart),
            makeDotComponent(actualFracPart.isEmpty() ? "" : "."),
            makeFracComponent(!focused ? displayFracPart : actualFracPart)
        );
        
        // Should we avoid setting content if no change?
        items.setAll(prospectiveContent);
    }
}
