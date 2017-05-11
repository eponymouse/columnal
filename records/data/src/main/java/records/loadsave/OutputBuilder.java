package records.loadsave;

import annotation.qual.Value;
import org.antlr.v4.runtime.Vocabulary;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.grammar.MainLexer;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Created by neil on 09/11/2016.
 */
@OnThread(Tag.FXPlatform)
public class OutputBuilder
{
    // Each outer item is a line; each inner item is a list of tokens to be glued together with whitespace
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private final ArrayList<List<String>> lines = new ArrayList<>();
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private @Nullable ArrayList<String> curLine = null;

    @OnThread(Tag.Any)
    public OutputBuilder()
    {

    }

    // Gets the current line, making a new one if needed
    @OnThread(Tag.Any)
    private synchronized ArrayList<String> cur()
    {
        if (curLine == null)
            curLine = new ArrayList<>();
        return curLine;
    }

    // Outputs a token from MainLexer
    @OnThread(Tag.Any)
    public synchronized OutputBuilder t(int token)
    {
        return t(token, MainLexer.VOCABULARY);
    }

    // Outputs a token from given vocab
    @OnThread(Tag.Any)
    public synchronized OutputBuilder t(int token, Vocabulary vocabulary)
    {
        String literalName = vocabulary.getLiteralName(token);
        // Awkward to throw an exception here.  Tests should pick this up.
        //if (literalName == null)
        //    throw new InternalException("Unknown token in vocabulary: " + token);
        cur().add(stripQuotes(literalName));
        return this;
    }

    @OnThread(Tag.Any)
    private static String stripQuotes(String quoted)
    {
        if (quoted.startsWith("'") && quoted.endsWith("'"))
            return quoted.substring(1, quoted.length() - 1);
        else
            throw new IllegalArgumentException("Could not remove quotes: <<" + quoted + ">>");
    }

    // Outputs a table identifier, quoted if necessary
    @OnThread(Tag.Any)
    public synchronized OutputBuilder id(TableId id)
    {
        return id(id.getOutput());
    }

    // Outputs a column identifier, quoted if necessary
    @OnThread(Tag.Any)
    public synchronized OutputBuilder id(ColumnId id)
    {
        return id(id.getOutput());
    }

    @OnThread(Tag.Any)
    public synchronized OutputBuilder quote(TypeId id)
    {
        cur().add(quoted(id.getOutput()));
        return this;
    }

    // Outputs a column identifier, quoted if necessary
    @OnThread(Tag.Any)
    public synchronized OutputBuilder quote(ColumnId id)
    {
        cur().add(quoted(id.getOutput()));
        return this;
    }

    // Outputs an identifier, quoted if necessary
    @OnThread(Tag.Any)
    public synchronized OutputBuilder id(String id)
    {
        cur().add(quotedIfNecessary(id));
        return this;
    }

    // Outputs a quoted absolute path
    @OnThread(Tag.Any)
    public synchronized OutputBuilder path(Path path)
    {
        cur().add(quoted(path.toFile().getAbsolutePath()));
        return this;
    }

    // Add a newline
    @OnThread(Tag.Any)
    public synchronized OutputBuilder nl()
    {
        if (curLine == null)
            curLine = new ArrayList<>();
        lines.add(curLine);
        curLine = null;
        return this;
    }


    @OnThread(Tag.Any)
    public static String quoted(String s)
    {
        // Order matters; escape ^ by itself first:
        return "\"" + s.replace("^", "^c").replace("\"", "^q").replace("\n", "^n").replace("\r", "^r").replace("@", "^a") + "\"";
    }

    @OnThread(Tag.Any)
    public static String quotedIfNecessary(String s)
    {
        if (GrammarUtility.validUnquoted(s))
            return s;
        else
            return quoted(s);

    }

    @OnThread(Tag.Any)
    public synchronized List<String> toLines()
    {
        ArrayList<String> finished = new ArrayList<>(Utility.<List<String>, String>mapList(lines, line -> line.stream().collect(Collectors.joining(" "))));
        if (curLine != null)
            finished.add(curLine.stream().collect(Collectors.joining(" ")));
        return finished;
    }

    @Override
    @OnThread(Tag.Any)
    public synchronized String toString()
    {
        String finished = lines.stream().map(line -> line.stream().collect(Collectors.joining(" ")) + "\n").collect(Collectors.joining());
        if (curLine != null)
            finished += curLine.stream().collect(Collectors.joining(" "));
        return finished;
    }

    // Outputs a single value
    @OnThread(Tag.Any)
    public synchronized OutputBuilder dataValue(DataType type, @Value Object value) throws UserException, InternalException
    {
        // Defeat thread checker:
        return ((ExBiFunction<DataTypeValue, Integer, OutputBuilder>)this::data).apply(type.fromCollapsed((i, prog) -> value), 0);
    }

    // Outputs an element of an entire data set
    @OnThread(Tag.Simulation)
    public synchronized OutputBuilder data(DataTypeValue type, int index) throws UserException, InternalException
    {
        cur().add(type.applyGet(new DataTypeVisitorGet<String>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public String number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                @Value @NonNull Number number = g.get(index);
                if (number instanceof BigDecimal)
                    return ((BigDecimal)number).toPlainString();
                else
                    return number.toString();
            }

            @Override
            @OnThread(Tag.Simulation)
            public String text(GetValue<@Value String> g) throws InternalException, UserException
            {
                return quoted(g.get(index));
            }

            @Override
            @OnThread(Tag.Simulation)
            public String tagged(TypeId typeName, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                TagType<DataTypeValue> t = tagTypes.get(g.get(index));
                @Nullable DataTypeValue inner = t.getInner();
                if (inner == null)
                    return "\\" + quotedIfNecessary(t.getName());
                else
                    return "\\" + quotedIfNecessary(t.getName()) + ":" + inner.applyGet(this);
            }

            @Override
            @OnThread(Tag.Simulation)
            public String bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                return g.get(index).toString();
            }

            @Override
            @OnThread(Tag.Simulation)
            public String date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                return quoted(dateTimeInfo.getFormatter().format(g.get(index)));
            }

            @Override
            @OnThread(Tag.Simulation)
            public String tuple(List<DataTypeValue> types) throws InternalException, UserException
            {
                OutputBuilder b = new OutputBuilder();
                b.raw("(");
                boolean first = true;
                for (DataTypeValue dataTypeValue : types)
                {
                    if (!first)
                        b.raw(",");
                    first = false;
                    b.data(dataTypeValue, index);
                }
                b.raw(")");
                return b.toString();
            }

            @Override
            @OnThread(Tag.Simulation)
            public String array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
            {
                OutputBuilder b = new OutputBuilder();
                b.raw("[");
                @NonNull Pair<Integer, DataTypeValue> details = g.get(index);
                for (int i = 0; i < details.getFirst(); i++)
                {
                    if (i > 0)
                        b.raw(",");
                    b.data(details.getSecond(), i);
                }
                b.raw("]");
                return b.toString();
            }
        }));
        return this;
    }

    // Don't forget, this will get an extra space added to it as spacing
    @OnThread(Tag.Any)
    public synchronized OutputBuilder ws(String whiteSpace)
    {
        cur().add(whiteSpace);
        return this;
    }

    // Outputs the set of lines between @BEGIN/@END tags
    @OnThread(Tag.Simulation)
    public synchronized void inner(Supplier<List<String>> genDetail)
    {
        begin().nl();
        for (String line : genDetail.get())
        {
            indent().raw(line).nl();
        }
        end().nl();
    }

    @OnThread(Tag.Any)
    public OutputBuilder begin()
    {
        return raw("@BEGIN");
    }

    // Outputs the given raw string
    @OnThread(Tag.Any)
    public OutputBuilder raw(String item)
    {
        cur().add(item);
        return this;
    }

    // Adds spacing at the current position
    @OnThread(Tag.Any)
    public OutputBuilder indent()
    {
        // Second space will be added after:
        return ws(" ");
    }

    @OnThread(Tag.Any)
    public OutputBuilder end()
    {
        return raw("@END");
    }

    // Outputs a number (without E-notation)
    @OnThread(Tag.Any)
    public synchronized OutputBuilder d(double number)
    {
        cur().add(String.format("%f", number));
        return this;
    }

    // Outputs an arbitrary keyword
    @OnThread(Tag.Any)
    public synchronized OutputBuilder kw(String keyword)
    {
        return raw(keyword);
    }

    @OnThread(Tag.Any)
    public synchronized void s(String string)
    {
        cur().add(quoted(string));
    }

    @OnThread(Tag.Any)
    public synchronized void unit(String s)
    {
        cur().add("{" + s + "}");
    }

    @OnThread(Tag.Any)
    public synchronized void n(long n)
    {
        cur().add(Long.toString(n));
    }
}
