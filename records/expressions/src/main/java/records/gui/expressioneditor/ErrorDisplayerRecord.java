package records.gui.expressioneditor;

import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.transformations.expression.Expression;
import records.transformations.expression.FixedTypeExpression;
import records.transformations.expression.UnitExpression;
import records.types.TypeConcretisationError;
import records.types.TypeExp;
import styled.StyledString;
import utility.Either;

import java.util.ArrayList;
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
    private final IdentityHashMap<Expression, ErrorDisplayer<Expression>> expressionDisplayers = new IdentityHashMap<>();
    private final IdentityHashMap<UnitExpression, ErrorDisplayer<UnitExpression>> unitDisplayers = new IdentityHashMap<>();
    private final IdentityHashMap<Expression, Either<TypeConcretisationError, TypeExp>> types = new IdentityHashMap<>();

    @SuppressWarnings("initialization")
    public <EXPRESSION extends Expression> @NonNull EXPRESSION record(@UnknownInitialization(Object.class) ErrorDisplayer<Expression> displayer, @NonNull EXPRESSION e)
    {
        expressionDisplayers.put(e, displayer);
        return e;
    }

    @SuppressWarnings("initialization")
    public <UNIT_EXPRESSION extends UnitExpression> @NonNull UNIT_EXPRESSION recordUnit(@UnknownInitialization(Object.class) ErrorDisplayer<UnitExpression> displayer, @NonNull UNIT_EXPRESSION e)
    {
        unitDisplayers.put(e, displayer);
        return e;
    }

    public boolean showError(Expression e, StyledString s, List<ErrorAndTypeRecorder.QuickFix<Expression>> quickFixes)
    {
        @Nullable ErrorDisplayer<Expression> d = expressionDisplayers.get(e);
        if (d != null)
        {
            d.showError(s, quickFixes);
            return true;
        }
        else
            return false;
    }
    
    public void showAllTypes(TypeManager typeManager)
    {
        expressionDisplayers.forEach(((expression, errorDisplayer) -> {
            Either<TypeConcretisationError, TypeExp> typeDetails = types.get(expression);
            Log.debug("Showing " + expression + " item " + typeDetails + " showing error: " + errorDisplayer.isShowingError());
            if (typeDetails != null)
            {
                try
                {
                    typeDetails.flatMapEx(typeExp -> typeExp.toConcreteType(typeManager).mapEx(dataType -> dataType.toDisplay(false)))
                        .either_(err -> {
                            errorDisplayer.showType("");
                            errorDisplayer.showError(err.getErrorText(), ExpressionEditorUtil.quickFixesForTypeError(expression, err.getSuggestedTypeFix()));
                        }, display -> errorDisplayer.showType(display));
                }
                catch (InternalException | UserException e)
                {
                    if (!errorDisplayer.isShowingError())
                        errorDisplayer.showError(StyledString.s(e.getLocalizedMessage()), Collections.emptyList());
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
}
