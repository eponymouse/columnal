package records.gui.stf;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
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
    private final Item integerComponent;
    private final Item dotComponent;
    private final Item fracComponent;
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
        integerComponent = new Item(getItemParents(), actualIntegerPart, ItemVariant.EDITABLE_NUMBER_INT, TranslationUtility.getString("entry.prompt.number")).withStyleClasses("stf-number-int");
        items.add(integerComponent);
        actualFracPart = initial == null ? "" : Utility.getFracPartAsString(initial, 0, Integer.MAX_VALUE);
        dotComponent = new Item(getItemParents(), actualFracPart.isEmpty() ? "" : ".", ItemVariant.NUMBER_DOT, "").withStyleClasses("stf-number-dot");
        items.add(dotComponent);
        fracComponent = new Item(getItemParents(), actualFracPart, ItemVariant.EDITABLE_NUMBER_FRAC, "").withStyleClasses("stf-number-frac");
        items.add(fracComponent);

        displayIntegerPart = actualIntegerPart;
        displayFracPart = actualFracPart;
    }

    @Override
    public Either<List<ErrorFix>, @Value Number> endEdit(StructuredTextField field)
    {
        try
        {
            return Either.right(DataTypeUtility.value(Utility.parseNumber(getItem(ItemVariant.EDITABLE_NUMBER_INT) + "." + getItem(ItemVariant.EDITABLE_NUMBER_FRAC))));
        }
        catch (UserException e)
        {
            return Either.left(Collections.emptyList());
        }
    }

    public void setDisplay(String displayIntegerPart, String displayFracPart)
    {
        this.displayIntegerPart = displayIntegerPart;
        this.displayFracPart = displayFracPart;
        updateComponentContent();
    }

    @Override
    public void focusChanged(boolean focused)
    {
        this.focused = focused;
        updateComponentContent();
    }

    private void updateComponentContent()
    {
        ImmutableList<Item> prospectiveContent = ImmutableList.of(
            integerComponent.replaceContent(focused ? displayIntegerPart : actualIntegerPart),
            dotComponent,
            fracComponent.replaceContent(focused ? displayFracPart : actualFracPart)
        );
        
        // Should we avoid setting content if no change?
        items.setAll(prospectiveContent);
    }
}
