package records.importers;

import annotation.units.GridAreaColIndex;
import annotation.units.GridAreaRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Window;
import log.Log;
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
import records.gui.grid.GridAreaCellPosition;
import records.importers.GuessFormat.Import;
import records.importers.GuessFormat.ImportInfo;
import records.importers.ImportPlainTable.PlainImportInfo;
import records.importers.base.Importer;
import records.importers.gui.ImportChoicesDialog;
import utility.FXPlatformConsumer;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.SimulationConsumer;
import utility.SimulationSupplier;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.UnitType;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by neil on 31/10/2016.
 */
public class HTMLImporter implements Importer
{
    // Gives back an HTML document to display, and a function which takes a table index then imports that.
    @OnThread(Tag.Simulation)
    public static Pair<Document, FXPlatformConsumer<Integer>> importHTMLFile(
        @Nullable Window parentWindow, TableManager mgr, File htmlFile, URL source, CellPosition destination, SimulationConsumer<ImmutableList<DataSource>> withDataSources) throws IOException
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
            importTable(parentWindow, mgr, htmlFile, destination, withDataSources, results, tables, tableIndex);
        };
        
        return new Pair<>(doc, importTable);
    }

    @OnThread(Tag.FXPlatform)
    protected static void importTable(@Nullable Window parentWindow, TableManager mgr, File htmlFile, CellPosition destination, SimulationConsumer<ImmutableList<DataSource>> withDataSources, ArrayList<FXPlatformSupplier<@Nullable SimulationSupplier<DataSource>>> results, Elements tables, Integer tableIndex)
    {
        Element table = tables.get(tableIndex);
        // vals is a list of rows:
        final List<ArrayList<String>> vals = new ArrayList<>();

        // Maps position to pending item.  Abusing GridAreaCellPosition a little: it means table position here
        final Map<GridAreaCellPosition, String> pendingSpanItems = new HashMap<>();

        @SuppressWarnings("units")
        final @GridAreaRowIndex int ROW = 1;
        @SuppressWarnings("units")
        final @GridAreaColIndex int COL = 1;

        for (Element tableBit : table.children())
        {
            if (!tableBit.tagName().equals("tbody"))
                continue;

            Elements tableChildren = tableBit.children();
            @SuppressWarnings("units")
            @GridAreaRowIndex int rowIndex = 0;
            for (Element row : tableChildren)
            {
                if (!row.tagName().equals("tr"))
                    continue;
                ArrayList<String> rowVals = new ArrayList<>();
                vals.add(rowVals);
                Elements children = row.children();
                @SuppressWarnings("units")
                @GridAreaColIndex int columnIndex = 0;
                GridAreaCellPosition nextPos = new GridAreaCellPosition(rowIndex, columnIndex);
                while (pendingSpanItems.containsKey(nextPos))
                {
                    rowVals.add(pendingSpanItems.get(nextPos));
                    columnIndex += 1 * COL;
                    nextPos = new GridAreaCellPosition(rowIndex, columnIndex);
                }

                for (Element cell : children)
                {
                    if (!cell.tagName().equals("td") && !cell.tagName().equals("th"))
                        continue;
                    rowVals.add(cell.wholeText());
                    int rowSpan = 1;
                    int colSpan = 1;
                    if (cell.hasAttr("colspan"))
                    {
                        try
                        {
                            colSpan = Integer.valueOf(cell.attr("colspan"));
                        }
                        catch (NumberFormatException e)
                        {
                            Log.log(e);
                            // Leave it at 1
                        }
                    }
                    if (cell.hasAttr("rowspan"))
                    {
                        try
                        {
                            rowSpan = Integer.valueOf(cell.attr("rowspan"));
                        }
                        catch (NumberFormatException e)
                        {
                            Log.log(e);
                            // Leave it at 1
                        }
                    }
                    // add to current row (though it will just be removed by while loop beneath):
                    if (colSpan > 1)
                    {
                        for (@GridAreaColIndex int extraCol = 1 * COL; extraCol < colSpan; extraCol += 1 * COL)
                        {
                            pendingSpanItems.put(new GridAreaCellPosition(rowIndex, columnIndex + extraCol), cell.text());
                        }
                    }
                    // Add to future rows:
                    if (rowSpan > 1)
                    {
                        for (@GridAreaRowIndex int extraRow = 1 * ROW; extraRow < rowSpan; extraRow += 1 * ROW)
                        {
                            for (@GridAreaColIndex int extraCol = 0 * COL; extraCol < colSpan; extraCol += 1 * COL)
                            {
                                pendingSpanItems.put(new GridAreaCellPosition(rowIndex + extraRow, columnIndex + extraCol), cell.text());
                            }
                        }
                    }

                    nextPos = new GridAreaCellPosition(rowIndex, columnIndex);
                    while (pendingSpanItems.containsKey(nextPos))
                    {
                        rowVals.add(pendingSpanItems.get(nextPos));
                        columnIndex += 1 * COL;
                        nextPos = new GridAreaCellPosition(rowIndex, columnIndex);
                    }
                }

                rowIndex += 1 * ROW;
            }
        }

        ImporterUtility.rectangulariseAndRemoveBlankRows(vals);

        Import<UnitType, PlainImportInfo> imp = new ImportPlainTable(vals.isEmpty() ? 0 : vals.get(0).size(), mgr, vals)
        {
            @Override
            public ColumnId srcColumnName(int index)
            {
                return new ColumnId("C" + (index + 1));
            }
        };

        results.add(() -> {
            @Nullable ImportInfo<PlainImportInfo> outcome = new ImportChoicesDialog<>(parentWindow, htmlFile.getName(), imp).showAndWait().orElse(null);

            if (outcome != null)
            {
                @NonNull ImportInfo<PlainImportInfo> outcomeNonNull = outcome;
                SimulationSupplier<DataSource> makeDataSource = () -> new ImmediateDataSource(mgr, outcomeNonNull.getInitialLoadDetails(destination), ImporterUtility.makeEditableRecordSet(mgr.getTypeManager(), outcomeNonNull.getFormat().trim.trim(vals), outcomeNonNull.getFormat().columnInfo));
                return makeDataSource;
            } else
                return null;
        });

        List<SimulationSupplier<DataSource>> sources = results.stream().flatMap((FXPlatformSupplier<@Nullable SimulationSupplier<DataSource>> s) -> Utility.streamNullable(s.get())).collect(Collectors.toList());
        Workers.onWorkerThread("Loading HTML", Priority.LOAD_FROM_DISK, () -> FXUtility.alertOnError_("Error loading HTML", () -> withDataSources.consume(Utility.mapListExI(sources, s -> s.get()))));
    }

    @OnThread(Tag.Simulation)
    private static void importHTMLFileThen(Window parentWindow, TableManager mgr, File htmlFile, CellPosition destination, URL source, SimulationConsumer<ImmutableList<DataSource>> withDataSources) throws IOException, InternalException, UserException
    {
        Pair<Document, FXPlatformConsumer<Integer>> p = importHTMLFile(parentWindow, mgr, htmlFile, source, destination, withDataSources);

        Platform.runLater(() -> {
            new PickHTMLTableDialog(parentWindow, p.getFirst()).showAndWait().ifPresent(n -> p.getSecond().consume(n));
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
    public @OnThread(Tag.FXPlatform) void importFile(Window parent, TableManager tableManager, CellPosition destination, File src, URL origin, FXPlatformConsumer<DataSource> onLoad)
    {
        Workers.onWorkerThread("Importing HTML", Priority.LOAD_FROM_DISK, () -> FXUtility.alertOnError_("Error importing HTML", () -> {
            try
            {
                importHTMLFileThen(parent, tableManager, src, destination, origin, dataSources -> {
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
    
    @OnThread(Tag.FXPlatform)
    private static class PickHTMLTableDialog extends Dialog<Integer>
    {
        public PickHTMLTableDialog(Window parent, Document doc)
        {
            initOwner(parent);
            initModality(Modality.WINDOW_MODAL);
            getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
            setResizable(true);
            getDialogPane().getStylesheets().addAll(
                FXUtility.getStylesheet("general.css"),
                FXUtility.getStylesheet("dialogs.css")
            );
            getDialogPane().getStyleClass().add("pick-html-table-dialog-pane");
            // To allow cancel to work:
            setResultConverter(bt -> null);
            
            WebView webView = new WebView();
            Node instruction = new Text("Click any red-bordered table to import it.");
            instruction.getStyleClass().add("pick-html-table-instruction");
            TextFlow textFlow = new TextFlow(instruction);
            textFlow.setTextAlignment(TextAlignment.CENTER);
            BorderPane.setAlignment(textFlow, Pos.CENTER);
            BorderPane borderPane = new BorderPane(webView, textFlow, null, null, null);
            BorderPane.setMargin(webView, new Insets(10, 0, 0, 0));
            getDialogPane().setContent(borderPane);
            FXUtility.addChangeListenerPlatform(webView.getEngine().documentProperty(), webViewDoc -> enableGUIImportLinks(webViewDoc, n -> setResult(n)));
            webView.getEngine().loadContent(doc.html());

            //FXUtility.onceNotNull(getDialogPane().sceneProperty(), org.scenicview.ScenicView::show);
        }    
    }
}
