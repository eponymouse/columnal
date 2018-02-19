package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.SimulationRunnable;
import utility.Utility;

import java.util.List;

public class InferTypeColumn extends EditableColumn
{
    // If we have inferred type, we act as a simple wrapper around the true column.
    // If we have not yet inferred, we store a simple string with the data values.
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private Either<MemoryStringColumn, EditableColumn> contents;
    
    public InferTypeColumn(RecordSet recordSet, ColumnId name, List<String> startingValues) throws InternalException
    {
        super(recordSet, name);
        contents = Either.left(new MemoryStringColumn(recordSet, name, startingValues, ""));
    }

    @Override
    public @OnThread(Tag.Simulation) synchronized SimulationRunnable insertRows(int index, int count) throws InternalException, UserException
    {
        return contents.eitherEx(values -> values.insertRows(index, count), column -> insertRows(index, count));
    }

    @Override
    public @OnThread(Tag.Simulation) synchronized SimulationRunnable removeRows(int index, int count) throws InternalException, UserException
    {
        return contents.eitherEx(values -> values.removeRows(index, count), column -> removeRows(index, count));
    }

    @Override
    public @OnThread(Tag.Any) synchronized DataTypeValue getType() throws InternalException, UserException
    {
        return contents.eitherEx(values -> {
            return DataTypeValue.toInfer(
                new GetValue<@Value String>()
                {
                    @Override
                    public @Value String getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
                    {
                        return values.getValue(index);
                    }

                    @Override
                    public @OnThread(Tag.Simulation) void set(int index, @Value String value) throws InternalException, UserException
                    {
                        values.setValue(index, value);
                        
                        List<Pair<DataType, @Value Object>> typeGuesses = Utility.<String, Pair<DataType, @Value Object>>mapList(values.getAll(), InferTypeColumn::guessType);
                        
                        if (typeGuesses.stream().map(p -> p.getFirst()).distinct().count() == 1 && !typeGuesses.get(0).getFirst().isToInfer())
                        {
                            // We can switch to that type!
                            contents = Either.right(typeGuesses.get(0).getFirst().makeImmediateColumn(getName(), Utility.<Pair<DataType, @Value Object>, @Value Object>mapList(typeGuesses, p -> p.getSecond()), DataTypeUtility.makeDefaultValue(typeGuesses.get(0).getFirst())).apply(getRecordSet()));
                        }
                    }
                }
            );         
        }, column -> column.getType());
    }

    private static Pair<DataType, @Value Object> guessType(String src)
    {
        if (!src.isEmpty())
            return new Pair<>(DataType.typeVariable("inferred"), DataTypeUtility.value(""));
        
        try
        {
            Number number = Utility.parseNumber(src);
            return new Pair<>(DataType.NUMBER, DataTypeUtility.value(number));
        }
        catch (UserException e)
        {
            // Not a number, then...
        }
        
        // TODO try dates, tuples, arrays
        
        // Our best remaining guess is a string:
        return new Pair<>(DataType.TEXT, DataTypeUtility.value(src));
    }

    @Override
    public synchronized @NonNull @Value Object getDefaultValue()
    {
        return contents.<@Value Object>either(v -> DataTypeUtility.value(""), column -> column.getDefaultValue());
    }

    public synchronized void add(String value) throws InternalException
    {
        if (contents.isRight())
            throw new InternalException("Cannot add string to automatic column after type is inferred");
        contents.getLeft().add(value);
    }
}
