package records.importers.gui;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.KeyForBottom;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.units.qual.UnitsBottom;
import records.gui.ErrorableTextField;
import records.gui.ErrorableTextField.ConversionResult;
import records.importers.ChoiceDetails;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformFunction;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LabelledGrid.Row;
import utility.gui.TranslationUtility;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class ImporterGUI
{
    @OnThread(Tag.FXPlatform)
    public static <C> Row makeGUI(ChoiceDetails<C> choiceDetails, ObjectProperty<@Nullable C> currentChoice)
    {
        Node choiceNode;
        if (choiceDetails.quickPicks.isEmpty() && choiceDetails.stringEntry == null)
        {
            // This is only if quick picks is empty and manual entry is not possible:
            choiceNode = new Label("No possible options for " + TranslationUtility.getString(choiceDetails.getLabelKey()));
            currentChoice.set(null);
        }
        else if (choiceDetails.quickPicks.size() == 1 && choiceDetails.stringEntry == null)
        {
            // Only one option:
            choiceNode = new Label(choiceDetails.quickPicks.get(0).toString());
            currentChoice.set(choiceDetails.quickPicks.get(0));
        }
        else
        {
            List<PickOrOther<C>> quickAndOther = new ArrayList<>();
            for (C quickPick : choiceDetails.quickPicks)
            {
                quickAndOther.add(new PickOrOther<>(quickPick));
            }
            if (choiceDetails.stringEntry != null)
                quickAndOther.add(new PickOrOther<>());
            final @NonNull @Initialized ComboBox<PickOrOther<C>> combo = GUI.comboBoxStyled(FXCollections.<PickOrOther<C>>observableArrayList(quickAndOther));
            GUI.addIdClass(combo, choiceDetails.getLabelKey());
            @Nullable C choice = currentChoice.get();
            if (choice == null || !combo.getItems().contains(new PickOrOther<>(choice)))
                combo.getSelectionModel().selectFirst();
            else
                combo.getSelectionModel().select(new PickOrOther<>(choice));

            final @Nullable @Initialized ObjectExpression<@Nullable C> fieldValue;
            if (choiceDetails.stringEntry != null)
            {
                final @NonNull Function<String, Either<@Localized String, @NonNull C>> stringEntry = choiceDetails.stringEntry;
                ErrorableTextField<@NonNull C> field = new ErrorableTextField<@NonNull C>(s -> {
                    return stringEntry.apply(s).<ConversionResult<@NonNull C>>either(e -> ConversionResult.<@NonNull C>error(e), v -> ConversionResult.<@NonNull C>success(v));
                });
                fieldValue = field.valueProperty();
                choiceNode = new HBox(combo, field.getNode());
                field.getNode().visibleProperty().bind(Bindings.equal(combo.getSelectionModel().selectedItemProperty(), new PickOrOther<>()));
                field.getNode().managedProperty().bind(field.getNode().visibleProperty());
                FXUtility.addChangeListenerPlatformNN(field.getNode().visibleProperty(), vis -> {
                    if (vis)
                        field.requestFocusWhenInScene();
                });
            }
            else
            {
                fieldValue = null;
                choiceNode = combo;
            }
            ReadOnlyObjectProperty<@Nullable PickOrOther<C>> selectedItemProperty = combo.getSelectionModel().selectedItemProperty();
            FXPlatformFunction<@Nullable PickOrOther<C>, @Nullable C> extract = (@Nullable PickOrOther<C> selectedItem) -> {
                if (selectedItem != null && selectedItem.value != null)
                    return selectedItem.value;
                else if (selectedItem != null && selectedItem.value == null && fieldValue != null && fieldValue.get() != null)
                    return fieldValue.get();
                else
                    return null;
            };
            if (fieldValue == null)
            {
                FXUtility.addChangeListenerPlatform(selectedItemProperty, s -> currentChoice.set(extract.apply(s)));
            }
            else
            {
                FXUtility.addChangeListenerPlatform(selectedItemProperty, s -> currentChoice.set(extract.apply(s)));
                FXUtility.addChangeListenerPlatform(fieldValue, f -> currentChoice.set(extract.apply(selectedItemProperty.get())));
            }
        }
        return GUI.labelledGridRow(choiceDetails.getLabelKey(), choiceDetails.getHelpId(), choiceNode);
    }

    // Either a value of type C, or an "Other" item
    // Public for testing
    public static class PickOrOther<C>
    {
        private final @Nullable C value;

        public PickOrOther()
        {
            this.value = null;
        }

        public PickOrOther(C value)
        {
            this.value = value;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PickOrOther<?> that = (PickOrOther<?>) o;

            return value != null ? value.equals(that.value) : that.value == null;
        }

        @Override
        public int hashCode()
        {
            return value != null ? value.hashCode() : 0;
        }

        @Override
        public @Localized String toString()
        {
            return value == null ? TranslationUtility.getString("import.choice.specify") : Utility.universal(value.toString());
        }
    }
}
