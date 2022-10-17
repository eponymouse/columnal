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

package xyz.columnal.styled;

import javafx.scene.text.Text;
import xyz.columnal.styled.StyledString.Style;

public class CommonStyles
{
    private abstract static class BasicStyle<S extends Style<S>> extends Style<S>
    {
        public BasicStyle(Class<S> thisClass)
        {
            super(thisClass);
        }

        @Override
        protected S combine(S with)
        {
            return with;
        }

        @Override
        protected boolean equalsStyle(S item)
        {
            return true;
        }
    }
    
    private static class Italic extends BasicStyle<Italic>
    {
        private Italic()
        {
            super(Italic.class);
        }

        @Override
        protected void style(Text t)
        {
            t.setStyle("-fx-font-style: italic;");
        }
    }
    
    public static final Italic ITALIC = new Italic();

    private static class Monospace extends BasicStyle<Monospace>
    {
        private Monospace()
        {
            super(Monospace.class);
        }

        @Override
        protected void style(Text t)
        {
            // TODO
        }
    }

    public static final Monospace MONOSPACE = new Monospace();
}
