package xyz.columnal.transformations;

import com.google.common.collect.ImmutableList;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import xyz.columnal.data.SaveTag;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.TableManager.TransformationLoader;
import xyz.columnal.data.Transformation;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.grammar.DisplayLexer;
import records.grammar.DisplayParser;
import records.grammar.MainLexer;
import records.grammar.MainParser;
import records.grammar.MainParser.DetailContext;
import records.grammar.MainParser.DetailPrefixedContext;
import records.grammar.MainParser.SourceNameContext;
import records.grammar.MainParser.TableContext;
import records.grammar.MainParser.TableIdContext;
import records.grammar.MainParser.TransformationContext;
import records.grammar.MainParser.TransformationNameContext;
import records.grammar.TableParser2;
import records.grammar.TableParser2.TableTransformationContext;
import records.grammar.Versions.ExpressionVersion;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 02/11/2016.
 */
public class TransformationManager implements TransformationLoader
{
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private static @MonotonicNonNull TransformationManager instance;

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
        // Note: the order here is the order they are shown in the transformation edit dialog,
        // but is otherwise unimportant.
        return ImmutableList.of(
            new Calculate.Info(),
            new Aggregate.Info(),
            new Filter.Info(),
            new Sort.Info(),
            new Join.Info(),
            new Concatenate.Info(),
            new HideColumns.Info(),
            new ManualEdit.Info(),
            new RTransformation.Info(),
                
            // Not shown in dialog, as shown separately:
            new Check.Info()
        );
    }

    @OnThread(Tag.Simulation)
    @Override
    public Transformation loadOne(TableManager mgr, TableContext table, ExpressionVersion expressionVersion) throws UserException, InternalException
    {
        try
        {
            TransformationContext transformationContext = table.transformation();
            TransformationNameContext transformationName = transformationContext.transformationName();
            TransformationInfo t = getTransformation(transformationName.getText());
            DetailPrefixedContext detailContext = transformationContext.detailPrefixed();
            String detail = Utility.getDetail(detailContext);
            @SuppressWarnings("identifier")
            List<TableId> source = Utility.<SourceNameContext, TableId>mapList(transformationContext.sourceName(), s -> new TableId(s.item().getText()));
            TableIdContext tableIdContext = transformationContext.tableId();
            @SuppressWarnings("identifier")
            Transformation transformation = t.load(mgr, Table.loadDetails(new TableId(tableIdContext.getText()), detailContext, table.display()), source, detail, expressionVersion);
            mgr.record(transformation);
            return transformation;
        }
        catch (NullPointerException e)
        {
            throw new UserException("Could not read transformation: failed to read data", e);
        }
    }

    @Override
    @OnThread(Tag.Simulation)
    public Transformation loadOne(TableManager mgr, SaveTag saveTag, TableTransformationContext table, ExpressionVersion expressionVersion) throws UserException, InternalException
    {
        try
        {
            TableParser2.TransformationContext transformationContext = table.transformation();
            TableParser2.TransformationNameContext transformationName = transformationContext.transformationName();
            TransformationInfo t = getTransformation(transformationName.getText());
            TableParser2.DetailContext detailContext = transformationContext.detail();
            String detail = Utility.getDetail(detailContext);
            @SuppressWarnings("identifier")
            List<TableId> source = Utility.<TableParser2.SourceNameContext, TableId>mapList(transformationContext.sourceName(), s -> new TableId(s.item().getText()));
            TableParser2.TableIdContext tableIdContext = transformationContext.tableId();
            @SuppressWarnings("identifier")
            Transformation transformation = Utility.parseAsOne(Utility.getDetail(table.display().detail()), DisplayLexer::new, DisplayParser::new, p ->
                t.load(mgr, Table.loadDetails(new TableId(tableIdContext.getText()), saveTag, p.tableDisplayDetails()), source, detail, expressionVersion)
            );
            mgr.record(transformation);
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
            if (t.getCanonicalName().equals(text))
                return t;
        }
        throw new UserException("Transformation not found: \"" + text + "\"");
    }
}
