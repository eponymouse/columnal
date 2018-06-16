package records.transformations.expression;

import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
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
import utility.gui.TranslationUtility;

/**
 * A quick fix for an error.  Has a title to display, and a thunk to run
 * to get a replacement expression for the error source.
 */
public final class QuickFix<EXPRESSION extends StyledShowable, SEMANTIC_PARENT>
{
    private final StyledString title;
    // Identified by reference, not by contents/hashCode.
    private final EXPRESSION replacementTarget;
    private final FXPlatformSupplierInt<@UnknownIfRecorded EXPRESSION> makeReplacement;
    private final ImmutableList<String> cssClasses;

    public QuickFix(@LocalizableKey String titleKey, EXPRESSION replacementTarget, FXPlatformSupplierInt<@NonNull EXPRESSION> makeReplacement)
    {
        this(StyledString.s(TranslationUtility.getString(titleKey)),
            ImmutableList.of(),
            replacementTarget, makeReplacement);
    }
    
    public QuickFix(StyledString title, ImmutableList<String> cssClasses, EXPRESSION replacementTarget, FXPlatformSupplierInt<EXPRESSION> makeReplacement)
    {
        this.title = title;
        this.cssClasses = cssClasses;
        this.replacementTarget = replacementTarget;
        this.makeReplacement = makeReplacement;
    }

    public StyledString getTitle()
    {
        return title;
    }
    
    @OnThread(Tag.FXPlatform)
    // Gets the replacement target (first item) and replacement (second item)
    public Pair<EXPRESSION, EXPRESSION> getReplacement() throws InternalException
    {
        return new Pair<>(replacementTarget, makeReplacement.get());
    }

    public ImmutableList<String> getCssClasses()
    {
        return cssClasses;
    }
}
