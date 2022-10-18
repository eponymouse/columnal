/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Point2D;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.Table;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.gui.lexeditor.ExpressionEditor;
import xyz.columnal.gui.lexeditor.TopLevelEditor.Focus;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.TypeState;
import xyz.columnal.transformations.function.FunctionList;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformSupplierInt;
import xyz.columnal.utility.gui.DialogPaneWithSideButtons;
import xyz.columnal.utility.gui.DoubleOKLightDialog;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;

import java.util.Optional;

/**
 * Edit an expression by itself, used primarily for filter.
 */
@OnThread(Tag.FXPlatform)
public class EditExpressionDialog extends DoubleOKLightDialog<Expression>
{
    private final ExpressionEditor expressionEditor;
    private Expression curValue;

    public EditExpressionDialog(View parent, @Nullable Table srcTable, @Nullable Expression initialExpression, boolean selectAll, ColumnLookup columnLookup, FXPlatformSupplierInt<TypeState> makeTypeState, @Nullable DataType expectedType, @Nullable @LocalizableKey String topMessageKey)
    {
        super(parent, new DialogPaneWithSideButtons());
        setResizable(true);
        initModality(Modality.NONE);

        expressionEditor = new ExpressionEditor(initialExpression, new ReadOnlyObjectWrapper<@Nullable Table>(srcTable), new ReadOnlyObjectWrapper<>(columnLookup), expectedType, parent, parent.getManager().getTypeManager(), makeTypeState, FunctionList.getFunctionLookup(parent.getManager().getUnitManager()), e -> {curValue = e;});
        curValue = expressionEditor.save(true);

        BorderPane borderPane = new BorderPane(expressionEditor.getContainer(), topMessageKey == null ? null : GUI.label(topMessageKey), null, null, null);
        getDialogPane().setContent(borderPane);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("cancel-button");
        FXUtility.fixButtonsWhenPopupShowing(getDialogPane());
        setOnHiding(e -> {
            expressionEditor.cleanup();
        });
        setOnShown(e -> {
            // Have to use runAfter to combat ButtonBarSkin grabbing focus:
            FXUtility.runAfter(() -> {
                expressionEditor.focus(Focus.LEFT);
                if (selectAll)
                    expressionEditor.selectAll();
            });
        });
        //FXUtility.onceNotNull(getDialogPane().sceneProperty(), org.scenicview.ScenicView::show);
    }

    public Optional<Expression> showAndWaitCentredOn(Point2D mouseScreenPos)
    {
        return super.showAndWaitCentredOn(mouseScreenPos, 400, 200);
    }

    @Override
    protected void showAllErrors()
    {
        expressionEditor.showAllErrors();
    }

    @Override
    protected Validity checkValidity()
    {
        expressionEditor.save(true);
        if (expressionEditor.hasErrors())
            return Validity.ERROR_BUT_CAN_SAVE;
        else
            return Validity.NO_ERRORS;
    }

    @Override
    protected @Nullable Expression calculateResult()
    {
        return curValue;
    }
}
