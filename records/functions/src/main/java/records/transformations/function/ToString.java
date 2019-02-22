package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import records.transformations.expression.function.ValueFunction;

import java.time.temporal.TemporalAccessor;

public class ToString extends FunctionDefinition
{
    public ToString() throws InternalException
    {
        super("conversion:to text");
    }

    @Override
    @OnThread(Tag.Simulation)
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        DataType type = paramTypes.apply("t").getRight("Variable t should be type but was unit");
        if (type == null)
            throw new InternalException("");
        return new Instance(type);
    }

    private static class Instance extends ValueFunction
    {
        private final DataType type;

        public Instance(DataType type)
        {
            this.type = type;
        }
        
        @Override
        public @Value Object call() throws UserException, InternalException
        {
            return DataTypeUtility.value(convertToString(type, arg(0)));
        }

        @OnThread(Tag.Simulation)
        private static String convertToString(DataType type, @Value Object param) throws InternalException, UserException
        {
            return type.apply(new DataTypeVisitorEx<String, UserException>()
            {
                @Override
                @OnThread(Tag.Simulation)
                public String number(NumberInfo numberInfo) throws InternalException, InternalException
                {
                    return Utility.numberToString(Utility.cast(param, Number.class));
                }

                @Override
                @OnThread(Tag.Simulation)
                public String text() throws InternalException, InternalException
                {
                    return "\"" + GrammarUtility.escapeChars(Utility.cast(param, String.class)) + "\"";
                }

                @Override
                @OnThread(Tag.Simulation)
                public String date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
                {
                    return dateTimeInfo.getStrictFormatter().format(Utility.cast(param, TemporalAccessor.class));
                }

                @Override
                @OnThread(Tag.Simulation)
                public String bool() throws InternalException, InternalException
                {
                    return param.toString();
                }

                @Override
                @OnThread(Tag.Simulation)
                public String tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                {
                    TaggedValue taggedValue = Utility.cast(param, TaggedValue.class);
                    TagType<DataType> tag = tags.get(taggedValue.getTagIndex());
                    return tag.getName() + ((taggedValue.getInner() == null || tag.getInner() == null) ? "" : ("(" + convertToString(tag.getInner(), taggedValue.getInner()) + ")"));
                }

                @Override
                @OnThread(Tag.Simulation)
                public String tuple(ImmutableList<DataType> inner) throws InternalException, UserException
                {
                    @Value Object @Value[] values = Utility.castTuple(param, inner.size());
                    StringBuilder s = new StringBuilder("(");
                    for (int i = 0; i < values.length; i++)
                    {
                        if (i > 0)
                            s.append(", ");
                        s.append(convertToString(inner.get(i), values[i]));
                    }
                    return s.append(")").toString();
                }

                @Override
                @OnThread(Tag.Simulation)
                public String array(@Nullable DataType inner) throws InternalException, UserException
                {
                    if (inner == null)
                        return "[]";
                    
                    ListEx listEx = Utility.cast(param, ListEx.class);
                    StringBuilder s = new StringBuilder("[");
                    for (int i = 0; i < listEx.size(); i++)
                    {
                        if (i > 0)
                            s.append(", ");
                        s.append(convertToString(inner, listEx.get(i)));
                    }
                    return s.append("]").toString();
                }
            });
        }
    }
}
