package records.importers;

import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import records.data.*;
import records.importers.GuessFormat.ImportInfo;
import records.importers.base.Importer;
import records.importers.gui.ImportChoicesDialog;
import records.importers.gui.ImportChoicesDialog.SourceInfo;
import utility.FXPlatformConsumer;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.SimulationConsumer;
import utility.SimulationFunction;
import utility.SimulationSupplier;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 31/10/2016.
 */
public class HTMLImporter implements Importer
{
    @OnThread(Tag.Simulation)
    private static void importHTMLFileThen(TableManager mgr, File htmlFile, SimulationConsumer<ImmutableList<DataSource>> withDataSources) throws IOException, InternalException, UserException
    {
        ArrayList<FXPlatformSupplier<@Nullable SimulationSupplier<DataSource>>> results = new ArrayList<>();
        Document doc = parse(htmlFile);
        Elements tables = doc.select("table");

        for (Element table : tables)
        {
            // TODO pick the header section out for column titles
            // vals is a list of rows:
            final List<List<String>> vals = new ArrayList<>();
            for (Element tableBit : table.children())
            {
                if (!tableBit.tagName().equals("tbody"))
                    continue;

                for (Element row : tableBit.children())
                {
                    if (!row.tagName().equals("tr"))
                        continue;
                    List<String> rowVals = new ArrayList<>();
                    vals.add(rowVals);
                    for (Element cell : row.children())
                    {
                        if (!cell.tagName().equals("td"))
                            continue;
                        rowVals.add(cell.text());
                    }
                }
            }

            SourceInfo sourceInfo = ImporterUtility.makeSourceInfo(vals);

            // TODO show a dialog
            SimulationFunction<Format, EditableRecordSet> loadData = format -> {
                return ImporterUtility.makeEditableRecordSet(mgr, vals, format);
                // Make sure we don't keep a reference to vals:
                // Not because we null it, but because we make it non-final.
                //results.add(new ImmediateDataSource(mgr, new EditableRecordSet(columns, () -> len)));
            };
            results.add(() -> {
                @Nullable Pair<ImportInfo, Format> outcome = new ImportChoicesDialog<>(mgr, htmlFile.getName(), GuessFormat.guessGeneralFormat(mgr.getUnitManager(), vals), loadData, c -> sourceInfo).showAndWait().orElse(null);

                if (outcome != null)
                {
                    @NonNull Pair<ImportInfo, Format> outcomeNonNull = outcome;
                    SimulationSupplier<DataSource> makeDataSource = () -> new ImmediateDataSource(mgr, loadData.apply(outcomeNonNull.getSecond()));
                    return makeDataSource;
                }
                else
                    return null;
            });
        }
        Platform.runLater(() -> {
            List<SimulationSupplier<DataSource>> sources = results.stream().flatMap((FXPlatformSupplier<@Nullable SimulationSupplier<DataSource>> s) -> Utility.streamNullable(s.get())).collect(Collectors.toList());
            Workers.onWorkerThread("Loading HTML", Priority.LOAD_FROM_DISK, () -> Utility.alertOnError_(() -> withDataSources.consume(Utility.mapListExI(sources, s -> s.get()))));
        });
    }

    @SuppressWarnings("nullness")
    private static Document parse(File htmlFile) throws IOException
    {
        return Jsoup.parse(htmlFile, null);
    }

    @Override
    public @Localized String getName()
    {
        return "HTML Import";
    }

    @Override
    public @OnThread(Tag.Any) ImmutableList<Pair<@Localized String, ImmutableList<String>>> getSupportedFileTypes()
    {
        return ImmutableList.of(new Pair<>("HTML", ImmutableList.of("*.html", "*.htm")));
    }

    @Override
    public @OnThread(Tag.FXPlatform) void importFile(Window parent, TableManager tableManager, File src, FXPlatformConsumer<DataSource> onLoad)
    {
        Workers.onWorkerThread("Importing HTML", Priority.LOAD_FROM_DISK, () -> Utility.alertOnError_(() -> {
            try
            {
                importHTMLFileThen(tableManager, src, dataSources -> {
                    Platform.runLater(() -> {
                        for (DataSource dataSource : dataSources)
                        {
                            onLoad.consume(dataSource);
                        }
                    });
                });
            }
            catch (IOException e)
            {
                throw new UserException("IO Error", e);
            }
        }));
    }
}
