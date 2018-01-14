package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.gui.expressioneditor.ExpressionEditorUtil;
import records.types.TypeConcretisationError;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformFunction;
import utility.FXPlatformSupplier;
import utility.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A listener that records where errors have occurred when checking an expression.
 */
public interface ErrorAndTypeRecorder
{
    public default @Nullable TypeExp recordError(Expression src, Either<StyledString, TypeExp> errorOrType)
    {
        return recordError(src, errorOrType, ImmutableList.of());
    }
    
    /**
     * Records an error source and error message, with no available quick fixes
     */
    public default @Nullable TypeExp recordError(Expression src, Either<StyledString, TypeExp> errorOrType, ImmutableList<QuickFix<Expression>> quickFixes)
    {
        return errorOrType.<@Nullable TypeExp>either(err -> {
            recordError(src, err, quickFixes);
            return null;
        }, val -> val);
    }

    /**
     * Records an error source and error message
     */
    public default <T> @Nullable T recordLeftError(Expression src, Either<TypeConcretisationError, T> errorOrVal)
    {
        return errorOrVal.<@Nullable T>either(err -> {
            @Nullable DataType fix = err.getSuggestedTypeFix();
            recordError(src, err.getErrorText(), ExpressionEditorUtil.quickFixesForTypeError(src, fix));
            return null;
        }, val -> val);
    }

    /**
     * Records the type for a particular expression.
     */
    public default @Recorded @Nullable TypeExp recordType(Expression expression, @Nullable TypeExp typeExp)
    {
        if (typeExp != null)
            return recordTypeNN(expression, typeExp);
        else
            return null;
    }

    /**
     * A curried version of the two-arg function of the same name.
     */
    @Pure
    public default Consumer<StyledString> recordError(Expression src)
    {
        return errMsg -> recordError(src, errMsg, Collections.emptyList());
    }

    /**
     * Records an error source and error message, and a list of possible quick fixes
     */
    // TODO make the String @Localized
    public <EXPRESSION> void recordError(EXPRESSION src, StyledString error, List<QuickFix<EXPRESSION>> fixes);

    public default @Recorded @Nullable TypeExp recordTypeAndError(Expression expression, Either<StyledString, TypeExp> typeOrError)
    {
        return recordType(expression, recordError(expression, typeOrError));
    }

    public @Recorded @NonNull TypeExp recordTypeNN(Expression expression, @NonNull TypeExp typeExp);

    /**
     * A quick fix for an error.  Has a title to display, and a thunk to run
     * to get a replacement expression for the error source.
     */
    public final static class QuickFix<EXPRESSION>
    {
        private final @Localized String title;
        private final FXPlatformFunction<QuickFixParams, Pair<ReplacementTarget, EXPRESSION>> fixedReplacement;

        public QuickFix(@Localized String title, FXPlatformFunction<QuickFixParams, Pair<ReplacementTarget, EXPRESSION>> fixedReplacement) {
            this.title = title;
            this.fixedReplacement = fixedReplacement;
        }

        public @Localized String getTitle() {
            return title;
        }
        
        @OnThread(Tag.FXPlatform)
        public Pair<ReplacementTarget, EXPRESSION> getFixedVersion(@Nullable Window parentWindow, TableManager tableManager)
        {
            return fixedReplacement.apply(new QuickFixParams(parentWindow, tableManager));
        }
        
        public static enum ReplacementTarget
        {
            CURRENT, PARENT;
        }
        
        public final class QuickFixParams
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
}
