package records.gui.lexeditor;

import annotation.recorded.qual.UnknownIfRecorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.error.InternalException;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.CanonicalSpan;
import records.transformations.expression.ExpressionUtil;
import records.transformations.expression.QuickFix;
import records.transformations.expression.QuickFix.QuickFixAction;
import records.transformations.expression.QuickFix.QuickFixReplace;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformRunnable;
import utility.FXPlatformSupplier;
import utility.FXPlatformSupplierInt;
import utility.Pair;
import utility.Utility;
import utility.TranslationUtility;

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
    // Identified by position:
    private final CanonicalSpan replacementTarget;
    private final Either<QuickFixAction, QuickFixReplace<Pair<String, StyledString>>> actionOrMakeReplacement;
    private final FXPlatformSupplier<ImmutableList<String>> cssClasses;
    private @MonotonicNonNull StyledString cachedTitle;

    public TextQuickFix(@LocalizableKey String titleKey, CanonicalSpan replacementTarget, QuickFixReplace<Pair<String, StyledString>> makeReplacement)
    {
        this(StyledString.s(TranslationUtility.getString(titleKey)),
            ImmutableList.of(),
            replacementTarget, makeReplacement);
    }

    public TextQuickFix(StyledString title, ImmutableList<String> cssClasses, CanonicalSpan replacementTarget, QuickFixReplace<Pair<String, StyledString>> makeReplacement)
    {
        this(title, cssClasses, replacementTarget, Either.right(makeReplacement));
    }
    
    private TextQuickFix(StyledString title, ImmutableList<String> cssClasses, CanonicalSpan replacementTarget, Either<QuickFixAction, QuickFixReplace<Pair<String, StyledString>>> actionOrMakeReplacement)
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
                    return Utility.<String>concatI(cssClasses, actionOrMakeReplacement.<ImmutableList<String>>eitherInt(a -> ImmutableList.<String>of(), m -> ImmutableList.<String>of(ExpressionUtil.makeCssClass(m.makeReplacement().getFirst()))));
                }
                catch (InternalException e)
                {
                    Log.log(e);
                    return cssClasses;
                }
            }
        };
        this.replacementTarget = replacementTarget;
        this.actionOrMakeReplacement = actionOrMakeReplacement;
    }
    
    public <@NonNull EXPRESSION extends StyledShowable> TextQuickFix(CanonicalSpan location, Function<@UnknownIfRecorded EXPRESSION, String> toText, QuickFix<EXPRESSION> treeFix)
    {
        this(treeFix.getTitle(), treeFix.getCssClasses(), location, treeFix.getActionOrReplacement().map((QuickFixReplace<@UnknownIfRecorded EXPRESSION> m) -> () -> {
            @UnknownIfRecorded EXPRESSION s = m.makeReplacement();
            return new Pair<>(toText.apply(s), s.toStyledString());
        }));
    }

    @OnThread(Tag.FXPlatform)
    public StyledString getTitle()
    {
        if (cachedTitle != null)
            return cachedTitle;
        
        StyledString replacement;
        try
        {
            replacement = actionOrMakeReplacement.eitherInt(a -> StyledString.s(""), m -> m.makeReplacement().getSecond());
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
    public Either<QuickFixAction, Pair<CanonicalSpan, String>> getReplacement() throws InternalException
    {
        return actionOrMakeReplacement.mapInt(m -> new Pair<>(replacementTarget, m.makeReplacement().getFirst()));
    }

    @OnThread(Tag.FXPlatform)
    public ImmutableList<String> getCssClasses()
    {
        return cssClasses.get();
    }

    public TextQuickFix offsetBy(@CanonicalLocation int caretPosOffset)
    {
        return new TextQuickFix(title, cssClasses.get(), replacementTarget.offsetBy(caretPosOffset), actionOrMakeReplacement);
    }
}
