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

import com.google.common.collect.ImmutableList;
import javafx.geometry.BoundingBox;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.StageStyle;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.Optional;

/**
 * A utility subclass of Dialog that has a minimal look: no window decorations, but 
 * a drop shadow to make it stand out from item beneath.
 */
@OnThread(Tag.FXPlatform)
public abstract class LightDialog<R> extends Dialog<R>
{
    protected LightDialog(DimmableParent parent, @Nullable DialogPane customDialogPane)
    {
        initOwner(parent.dimWhileShowing(this));
        initStyle(StageStyle.TRANSPARENT);
        initModality(FXUtility.windowModal());
        
        if (customDialogPane != null)
            setDialogPane(customDialogPane);

        final @NonNull DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().addAll(
            FXUtility.getStylesheet("general.css"),
            FXUtility.getStylesheet("dialogs.css")
        );
        dialogPane.getStyleClass().add("light-dialog-pane");
        //getDialogPane().setEffect(new DropShadow());
        FXUtility.addChangeListenerPlatform(dialogPane.contentProperty(), content -> {
            if (content != null)
            {
                content.getStyleClass().add("light-dialog-pane-content");
            }
        });
        
        dialogPane.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.isStillSincePress())
            {
                @Nullable Node button = ImmutableList.of(ButtonType.OK, ButtonType.CANCEL, ButtonType.CLOSE).stream().flatMap(b -> Utility.streamNullable(dialogPane.lookupButton(b))).findFirst().orElse(null);
                if (button != null)
                    button.requestFocus();
            }
        });
        
        // Fire buttons on mouse release as if there is a popup showing,
        // we miss the click event:
        FXUtility.listenAndCallNow(getDialogPane().getButtonTypes(), buttons -> {
            for (ButtonType buttonType : buttons)
            {
                Node n = getDialogPane().lookupButton(buttonType);
                if (n != null && n instanceof Button)
                {
                    ((Button)n).setOnMouseReleased(ev -> {
                        if (ev.getButton() == MouseButton.PRIMARY)
                            ((Button)n).fire();
                    });
                    
                }
            }
        });
        
        Scene scene = dialogPane.getScene();
        // Scene should always be set by call to setDialogPane above, but satisfy null checker:
        if (scene != null)
        {
            //org.scenicview.ScenicView.show(scene);
            scene.setFill(null);
            // Roughly taken from https://stackoverflow.com/questions/19455059/allow-user-to-resize-an-undecorated-stage and https://stackoverflow.com/questions/13206193/how-to-make-an-undecorated-window-movable-draggable-in-javafx
            double xOffset[] = new double[] {0};
            double yOffset[] = new double[] {0};
            scene.addEventHandler(MouseEvent.ANY, e -> {
                if (!FXUtility.mouse(this).isResizable())
                    return;
                final double border = 5;
                Point2D p = new Point2D(e.getSceneX(), e.getSceneY());
                // By default, the bounding box for the dialog pane includes the padding we put around it
                // for the shadow, so we have to manually subtract it here:
                Insets insets = dialogPane.getInsets();
                BoundingBox localBounds = new BoundingBox(insets.getLeft(), insets.getTop(), 
                    dialogPane.getWidth() - insets.getRight() - insets.getLeft(), 
                    dialogPane.getHeight() - insets.getBottom() - insets.getTop()); 
                if (e.getEventType().equals(MouseEvent.MOUSE_MOVED))
                {
                    if (p.getY() < localBounds.getMinY() - border || p.getY() > localBounds.getMaxY() + border
                        || p.getX() < localBounds.getMinX() - border || p.getX() > localBounds.getMaxX() + border)
                    {
                        dialogPane.setCursor(null);
                        return;
                    }
                    boolean touchingTop = Math.abs(localBounds.getMinY() - p.getY()) < border;
                    boolean touchingBottom = Math.abs(localBounds.getMaxY() - p.getY()) < border;
                    boolean touchingLeft = Math.abs(localBounds.getMinX() - p.getX()) < border;
                    boolean touchingRight = Math.abs(localBounds.getMaxX() - p.getX()) < border;
                    if (touchingTop && touchingLeft)
                    {
                        dialogPane.setCursor(Cursor.NW_RESIZE);
                    }
                    else if (touchingTop && touchingRight)
                    {
                        dialogPane.setCursor(Cursor.NE_RESIZE);
                    }
                    else if (touchingBottom && touchingRight)
                    {
                        dialogPane.setCursor(Cursor.SE_RESIZE);
                    }
                    else if (touchingBottom && touchingLeft)
                    {
                        dialogPane.setCursor(Cursor.SW_RESIZE);
                    }
                    else if (touchingTop)
                    {
                        dialogPane.setCursor(Cursor.N_RESIZE);
                    }
                    else if (touchingBottom)
                    {
                        dialogPane.setCursor(Cursor.S_RESIZE);
                    }
                    else if (touchingLeft)
                    {
                        dialogPane.setCursor(Cursor.W_RESIZE);
                    }
                    else if (touchingRight)
                    {
                        dialogPane.setCursor(Cursor.E_RESIZE);
                    }
                    else
                    {
                        dialogPane.setCursor(null);
                    }
                }
                else if (e.getEventType().equals(MouseEvent.MOUSE_EXITED))
                {
                    // NOTE: do not blank the cursor!  This caused the bug about resizing too fast cancelling the resize.
                    //dialogPane.setCursor(null);
                }
                else
                {
                    boolean resizingTop = FXUtility.isResizingTop(dialogPane.getCursor());
                    boolean resizingBottom = FXUtility.isResizingBottom(dialogPane.getCursor());
                    boolean resizingLeft = FXUtility.isResizingLeft(dialogPane.getCursor());
                    boolean resizingRight = FXUtility.isResizingRight(dialogPane.getCursor());
                    if (e.getEventType().equals(MouseEvent.MOUSE_PRESSED))
                    {
                        if (resizingTop)
                            yOffset[0] = localBounds.getMinY() - p.getY();
                        else if (resizingBottom)
                            yOffset[0] = localBounds.getMaxY() - p.getY();

                        // Not an else; can resize vertical and horizontal:
                        if (resizingLeft)
                            xOffset[0] = localBounds.getMinX() - p.getX();
                        else if (resizingRight)
                            xOffset[0] = localBounds.getMaxX() - p.getX();

                        if (!resizingTop && !resizingBottom && !resizingLeft && !resizingRight)
                        {
                            xOffset[0] = e.getSceneX();
                            yOffset[0] = e.getSceneY();
                        }
                    }

                    if (e.getEventType().equals(MouseEvent.MOUSE_DRAGGED))
                    {
                        double minWidth = dialogPane.minWidth(-1) + insets.getLeft() + insets.getRight();
                        double minHeight = dialogPane.minHeight(-1) + insets.getTop() + insets.getBottom();

                        if (resizingTop)
                        {
                            double newY = e.getScreenY() + yOffset[0] - insets.getTop();
                            double oldHeight = getHeight();
                            setHeight(Math.max(minHeight, oldHeight + getY() - newY));
                            setY(getY() + oldHeight - getHeight());
                        }
                        else if (resizingBottom)
                        {
                            setHeight(Math.max(minHeight, e.getScreenY() + yOffset[0] - getY() + insets.getBottom()));
                        }
                        // Not else, can resize both at same time:
                        if (resizingLeft)
                        {
                            double newX = e.getScreenX() + xOffset[0] - insets.getLeft();
                            double oldWidth = getWidth();
                            setWidth(Math.max(minWidth, oldWidth + getX() - newX));
                            setX(getX() + oldWidth - getWidth());
                        }
                        else if (resizingRight)
                        {
                            setWidth(Math.max(minWidth, e.getScreenX() + xOffset[0] - getX() + insets.getRight()));
                        }

                        if (!resizingTop && !resizingBottom && !resizingLeft && !resizingRight)
                        {
                            LightDialog.this.setX(e.getScreenX() - xOffset[0]);
                            LightDialog.this.setY(e.getScreenY() - yOffset[0]);
                        }
                    }
                }
            });
        }
    }

    protected LightDialog(DimmableParent parent)
    {
        this(parent, null);
    }
    
    protected Optional<R> showAndWaitCentredOn(Point2D mouseScreenPos, double contentWidth, double contentHeight)
    {
        // 40 pixels for display padding for drop shadow, and rough guess of 40 more for button bar  
        double idealLeftX = mouseScreenPos.getX() - (contentWidth + 40) * 0.5;
        double idealTopY = mouseScreenPos.getY() - (contentHeight + 40 + 40) * 0.5;
        @Nullable Screen screen = Screen.getScreens().stream().filter(s -> s.getBounds().contains(mouseScreenPos)).findFirst().orElse(null);
        if (screen == null)
        {
            // Weird, but I guess just put the dialog where we think it should go:
            setX(idealLeftX);
            setY(idealTopY);
        }
        else
        {
            double SPACE = 20;
            setX(Utility.clampIncl(screen.getBounds().getMinX() + SPACE, idealLeftX, screen.getBounds().getMaxX() - contentWidth - SPACE));
            setY(Utility.clampIncl(screen.getBounds().getMinY() + SPACE, idealTopY, screen.getBounds().getMaxY() - contentHeight - SPACE));
        }
        return showAndWait();
    }

    protected void centreDialogButtons(@UnknownInitialization(Dialog.class) LightDialog<R> this)
    {
        // Hack!
        // Taken from https://stackoverflow.com/questions/36009764/how-to-align-ok-button-of-a-dialog-pane-in-javafx
        Region spacer = new Region();
        ButtonBar.setButtonData(spacer, ButtonBar.ButtonData.BIG_GAP);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getDialogPane().applyCss();
        HBox hbox = (HBox) getDialogPane().lookup(".container");
        hbox.getChildren().add(spacer);
    }
}
