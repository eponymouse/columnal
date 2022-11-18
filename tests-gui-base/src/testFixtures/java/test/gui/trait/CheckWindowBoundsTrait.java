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

package test.gui.trait;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Window;
import org.hamcrest.MatcherAssert;
import org.testjavafx.FxRobotInterface;
import test.gui.TFXUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;

public interface CheckWindowBoundsTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    public default void checkWindowWithinScreen()
    {
        for (Window window : TFXUtil.fx(() -> listWindows()))
        {
            double windowX = TFXUtil.fx(() -> window.getX());
            double windowY = TFXUtil.fx(() -> window.getY());
            List<Screen> screens = TFXUtil.fx(() -> Screen.getScreensForRectangle(windowX, windowY, window.getWidth(), window.getHeight()));
            assertEquals(1, screens.size());
            Screen screen = screens.get(0);
            Rectangle2D screenBounds = TFXUtil.fx(() -> screen.getVisualBounds());
            MatcherAssert.assertThat(windowX, greaterThanOrEqualTo(screenBounds.getMinX()));
            MatcherAssert.assertThat(windowY, greaterThanOrEqualTo(screenBounds.getMinY()));
            MatcherAssert.assertThat(windowX + TFXUtil.fx(() -> window.getWidth()), lessThanOrEqualTo(screenBounds.getMaxX()));
            MatcherAssert.assertThat(windowY + TFXUtil.fx(() -> window.getHeight()), lessThanOrEqualTo(screenBounds.getMaxY()));
        }
    }
}
