/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.exporters.manager;

import com.google.common.collect.ImmutableList;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser.ExtensionFilter;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.Table;
import xyz.columnal.exporters.Exporter;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.gui.DimmableParent;
import xyz.columnal.utility.gui.ErrorableDialog;
import xyz.columnal.utility.gui.FXUtility;

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
            ArrayList<ExtensionFilter> filters = new ArrayList<>();
            filters.add(new ExtensionFilter(exporter.getName(), exporter.getSupportedFileTypes()));
            filters.add(new ExtensionFilter(TranslationUtility.getString("exporter.all.files"), "*.*"));
            
            File file = parent.<@Nullable File>dimAndWait(w -> FXUtility.chooseFileSave("data.export.dialogTitle", "dataExport", w, filters.toArray(new ExtensionFilter[0])));
            if (file != null)
            {
                final File fileNonNull = file;
                Workers.onWorkerThread("Export to " + file.getAbsolutePath(), Workers.Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.exporting"), () -> exporter.exportData(fileNonNull, table)));
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

        public PickExporterPane(FXPlatformConsumer<Exporter> onDoubleClick)
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
                            setText(item.getName() + " (" + item.getSupportedFileTypes().stream().collect(Collectors.joining(", ")) + ")");
                        }
                    }
                };
            });
            exporterList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            exporterList.getSelectionModel().selectFirst();
            exporterList.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY)
                {
                    Exporter selected = exporterList.getSelectionModel().getSelectedItem();
                    if (selected != null)
                        onDoubleClick.consume(selected);
                }
            });
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
            pickExporterPane = new PickExporterPane(e -> {
                setResult(e);
                close();
            });
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
