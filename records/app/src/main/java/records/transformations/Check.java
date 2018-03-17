package records.transformations;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.errors.ExpressionErrorException;
import records.errors.ExpressionErrorException.EditableExpression;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.MultipleTableLookup;
import records.transformations.expression.TypeState;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@OnThread(Tag.Simulation)
public class Check extends Transformation
{
    private static final String PREFIX = "CHECK";
    private final @Nullable RecordSet recordSet;
    private final String error;
    @OnThread(Tag.Any)
    private final Expression checkExpression;
    private @MonotonicNonNull DataType type;
    
    public Check(TableManager mgr, InitialLoadDetails initialLoadDetails, Expression checkExpression) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.checkExpression = checkExpression;
        RecordSet theRecordSet = null;
        String theError = "Unknown error";
        try
        {
            theRecordSet = new KnownLengthRecordSet(
                    ImmutableList.of(rs -> DataType.BOOLEAN.makeCalculatedColumn(rs, new ColumnId("result"), n -> Utility.later(this).getResult()))
                    , 1
            );
        }
        catch (UserException e)
        {
            theError = e.getLocalizedMessage();
        }
        this.recordSet = theRecordSet;
        this.error = theError;
    }

    @OnThread(Tag.Simulation)
    private @Value Object getResult() throws InternalException, UserException
    {
        if (type == null)
        {
            ErrorAndTypeRecorderStorer errors = new ErrorAndTypeRecorderStorer();
            @Nullable TypeExp checked = checkExpression.check(new MultipleTableLookup(getManager(), null), new TypeState(getManager().getUnitManager(), getManager().getTypeManager()), errors);
            @Nullable DataType typeFinal = null;
            if (checked != null)
                typeFinal = errors.recordLeftError(checkExpression, checked.toConcreteType(getManager().getTypeManager()));

            if (typeFinal == null)
                throw new ExpressionErrorException(errors.getAllErrors().findFirst().orElse(StyledString.s("Unknown type error")), new EditableExpression(checkExpression, null, true, DataType.BOOLEAN)
                {
                    @Override
                    @OnThread(Tag.Simulation)
                    public Table replaceExpression(Expression changed) throws InternalException
                    {
                        return new Check(getManager(), getDetailsForCopy(), changed);
                    }
                });

            type = typeFinal;
        }
        return checkExpression.getValue(0, new EvaluateState());
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException
    {
        if (recordSet == null)
            throw new UserException(error);
        return recordSet;
    }

    @Override
    protected @OnThread(Tag.Any) Stream<TableId> getPrimarySources()
    {
        return Stream.empty();
    }

    @Override
    @OnThread(Tag.Any)
    public Stream<TableId> getSourcesFromExpressions()
    {
        return TransformationUtil.tablesFromExpression(checkExpression);
    }

    @Override
    @OnThread(Tag.Any)
    protected String getTransformationName()
    {
        return "check";
    }

    @Override
    @OnThread(Tag.Any)
    protected List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        return Collections.singletonList(PREFIX + " " + checkExpression.save(BracketedStatus.MISC, renames));
    }

    @Override
    protected int transformationHashCode()
    {
        return checkExpression.hashCode();
    }

    @Override
    protected boolean transformationEquals(Transformation obj)
    {
        if (obj instanceof Check)
            return checkExpression.equals(((Check)obj).checkExpression);
        return false;
    }
}
