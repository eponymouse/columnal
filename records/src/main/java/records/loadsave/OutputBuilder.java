package records.loadsave;

import org.antlr.v4.runtime.Vocabulary;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.TableId;
import records.data.datatype.DataType.NumberDisplayInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;
import utility.Utility;

import java.nio.file.Path;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;
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
        cur().add(stripQuotes(vocabulary.getLiteralName(token)));
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
        return "\"" + s.replace("^", "^^").replace("\"", "^\"").replace("\n", "^n").replace("\r", "^r") + "\"";
    }

    @OnThread(Tag.Any)
    public static String quotedIfNecessary(String s)
    {
        if (Utility.validUnquoted(s))
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
    public synchronized String toString()
    {
        String finished = lines.stream().map(line -> line.stream().collect(Collectors.joining(" ")) + "\n").collect(Collectors.joining());
        if (curLine != null)
            finished += curLine;
        return finished;
    }

    // Outputs an element of an entire data set
    @OnThread(Tag.Simulation)
    public synchronized void data(DataTypeValue type, int index) throws UserException, InternalException
    {
            cur().add(type.applyGet(new DataTypeVisitorGet<String>()
            {
                @Override
                public String number(GetValue<Number> g, NumberDisplayInfo displayInfo) throws InternalException, UserException
                {
                    return g.get(index).toString();
                }

                @Override
                public String text(GetValue<String> g) throws InternalException, UserException
                {
                    return quoted(g.get(index));
                }

                @Override
                public String tagged(List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
                {
                    TagType<DataTypeValue> t = tagTypes.get(g.get(index));
                    @Nullable DataTypeValue inner = t.getInner();
                    if (inner == null)
                        return "\\" + quotedIfNecessary(t.getName());
                    else
                        return "\\" + quotedIfNecessary(t.getName()) + ":" + inner.applyGet(this);
                }

                @Override
                public String bool(GetValue<Boolean> g) throws InternalException, UserException
                {
                    return g.get(index).toString();
                }

                @Override
                public String date(GetValue<Temporal> g) throws InternalException, UserException
                {
                    return quoted(g.get(index).toString());
                }
            }));
    }

    // Don't forget, this will get an extra space added to it as spacing
    @OnThread(Tag.Any)
    public synchronized OutputBuilder ws(String whiteSpace)
    {
        cur().add(whiteSpace);
        return this;
    }

    // Outputs the set of lines between @BEGIN/@END tags
    @OnThread(Tag.FXPlatform)
    public synchronized void inner(FXPlatformSupplier<List<String>> genDetail)
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
    private OutputBuilder raw(String item)
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
