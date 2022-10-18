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
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PopOver.ArrowLocation;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.Help.HelpInfo;

/**
 * A little question mark in a circle which offers a short toolip when hovered over,
 * or a longer tooltip if clicked.
 */
@OnThread(Tag.FXPlatform)
class HelpBox extends StackPane
{
    private final @HelpKey String helpId;
    private @MonotonicNonNull PopOver popOver;
    // Showing full is also equivalent to whether it is pinned.
    private final BooleanProperty showingFull = new SimpleBooleanProperty(false);
    private final BooleanProperty keyboardFocused = new SimpleBooleanProperty(false);
    private @Nullable FXPlatformRunnable cancelHover;
    // To prevent GC issues:
    private @Nullable BooleanExpression keyboardFocusedBoundTo;

    public HelpBox(@HelpKey String helpId)
    {
        this.helpId = helpId;
        getStyleClass().add("help-box");
        Circle circle = new Circle(12.0);
        circle.getStyleClass().add("circle");
        Text text = new Text("?");
        text.getStyleClass().add("question");
        getChildren().setAll(circle, text);
        // We extend the node beneath the circle to put some space between the circle
        // and where the arrow of the popover shows, otherwise the popover interrupts the
        // mouseover detection and things get weird:
        minHeightProperty().set(20);
        text.setMouseTransparent(true);

        text.rotateProperty().bind(Bindings.when(showingFull).then(-45.0).otherwise(0.0));

        circle.setOnMouseEntered(e -> {
            cancelHover = FXUtility.runAfterDelay(Duration.millis(400), () -> {
                if (!popupShowing())
                    FXUtility.mouse(this).showPopOver();
            });
        });
        circle.setOnMouseExited(e -> {
            if (cancelHover != null)
            {
                cancelHover.run();
                cancelHover = null;
            }
            if (popupShowing() && !showingFull.get())
            {
                popOver.hide();
            }
        });
        circle.setOnMouseClicked(e -> {
            boolean wasPinned = showingFull.get();
            showingFull.set(true);
            if (!popupShowing())
            {
                FXUtility.mouse(this).showPopOver();
            }
            else
            {
                if (wasPinned)
                    popOver.hide();
            }
            e.consume();
        });
    }


    @EnsuresNonNullIf(expression = "popOver", result = true)
    private boolean popupShowing(@UnknownInitialization(StackPane.class) HelpBox this)
    {
        return popOver != null && popOver.isShowing();
    }

    @OnThread(Tag.FXPlatform)
    private void showPopOver()
    {
        if (popOver == null)
        {
            @Nullable HelpInfo helpInfo = Help.getHelpInfo(helpId);
            if (helpInfo != null)
            {
                Text shortText = new Text(helpInfo.shortText);
                shortText.getStyleClass().add("short");
                TextFlow textFlow = new TextFlow(shortText);
                Text more = new Text();
                more.textProperty().bind(new ReadOnlyStringWrapper("\n\n").concat(
                    Bindings.when(keyboardFocused)
                        .then(TranslationUtility.getString("help.more.keyboard"))
                        .otherwise(TranslationUtility.getString("help.more"))));
                more.getStyleClass().add("more");
                more.visibleProperty().bind(showingFull.not());
                more.managedProperty().bind(more.visibleProperty());
                textFlow.getChildren().add(more);

                textFlow.getChildren().addAll(Utility.mapList(helpInfo.fullParas, p ->
                {
                    // Blank line between paragraphs:
                    Text t = new Text("\n\n" + p);
                    t.getStyleClass().add("full");
                    t.visibleProperty().bind(showingFull);
                    t.managedProperty().bind(t.visibleProperty());
                    return t;
                }));


                BorderPane pane = new BorderPane(textFlow);
                pane.getStyleClass().add("help-content");
                FXUtility.addChangeListenerPlatformNN(showingFull, b -> pane.requestLayout());

                popOver = new PopOver(pane);
                popOver.setTitle(helpInfo.title);
                popOver.setArrowLocation(ArrowLocation.TOP_LEFT);
                popOver.getStyleClass().add("help-popup");
                popOver.setAnimated(false);
                popOver.setArrowIndent(30);
                popOver.setOnHidden(e -> {showingFull.set(false);});
                // Remove minimum height constraint:
                // We can only do this once skin has been set (which is what binds
                // it in the first place):
                FXUtility.onceNotNull(popOver.skinProperty(), sk -> {
                    if (popOver != null)
                    {
                        popOver.getRoot().minHeightProperty().unbind();
                        popOver.getRoot().minHeightProperty().set(0);
                    }
                });
            }

        }
        // Not guaranteed to have been created, if we can't find the hint:
        if (popOver != null)
        {
            popOver.show(this);
            //org.scenicview.ScenicView.show(popOver.getRoot().getScene());
        }
    }

    /**
     * Cycles through: not showing, showing, showing full.
     */
    public void cycleStates()
    {
        if (!popupShowing())
        {
            showPopOver();
        }
        else
        {
            if (!showingFull.get())
            {
                showingFull.set(true);
            }
            else
            {
                popOver.hide();
            }
        }
    }

    public void bindKeyboardFocused(@Nullable BooleanExpression keyboardFocused)
    {
        if (keyboardFocused != null)
            this.keyboardFocused.bind(keyboardFocused);
        this.keyboardFocusedBoundTo = keyboardFocused;
    }
}
