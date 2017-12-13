package records.importers;

import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.EventTarget;
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
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 31/10/2016.
 */
public class HTMLImporter implements Importer
{
    @OnThread(Tag.Simulation)
    private static void importHTMLFileThen(TableManager mgr, File htmlFile, URL source, SimulationConsumer<ImmutableList<DataSource>> withDataSources) throws IOException, InternalException, UserException
    {
        ArrayList<FXPlatformSupplier<@Nullable SimulationSupplier<DataSource>>> results = new ArrayList<>();
        Document doc = parse(htmlFile);
        URL parent = source;
        try
        {
            parent = (source.getPath().endsWith("/") ? source.toURI().resolve("..") : source.toURI().resolve(".")).toURL();
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
        doc.head().prepend("<base href=\"" + parent.toExternalForm() + "\">");
        doc.head().append("<link rel=\"stylesheet\" href=\"" + FXUtility.getStylesheet("htmlimport.css") + "\">");
        Elements tables = doc.select("table");

        for (int i = 0; i < tables.size(); i++)
        {
            Element table = tables.get(i);
            // Exclude nested tables (TODO have tickbox controlling this)
            if (table.children().stream().allMatch(e -> e.select("table").isEmpty()))
            {
                table.prepend("<div class='overlay'></div>").wrap("<a class='gui_import' style='' href='gui_import:id" + i + "'></a>");
            }
        }
        
        FXPlatformConsumer<Integer> importTable = tableIndex -> {
            Element table = tables.get(tableIndex);
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
                        if (!cell.tagName().equals("td") && !cell.tagName().equals("th"))
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
                } else
                    return null;
            });
            
            List<SimulationSupplier<DataSource>> sources = results.stream().flatMap((FXPlatformSupplier<@Nullable SimulationSupplier<DataSource>> s) -> Utility.streamNullable(s.get())).collect(Collectors.toList());
            Workers.onWorkerThread("Loading HTML", Priority.LOAD_FROM_DISK, () -> Utility.alertOnError_(() -> withDataSources.consume(Utility.mapListExI(sources, s -> s.get()))));
        };

        Platform.runLater(() -> {
            Stage s = new Stage();
            WebView webView = new WebView();
            Label instruction = new Label("Click any red-bordered table to import it.");
            BorderPane borderPane = new BorderPane(webView, instruction, null, new Button("Done"), null);
            s.setScene(new Scene(borderPane));
            FXUtility.addChangeListenerPlatform(webView.getEngine().documentProperty(), webViewDoc -> enableGUIImportLinks(webViewDoc, importTable));
            webView.getEngine().loadContent(doc.html());
            s.show();
        });
    }
    
    @OnThread(Tag.FXPlatform)
    private static void enableGUIImportLinks(org.w3c.dom.@Nullable Document doc, FXPlatformConsumer<Integer> importTable)
    {
        if (doc != null)
        {
            // First find the anchors.
            NodeList anchors = doc.getElementsByTagName("a");
            for (int i = 0; i < anchors.getLength(); i++)
            {
                org.w3c.dom.Node anchorItem = anchors.item(i);
                if (anchorItem == null || anchorItem.getAttributes() == null)
                    continue;
                
                org.w3c.dom.Node anchorHref = anchorItem.getAttributes().getNamedItem("href");
                if (anchorHref != null && anchorHref.getNodeValue() != null)
                {
                    String href = anchorHref.getNodeValue();
                    if (href.startsWith("gui_import:id"))
                    {
                        ((EventTarget) anchorItem).addEventListener("click", e ->
                        {
                            int tableNum = Integer.parseInt(href.substring("gui_import:id".length()).trim());
                            importTable.consume(tableNum);
                            e.stopPropagation();
                        }, true);
                    }
                }
            }
        }
    }

    @SuppressWarnings("nullness")
    private static Document parse(File htmlFile) throws IOException
    {
        return Jsoup.parse(htmlFile, null);
    }

    @Override
    public @Localized String getName()
    {
        return TranslationUtility.getString("importer.html.files");
    }

    @Override
    public @OnThread(Tag.Any) ImmutableList<Pair<@Localized String, ImmutableList<String>>> getSupportedFileTypes()
    {
        return ImmutableList.of(new Pair<@Localized String, ImmutableList<String>>(TranslationUtility.getString("importer.html.files"), ImmutableList.of("*.html", "*.htm")));
    }

    @Override
    public @OnThread(Tag.FXPlatform) void importFile(Window parent, TableManager tableManager, File src, URL origin, FXPlatformConsumer<DataSource> onLoad)
    {
        Workers.onWorkerThread("Importing HTML", Priority.LOAD_FROM_DISK, () -> Utility.alertOnError_(() -> {
            try
            {
                importHTMLFileThen(tableManager, src, origin, dataSources -> {
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
