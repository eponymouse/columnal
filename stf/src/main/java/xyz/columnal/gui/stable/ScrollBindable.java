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

package xyz.columnal.gui.stable;

import xyz.columnal.gui.stable.ScrollGroup.Token;
import threadchecker.OnThread;
import threadchecker.Tag;

// An item which can have its scrolling bound to VirtScrollStrTextGrid
@OnThread(Tag.FXPlatform)
public interface ScrollBindable
{
    // extra pixels values are always positive.  scrollBy can be negative or positive
    // Return true if you need layout doing afterwards
    public boolean scrollXLayoutBy(Token token, double extraPixelsToShowBefore, double scrollBy, double extraPixelsToShowAfter);
    public boolean scrollYLayoutBy(Token token, double extraPixelsToShowBefore, double scrollBy, double extraPixelsToShowAfter);

    @OnThread(Tag.FXPlatform)
    public void redoLayoutAfterScroll();
    
    @OnThread(Tag.FXPlatform)
    public void updateClip();
}
