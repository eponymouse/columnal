package records.gui;

import javafx.geometry.Point2D;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import records.data.Table;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.LightDialog;

import java.util.Optional;

@OnThread(Tag.FXPlatform)
public class PickTableDialog extends LightDialog<Table>
{
    public PickTableDialog(View view, Point2D lastScreenPos)
    {
        // We want the cancel button to appear to the right, because otherwise the auto complete hides it:
        super(view.getWindow(), new DialogPane() {
            private @MonotonicNonNull ButtonBar buttonBar;
            
            private ButtonBar getButtonBar()
            {
                // Not the worst hack; better than using CSS:
                if (buttonBar == null)
                    buttonBar = Utility.filterClass(getChildren().stream(), ButtonBar.class).findFirst().orElse(new ButtonBar());
                return buttonBar;
            }
            
            @Override
            protected void layoutChildren()
            {
                final double leftPadding = snappedLeftInset();
                final double topPadding = snappedTopInset();
                final double rightPadding = snappedRightInset();
                final double bottomPadding = snappedBottomInset();
                
                double w = getWidth() - leftPadding - rightPadding;
                double h = getHeight() - topPadding - bottomPadding;
                
                double buttonBarWidth = getButtonBar().prefWidth(h);
                double buttonBarHeight = getButtonBar().minHeight(buttonBarWidth);
                // We align button bar to the bottom, to get cancel to line up with text field
                // Bit of a hack: we adjust for content's padding
                double contentBottomPadding = getContent() instanceof Pane ? ((Pane)getContent()).snappedBottomInset() : 0;
                getButtonBar().resizeRelocate(leftPadding + w - buttonBarWidth, topPadding + h - buttonBarHeight - contentBottomPadding, buttonBarWidth, buttonBarHeight);
                Optional.ofNullable(getContent()).ifPresent(c -> c.resizeRelocate(leftPadding, topPadding, w - buttonBarWidth, h));
            }

            @Override
            protected double computeMinWidth(double height)
            {
                return snappedLeftInset() + snappedRightInset() + Optional.ofNullable(getContent()).map(n -> n.minWidth(height)).orElse(0.0) + getButtonBar().minWidth(height);
            }

            @Override
            protected double computeMinHeight(double width)
            {
                return snappedTopInset() + snappedBottomInset() + Math.max(Optional.ofNullable(getContent()).map(n -> n.minHeight(width)).orElse(0.0), getButtonBar().minHeight(width));
            }

            @Override
            protected double computePrefWidth(double height)
            {
                return snappedLeftInset() + snappedRightInset() + Optional.ofNullable(getContent()).map(n -> n.prefWidth(height)).orElse(0.0) + getButtonBar().prefWidth(height);
            }

            @Override
            protected double computePrefHeight(double width)
            {
                return snappedTopInset() + snappedBottomInset() + Math.max(Optional.ofNullable(getContent()).map(n -> n.prefHeight(width)).orElse(0.0), getButtonBar().prefHeight(width));
            }
        });
        initModality(Modality.NONE);


        PickTablePane pickTablePane = new PickTablePane(view, t -> {
            setResult(t);
            close();
        });
        getDialogPane().setContent(pickTablePane);

        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        setResultConverter(bt -> null);
        centreDialogButtons();
        FXUtility.fixButtonsWhenPopupShowing(getDialogPane());
        
        setOnShowing(e -> {
            view.enableTablePickingMode(lastScreenPos, t -> {
                // We shouldn't need the mouse call here, I think this is a checker framework bug:
                FXUtility.mouse(this).setResult(t);
                close();
            });
            pickTablePane.focusEntryField();
        });
        setOnHiding(e -> {
            view.disableTablePickingMode();
        });
        getDialogPane().getStyleClass().add("pick-table-dialog");
        //org.scenicview.ScenicView.show(getDialogPane().getScene());
    }

}
