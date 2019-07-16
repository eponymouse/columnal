package records.exporters.manager;

import com.google.common.collect.ImmutableList;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.apache.commons.io.FilenameUtils;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableManager;
import records.exporters.Exporter;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.TranslationUtility;
import utility.Workers;
import utility.gui.DimmableParent;
import utility.gui.ErrorableDialog;
import utility.gui.FXUtility;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@OnThread(Tag.FXPlatform)
public class ExporterManager
{
    // Singleton:
    private static final ExporterManager SINGLETON = new ExporterManager();

    private final List<Exporter> registeredExporters = new ArrayList<>();

    public void registerExporter(Exporter Exporter)
    {
        registeredExporters.add(Exporter);
    }

    public void chooseAndExportFile(DimmableParent parent, Table table)
    {
        new PickExporterDialog().showAndWait().ifPresent(exporter -> {
            File file = FXUtility.getFileSaveAs(parent);
            if (file != null)
            {
                final File fileNonNull = file;
                Workers.onWorkerThread("Export to " + file.getAbsolutePath(), Workers.Priority.SAVE, () -> FXUtility.alertOnError_("Error exporting", () -> exporter.exportData(fileNonNull, table.getData())));
            }
        });
    }

    // To avoid checker framework bug:
    private static Stream<? extends String> streamSecond(Pair<String, ImmutableList<String>> p)
    {
        return p.getSecond().stream();
    }


    public static ExporterManager getInstance()
    {
        return SINGLETON;
    }

    private class PickExporterPane extends BorderPane
    {
        private final ListView<Exporter> exporterList;

        public PickExporterPane()
        {
            this.exporterList = new ListView<>(FXCollections.observableArrayList(registeredExporters));
            exporterList.setCellFactory(lv -> {
                return new ListCell<Exporter>() {
                    @Override
                    protected void updateItem(@Nullable Exporter item, boolean empty)
                    {
                        super.updateItem(item, empty);
                        if (item != null && !empty)
                        {
                            setText(item.getName() + "\n" + item.getSupportedFileTypes().stream().map((Pair<@Localized String, ImmutableList<String>> p) -> p.getFirst() + "(" + p.getSecond().stream().collect(Collectors.joining(", ")) + ")").collect(Collectors.joining("; ")));
                        }
                    }
                };
            });
            exporterList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            exporterList.getSelectionModel().selectFirst();
            setCenter(exporterList);
        }

        public @Nullable Exporter get()
        {
            return exporterList.getSelectionModel().getSelectedItem();
        }
    }

    @OnThread(Tag.FXPlatform)
    private class PickExporterDialog extends ErrorableDialog<Exporter>
    {
        private final PickExporterPane pickExporterPane;

        public PickExporterDialog()
        {
            pickExporterPane = new PickExporterPane();
            getDialogPane().setContent(new VBox(new Label("Pick Exporter"), pickExporterPane, getErrorLabel()));

        }

        @Override
        protected Either<@Localized String, Exporter> calculateResult()
        {
            @Nullable Exporter sel = pickExporterPane.get();
            if (sel != null)
                return Either.right(sel);
            else
                return Either.left(TranslationUtility.getString("exporter.error.nopick"));
        }
    }
}