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

package xyz.columnal.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeUtility.ComparableValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.ManualEdit;
import xyz.columnal.transformations.ManualEdit.ColumnReplacementValues;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.ComparableEither;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExFunction;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationSupplier;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.DimmableParent;
import xyz.columnal.utility.gui.FancyList;
import xyz.columnal.utility.gui.LightDialog;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

// If the document is closed by clicking a hyperlink, that
// location is returned as a pair of the identifier and
// the replaced column.  If Close is clicked instead then
// empty is returned
@OnThread(Tag.FXPlatform)
public class ManualEditEntriesDialog extends LightDialog<Pair<Optional<Pair<ComparableValue, ColumnId>>, SimulationSupplier<ImmutableMap<ColumnId, ColumnReplacementValues>>>>
{
    public ManualEditEntriesDialog(DimmableParent parent, @Nullable ColumnId keyColumn, ExFunction<ColumnId, DataType> lookupColumnType, ImmutableList<Entry> originalEntries)
    {
        super(parent);
        setResizable(true);
        FancyList<Entry, HBox> fancyList = new FancyList<Entry, HBox>(originalEntries, true, false, false)
        {
            @Override
            protected @OnThread(Tag.FXPlatform) Pair<HBox, FXPlatformSupplier<Entry>> makeCellContent(Optional<Entry> initialContentNull, boolean editImmediately)
            {
                // Can't be null because we don't allow adding items:
                @SuppressWarnings("optional")
                @NonNull Entry initialContent = initialContentNull.get();
                
                HBox content = new HBox(new Label("Loading..."));
                Workers.onWorkerThread("Loading replacement values", Priority.FETCH, () -> {
                    try
                    {
                        String keyValue = DataTypeUtility.valueToString(initialContent.identifierValue.getValue());
                        String replacementValue = initialContent.replacementValue.eitherEx(err -> err, v -> DataTypeUtility.valueToString(v.getValue()));
                        FXPlatformRunnable jumpTo = () -> {
                            ImmutableList<Entry> items = getItems();
                            ManualEditEntriesDialog.this.setResult(new Pair<>(Optional.of(new Pair<>(initialContent.identifierValue, initialContent.getReplacementColumn())), () -> fromEntries(items, lookupColumnType)));
                            ManualEditEntriesDialog.this.close();
                        };
                        
                        Platform.runLater(() ->
                            content.getChildren().setAll(new HBox(
                                hyperLink(new Label(keyValue), jumpTo),
                                hyperLink(new Label(initialContent.replacementColumn.getRaw()), jumpTo),
                                hyperLink(new Label(replacementValue), jumpTo)
                        )));
                    }
                    catch (UserException | InternalException e)
                    {
                        if (e instanceof InternalException)
                            Log.log(e);
                        Platform.runLater(() -> content.getChildren().setAll(new Label("Error: " + e.getLocalizedMessage())));
                    }
                });
                
                return new Pair<>(content, () -> initialContent);
            }
        };

        Label emptyLabel = new Label("No edits");
        StackPane stackPane = new StackPane(fancyList.getNode(), emptyLabel);
        stackPane.setMinWidth(300);
        stackPane.setMinHeight(200);
        fancyList.addEmptyListenerAndCallNow(emptyLabel::setVisible);
        
        getDialogPane().setContent(stackPane);
        getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        getDialogPane().lookupButton(ButtonType.CLOSE).getStyleClass().add("close-button");
        centreDialogButtons();
    
        setResultConverter(bt -> {
            ImmutableList<Entry> items = fancyList.getItems();
            return new Pair<>(Optional.empty(), () -> fromEntries(items, lookupColumnType));
        });
    }

    private static Label hyperLink(Label label, FXPlatformRunnable jumpTo)
    {
        label.getStyleClass().add("jump-to-link");
        label.setOnMouseClicked(e -> {
            jumpTo.run();
        });
        return label;
    }

    @OnThread(Tag.Simulation)
    public static ImmutableList<Entry> getEntries(ManualEdit manualEdit)
    {
        return manualEdit.getReplacements().entrySet().stream().flatMap(e -> e.getValue().streamAll().map(r -> new Entry(r.getFirst(), e.getKey(), r.getSecond())
        )).collect(ImmutableList.<Entry>toImmutableList());
    }
    
    @OnThread(Tag.Simulation)
    private static ImmutableMap<ColumnId, ColumnReplacementValues> fromEntries(ImmutableList<Entry> entries, ExFunction<ColumnId, DataType> lookupColumnType) throws InternalException, UserException
    {
        HashMap<ColumnId, TreeMap<ComparableValue, Either<String, ComparableValue>>> items = new HashMap<>();

        for (Entry entry : entries)
        {
            items.computeIfAbsent(entry.getReplacementColumn(), c -> new TreeMap<>()).put(entry.getIdentifierValue(), entry.getReplacementValue());
        }
        
        ImmutableMap.Builder<ColumnId, ColumnReplacementValues> r = ImmutableMap.builder();

        for (Map.Entry<ColumnId, TreeMap<ComparableValue, Either<String, ComparableValue>>> entry : items.entrySet())
        {
            ColumnId c = entry.getKey();
            TreeMap<ComparableValue, Either<String, ComparableValue>> m = entry.getValue();
            r.put(c, new ColumnReplacementValues(lookupColumnType.apply(c), m.entrySet().stream().<Pair<@Value Object, Either<String, @Value Object>>>map(e -> new Pair<@Value Object, Either<String, @Value Object>>(e.getKey().getValue(), e.getValue().<@Value Object>map(v -> v.getValue()))).collect(ImmutableList.<Pair<@Value Object, Either<String, @Value Object>>>toImmutableList())));
        }

        return r.build();
    }

    public static class Entry
    {
        private final ComparableValue identifierValue;
        private final ColumnId replacementColumn;
        private final ComparableEither<String, ComparableValue> replacementValue;

        public Entry(ComparableValue identifierValue, ColumnId replacementColumn, ComparableEither<String, ComparableValue> replacementValue)
        {
            this.identifierValue = identifierValue;
            this.replacementColumn = replacementColumn;
            this.replacementValue = replacementValue;
        }

        public ComparableValue getIdentifierValue()
        {
            return identifierValue;
        }

        public ColumnId getReplacementColumn()
        {
            return replacementColumn;
        }

        public ComparableEither<String, ComparableValue> getReplacementValue()
        {
            return replacementValue;
        }

        @Override
        @OnThread(value = Tag.Simulation, ignoreParent = true)
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry entry = (Entry) o;
            return identifierValue.equals(entry.identifierValue) &&
                    replacementColumn.equals(entry.replacementColumn) &&
                    replacementValue.equals(entry.replacementValue);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(identifierValue, replacementColumn, replacementValue);
        }

        // Used for debugging:

        @Override
        public String toString()
        {
            return "Entry{" +
                "identifierValue=" + identifierValue +
                ", replacementColumn=" + replacementColumn +
                ", replacementValue=" + replacementValue +
                '}';
        }
    }
}
