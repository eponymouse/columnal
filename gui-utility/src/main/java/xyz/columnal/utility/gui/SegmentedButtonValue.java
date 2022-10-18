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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectExpression;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.controlsfx.control.SegmentedButton;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TranslationUtility;

import java.util.IdentityHashMap;

/**
 * An augmented version of SegmentedButton that allows you
 * to get the selected value from the buttons easily.
 */
@OnThread(Tag.FX)
public class SegmentedButtonValue<T> extends SegmentedButton
{
    private final IdentityHashMap<Toggle, T> buttons = new IdentityHashMap<>();
    private final ObjectExpression<T> valueProperty;

    @SuppressWarnings("nullness") // We know each button will have a mapped value in the binding
    @SafeVarargs
    public SegmentedButtonValue(Pair<@LocalizableKey String, T>... choices)
    {
        for (Pair<@LocalizableKey String, T> choice : choices)
        {
            ToggleButton button = new TickToggleButton(TranslationUtility.getString(choice.getFirst()));
            getButtons().add(button);
            buttons.put(button, choice.getSecond());
        }

        getButtons().get(0).setSelected(true);
        valueProperty = Bindings.createObjectBinding(() -> buttons.get(getToggleGroup().getSelectedToggle()), getToggleGroup().selectedToggleProperty());
    }

    public ObjectExpression<T> valueProperty()
    {
        return valueProperty;
    }

    @OnThread(Tag.FX)
    private static class TickToggleButton extends ToggleButton
    {
        public TickToggleButton(@Localized String text)
        {
            super(text);
            Label tick = new Label("\u2713");
            setGraphic(tick);
            tick.visibleProperty().bind(selectedProperty());
        }
    }
}
