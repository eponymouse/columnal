package records.transformations.expression;

import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableManager;
import records.error.InternalException;
import records.gui.expressioneditor.OperandOps;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunctionInt;
import utility.FXPlatformSupplier;
import utility.FXPlatformSupplierInt;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

/**
 * A quick fix for an error.  Has a title to display, and a thunk to run
 * to get a replacement expression for the error source.
 * 
 * This works on a tree basis, not text-based.
 */
public final class QuickFix<EXPRESSION extends StyledShowable>
{
    private final StyledString title;
    // Identified by reference, not by contents/hashCode.
    private final EXPRESSION replacementTarget;
    private final FXPlatformSupplierInt<@UnknownIfRecorded EXPRESSION> makeReplacement;
    private final FXPlatformSupplier<ImmutableList<String>> cssClasses;
    private @MonotonicNonNull StyledString cachedTitle;

    public QuickFix(@LocalizableKey String titleKey, EXPRESSION replacementTarget, FXPlatformSupplierInt<@NonNull @UnknownIfRecorded EXPRESSION> makeReplacement)
    {
        this(StyledString.s(TranslationUtility.getString(titleKey)),
            ImmutableList.of(),
            replacementTarget, makeReplacement);
    }
    
    public QuickFix(StyledString title, ImmutableList<String> cssClasses, EXPRESSION replacementTarget, FXPlatformSupplierInt<@UnknownIfRecorded EXPRESSION> makeReplacement)
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
                    return Utility.<String>concatI(cssClasses, ImmutableList.<String>of(OperandOps.makeCssClass(makeReplacement.get())));
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

    @OnThread(Tag.FXPlatform)
    public StyledString getTitle()
    {
        if (cachedTitle != null)
            return cachedTitle;
        
        StyledString replacement;
        try
        {
            replacement = makeReplacement.get().toStyledString();
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
    public Pair<EXPRESSION, @UnknownIfRecorded EXPRESSION> getReplacement() throws InternalException
    {
        return new Pair<>(replacementTarget, makeReplacement.get());
    }

    public EXPRESSION getReplacementTarget()
    {
        return replacementTarget;
    }

    @OnThread(Tag.FXPlatform)
    public ImmutableList<String> getCssClasses()
    {
        return cssClasses.get();
    }
}
