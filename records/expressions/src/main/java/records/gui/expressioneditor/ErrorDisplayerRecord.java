package records.gui.expressioneditor;

import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.UnitExpression;
import records.types.TypeExp;
import utility.Either;

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
    private final IdentityHashMap<Expression, ErrorDisplayer> expressionDisplayers = new IdentityHashMap<>();
    private final IdentityHashMap<UnitExpression, ErrorDisplayer> unitDisplayers = new IdentityHashMap<>();
    private final IdentityHashMap<Expression, Either<String, TypeExp>> types = new IdentityHashMap<>();

    @SuppressWarnings("initialization")
    public <EXPRESSION extends Expression> @NonNull EXPRESSION record(@UnknownInitialization(Object.class) ErrorDisplayer displayer, @NonNull EXPRESSION e)
    {
        expressionDisplayers.put(e, displayer);
        return e;
    }

    @SuppressWarnings("initialization")
    public <UNIT_EXPRESSION extends UnitExpression> @NonNull UNIT_EXPRESSION recordUnit(@UnknownInitialization(Object.class) ErrorDisplayer displayer, @NonNull UNIT_EXPRESSION e)
    {
        unitDisplayers.put(e, displayer);
        return e;
    }

    public boolean showError(Expression e, String s, List<ErrorRecorder.QuickFix> quickFixes)
    {
        @Nullable ErrorDisplayer d = expressionDisplayers.get(e);
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
            String typeDisplay = "";
            try
            {
                Either<String, TypeExp> typeDetails = types.get(expression);
                if (typeDetails != null)
                    typeDisplay = typeDetails.eitherEx(err -> err, typeExp -> typeExp.toConcreteType(typeManager).eitherEx(err -> err, dataType -> dataType.toDisplay(false)));
            }
            catch (InternalException | UserException e)
            {
                typeDisplay = e.getLocalizedMessage();
            }
            errorDisplayer.showType(typeDisplay);
        }));
    }

    public void recordType(Expression src, Either<String, TypeExp> errorOrType)
    {
        types.put(src, errorOrType);
    }
}
