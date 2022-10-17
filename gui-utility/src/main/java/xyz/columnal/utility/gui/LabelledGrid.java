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

import annotation.help.qual.HelpKey;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TranslationUtility;

/**
 * A GridPane with three columns (label, help box, and content) and
 * a variable number of rows.  
 */
@OnThread(Tag.FXPlatform)
public final class LabelledGrid extends GridPane
{
    // Count of the number of rows that have been added.
    private int rows = 0;

    public static Row radioGridRow(@LocalizableKey String labelKey, @HelpKey String helpId, ToggleGroup toggleGroup, String... radioStyleClasses)
    {
        RadioButton radioButton = GUI.addIdClass(new RadioButton(TranslationUtility.getString(labelKey)), labelKey);
        radioButton.setToggleGroup(toggleGroup);
        setHalignment(radioButton, HPos.LEFT);
        radioButton.getStyleClass().addAll(radioStyleClasses);
        return new Row(radioButton, GUI.helpBox(helpId, radioButton), null);
    }

    public static Row labelledGridRow(@LocalizableKey String labelKey, @HelpKey String helpId, Node node, String... labelStyleClasses)
    {
        return classicRow(GUI.label(labelKey, labelStyleClasses), GUI.helpBox(helpId, node), node);
    }

    // A grid row with no help button
    public static Row unhelpfulGridRow(@LocalizableKey String labelKey, Node node)
    {
        Node label = GUI.label(labelKey);
        setHalignment(label, HPos.RIGHT);
        GridPane.setHgrow(node, Priority.ALWAYS);
        return new Row(label, null, node);
    }

    public static Pair<CheckBox, Row> tickGridRow(@LocalizableKey String labelKey, @HelpKey String helpId, Node node, String... tickBoxStyleClasses)
    {
        CheckBox checkBox = new CheckBox(TranslationUtility.getString(labelKey));
        GUI.addIdClass(checkBox, labelKey);
        checkBox.getStyleClass().addAll(tickBoxStyleClasses);
        return new Pair<>(checkBox, classicRow(checkBox, GUI.helpBox(helpId, node), node));
    }

    public static Row labelledGridRow(AlignedLabels alignedLabels, @LocalizableKey String labelKey, @HelpKey String helpId, Node node, String... labelStyleClasses)
    {
        return classicRow(alignedLabels.addLabel(labelKey, labelStyleClasses), GUI.helpBox(helpId, node), node);
    }

    private static Row classicRow(Node label, HelpBox helpBox, Node content)
    {
        setHalignment(label, HPos.RIGHT);
        GridPane.setHgrow(content, Priority.ALWAYS);
        return new Row(label, helpBox, content);
    }

    public static Row labelOnlyRow(Node label)
    {
        return new Row(label, null, null);
    }

    public static Row contentOnlyRow(Node content)
    {
        return new Row(null, null, content);
    }

    public static Row fullWidthRow(Node fullWidthItem)
    {
        setColumnSpan(fullWidthItem, 3);
        return new Row(fullWidthItem, null, null);
    }

    public static final class Row
    {
        private final @Nullable Node lhs;
        private final @Nullable HelpBox helpBox;
        private final @Nullable Node rhs;

        private Row(@Nullable Node lhs, @Nullable HelpBox helpBox, @Nullable Node rhs)
        {
            this.lhs = lhs;
            this.helpBox = helpBox;
            this.rhs = rhs;
        }
        
        public void setLabelHAlignment(HPos hAlignment)
        {
            if (lhs != null)
                GridPane.setHalignment(lhs, hAlignment);
        }
    }
    
    // A row with only content, but that still only occupies column 2:
    // TODO
    

    public LabelledGrid(Row... rows)
    {
        getStyleClass().add("labelled-grid");
        for (Row row : rows)
        {
            addRow(row);
        }
    }

    public void addRow(Row row)
    {
        if (row.lhs != null)
            add(row.lhs, 0, this.rows);
        if (row.helpBox != null)
            add(row.helpBox, 1, this.rows);
        if (row.rhs != null)
            add(row.rhs, 2, this.rows);
        this.rows += 1;
    }
}
