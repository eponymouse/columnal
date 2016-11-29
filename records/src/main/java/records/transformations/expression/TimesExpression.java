package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Created by neil on 29/11/2016.
 */
public abstract class TimesExpression extends NaryOpExpression
{
    public TimesExpression(List<Expression> expressions)
    {
        super(expressions);
    }
    /*
    private DataType type;

    @Override
    protected String saveOp(int index)
    {
        return "*";
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, BiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        type = checkNumbers(data, state, onError);
        return type;
    }

    @Override
    public DataTypeValue getTypeValue(RecordSet data, EvaluateState state) throws UserException, InternalException
    {
        return DataTypeValue.number(type.getNumberDisplayInfo(), (i, prog) -> {
            List<Number> numbers;

        });
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src) throws InternalException, UserException
    {
        return null;
    }
    */
}
