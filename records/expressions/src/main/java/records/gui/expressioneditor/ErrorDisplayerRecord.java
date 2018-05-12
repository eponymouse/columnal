package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
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
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeParent;
import records.typeExp.TypeConcretisationError;
import records.typeExp.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Created by neil on 24/02/2017.
 */
public class ErrorDisplayerRecord
{    
    // We use IdentityHashMap because we want to distinguish between multiple duplicate sub-expressions,
    // e.g. in the expression 2 + abs(2), we want to assign any error to the right 2.  Because of this
    // we use identity hash map, and we cannot use Either (which would break this property).  So two maps it is:
    private final IdentityHashMap<Expression, ErrorDisplayer<Expression, ExpressionNodeParent>> expressionDisplayers = new IdentityHashMap<>();
    private final IdentityHashMap<UnitExpression, ErrorDisplayer<UnitExpression, UnitNodeParent>> unitDisplayers = new IdentityHashMap<>();
    private final IdentityHashMap<TypeExpression, ErrorDisplayer<TypeExpression, TypeParent>> typeDisplayers = new IdentityHashMap<>();
    private final IdentityHashMap<Expression, Either<TypeConcretisationError, TypeExp>> types = new IdentityHashMap<>();

    private final IdentityHashMap<Object, Pair<StyledString, List<QuickFix<?, ?>>>> pending = new IdentityHashMap<>();
    
    @SuppressWarnings({"initialization", "unchecked", "recorded"})
    public <EXPRESSION extends Expression> @NonNull @Recorded EXPRESSION record(@UnknownInitialization(Object.class) ErrorDisplayer<Expression, ExpressionNodeParent> displayer, @NonNull EXPRESSION e)
    {
        expressionDisplayers.put(e, displayer);
        Pair<StyledString, List<QuickFix<?, ?>>> pendingItem = pending.remove(e);
        if (pendingItem != null)
        {
            showError(e, pendingItem.getFirst(), (List) pendingItem.getSecond());
            
        }
        return e;
    }

    @SuppressWarnings({"initialization", "recorded"})
    public <UNIT_EXPRESSION extends UnitExpression> @NonNull @Recorded UNIT_EXPRESSION recordUnit(@UnknownInitialization(Object.class) ErrorDisplayer<UnitExpression, UnitNodeParent> displayer, @NonNull UNIT_EXPRESSION e)
    {
        unitDisplayers.put(e, displayer);
        return e;
    }

    @SuppressWarnings({"initialization", "recorded"})
    public <TYPE_EXPRESSION extends TypeExpression> @NonNull @Recorded TYPE_EXPRESSION recordType(@UnknownInitialization(Object.class) ErrorDisplayer<TypeExpression, TypeParent> displayer, @NonNull TYPE_EXPRESSION e)
    {
        typeDisplayers.put(e, displayer);
        return e;
    }

    private void showError(Expression e, @Nullable StyledString s, List<ErrorAndTypeRecorder.QuickFix<Expression,ExpressionNodeParent>> quickFixes)
    {
        @Nullable ErrorDisplayer<Expression, ExpressionNodeParent> d = expressionDisplayers.get(e);
        if (d != null)
        {
            d.addErrorAndFixes(s == null ? StyledString.s("") : s, quickFixes);
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
                            errorDisplayer.showType("");
                            errorDisplayer.addErrorAndFixes(err.getErrorText(), ExpressionEditorUtil.quickFixesForTypeError(typeManager.getUnitManager(), expression, err.getSuggestedTypeFix()));
                        }, display -> errorDisplayer.showType(display));
                }
                catch (InternalException | UserException e)
                {
                    errorDisplayer.addErrorAndFixes(StyledString.s(e.getLocalizedMessage()), Collections.emptyList());
                }
            }
            else
            {
                errorDisplayer.showType("");
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
            public <EXPRESSION extends StyledShowable, SEMANTIC_PARENT> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION, SEMANTIC_PARENT>> quickFixes)
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
