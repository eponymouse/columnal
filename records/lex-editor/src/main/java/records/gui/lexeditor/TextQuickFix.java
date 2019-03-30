package records.gui.lexeditor;

import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import records.error.InternalException;
import records.gui.expressioneditor.OperandOps;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
import records.transformations.expression.QuickFix;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;
import utility.FXPlatformSupplierInt;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.util.function.Function;

/**
 * A quick fix for an error.  Has a title to display, and a thunk to run
 * to get a replacement chunk for the error source.
 * 
 * This quick fix relates to a span of text in the source that will be replaced,
 * as opposed to a tree-based quick fix.
 */
public final class TextQuickFix
{
    private final StyledString title;
    // Identified by reference, not by contents/hashCode.
    private final Span replacementTarget;
    private final FXPlatformSupplierInt<Pair<String, StyledString>> makeReplacement;
    private final FXPlatformSupplier<ImmutableList<String>> cssClasses;
    private @MonotonicNonNull StyledString cachedTitle;

    public TextQuickFix(@LocalizableKey String titleKey, Span replacementTarget, FXPlatformSupplierInt<Pair<String, StyledString>> makeReplacement)
    {
        this(StyledString.s(TranslationUtility.getString(titleKey)),
            ImmutableList.of(),
            replacementTarget, makeReplacement);
    }
    
    public TextQuickFix(StyledString title, ImmutableList<String> cssClasses, Span replacementTarget, FXPlatformSupplierInt<Pair<String, StyledString>> makeReplacement)
    {
        this.title = title;
        this.cssClasses = new FXPlatformSupplier<ImmutableList<String>>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public ImmutableList<String> get()
            {
                try
                {
                    return Utility.<String>concatI(cssClasses, ImmutableList.<String>of(OperandOps.makeCssClass(makeReplacement.get().getFirst())));
                }
                catch (InternalException e)
                {
                    Log.log(e);
                    return cssClasses;
                }
            }
        };
        this.replacementTarget = replacementTarget;
        this.makeReplacement = makeReplacement;
    }
    
    public <EXPRESSION extends StyledShowable> TextQuickFix(Span location, Function<EXPRESSION, String> toText, QuickFix<EXPRESSION> treeFix)
    {
        this(treeFix.getTitle(), treeFix.getCssClasses(), location, () -> {
            EXPRESSION s = treeFix.getReplacement().getSecond();
            return new Pair<>(toText.apply(s), s.toStyledString());
        });
    }

    @OnThread(Tag.FXPlatform)
    public StyledString getTitle()
    {
        if (cachedTitle != null)
            return cachedTitle;
        
        StyledString replacement;
        try
        {
            replacement = makeReplacement.get().getSecond();
        }
        catch (InternalException e)
        {
            Log.log(e);
            replacement = StyledString.s("");
        }
        cachedTitle = StyledString.concat(title, StyledString.s(" \u21fe "), replacement);
        return cachedTitle;
    }
    
    @OnThread(Tag.FXPlatform)
    // Gets the replacement target (first item) and replacement (second item)
    public Pair<Span, String> getReplacement() throws InternalException
    {
        return new Pair<>(replacementTarget, makeReplacement.get().getFirst());
    }

    @OnThread(Tag.FXPlatform)
    public ImmutableList<String> getCssClasses()
    {
        return cssClasses.get();
    }
}
