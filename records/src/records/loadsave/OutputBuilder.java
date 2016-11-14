package records.loadsave;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorGet;
import records.data.datatype.DataType.GetValue;
import records.data.datatype.DataType.NumberDisplayInfo;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;
import utility.Utility;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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

    @OnThread(Tag.Any)
    private synchronized ArrayList<String> cur()
    {
        if (curLine == null)
            curLine = new ArrayList<>();
        return curLine;
    }

    // Outputs a token
    public synchronized OutputBuilder t(int token)
    {
        cur().add(stripQuotes(MainLexer.VOCABULARY.getLiteralName(token)));
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

    public synchronized OutputBuilder id(TableId id)
    {
        return id(id.getOutput());
    }

    public synchronized OutputBuilder id(String id)
    {
        //TODO validate
        cur().add(id);
        return this;
    }

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
    private static String quoted(String s)
    {
        // Order matters; escape ^ by itself first:
        return "\"" + s.replace("^", "^^").replace("\"", "^\"").replace("\n", "^n").replace("\r", "^r") + "\"";
    }

    @Override
    public synchronized String toString()
    {
        String finished = lines.stream().map(line -> line.stream().collect(Collectors.joining(" "))).collect(Collectors.joining("\n"));
        if (curLine != null)
            finished += curLine;
        return finished;
    }

    @OnThread(Tag.Simulation)
    public synchronized void data(DataType type, int index)
    {
        Utility.alertOnError_(() -> {
            cur().add(type.apply(new DataTypeVisitorGet<String>()
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
                public String tagged(List<TagType> tagTypes, GetValue<Integer> g) throws InternalException, UserException
                {
                    TagType t = tagTypes.get(g.get(index));
                    @Nullable DataType inner = t.getInner();
                    if (inner == null)
                        return t.getName();
                    else
                        return t.getName() + ":" + inner.apply(this);
                }
            }));
        });
    }

    // Don't forget, this will get an extra space added to it
    @OnThread(Tag.Any)
    public synchronized void ws(String whiteSpace)
    {
        cur().add(whiteSpace);
    }

    public synchronized void inner(FXPlatformSupplier<List<String>> genDetail)
    {
        t(MainLexer.BEGIN).nl();
        lines.addAll(Utility.<String, List<String>>mapList(genDetail.get(), Collections::singletonList));
        t(MainLexer.END).nl();
    }
}
