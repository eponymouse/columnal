package records.transformations.expression;

import edu.emory.mathcs.backport.java.util.Collections;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.loadsave.OutputBuilder;
import utility.ExBiConsumer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

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
    public List<Object> getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return Collections.singletonList(value);
    }

    @Override
    public String save()
    {
        return OutputBuilder.quoted(value);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src)
    {
        throw new UnimplementedException();
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
