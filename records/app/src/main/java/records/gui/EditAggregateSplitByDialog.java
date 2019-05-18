package records.gui;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.Table;
import records.error.InternalException;
import records.error.UserException;
import records.gui.AutoComplete.CompletionListener;
import records.gui.AutoComplete.SimpleCompletion;
import records.gui.AutoComplete.WhitespacePolicy;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformSupplier;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.gui.ErrorableLightDialog;
import utility.gui.FXUtility;
import utility.gui.FancyList;
import utility.gui.Instruction;
import utility.TranslationUtility;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@OnThread(Tag.FXPlatform)
public class EditAggregateSplitByDialog extends ErrorableLightDialog<ImmutableList<ColumnId>>
{
    private final @Nullable Table srcTable;
    private final AggregateSplitByPane splitList;

    public EditAggregateSplitByDialog(View parent, @Nullable Point2D lastScreenPos, @Nullable Table srcTable, @Nullable Pair<ColumnId, ImmutableList<String>> example, ImmutableList<ColumnId> originalSplitBy)
    {
        super(parent, true);
        setResizable(true);
        initModality(Modality.NONE);
        this.srcTable = srcTable;

        splitList = new AggregateSplitByPane(srcTable, originalSplitBy, example);
        splitList.setMinWidth(250.0);
        splitList.setMinHeight(150.0);
        splitList.setPrefWidth(300.0);
        splitList.setPrefHeight(250.0);
        
        getDialogPane().setContent(splitList);
        getDialogPane().getStylesheets().addAll(
            FXUtility.getStylesheet("general.css"),
            FXUtility.getStylesheet("dialogs.css")
        );
        getDialogPane().getStyleClass().add("sort-list-dialog");
        setOnShowing(e -> {
            //org.scenicview.ScenicView.show(getDialogPane().getScene());
            parent.enableColumnPickingMode(lastScreenPos, p -> Objects.equals(srcTable, p.getFirst()), t -> {
                splitList.pickColumnIfEditing(t);
            });
        });
        setOnHiding(e -> {
            parent.disablePickingMode();
        });
    }

    @Override
    protected @OnThread(Tag.FXPlatform) Either<@Localized String, ImmutableList<ColumnId>> calculateResult()
    {
        return splitList.getItems();
    }
}
