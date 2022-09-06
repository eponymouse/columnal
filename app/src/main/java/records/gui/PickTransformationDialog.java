package records.gui;

import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import records.transformations.TransformationInfo;
import records.transformations.TransformationManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.DimmableParent;
import utility.gui.FXUtility;
import utility.gui.LightDialog;
import utility.gui.ScrollPaneFill;

import java.util.Optional;

@OnThread(Tag.FXPlatform)
public class PickTransformationDialog extends LightDialog<Pair<Point2D, TransformationInfo>>
{
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 165;
    private static final double WIDTH = BUTTON_WIDTH * 4 + 3 * 10;
    private static final double HEIGHT = BUTTON_HEIGHT * 2 + 3 * 10;

    public PickTransformationDialog(DimmableParent parent)
    {
        super(parent);
        setResizable(true);
        
        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("pick-transformation-tile-pane");

        makeTransformationButtons(gridPane);
        
        FXUtility.forcePrefSize(gridPane);
        ScrollPane scrollPane = new ScrollPane(gridPane) {
            @Override
            public void requestFocus()
            {
            }
        };
        scrollPane.getStyleClass().add("pick-transformation-scroll-pane");
        scrollPane.setHbarPolicy(ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollBarPolicy.ALWAYS);
        BorderPane borderPane = new BorderPane(scrollPane);
        borderPane.getStyleClass().add("pick-transformation-scroll-pane-wrapper");
        borderPane.setPrefHeight(HEIGHT + 50);
        getDialogPane().setContent(borderPane);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        setResultConverter(bt -> null);
        centreDialogButtons();
        //org.scenicview.ScenicView.show(getDialogPane().getScene());
    }

    private void makeTransformationButtons(@UnknownInitialization(LightDialog.class) PickTransformationDialog this, GridPane gridPane)
    {
        int column = 0;
        int row = 0;
        for (TransformationInfo transformationInfo : TransformationManager.getInstance().getTransformations())
        {
            // Check is shown in the first dialog:
            if (transformationInfo.getCanonicalName().equals("check"))
                continue;
            Button button = new ExplainedButton(transformationInfo.getDisplayNameKey(), transformationInfo.getExplanationKey(), transformationInfo.getImageFileName(), BUTTON_WIDTH, p -> {
                FXUtility.mouse(this).setResult(new Pair<>(p, transformationInfo));
                close();
            });
            gridPane.add(button, column, row);
            column = (column + 1) % 3;
            if (column == 0)
                row += 1;
        }
    }

    public Optional<Pair<Point2D, TransformationInfo>> showAndWaitCentredOn(Point2D mouseScreenPos)
    {
        return super.showAndWaitCentredOn(mouseScreenPos, WIDTH, HEIGHT);
    }
}
