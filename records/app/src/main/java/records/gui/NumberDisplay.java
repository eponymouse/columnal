package records.gui;

import annotation.qual.Value;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.fxmisc.richtext.CharacterHit;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.StyledText;
import org.fxmisc.undo.UndoManagerFactory;
import records.data.Column;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.NumberDisplayInfo;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.SimulationRunnable;
import utility.Utility;
import utility.Workers;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

/**
 * Created by neil on 24/05/2017.
 */
@OnThread(Tag.FXPlatform)
// package-visible
class NumberDisplay
{
    private static final String NUMBER_DOT = "\u00B7"; //"\u2022";
    private static final String ELLIPSIS = "\u2026";//"\u22EF";

    private final @NonNull StyleClassedTextArea textArea;
    private final BooleanBinding notFocused;
    private String fullFracPart;
    private String fullIntegerPart;
    private @Nullable Number currentEditValue = null;
    private String displayFracPart;
    private String displayIntegerPart;
    private String displayDot;
    private boolean displayDotVisible;

    @OnThread(Tag.FXPlatform)
    public NumberDisplay(int rowIndex, Number n, GetValue<Number> g, @Nullable NumberDisplayInfo ndi, Column column, FXPlatformConsumer<OptionalInt> formatVisible)
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
                        column.modified(rowIndex);
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

    @EnsuresNonNull({"fullIntegerPart", "fullFracPart"})
    public void extractFullParts(@UnderInitialization(Object.class) NumberDisplay this, Number n)
    {
        fullIntegerPart = Utility.getIntegerPart(n).toString();
        fullFracPart = Utility.getFracPartAsString(n, 0, -1);
    }

    @SuppressWarnings("initialization") // Due to use of various fields
    private void updateDisplay(@UnknownInitialization(Object.class) NumberDisplay this)
    {
        List<String> dotStyle = new ArrayList<>();
        dotStyle.add("number-display-dot");
        if (!displayDotVisible)
            dotStyle.add("number-display-dot-invisible");
        textArea.replace(TableDisplayUtility.docFromSegments(
            new StyledText<>(displayIntegerPart, Arrays.asList("number-display-int")),
            new StyledText<>(displayDot, dotStyle),
            new StyledText<>(displayFracPart, Arrays.asList("number-display-frac"))
        ));
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
    public void shrinkToNormalAfterEditing(@UnderInitialization(Object.class) NumberDisplay this)
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


    public static void formatColumn(@Nullable NumberDisplayInfo displayInfo, DisplayCache<Number, NumberDisplay>.VisibleDetails vis)
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

        for (NumberDisplay display : vis.visibleCells)
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

    public static DisplayCache<@Value Number, NumberDisplay> makeDisplayCache(GetValue<Number> g, @Nullable NumberDisplayInfo displayInfo, Column column)
    {
        return new DisplayCache<@Value Number, NumberDisplay>(g, vis -> formatColumn(displayInfo, vis), n -> n.textArea) {
            @Override
            protected NumberDisplay makeGraphical(int rowIndex, @Value Number value)
            {
                return new NumberDisplay(rowIndex, value, g, displayInfo, column, this::formatVisible);
            }

            @Override
            public void edit(int rowIndex, @Nullable Point2D scenePoint)
            {
                @Nullable NumberDisplay rowIfShowing = getRowIfShowing(rowIndex);
                if (rowIfShowing != null)
                {
                    @NonNull NumberDisplay numberDisplay = rowIfShowing;
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
            public boolean isEditable()
            {
                return true;
            }
        };
    }


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
            root.getStyleClass().add("number-display");
            Scene scene = new Scene(root);
            scene.getStylesheets().addAll(FXUtility.getSceneStylesheets());
            t.setText("0000000000");
            t.getStyleClass().setAll("number-display-int");
            t.applyCss();
            LEFT_DIGIT_WIDTH = t.getLayoutBounds().getWidth() / 10.0;
            t.setText("0000000000");
            t.getStyleClass().setAll("number-display-frac");
            t.applyCss();
            RIGHT_DIGIT_WIDTH = t.getLayoutBounds().getWidth() / 10.0;
            t.setText("...........");
            t.getStyleClass().setAll("number-display-dot");
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
