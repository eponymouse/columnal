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

package test.gui;

import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import xyz.columnal.gui.InitialWindow;
import xyz.columnal.gui.MainWindow;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by neil on 10/06/2017.
 */
@OnThread(value = Tag.FXPlatform, ignoreParent = true)
public class TestInitialWindow extends FXApplicationTest
{
    private @MonotonicNonNull Stage initialWindow;

    @Override
    public void start(Stage _stage) throws Exception
    {
        super.start(_stage);
        InitialWindow.show(windowToUse, null);
        initialWindow = windowToUse;
    }

    @Test
    @RequiresNonNull("initialWindow")
    @OnThread(Tag.Any)
    public void testNew()
    {
        assertTrue(TFXUtil.fx(() -> initialWindow.isShowing()));
        assertTrue(TFXUtil.fx(() -> MainWindow._test_getViews()).isEmpty());
        clickOn(".id-initial-new");
        assertFalse(TFXUtil.fx(() -> initialWindow.isShowing()));
        assertEquals(1, TFXUtil.fx(() -> MainWindow._test_getViews()).size());
        assertTrue(TFXUtil.fx(() -> MainWindow._test_getViews().entrySet().iterator().next().getValue().isShowing()));
    }
}
