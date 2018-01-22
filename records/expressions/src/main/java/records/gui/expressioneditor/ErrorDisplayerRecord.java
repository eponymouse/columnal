package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.transformations.expression.Expression;
import records.transformations.expression.UnitExpression;
import records.types.TypeConcretisationError;
import records.types.TypeExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Created by neil on 24/02/2017.
 */
public class ErrorDisplayerRecord
{
    private static class ErrorDetails<EXPRESSION>
    {
        public final ErrorDisplayer<EXPRESSION> displayer;
        public final ArrayList<StyledString> errors = new ArrayList<>();
        public final ArrayList<QuickFix<EXPRESSION>> fixes = new ArrayList<>();

        private ErrorDetails(ErrorDisplayer<EXPRESSION> displayer)
        {
            this.displayer = displayer;
        }

        public void addErrorAndFixes(@Nullable StyledString error, List<QuickFix<EXPRESSION>> quickFixes)
        {
            if (error != null)
                this.errors.add(error);
            this.fixes.addAll(quickFixes);
            displayer.showError(StyledString.concat(errors.toArray(new StyledString[0])), fixes);
        }
    }
    
    // We use IdentityHashMap because we want to distinguish between multiple duplicate sub-expressions,
    // e.g. in the expression 2 + abs(2), we want to assign any error to the right 2.  Because of this
    // we use identity hash map, and we cannot use Either (which would break this property).  So two maps it is:
    private final IdentityHashMap<Expression, ErrorDetails<Expression>> expressionDisplayers = new IdentityHashMap<>();
    private final IdentityHashMap<UnitExpression, ErrorDetails<UnitExpression>> unitDisplayers = new IdentityHashMap<>();
    private final IdentityHashMap<Expression, Either<TypeConcretisationError, TypeExp>> types = new IdentityHashMap<>();

    private final IdentityHashMap<Object, Pair<StyledString, List<QuickFix<?>>>> pending = new IdentityHashMap<>();
    
    @SuppressWarnings({"initialization", "unchecked"})
    public <EXPRESSION extends Expression> @NonNull EXPRESSION record(@UnknownInitialization(Object.class) ErrorDisplayer<Expression> displayer, @NonNull EXPRESSION e)
    {
        expressionDisplayers.put(e, new ErrorDetails<>(displayer));
        if (pending.containsKey(e))
            showError(e, pending.get(e).getFirst(), (List)pending.get(e).getSecond());
        return e;
    }

    @SuppressWarnings("initialization")
    public <UNIT_EXPRESSION extends UnitExpression> @NonNull UNIT_EXPRESSION recordUnit(@UnknownInitialization(Object.class) ErrorDisplayer<UnitExpression> displayer, @NonNull UNIT_EXPRESSION e)
    {
        unitDisplayers.put(e, new ErrorDetails<>(displayer));
        return e;
    }

    private void showError(Expression e, @Nullable StyledString s, List<ErrorAndTypeRecorder.QuickFix<Expression>> quickFixes)
    {
        @Nullable ErrorDetails<Expression> d = expressionDisplayers.get(e);
        if (d != null)
        {
            d.addErrorAndFixes(s, quickFixes);
        }
        else
        {
            pending.compute(e, (_k, p) -> {
                if (p == null)
                    return new Pair<>(s == null ? StyledString.s("") : s, ImmutableList.copyOf(quickFixes));
                else
                    return new Pair<>(s == null ? p.getFirst() : StyledString.concat(p.getFirst(), s), Utility.concatI(p.getSecond(), quickFixes));
            });
        }
    }
    
    public void showAllTypes(TypeManager typeManager)
    {
        expressionDisplayers.forEach(((expression, errorDisplayer) -> {
            Either<TypeConcretisationError, TypeExp> typeDetails = types.get(expression);
            //Log.debug("Showing " + expression + " item " + typeDetails + " showing error: " + errorDisplayer.isShowingError());
            if (typeDetails != null)
            {
                try
                {
                    typeDetails.flatMapEx(typeExp -> typeExp.toConcreteType(typeManager).mapEx(dataType -> dataType.toDisplay(false)))
                        .either_(err -> {
                            errorDisplayer.displayer.showType("");
                            errorDisplayer.addErrorAndFixes(err.getErrorText(), ExpressionEditorUtil.quickFixesForTypeError(expression, err.getSuggestedTypeFix()));
                        }, display -> errorDisplayer.displayer.showType(display));
                }
                catch (InternalException | UserException e)
                {
                    if (!errorDisplayer.errors.isEmpty())
                        errorDisplayer.addErrorAndFixes(StyledString.s(e.getLocalizedMessage()), Collections.emptyList());
                }
            }
            else
            {
                errorDisplayer.displayer.showType("");
            }
            
        }));
    }

    public void recordType(Expression src, Either<TypeConcretisationError, TypeExp> errorOrType)
    {
        types.put(src, errorOrType);
    }
    
    public ErrorAndTypeRecorder getRecorder()
    {
        return new ErrorAndTypeRecorder()
        {
            @Override
            public <EXPRESSION> void recordError(EXPRESSION src, StyledString error)
            {
                if (src instanceof Expression)
                {
                    ErrorDisplayerRecord.this.showError((Expression) src, error, ImmutableList.of());
                }
            }

            @SuppressWarnings("unchecked")
            @Override
            public <EXPRESSION> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION>> quickFixes)
            {
                if (src instanceof Expression && !quickFixes.isEmpty())
                {
                    ErrorDisplayerRecord.this.showError((Expression) src, null, (List) quickFixes);
                }
            }

            @Override
            @SuppressWarnings("recorded")
            public @Recorded @NonNull TypeExp recordTypeNN(Expression expression, @NonNull TypeExp typeExp)
            {
                ErrorDisplayerRecord.this.recordType(expression, Either.right(typeExp));
                return typeExp;
            }
        };
    }
}
