package records.importers;

import annotation.qual.Value;
import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import org.antlr.v4.runtime.Parser;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.DataSource;
import records.data.DataSource.LoadedFormat;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.MainLexer;
import records.grammar.MainParser;
import records.grammar.MainParser.IsolatedValuesContext;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ClipboardUtils
{

    public static final DataFormat DATA_FORMAT = new DataFormat("application/records");

    // Returns column-major, i.e. a list of columns
    @OnThread(Tag.FXPlatform)
    public static Optional<List<List<@Value Object>>> loadValuesFromClipboard(TypeManager typeManager)
    {
        @Nullable Object content = Clipboard.getSystemClipboard().getContent(DATA_FORMAT);

        if (content == null)
            return Optional.empty();
        else
        {
            try
            {
                return Optional.of(Utility.<List<List<@Value Object>>, MainParser>parseAsOne(content.toString(), MainLexer::new, MainParser::new, p -> loadIsolatedValues(p, typeManager)));
            }
            catch (UserException e)
            {
                Utility.showError(e);
            }
            catch (InternalException e)
            {
                Utility.log(e);
            }
            return Optional.empty();
        }
    }

    private static List<List<@Value Object>> loadIsolatedValues(MainParser main, TypeManager typeManager) throws InternalException, UserException
    {
        IsolatedValuesContext ctx = main.isolatedValues();
        // TODO check that units, types match
        List<LoadedFormat> format = DataSource.loadFormat(typeManager, ctx.dataFormat(), false);
        List<List<@Value Object>> cols = new ArrayList<>();
        for (int i = 0; i < format.size(); i++)
        {
            cols.add(new ArrayList<>());
        }
        Utility.loadData(ctx.values().detail(), p -> {
            for (int i = 0; i < format.size(); i++)
            {
                LoadedFormat colFormat = format.get(i);
                cols.get(i).add(DataType.loadSingleItem(colFormat.dataType, p, false));
            }
        });
        return cols;
    }

    @OnThread(Tag.FXPlatform)
    public static void copyValuesToClipboard(UnitManager unitManager, TypeManager typeManager, List<Column> columns)
    {
        if (columns.isEmpty())
            return;

        Workers.onWorkerThread("Copying to clipboard", Priority.FETCH, () -> {
            OutputBuilder b = new OutputBuilder();
            b.t(MainLexer.UNITS).begin().nl();
            b.end().t(MainLexer.UNITS).nl();
            b.t(MainLexer.TYPES).begin().nl();
            // TODO Utility.alertOnError_(() -> b.raw(unitManager.save()));
            Utility.alertOnError_(() -> b.raw(typeManager.save()).nl());
            b.end().t(MainLexer.TYPES).nl();
            b.t(MainLexer.FORMAT).begin().nl();
            Utility.alertOnError_(() ->
            {
                for (Column c : columns)
                {
                    b.t(FormatLexer.COLUMN, FormatLexer.VOCABULARY).quote(c.getName());
                    c.getType().save(b, false);
                    b.nl();
                }
            });
            b.end().t(MainLexer.FORMAT).nl();
            RecordSet data = columns.get(0).getRecordSet();
            Utility.alertOnError_(() -> {
                b.t(MainLexer.VALUES).begin().nl();
                for (int i = 0; data.indexValid(i); i++)
                {
                    b.indent();
                    for (Column c : data.getColumns())
                        b.data(c.getType(), i);
                    b.nl();
                }
            });
            b.end().t(MainLexer.VALUES).nl();
            String str = b.toString();
            // TODO also copy a text version which will paste into Excel (and Word?)
            Map<DataFormat, Object> copyData = new HashMap<>();
            copyData.put(DATA_FORMAT, str);
            // For debugging:
            copyData.put(DataFormat.PLAIN_TEXT, str);
            System.out.println("Copying: {{{\n" + str + "\n}}}");
            Platform.runLater(() -> Clipboard.getSystemClipboard().setContent(copyData));
        });
    }
}
