package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.RecordSet;
import records.data.datatype.BooleanDataType;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorGet;
import records.data.datatype.DataType.GetValue;
import records.data.datatype.DataType.NumberDisplayInfo;
import records.data.datatype.DataType.SpecificDataTypeVisitorGet;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.List;

/**
 * Created by neil on 25/11/2016.
 */
public class BinaryOpExpression extends Expression
{
    public static enum Op
    {
        AND("&"), OR("|"), EQUALS("="), ADD("+"), SUBTRACT("-");

        private final String symbol;

        Op(String symbol)
        {
            this.symbol = symbol;
        }

        public static @Nullable Op parse(String text)
        {
            for (Op op : values())
                if (op.symbol.equals(text))
                    return op;
            return null;
        }
    }

    private final Op op;
    private final Expression lhs;
    private final Expression rhs;

    public BinaryOpExpression(Expression lhs, Op op, Expression rhs)
    {
        this.op = op;
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public DataType getType(RecordSet data) throws UserException, InternalException
    {
        switch (op)
        {
            case AND:
            case OR:
                return new BooleanDataType((i, prog) ->
                {
                    Pair<@Nullable ProgressListener, @Nullable ProgressListener> progs = ProgressListener.split(prog);
                    boolean lhsVal = lhs.getBoolean(data, i, progs.getFirst());
                    if ((op == Op.AND && lhsVal == false) || (op == Op.OR && lhsVal == true))
                        return lhsVal;
                    boolean rhsVal = rhs.getBoolean(data, i, progs.getSecond());
                    return rhsVal;
                });
            case EQUALS:
                return new BooleanDataType((i, prog) -> {
                    return 0 == compareValueCheckType(lhs.getType(data), rhs.getType(data), i, prog);
                });
            default:
                throw new UserException("Unsupported operation: " + op);
        }

    }

    @OnThread(Tag.Simulation)
    private static int compareValueCheckType(DataType typeA, DataType typeB, int index, @Nullable ProgressListener prog) throws UserException, InternalException
    {
        Pair<@Nullable ProgressListener, @Nullable ProgressListener> progs = ProgressListener.split(prog);
        return typeA.apply(new DataTypeVisitorGet<Integer>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public Integer number(GetValue<Number> gA, NumberDisplayInfo displayInfo) throws InternalException, UserException
            {
                return typeB.apply(new SpecificDataTypeVisitorGet<Integer>(new UserException("Expected numeric type, to match left-hand side")){
                    @Override
                    @OnThread(Tag.Simulation)
                    public Integer number(GetValue<Number> gB, NumberDisplayInfo displayInfo) throws InternalException, UserException
                    {
                        return Utility.compareNumbers(gA.getWithProgress(index, progs.getFirst()), gB.getWithProgress(index, progs.getSecond()));
                    }
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public Integer text(GetValue<String> gA) throws InternalException, UserException
            {
                return typeB.apply(new SpecificDataTypeVisitorGet<Integer>(new UserException("Expected text type, to match left-hand side")){
                    @Override
                    @OnThread(Tag.Simulation)
                    public Integer text(GetValue<String> gB) throws InternalException, UserException
                    {
                        return gA.getWithProgress(index, progs.getFirst()).compareTo(gB.getWithProgress(index, progs.getSecond()));
                    }
                });
            }

            @Override
            @OnThread(Tag.Simulation)
            public Integer tagged(List<TagType> tagsA, GetValue<Integer> gA) throws InternalException, UserException
            {
                return typeB.apply(new SpecificDataTypeVisitorGet<Integer>(new UserException("Expected tagged type, to match left-hand side")){
                    @Override
                    @OnThread(Tag.Simulation)
                    public Integer tagged(List<TagType> tagsB, GetValue<Integer> gB) throws InternalException, UserException
                    {
                        checkSame(tagsA, tagsB);
                        int tagA = gA.getWithProgress(index, progs.getFirst());
                        int tagB = gB.getWithProgress(index, progs.getSecond());
                        if (tagA != tagB)
                            return Integer.compare(tagA, tagB);
                        DataType innerA = tagsA.get(tagA).getInner();
                        DataType innerB = tagsB.get(tagB).getInner();
                        if (innerA == null && innerB == null)
                            return 0; // No further data, it's a match
                        if (innerA == null || innerB == null)
                            throw new InternalException("Types expected equal but inner types differ");
                        return compareValueCheckType(innerA, innerB, index, prog);
                    }
                });
            }
        });
    }

    private static void checkSame(List<TagType> tagsA, List<TagType> tagsB) throws UserException
    {
        if (tagsA.size() != tagsB.size())
            throw new UserException("Types don't match; different number of tags");

        for (int i = 0; i < tagsA.size(); i++)
        {
            // This makes order matter; should it?
            if (!tagsA.get(i).getName().equals(tagsB.get(i).getName()))
                throw new UserException("Types don't match; different tag names");
        }
    }

    @Override
    public @OnThread(Tag.FXPlatform) String save()
    {
        return "(" + lhs.save() + " " + op.symbol + " " + rhs.save() + ")";
    }
}
