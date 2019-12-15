package records.importers;

import com.google.common.collect.ImmutableList;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.Localized;
import records.data.CellPosition;
import records.data.DataSource;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.TableManager;
import records.error.InternalException;
import records.error.UserException;
import records.rinterop.ConvertFromR;
import records.rinterop.RRead;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.IdentifierUtility;
import utility.Pair;
import utility.SimulationConsumerNoError;
import utility.TranslationUtility;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.DialogPaneWithSideButtons;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.Random;

public class RImporter implements Importer
{
    @Override
    public ImmutableList<String> getSupportedFileTypes()
    {
        return ImmutableList.of("*.rds", "*.Rdata");
    }

    @Override
    public void importFile(Window parent, TableManager tableManager, CellPosition destPosition, File src, URL origin, SimulationConsumerNoError<DataSource> onLoad)
    {
        Workers.onWorkerThread("Loading " + src + " " + origin, Priority.LOAD_FROM_DISK, () -> {
            try
            {
                ImmutableList<Pair<String, EditableRecordSet>> tables = ConvertFromR.convertRToTable(tableManager.getTypeManager(), RRead.readRData(src), true);
    
                switch (tables.size())
                {
                    case 0:
                        throw new UserException("No tables found in file");
                    case 1:
                        register(tableManager, destPosition, tables.iterator(), onLoad);
                        break;
                    default:
                        FXUtility.runFX(() -> {
                            ImmutableList<Pair<String, EditableRecordSet>> picked = new PickImportsDialog(parent, tables).showAndWait().orElse(ImmutableList.of());
                            if (!picked.isEmpty())
                            {
                                Workers.onWorkerThread("Loading " + src + " " + origin, Priority.LOAD_FROM_DISK, () -> register(tableManager, destPosition, picked.iterator(), onLoad));
                            }
                        });
                        break;
                }
            }
            catch (InternalException | UserException | IOException ex)
            {
                FXUtility.logAndShowError("import.r.error", ex);
            }
        });
    }
    
    @OnThread(Tag.Simulation)
    private void register(TableManager tableManager, CellPosition cellPosition, Iterator<Pair<String, EditableRecordSet>> tables, SimulationConsumerNoError<DataSource> onLoad)
    {
        if (tables.hasNext())
        {
            Pair<String, EditableRecordSet> reg = tables.next();
            ImmediateDataSource immediateDataSource = new ImmediateDataSource(tableManager, new InitialLoadDetails(new TableId(IdentifierUtility.fixExpressionIdentifier(reg.getFirst(), IdentifierUtility.identNum("RTable", new Random().nextInt(10000)))), null, cellPosition, null), reg.getSecond());
            onLoad.consume(immediateDataSource);
            // Recurse:
            int width = immediateDataSource.getData().getColumns().size();
            register(tableManager, cellPosition.offsetByRowCols(0, width + 1), tables, onLoad);
        }
    }

    @Override
    public @Localized String getName()
    {
        return TranslationUtility.getString("importer.r.files");
    }

    @OnThread(Tag.FXPlatform)
    private class PickImportsDialog extends Dialog<ImmutableList<Pair<String, EditableRecordSet>>>
    {
        public PickImportsDialog(Window parent, ImmutableList<Pair<String, EditableRecordSet>> tables)
        {
            setDialogPane(new DialogPaneWithSideButtons());
            getDialogPane().getStylesheets().addAll(FXUtility.getSceneStylesheets("general", "dialogs"));
            initModality(Modality.APPLICATION_MODAL);
            initOwner(parent);
            setTitle("Choose tables to import");
            // Indexes match those from tables
            ListView<String> listView = new ListView<>(FXCollections.<String>observableList(Utility.<Pair<String, EditableRecordSet>, String>mapListI(tables, p -> p.getFirst())));
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            listView.getSelectionModel().selectAll();
            BorderPane.setMargin(listView, new Insets(10, 0, 0, 0));
            getDialogPane().setContent(GUI.borderTopCenter(GUI.label("importer.r.pickTables"), listView));
            getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            FXUtility.listen(listView.getSelectionModel().getSelectedIndices(), selected -> {
                getDialogPane().lookupButton(ButtonType.OK).setDisable(listView.getSelectionModel().isEmpty());
            });
            setResultConverter(bt -> {
                if (bt == ButtonType.OK)
                {
                    return Utility.mapListI(listView.getSelectionModel().getSelectedIndices(), i -> tables.get(i));
                }
                return ImmutableList.of();
            });
            FXUtility.runAfter(listView::requestFocus);
        }
    }
}
