package records.loadsave;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.grammar.MainLexer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Created by neil on 09/11/2016.
 */
public class OutputBuilder
{
    private final ArrayList<ArrayList<String>> lines = new ArrayList<>();
    private @Nullable ArrayList<String> curLine = null;

    private ArrayList<String> cur()
    {
        if (curLine == null)
            curLine = new ArrayList<>();
        return curLine;
    }

    // Outputs a token
    public OutputBuilder t(int token)
    {
        cur().add(stripQuotes(MainLexer.VOCABULARY.getLiteralName(token)));
        return this;
    }

    private static String stripQuotes(String quoted)
    {
        if (quoted.startsWith("'") && quoted.endsWith("'"))
            return quoted.substring(1, quoted.length() - 1);
        else
            throw new IllegalArgumentException("Could not remove quotes: <<" + quoted + ">>");
    }

    public OutputBuilder id(String id)
    {
        cur().add(id);
        return this;
    }

    public OutputBuilder path(Path path)
    {
        cur().add(quoted(path.toFile().getAbsolutePath()));
        return this;
    }

    // Add a newline
    public OutputBuilder nl()
    {
        if (curLine == null)
            curLine = new ArrayList<>();
        lines.add(curLine);
        curLine = null;
        return this;
    }


    private String quoted(String s)
    {
        // Order matters; escape ^ by itself first:
        return "\"" + s.replace("^", "^^").replace("\"", "^\"").replace("\n", "^n").replace("\r", "^r") + "\"";
    }

    @Override
    public String toString()
    {
        String finished = lines.stream().map(line -> line.stream().collect(Collectors.joining(" "))).collect(Collectors.joining("\n"));
        if (curLine != null)
            finished += curLine;
        return finished;
    }
}
