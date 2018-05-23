package records.transformations.expression;

import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableManager;
import records.error.InternalException;
import records.gui.expressioneditor.OperandOps;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunctionInt;
import utility.Pair;
import utility.gui.TranslationUtility;

/**
 * A quick fix for an error.  Has a title to display, and a thunk to run
 * to get a replacement expression for the error source.
 */
public final class QuickFix<EXPRESSION extends StyledShowable, SEMANTIC_PARENT>
{
    private final StyledString title;
    private final FXPlatformFunctionInt<QuickFixParams, Pair<ReplacementTarget, @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT>>> fixedReplacement;
    private final ImmutableList<String> cssClasses;

    public QuickFix(@LocalizableKey String titleKey, ReplacementTarget replacementTarget, @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT> replacement)
    {
        this(StyledString.concat(
                StyledString.s(TranslationUtility.getString(titleKey)),
                StyledString.s(": "),
                replacement.toStyledString()),
            ImmutableList.of(OperandOps.makeCssClass(replacement)),
            p -> new Pair<ReplacementTarget, @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT>>(replacementTarget, replacement));
    }
    
    public QuickFix(StyledString title, ImmutableList<String> cssClasses, FXPlatformFunctionInt<QuickFixParams, Pair<ReplacementTarget, @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT>>> fixedReplacement)
    {
        this.title = title;
        this.cssClasses = cssClasses;
        this.fixedReplacement = fixedReplacement;
    }

    public StyledString getTitle()
    {
        return title;
    }
    
    @OnThread(Tag.FXPlatform)
    public Pair<ReplacementTarget, @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT>> getFixedVersion(@Nullable Window parentWindow, TableManager tableManager) throws InternalException
    {
        return fixedReplacement.apply(new QuickFixParams(parentWindow, tableManager));
    }

    public ImmutableList<String> getCssClasses()
    {
        return cssClasses;
    }

    public static enum ReplacementTarget
    {
        CURRENT, PARENT;
    }
    
    public static final class QuickFixParams
    {
        public final @Nullable Window parentWindow;
        public final TableManager tableManager;

        public QuickFixParams(@Nullable Window parentWindow, TableManager tableManager)
        {
            this.parentWindow = parentWindow;
            this.tableManager = tableManager;
        }
    }
}
