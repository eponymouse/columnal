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

package xyz.columnal.gui.lexeditor.completion;

import annotation.units.CanonicalLocation;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.gui.lexeditor.completion.LexAutoComplete.LexSelectionBehaviour;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;

import java.util.Objects;

/**
 * Note -- the with methods modify this class in-place.  
 * This shouldn't be an issue as the methods are only
 * used in a builder style.
 */
public class LexCompletion
{
    public final @CanonicalLocation int startPos;
    public final @CanonicalLocation int lastShowPosIncl;
    // If null, completing does not change content, but rather opens the further details in a new window 
    public @MonotonicNonNull String content;
    StyledString display;
    public int relativeCaretPos;
    LexSelectionBehaviour selectionBehaviour;
    // Either:
    //   - Left(HTML content)
    //   - Right(HTML file name (e.g. function-abs.html), optional anchor)
    public @MonotonicNonNull Either<String, Pair<String, @Nullable String>> furtherDetails;
    String sideText;

    private LexCompletion(@CanonicalLocation int startPos, @CanonicalLocation int lastShowPosIncl, @Nullable String content, StyledString display, int relativeCaretPos, LexSelectionBehaviour selectionBehaviour, @Nullable Either<String, Pair<String, @Nullable String>> furtherDetails, String sideText)
    {
        this.startPos = startPos;
        this.lastShowPosIncl = lastShowPosIncl;
        if (content != null)
            this.content = content;
        this.display = display;
        this.relativeCaretPos = relativeCaretPos;
        this.selectionBehaviour = selectionBehaviour;
        if (furtherDetails != null)
            this.furtherDetails = furtherDetails;
        this.sideText = sideText;
    }

    public LexCompletion(@CanonicalLocation int startPos, int lengthToShowFor, String content)
    {
        this.startPos = startPos;
        @SuppressWarnings("units")
        @CanonicalLocation int lastShowPosIncl = startPos + lengthToShowFor;
        this.lastShowPosIncl = lastShowPosIncl;
        this.content = content;
        this.display = StyledString.s(content);
        this.relativeCaretPos = content.length();
        this.selectionBehaviour = LexSelectionBehaviour.NO_AUTO_SELECT;
        this.sideText = "";
    }
    
    public LexCompletion(@CanonicalLocation int startIncl, @CanonicalLocation int endIncl, StyledString display, String htmlPageName)
    {
        this(startIncl, endIncl, null, display, 0, LexSelectionBehaviour.NO_AUTO_SELECT, Either.right(new Pair<String, @Nullable String>(htmlPageName, null)), "");
    }
    
    public LexCompletion withReplacement(String newContent)
    {
        this.content = newContent;
        this.display = StyledString.s(newContent);
        return this;
    }

    public LexCompletion withReplacement(String newContent, StyledString display)
    {
        this.content = newContent;
        this.display = display;
        return this;
    }

    public LexCompletion withDisplay(StyledString display)
    {
        this.display = display;
        return this;
    }
    
    public LexCompletion withCaretPosAfterCompletion(int pos)
    {
        this.relativeCaretPos = pos;
        return this;
    }
    
    public LexCompletion withSelectionBehaviour(LexSelectionBehaviour selectionBehaviour)
    {
        this.selectionBehaviour = selectionBehaviour;
        return this;
    }
    
    public LexCompletion withFurtherDetailsURL(@Nullable String url)
    {
        if (url != null)
            this.furtherDetails = Either.right(new Pair<String, @Nullable String>(url, null));
        return this;
    }

    public LexCompletion withFurtherDetailsHTMLContent(String content)
    {
        this.furtherDetails = Either.left(content);
        return this;
    }
    
    public LexCompletion withSideText(String sideText)
    {
        this.sideText = sideText;
        return this;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LexCompletion that = (LexCompletion) o;
        return startPos == that.startPos &&
                lastShowPosIncl == that.lastShowPosIncl &&
                relativeCaretPos == that.relativeCaretPos &&
                Objects.equals(content, that.content) &&
                display.equals(that.display) &&
                selectionBehaviour == that.selectionBehaviour &&
                Objects.equals(furtherDetails, that.furtherDetails) &&
                Objects.equals(sideText, that.sideText);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(startPos, lastShowPosIncl, content, display, relativeCaretPos, selectionBehaviour, furtherDetails, sideText);
    }

    // Used for testing:
    @Override
    public String toString()
    {
        return "LexCompletion{" +
            "startPos=" + startPos +
            ", lastShowPosIncl=" + lastShowPosIncl +
            ", content='" + content + '\'' +
            ", display=" + display +
            ", relativeCaretPos=" + relativeCaretPos +
            ", selectionBehaviour=" + selectionBehaviour +
            ", furtherDetails=" + furtherDetails +
            ", sideText='" + sideText + '\'' +
            '}';
    }

    public LexCompletion offsetBy(@CanonicalLocation int offsetBy)
    {
        final LexCompletion original = this;
        return new LexCompletion(startPos + offsetBy, lastShowPosIncl + offsetBy, content, display, relativeCaretPos, selectionBehaviour, furtherDetails, sideText) {
            @Override
            public boolean showFor(@CanonicalLocation int caretPos)
            {
                return original.showFor(caretPos - offsetBy);
            }
        };
    }

    public boolean showFor(@CanonicalLocation int caretPos)
    {
        return startPos <= caretPos && caretPos <= lastShowPosIncl;
    }
    
    public LexCompletion copyNoShowAtFirstPos()
    {
        return new LexCompletion(startPos, lastShowPosIncl, content, display, relativeCaretPos, selectionBehaviour, furtherDetails, sideText) {
            @Override
            public boolean showFor(@CanonicalLocation int caretPos)
            {
                return caretPos == startPos ? false : super.showFor(caretPos);
            }
        };
    }
}
