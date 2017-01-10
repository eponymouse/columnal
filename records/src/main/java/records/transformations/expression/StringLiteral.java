package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.loadsave.OutputBuilder;
import utility.ExBiConsumer;
import utility.Pair;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by neil on 25/11/2016.
 */
public class StringLiteral extends Literal
{
    private final String value;

    public StringLiteral(String value)
    {
        this.value = value;
    }

    @Override
    public DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError)
    {
        return DataType.TEXT;
    }

    @Override
    public Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return value;
    }

    @Override
    public String save(boolean topLevel)
    {
        return OutputBuilder.quoted(value);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables)
    {
        BigInteger[] bits = new BigInteger[] {new BigInteger("0")};
        value.codePoints().forEach(c -> {
            bits[0] = bits[0].shiftLeft(32);
            bits[0] = bits[0].or(BigInteger.valueOf(c));
        });
        return formulaManager.getBitvectorFormulaManager().makeBitvector((int)(MAX_STRING_SOLVER_LENGTH * 32), bits[0]);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StringLiteral that = (StringLiteral) o;

        return value.equals(that.value);
    }

    @Override
    public int hashCode()
    {
        return value.hashCode();
    }
}
