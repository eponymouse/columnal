package records.rinterop;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;

import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RPrettyPrint
{
    public static String prettyPrint(RValue rValue) throws UserException, InternalException
    {
        StringBuilder b = new StringBuilder();
        prettyPrint(rValue, b, "");
        return b.toString();
    }

    private static void prettyPrint(RValue rValue, StringBuilder b, String indent) throws UserException, InternalException
    {
        rValue.visit(new RVisitor<@Nullable Void>() {
            @Override
            public @Nullable Void visitNil() throws InternalException, UserException
            {
                b.append("<nil>");
                return null;
            }

            @Override
            public @Nullable Void visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
            {
                b.append((isSymbol ? "symb" : "") + "\"" + s + "\"");
                return null;
            }

            @Override
            public @Nullable Void visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException
            {
                b.append("boolean[" + values.length + (isNA != null ? "?" : ""));
                if (attributes != null)
                {
                    b.append(", attr=");
                    attributes.visit(this);
                }
                b.append("]");
                return null;
            }

            @Override
            public @Nullable Void visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                b.append("int[" + values.length + ":" + limited(Ints.asList(values)));
                if (attributes != null)
                {
                    b.append(", attr=");
                    attributes.visit(this);
                }
                b.append("]");
                return null;
            }

            @Override
            public @Nullable Void visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                b.append("double[" + values.length);
                if (attributes != null)
                {
                    b.append(", attr=");
                    attributes.visit(this);
                }
                b.append("]");
                return null;
            }

            @Override
            public @Nullable Void visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                b.append("String[" + values.size() + ": "+ limited(values));
                if (attributes != null)
                {
                    b.append(", attr=");
                    attributes.visit(this);
                }
                b.append("]");
                return null;
            }

            private String limited(List<?> values)
            {
                return values.stream().limit(3).map(o -> "" + o).collect(Collectors.joining(","));
            }

            @Override
            public @Nullable Void visitTemporalList(DateTimeType dateTimeType, ImmutableList<Optional<@Value TemporalAccessor>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                b.append(dateTimeType.toString() + "[" + values.size());
                if (attributes != null)
                {
                    b.append(", attr=");
                    attributes.visit(this);
                }
                b.append("]");
                return null;
            }

            @Override
            public @Nullable Void visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
            {
                b.append(isObject ? "object{\n" : "generic{\n");
                for (RValue value : values)
                {
                    b.append(indent);
                    prettyPrint(value, b, indent + "  ");
                    b.append(",\n");
                }
                if (attributes != null)
                {
                    b.append(indent);
                    b.append("attr=");
                    attributes.visit(this);
                    b.append("\n");
                }
                b.append(indent);
                b.append("}");
                return null;
            }

            @Override
            public @Nullable Void visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                b.append("pair{");
                for (PairListEntry value : items)
                {
                    if (value.attributes != null)
                    {
                        value.attributes.visit(this);
                        b.append(" -> ");
                    }
                    if (value.tag != null)
                    {
                        b.append("[");
                        value.tag.visit(this);
                        b.append("]@");
                    }
                    value.item.visit(this);
                    b.append(", ");
                }
                b.append("}");
                return null;
            }

            @Override
            public @Nullable Void visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                b.append("factor[" + values.length + ", levels=" + levelNames.stream().collect(Collectors.joining(",")) + "]");
                return null;
            }
        });
    }
}
