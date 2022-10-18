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

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.TranslationUtility;

import java.util.ArrayList;

/**
 * We have dialogs where we want to have a set of labels with identical
 * widths, but the labels are not all in the same grid pane because of 
 * extra formatting or dividers between them.
 * 
 * To make the labels align we use a hack.  We make each label a stack pane
 * containing all the labels, but only the one we want to show there is 
 * visible.  That way all the stack panes have the same widths, and all
 * the labels appear to have the same width.
 * 
 * Note that we end up with N^2 labels for N actually-visible labels, but
 * since N is <10, it's not a big issue.
 */
@OnThread(Tag.FXPlatform)
public class AlignedLabels
{
    // One stack pane per visible row (N), each containing N labels 
    private final ArrayList<StackPane> stackPanes = new ArrayList<>();
    // The text items, same length as stackPanes.
    private final ArrayList<@Localized String> labelTexts = new ArrayList<>();

    private final Pos alignment;

    public AlignedLabels(Pos alignment)
    {
        this.alignment = alignment;
    }
    
    public AlignedLabels()
    {
        this(Pos.CENTER_RIGHT);
    }

    /**
     * Add a new label with the given key, returns a StackPane that
     * may be modified to have more invisible labels by future addLabel
     * calls.
     */
    public StackPane addLabel(@LocalizableKey String labelKey, String... styleClasses)
    {
        // Add this text to existing panes:
        @Localized String labelText = TranslationUtility.getString(labelKey);
        for (StackPane existingPane : stackPanes)
        {
            Label l = new Label(labelText);
            l.getStyleClass().addAll(styleClasses);
            StackPane.setAlignment(l, alignment);
            l.setVisible(false);
            existingPane.getChildren().add(0, l);
        }
        // Now make a new pane with the existing and new:
        StackPane stackPane = new StackPane();
        for (String existingText : labelTexts)
        {
            Label l = new Label(existingText);
            StackPane.setAlignment(l, alignment);
            l.setVisible(false);
            stackPane.getChildren().add(l);
        }
        Label l = new Label(labelText);
        l.getStyleClass().addAll(styleClasses);
        StackPane.setAlignment(l, alignment);
        stackPane.getChildren().add(l);
        
        labelTexts.add(labelText);
        stackPanes.add(stackPane);
        return stackPane;
    }
}
