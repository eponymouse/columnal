package records.transformations;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import records.grammar.MainParser;
import records.grammar.MainParser.SourceNameContext;
import records.grammar.MainParser.TableContext;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Utility.DescriptiveErrorListener;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 02/11/2016.
 */
public class TransformationManager
{
    @MonotonicNonNull
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private static TransformationManager instance;

    @OnThread(Tag.Any)
    public synchronized static TransformationManager getInstance()
    {
        if (instance == null)
            instance = new TransformationManager();
        return instance;
    }

    @OnThread(Tag.Any)
    public List<TransformationInfo> getTransformations()
    {
        return Arrays.asList(
            //new SummaryStatistics.Info(),
            new Sort.Info(),
            new Filter.Info()
        );
    }

    // Not static because it needs to access the list of registered transformations
    @OnThread(Tag.Simulation)
    public Transformation loadOne(TableManager mgr, String source) throws InternalException, UserException
    {
        return Utility.parseAsOne(source, MainLexer::new, MainParser::new, parser -> loadOne(mgr, parser.table()));
    }

    @OnThread(Tag.Simulation)
    public Transformation loadOne(TableManager mgr, TableContext table) throws UserException, InternalException
    {
        try
        {
            TransformationInfo t = getTransformation(table.transformation().transformationName().getText());
            String detail = table.transformation().detail().DETAIL_LINE().stream().<String>map(TerminalNode::getText).collect(Collectors.joining(""));
            List<TableId> source = Utility.<SourceNameContext, TableId>mapList(table.transformation().sourceName(), s -> new TableId(s.item().getText()));
            Transformation transformation = t.load(mgr, new TableId(table.transformation().tableId().getText()), source, detail);
            transformation.loadPosition(table.position());
            return transformation;
        }
        catch (NullPointerException e)
        {
            throw new UserException("Could not read transformation: failed to read data", e);
        }
    }

    @OnThread(Tag.Any)
    private TransformationInfo getTransformation(String text) throws UserException
    {
        for (TransformationInfo t : getTransformations())
        {
            if (t.getName().equals(text))
                return t;
        }
        throw new UserException("Transformation not found: \"" + text + "\"");
    }
}
