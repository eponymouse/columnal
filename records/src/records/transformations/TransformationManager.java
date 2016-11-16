package records.transformations;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import records.grammar.MainParser;
import records.grammar.MainParser.PositionContext;
import records.grammar.MainParser.TableContext;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

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
            new SummaryStatistics.Info(),
            new Sort.Info()
        );
    }

    private static class DescriptiveErrorListener extends BaseErrorListener
    {
        public static DescriptiveErrorListener INSTANCE = new DescriptiveErrorListener();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine,
                                String msg, RecognitionException e)
        {
            String sourceName = recognizer.getInputStream().getSourceName();
            if (!sourceName.isEmpty()) {
                sourceName = String.format("%s:%d:%d: ", sourceName, line, charPositionInLine);
            }

            System.err.println(sourceName+"line "+line+":"+charPositionInLine+" "+msg);
        }
    }

    @OnThread(Tag.Simulation)
    public Transformation loadOne(TableManager mgr, String source) throws InternalException, UserException
    {
        try
        {
            MainParser parser = Utility.parseAsOne(source, s -> {
                Lexer l = new MainLexer(s);
                l.removeErrorListeners();
                l.addErrorListener(DescriptiveErrorListener.INSTANCE);
                return l;
            }, MainParser::new);
            parser.removeErrorListeners();
            parser.addErrorListener(DescriptiveErrorListener.INSTANCE);
            return load(mgr, parser.table());
        }
        catch (ParseCancellationException ex)
        {
            if (ex.getCause() instanceof RecognitionException)
            {
                RecognitionException re = (RecognitionException)ex.getCause();
            }
            throw ex;
        }
    }

    @OnThread(Tag.Simulation)
    private Transformation load(TableManager mgr, TableContext table) throws UserException, InternalException
    {
        TransformationInfo t = getTransformation(table.transformation().transformationName().getText());
        Transformation transformation = t.load(mgr, new TableId(table.transformation().tableId().getText()), table.transformation().detail().DETAIL_LINE().stream().<String>map(TerminalNode::getText).collect(Collectors.joining("")));
        transformation.loadPosition(table.position());
        return transformation;
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
