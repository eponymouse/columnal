package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.function.BiConsumer;

/**
 * Created by neil on 27/11/2016.
 */
public class BooleanLiteral extends Literal
{
    private final boolean value;

    public BooleanLiteral(boolean value)
    {
        this.value = value;
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, BiConsumer<Expression, String> onError)
    {
        return DataType.BOOLEAN;
    }

    @Override
    public DataTypeValue getTypeValue(RecordSet data, EvaluateState state) throws UserException, InternalException
    {
        return DataTypeValue.bool((i, prog) -> value);
    }

    @Override
    public @OnThread(Tag.FXPlatform) String save()
    {
        return Boolean.toString(value);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src)
    {
        return formulaManager.getBooleanFormulaManager().makeBoolean(value);
    }
}
