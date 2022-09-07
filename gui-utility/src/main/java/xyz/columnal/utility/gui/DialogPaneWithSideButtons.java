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

package xyz.columnal.utility.gui;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import xyz.columnal.log.Log;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * A DialogPane replacement that puts the buttons to the side rather than
 * at the bottom like default.
 * 
 * Useful when you don't want the auto-complete popups to hide the OK/cancel buttons.
 */
@OnThread(Tag.FXPlatform)
public class DialogPaneWithSideButtons extends DialogPane
{
    // createButtonBar() gets called by superclass so will be non-null
    // after construction:
    @SuppressWarnings("nullness")
    private VBox buttonBar;

    private @MonotonicNonNull Map<ButtonType, Node> buttonNodes;
    
    public DialogPaneWithSideButtons()
    {
        // Should be non-null because parent constructor calls createButtonBar.
        if (buttonBar != null)
        {
            // Important that our listener comes after the parent class's listener which is added in their constructor.
            updateButtons();
            FXUtility.listen(getButtonTypes(), c -> updateButtons());
        }
    }
    
    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected Node createButtonBar(@UnknownInitialization(DialogPane.class) DialogPaneWithSideButtons this)
    {
        buttonBar = new VBox();
        buttonBar.getStyleClass().add("side-button-bar");
        buttonBar.setMaxHeight(Double.MAX_VALUE);
        buttonBar.setFillWidth(true);
        return buttonBar;
    }

    // Part-borrowed from superclass method
    @RequiresNonNull("buttonBar")
    private void updateButtons(@UnknownInitialization(DialogPane.class) DialogPaneWithSideButtons this)
    {
        buttonBar.getChildren().clear();

        if (buttonNodes == null)
            buttonNodes = new WeakHashMap<>();
        
        boolean hasDefault = false;
        for (ButtonType cmd : sortButtons(getButtonTypes()))
        {
            Node genButton = lookupButton(cmd);
            
            // keep only first default button
            if (genButton instanceof Button)
            {
                ButtonData buttonType = cmd.getButtonData();

                Button button = (Button) genButton;
                button.setDefaultButton(!hasDefault && buttonType != null && buttonType.isDefaultButton());
                button.setCancelButton(buttonType != null && buttonType.isCancelButton());
                button.setMaxWidth(9999.0);

                hasDefault |= buttonType != null && buttonType.isDefaultButton();
            }
            buttonBar.getChildren().add(genButton);
        }
    }

    private static List<ButtonType> sortButtons(ObservableList<ButtonType> buttonTypes)
    {
        final String buttonOrder;
        if (SystemUtils.IS_OS_WINDOWS)
            buttonOrder = ButtonBar.BUTTON_ORDER_WINDOWS;
        else if (SystemUtils.IS_OS_MAC)
            buttonOrder = ButtonBar.BUTTON_ORDER_MAC_OS;
        else
            buttonOrder = ButtonBar.BUTTON_ORDER_LINUX;
        
        return buttonTypes.sorted(Comparator.comparingInt(bt ->
            buttonOrder.indexOf(bt.getButtonData().getTypeCode())
        ));
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected void layoutChildren()
    {
        final double leftPadding = snappedLeftInset();
        final double topPadding = snappedTopInset();
        final double rightPadding = snappedRightInset();
        final double bottomPadding = snappedBottomInset();
        
        double w = getWidth() - leftPadding - rightPadding;
        double h = getHeight() - topPadding - bottomPadding;
        
        double buttonBarWidth = buttonBar.prefWidth(h);
        double buttonBarHeight = buttonBar.minHeight(buttonBarWidth);
        // We align button bar to the bottom, to get lower button to line roughly with content
        Node content = getContent();
        buttonBar.resizeRelocate(leftPadding + w - buttonBarWidth, topPadding + h - buttonBarHeight, buttonBarWidth, buttonBarHeight);
        if (content != null)
            content.resizeRelocate(leftPadding, topPadding, w - buttonBarWidth, h);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computeMinWidth(double height)
    {
        return snappedLeftInset() + snappedRightInset() + Optional.ofNullable(getContent()).map(n -> n.minWidth(height)).orElse(0.0) + buttonBar.minWidth(height);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computeMinHeight(double width)
    {
        return snappedTopInset() + snappedBottomInset() + Math.max(Optional.ofNullable(getContent()).map(n -> n.minHeight(width)).orElse(0.0), buttonBar.minHeight(width));
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computePrefWidth(double height)
    {
        return snappedLeftInset() + snappedRightInset() + Optional.ofNullable(getContent()).map(n -> n.prefWidth(height)).orElse(0.0) + buttonBar.prefWidth(height);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computePrefHeight(double width)
    {
        return snappedTopInset() + snappedBottomInset() + Math.max(Optional.ofNullable(getContent()).map(n -> n.prefHeight(width)).orElse(0.0), buttonBar.prefHeight(width));
    }
}
