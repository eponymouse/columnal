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
    private static final String NUMBER_DOT = "."; //"\u00B7"; //"\u2022";
    
    // NumberEntry can have overrides while it is not focused for editing:
    private String actualIntegerPart;
    private String actualFracPart;

    private String displayIntegerPart;
    private String displayFracPart;
    
    private boolean showDot;
    
    private boolean focused;

    public NumberEntry(ImmutableList<Component<?>> parents, @Nullable Number initial)
    {
        super(parents);
        actualIntegerPart = initial == null ? "" : getIntegerPart(initial);
        Item integerComponent = makeIntegerComponent(actualIntegerPart);
        items.add(integerComponent);
        actualFracPart = initial == null ? "" : getFracPart(initial);
        Item dotComponent = makeDotComponent(actualFracPart.isEmpty() ? "" : ".");
        items.add(dotComponent);
        Item fracComponent = makeFracComponent(actualFracPart);
        items.add(fracComponent);

        displayIntegerPart = actualIntegerPart;
        displayFracPart = actualFracPart;
    }

    public static String getFracPart(Number n)
    {
        return Utility.getFracPartAsString(n, 0, Integer.MAX_VALUE);
    }

    public static String getIntegerPart(Number n)
    {
        return n instanceof BigDecimal ? ((BigDecimal) n).toBigInteger().toString() : n.toString();
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
        return new Item(getItemParents(), intPart, ItemVariant.EDITABLE_NUMBER_INT, "").withStyleClasses("stf-number-int");
    }

    @Override
    public Either<List<ErrorFix>, @Value Number> endEdit(StructuredTextField field)
    {
        try
        {
            actualIntegerPart = getItem(ItemVariant.EDITABLE_NUMBER_INT);
            actualFracPart = getItem(ItemVariant.EDITABLE_NUMBER_FRAC);
            @Value Number value = DataTypeUtility.value(Utility.parseNumber(actualIntegerPart + "." + actualFracPart));
            showDot = showDot || !actualFracPart.matches("^0*$");
            updateComponentContent();
            // We must fish out the actual after parsing and updating
            // the items in the component.  Otherwise the display-specific
            // aspects (extra spaces and zeroes for padding) can
            // be taken back into the actual items, which would not be right:
            actualIntegerPart = getIntegerPart(value);
            actualFracPart = getFracPart(value);
            displayIntegerPart = actualIntegerPart;
            displayFracPart = actualFracPart;
            return Either.right(value);
        }
        catch (UserException e)
        {
            return Either.left(Collections.<ErrorFix>emptyList());
        }
    }

    /**
     * Sets the display integer and fractional parts (for when the field is NOT focused).
     * Returns true if this was a change from before
     */
    public boolean setDisplay(String displayIntegerPart, boolean showDot, String displayFracPart)
    {
        if (this.displayIntegerPart.equals(displayIntegerPart)
            && this.showDot == showDot    
            && this.displayFracPart.equals(displayFracPart))
            return false;
        this.displayIntegerPart = displayIntegerPart;
        this.showDot = showDot;
        this.displayFracPart = displayFracPart;
        updateComponentContent();
        return true;
    }

    @Override
    public @Nullable CaretPositionMapper focusChanged(boolean focused)
    {
        this.focused = focused;
        // We have to work out whereabouts the caret currently lies.
        int prevInt = getItem(ItemVariant.EDITABLE_NUMBER_INT).length();
        int prevDot = getItem(ItemVariant.NUMBER_DOT).length();
        updateComponentContent();
        if (focused)
        {
            return n -> {
                String integerComponent = getItem(ItemVariant.EDITABLE_NUMBER_INT);
                String dotComponent = getItem(ItemVariant.NUMBER_DOT);
                // Clicking the left always stays left most:
                if (n == 0)
                    return 0;
                else if (n <= prevInt)
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
        Item dot = makeDotComponent(showDot ? NUMBER_DOT : "");
        if (actualFracPart.isEmpty() || !showDot)
            dot = dot.withStyleClasses("stf-number-dot-invisible");
        ImmutableList<Item> prospectiveContent = ImmutableList.of(
            makeIntegerComponent(!focused ? displayIntegerPart : actualIntegerPart),
            dot,
            makeFracComponent(!focused ? displayFracPart : actualFracPart)
        );
        
        // Should we avoid setting content if no change?
        items.setAll(prospectiveContent);
    }
}
