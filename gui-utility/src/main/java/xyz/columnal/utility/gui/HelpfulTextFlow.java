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

import annotation.units.DisplayLocation;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.shape.Path;
import javafx.scene.text.TextFlow;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;

@OnThread(Tag.FXPlatform)
public class HelpfulTextFlow extends TextFlow
{

    /**
     * Gets the click position of the target caret position, in local coordinates.
     *
     * @param targetPos The target caret pos (like a character index)
     * @param vPos The vertical position within the caret: top of it, middle of it, bottom of it?
     * @return The click position in local coordinates, plus a boolean indicating whether or not it is in bounds.
     */
    @OnThread(Tag.FXPlatform)
    public Pair<Point2D, Boolean> getClickPosFor(@DisplayLocation int targetPos, VPos vPos, Dimension2D translateBy)
    {
        Bounds bounds = FXUtility.offsetBoundsBy(new Path(caretShape(targetPos, true)).getBoundsInLocal(), 1.0f + (float)translateBy.getWidth(), (float)translateBy.getHeight());
        Point2D p;
        switch (vPos)
        {
            case TOP:
                p = new Point2D((bounds.getMinX() + bounds.getMaxX()) / 2.0, bounds.getMinY());
                break;
            case BOTTOM:
                p = new Point2D((bounds.getMinX() + bounds.getMaxX()) / 2.0, bounds.getMaxY());
                break;
            case CENTER:
            default:
                p = FXUtility.getCentre(bounds);
                break;
        }
        return new Pair<>(p, getBoundsInLocal().contains(p));
    }

}
