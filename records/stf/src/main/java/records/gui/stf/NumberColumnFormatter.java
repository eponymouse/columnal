package records.gui.stf;

import annotation.qual.Value;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.text.Text;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.StyleClassedTextArea;
import records.gui.stable.EditorKitCache;
import records.gui.stf.StructuredTextField.EditorKit;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;

/**
 * One per column.
 */
@OnThread(Tag.FXPlatform)
// package-visible
class NumberColumnFormatter implements FXPlatformConsumer<EditorKitCache<@Value Number>.VisibleDetails>
{
    private static final String NUMBER_DOT = "\u00B7"; //"\u2022";
    private static final String ELLIPSIS = "\u2026";//"\u22EF";

    private final ArrayList<NumberDetails> visibleItems = new ArrayList<>();
    
    @Override
    public @OnThread(Tag.FXPlatform) void consume(EditorKitCache<@Value Number>.VisibleDetails vis)
    {
        visibleItems.clear();
        for (@Nullable Pair<StructuredTextField, EditorKit<@Value Number>> visibleCell : vis.visibleCells)
        {
            if (visibleCell != null)
            {
                EditorKit<?> editorKit = visibleCell.getFirst().getEditorKit();
                if (editorKit != null)
                {
                    NumberEntry numberEntry = editorKit.getComponent(NumberEntry.class);
                    @Value @Nullable Number value = visibleCell.getSecond().getLastCompletedValue();
                    if (numberEntry != null && value != null)
                    {
                        visibleItems.add(new NumberDetails(visibleCell.getFirst(), numberEntry, value));
                    }
                }
            }
            
            
        }
        
        // Left length is number of digits to left of decimal place, right length is number of digits to right of decimal place
        int maxLeftLength = visibleItems.stream().mapToInt(d -> d == null ? 1 : d.fullIntegerPart.length()).max().orElse(1);
        int maxRightLength = visibleItems.stream().mapToInt(d -> d == null ? 0 : d.fullFracPart.length()).max().orElse(0);
        double pixelWidth = vis.width - 8; // Allow some padding

        // We truncate the right side if needed, to a minimum of minimumDP, at which point we truncate the left side
        // to what remains
        int minimumDP = Math.min(2, maxRightLength); //displayInfo == null ? 0 : displayInfo.getMinimumDP();
        while (rightToLeft(maxRightLength, pixelWidth) < maxLeftLength && maxRightLength > minimumDP && maxRightLength > 1) // can be zero only if already zero
        {
            maxRightLength -= 1;
        }
        while (rightToLeft(maxRightLength, pixelWidth) < maxLeftLength && maxLeftLength > 1)
        {
            maxLeftLength -= 1;
        }
        // Still not enough room for everything?  Just set it all to ellipsis if so:
        boolean onlyEllipsis = rightToLeft(maxRightLength, pixelWidth) < maxLeftLength;

        for (NumberDetails display : visibleItems)
        {
            if (display != null)
            {
                if (onlyEllipsis)
                {
                    display.displayIntegerPart = ELLIPSIS;
                    display.displayDot = "";
                    display.displayFracPart = "";
                }
                else
                {
                    display.displayIntegerPart = display.fullIntegerPart;
                    display.displayFracPart = display.fullFracPart;
                    display.displayDot = NUMBER_DOT;

                    while (display.displayFracPart.length() < maxRightLength)
                        display.displayFracPart += " "; //displayInfo == null ? " " : displayInfo.getPaddingChar();

                    if (display.displayFracPart.length() > maxRightLength)
                    {
                        display.displayFracPart = display.displayFracPart.substring(0, Math.max(0, maxRightLength - 1)) + ELLIPSIS;
                    }
                    if (display.displayIntegerPart.length() > maxLeftLength)
                    {
                        display.displayIntegerPart = ELLIPSIS + display.displayIntegerPart.substring(display.displayIntegerPart.length() - maxLeftLength + 1);
                    }

                    display.displayDotVisible = !display.fullFracPart.isEmpty();

                    display.updateDisplay();
                }
            }
        }
    }
    
    private class NumberDetails
    {
        private final StructuredTextField structuredTextField;
        private final String fullFracPart;
        private final String fullIntegerPart;
        private String displayFracPart;
        private String displayIntegerPart;
        private String displayDot;
        private boolean displayDotVisible;
        private final NumberEntry numberEntry;

        public NumberDetails(StructuredTextField structuredTextField, NumberEntry numberEntry, Number n)
        {
            this.structuredTextField = structuredTextField;
            this.numberEntry = numberEntry;
            fullIntegerPart = Utility.getIntegerPart(n).toString();
            fullFracPart = Utility.getFracPartAsString(n, 0, -1);
            // TODO should these be taken from NumberEntry?
            displayIntegerPart = fullIntegerPart;
            displayFracPart = fullFracPart;
            displayDot = ".";
            displayDotVisible = true;
        }

        private void updateDisplay()
        {
            //Log.debug("Replacing: " + displayIntegerPart + "//" + displayFracPart);
            if (numberEntry.setDisplay(displayIntegerPart, displayFracPart))
                structuredTextField.updateFromEditorKit();
            /*
            List<String> dotStyle = new ArrayList<>();
            dotStyle.add("number-display-dot");
            if (!displayDotVisible)
                dotStyle.add("number-display-dot-invisible");
            textArea.replace(TableDisplayUtility.docFromSegments(
                    new StyledText<>(displayIntegerPart, Arrays.asList("number-display-int")),
                    new StyledText<>(displayDot, dotStyle),
                    new StyledText<>(displayFracPart, Arrays.asList("number-display-frac"))
            ));
            */
        }
    }
    
    /*
    
    private final BooleanBinding notFocused;
    private @Nullable FXPlatformRunnable endEdit;
    private @Nullable Number currentEditValue = null;
    

    @OnThread(Tag.FXPlatform)
    public NumberColumnFormatter(int rowIndex, Number n, GetValue<@Value Number> g, @Nullable NumberDisplayInfo ndi, Column column, FXPlatformConsumer<OptionalInt> formatVisible)
    {
        currentEditValue = n;
        textArea = new StyleClassedTextArea(false) // plain undo manager
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void replaceText(int startIndex, int endIndex, String newPart)
            {
                String oldComplete = getText();
                String before = oldComplete.substring(0, startIndex);
                String oldText = oldComplete.substring(startIndex, endIndex);
                String end = oldComplete.substring(endIndex);
                String altered = newPart.replace(NUMBER_DOT, ".").replaceAll("[^0-9.+-]", "");
                // We also disallow + and - except at start, and only allow one dot:
                if (before.contains(NUMBER_DOT) || end.contains(NUMBER_DOT))
                    altered = altered.replace(".", "");
                if (before.isEmpty())
                {
                    // + or - would be allowed at the start
                }
                else
                {
                    altered = altered.replace("[+-]","");
                }
                // Check it is actually valid as a number:
                @Nullable Number n;
                @Nullable @Localized String error = null;
                try
                {
                    n = Utility.parseNumber(before.replace(NUMBER_DOT, ".") + altered + end.replace(NUMBER_DOT, "."));
                }
                catch (UserException e)
                {
                    error = e.getLocalizedMessage();
                    n = null;
                }
                currentEditValue = n;
                super.replaceText(startIndex, endIndex, altered.replace(".", NUMBER_DOT));
                // TODO sort out any restyling needed
                // TODO show error
            }
        };
        FXUtility.addChangeListenerPlatformNN(textArea.focusedProperty(), focused ->
        {
            if (!focused)
            {
                if (currentEditValue != null)
                {
                    @NonNull Number storeFinal = currentEditValue;
                    extractFullParts(storeFinal);
                    Workers.onWorkerThread("Storing value " + textArea.getText(), Workers.Priority.SAVE_ENTRY, () -> Utility.alertOnError_(() -> {
                        g.set(rowIndex, DataTypeUtility.value(storeFinal));
                    }));
                    textArea.deselect();
                    shrinkToNormalAfterEditing();
                    formatVisible.consume(OptionalInt.of(rowIndex));
                }
            }
        });
        textArea.setEditable(column.isEditable());
        textArea.setUseInitialStyleForInsertion(false);
        textArea.setUndoManager(UndoManagerFactory.fixedSizeHistoryFactory(3));

        Nodes.addInputMap(textArea, InputMap.consume(EventPattern.keyPressed(KeyCode.ENTER), e -> {
            if (endEdit != null)
                endEdit.run();
            e.consume();
        }));

        if (ndi == null)
            ndi = NumberDisplayInfo.SYSTEMWIDE_DEFAULT; // TODO use file-wide default
        extractFullParts(n);
        displayIntegerPart = fullIntegerPart;
        displayDot = NUMBER_DOT;
        displayFracPart = fullFracPart;
        updateDisplay();
        textArea.getStyleClass().add("number-display");
        StackPane.setAlignment(textArea, Pos.CENTER_RIGHT);
        // Doesn't get mouse events unless focused:
        notFocused = textArea.focusedProperty().not();
        textArea.mouseTransparentProperty().bind(notFocused);
    }


    public void expandToFullDisplayForEditing()
    {
        int pos = getCursorPosition();
        displayDotVisible = true;
        displayDot = fullFracPart.isEmpty() ? "" : NUMBER_DOT;
        displayIntegerPart = fullIntegerPart;
        displayFracPart = fullFracPart;
        updateDisplay();
        StackPane.setAlignment(textArea, Pos.CENTER_LEFT);
        setFullSize();
        setCursorPosition(pos);
    }

    @RequiresNonNull("textArea")
    public void shrinkToNormalAfterEditing(@UnderInitialization(Object.class) NumberColumnFormatter this)
    {
        textArea.setMinWidth(0);
    }

    private void setFullSize()
    {
        textArea.setMinWidth(8 + fullSize(displayIntegerPart.length(), displayFracPart.length()));
    }

    // A positive number means past decimal point (RHS of point = 1)
    // A negative number means before decimal point (LHS of point = -1)
    // What's mainly important is that setCursorPosition afterwards does the
    // sensible thing, even if get is before full expansion and set is after.
    private int getCursorPosition()
    {
        int caretPos = textArea.getCaretPosition();
        if (caretPos <= displayIntegerPart.length())
            return caretPos - displayIntegerPart.length() - 1;
        else
            return caretPos - displayIntegerPart.length(); // this will automatically be + 1 due to the dot
    }

    public void setCursorPosition(int pos)
    {
        if (pos < 0)
            textArea.moveTo(displayIntegerPart.length() + 1 + pos);
        else
            textArea.moveTo(displayIntegerPart.length() + pos);
    }


    public static void formatColumn(@Nullable NumberDisplayInfo displayInfo, EditorKitCache<Number, NumberColumnFormatter>.VisibleDetails vis)
    {
        // Left length is number of digits to left of decimal place, right length is number of digits to right of decimal place
        int maxLeftLength = vis.visibleCells.stream().mapToInt(d -> d == null ? 1 : d.fullIntegerPart.length()).max().orElse(1);
        int maxRightLength = vis.visibleCells.stream().mapToInt(d -> d == null ? 0 : d.fullFracPart.length()).max().orElse(0);
        double pixelWidth = vis.width - 8; // Allow some padding

        // We truncate the right side if needed, to a minimum of minimumDP, at which point we truncate the left side
        // to what remains
        int minimumDP = displayInfo == null ? 0 : displayInfo.getMinimumDP();
        while (rightToLeft(maxRightLength, pixelWidth) < maxLeftLength && maxRightLength > minimumDP && maxRightLength > 1) // can be zero only if already zero
        {
            maxRightLength -= 1;
        }
        while (rightToLeft(maxRightLength, pixelWidth) < maxLeftLength && maxLeftLength > 1)
        {
            maxLeftLength -= 1;
        }
        // Still not enough room for everything?  Just set it all to ellipsis if so:
        boolean onlyEllipsis = rightToLeft(maxRightLength, pixelWidth) < maxLeftLength;

        for (NumberColumnFormatter display : vis.visibleCells)
        {
            if (display != null)
            {
                display.textArea.setMaxWidth(vis.width);
                if (onlyEllipsis)
                {
                    display.displayIntegerPart = ELLIPSIS;
                    display.displayDot = "";
                    display.displayFracPart = "";
                }
                else
                {
                    display.displayIntegerPart = display.fullIntegerPart;
                    display.displayFracPart = display.fullFracPart;
                    display.displayDot = NUMBER_DOT;

                    while (display.displayFracPart.length() < maxRightLength)
                        display.displayFracPart += displayInfo == null ? " " : displayInfo.getPaddingChar();

                    if (display.displayFracPart.length() > maxRightLength)
                    {
                        display.displayFracPart = display.displayFracPart.substring(0, Math.max(0, maxRightLength - 1)) + ELLIPSIS;
                    }
                    if (display.displayIntegerPart.length() > maxLeftLength)
                    {
                        display.displayIntegerPart = ELLIPSIS + display.displayIntegerPart.substring(display.displayIntegerPart.length() - maxLeftLength + 1);
                    }

                    display.displayDotVisible = !display.fullFracPart.isEmpty();

                    display.updateDisplay();
                }
            }
        }
    }

    public static EditorKitCache<@Value Number, NumberColumnFormatter> makeDisplayCache(GetValue<@Value Number> g, @Nullable NumberDisplayInfo displayInfo, Column column)
    {
        return new EditorKitCache<@Value Number, NumberColumnFormatter>(g, vis -> formatColumn(displayInfo, vis), n -> n.textArea) {

            @Override
            protected NumberColumnFormatter makeGraphical(int rowIndex, @Value Number value, FXPlatformConsumer<Boolean> onFocusChange, FXPlatformRunnable relinquishFocus) throws InternalException, UserException
            {
                return new NumberColumnFormatter(rowIndex, value, g, displayInfo, column, this::formatVisible);
            }

            @Override
            public void edit(int rowIndex, @Nullable Point2D scenePoint)
            {
                @Nullable NumberColumnFormatter rowIfShowing = getRowIfShowing(rowIndex);
                if (rowIfShowing != null)
                {
                    @NonNull NumberColumnFormatter numberDisplay = rowIfShowing;
                    @NonNull StyleClassedTextArea textArea = numberDisplay.textArea;
                    if (scenePoint != null)
                    {
                        Point2D localPoint = textArea.sceneToLocal(scenePoint);
                        CharacterHit hit = textArea.hit(localPoint.getX(), localPoint.getY());
                        textArea.moveTo(hit.getInsertionIndex());
                    }
                    numberDisplay.expandToFullDisplayForEditing();
                    // TODO use viewOrder from Java 9 to bring to front
                    // TODO when focused, make white and add drop shadow around it
                    textArea.requestFocus();
                    if (scenePoint == null)
                    {
                        textArea.selectAll();
                    }
                }
                else
                {
                    System.err.println("Trying to edit row " + rowIndex + " but not showing");
                }
            }

            @Override
            public @Nullable InputMap<?> getInputMapForParent(int rowIndex)
            {
                return null; // TODO allow typing to start editing
            }

            @Override
            public boolean isEditable()
            {
                return true;
            }

            @Override
            public boolean editHasFocus(int rowIndex)
            {
                @Nullable NumberColumnFormatter rowIfShowing = getRowIfShowing(rowIndex);
                if (rowIfShowing != null)
                {
                    return rowIfShowing.textArea.isFocused();
                }
                return false;
            }
        };
    }
*/

    private static class DigitSizes
    {
        private static double LEFT_DIGIT_WIDTH;
        private static double RIGHT_DIGIT_WIDTH;
        private static double DOT_WIDTH;

        @OnThread(Tag.FXPlatform)
        public DigitSizes()
        {
            Text t = new Text();
            Group root = new Group(t);
            root.getStyleClass().add("stf-cell-number");
            Scene scene = new Scene(root);
            scene.getStylesheets().addAll(FXUtility.getSceneStylesheets());
            t.setText("0000000000");
            t.getStyleClass().setAll("stf-number-int");
            t.applyCss();
            LEFT_DIGIT_WIDTH = t.getLayoutBounds().getWidth() / 10.0;
            t.setText("0000000000");
            t.getStyleClass().setAll("stf-number-frac");
            t.applyCss();
            RIGHT_DIGIT_WIDTH = t.getLayoutBounds().getWidth() / 10.0;
            t.setText("...........");
            t.getStyleClass().setAll("stf-number-dot");
            DOT_WIDTH = t.getLayoutBounds().getWidth() / 10.0;
        }
    }
    private static @MonotonicNonNull DigitSizes SIZES;

    // Maps a number of digits on the right side of the decimal place to the amount
    // of digits which there is then room for on the left side of the decimal place:
    @OnThread(Tag.FXPlatform)
    private static int rightToLeft(int right, double totalWidth)
    {
        if (SIZES == null)
            SIZES = new DigitSizes();

        double width = totalWidth - SIZES.DOT_WIDTH;
        width -= right * SIZES.RIGHT_DIGIT_WIDTH;
        return (int)Math.floor(width / SIZES.LEFT_DIGIT_WIDTH);
    }

    // Given number of digits to left of point, number to right, calculates required width
    @OnThread(Tag.FXPlatform)
    private static double fullSize(int left, int right)
    {
        if (SIZES == null)
            SIZES = new DigitSizes();

        return left * SIZES.LEFT_DIGIT_WIDTH + (right == 0 ? 0 : (SIZES.DOT_WIDTH + SIZES.RIGHT_DIGIT_WIDTH * right));
    }
}
