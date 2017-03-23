package records.gui;

import annotation.qual.Value;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.transformations.TransformationEditor;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.TaggedValue;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.List;

/**
 * Created by neil on 21/03/2017.
 */
@OnThread(Tag.FXPlatform)
public class DataTypeGUI
{
    public static Pair<Node, ObservableValue<? extends @Value @Nullable Object>> getEditorFor(DataType dataType)
    {
        try
        {
            return dataType.apply(new DataTypeVisitorEx<Pair<Node, ObservableValue<? extends @Value @Nullable Object>>, InternalException>()
            {
                @Override
                @OnThread(Tag.FXPlatform)
                public Pair<Node, ObservableValue<? extends @Value @Nullable Object>> number(NumberInfo displayInfo) throws InternalException
                {
                    ErrorableTextField<@Value Number> field = new ErrorableTextField<@Value Number>(s ->
                        ErrorableTextField.validate(() -> Utility.parseNumber(s))
                    );
                    return new Pair<>(field.getNode(), field.valueProperty());
                }

                @Override
                @OnThread(Tag.FXPlatform)
                public Pair<Node, ObservableValue<? extends @Value @Nullable Object>> text() throws InternalException
                {
                    TextField field = new TextField();
                    return new Pair<>(new HBox(new Label("\""), field, new Label("\"")), field.textProperty());
                }

                @Override
                @OnThread(Tag.FXPlatform)
                public Pair<Node, ObservableValue<? extends @Nullable @Value Object>> date(DateTimeInfo dateTimeInfo) throws InternalException
                {
                    return new Pair<>(new Label("TODO"), new ReadOnlyObjectWrapper<>(""));
                }

                @Override
                @OnThread(Tag.FXPlatform)
                public Pair<Node, ObservableValue<? extends @Value @Nullable Object>> bool() throws InternalException
                {
                    SimpleBooleanProperty value = new SimpleBooleanProperty(true);
                    Label label = new Label();
                    label.setCursor(Cursor.HAND);
                    label.textProperty().bind(Bindings.when(value).then(TransformationEditor.getString("dataedit.bool.true")).otherwise(TransformationEditor.getString("dataedit.bool.false")));
                    label.setOnMouseClicked(e -> value.set(!value.get()));
                    return new Pair<>(label, value);
                }

                @Override
                @OnThread(Tag.FXPlatform)
                public Pair<Node, ObservableValue<? extends @Nullable @Value Object>> tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException
                {
                    ComboBox<TagType<DataType>> tagNameCombo = new ComboBox<>();
                    tagNameCombo.setConverter(new StringConverter<TagType<DataType>>()
                    {
                        @Override
                        public String toString(TagType<DataType> tag)
                        {
                            return tag.getName();
                        }

                        @Override
                        public TagType<DataType> fromString(String string)
                        {
                            throw new RuntimeException("Internal error: tag name should not be editable");
                        }
                    });
                    tagNameCombo.getItems().addAll(tags);
                    HBox hBox = new HBox(tagNameCombo);
                    SimpleObjectProperty<@Nullable @Value Object> value = new SimpleObjectProperty<>();
                    Utility.addChangeListenerPlatform(tagNameCombo.getSelectionModel().selectedItemProperty(), selTag ->
                    {
                        if (selTag == null)
                            return; // Shouldn't happen...

                        if (selTag.getInner() != null)
                        {
                            Pair<Node, ObservableValue<?>> editor = getEditorFor(selTag.getInner());
                            hBox.getChildren().setAll(tagNameCombo, editor.getFirst());
                            editor.getSecond().addListener(new ChangeListener<@Nullable @Value Object>()
                            {
                                @Override
                                public void changed(ObservableValue<?> observable, @Nullable @Value Object oldValue, @Nullable @Value Object newValue)
                                {
                                    if (hBox.getChildren().contains(editor.getFirst()))
                                    {
                                        // Still us editing, so do the update:
                                        value.setValue(new TaggedValue(tags.indexOf(selTag), editor.getSecond().getValue()));
                                    } else
                                    {
                                        // Different editor now; remove us as listener:
                                        editor.getSecond().removeListener(this);
                                    }
                                }
                            });
                        } else
                        {
                            // Easy version; no inner item
                            hBox.getChildren().setAll(tagNameCombo);
                            value.setValue(new TaggedValue(tags.indexOf(selTag), 0));
                        }
                    });
                    return new Pair<>(hBox, value);
                }

                @Override
                @OnThread(Tag.FXPlatform)
                public Pair<Node, ObservableValue<? extends @Nullable @Value Object>> tuple(List<DataType> inner) throws InternalException
                {
                    return new Pair<>(new Label("TODO"), new ReadOnlyObjectWrapper<>(""));
                }

                @Override
                @OnThread(Tag.FXPlatform)
                public Pair<Node, ObservableValue<? extends @Nullable @Value Object>> array(@Nullable DataType inner) throws InternalException
                {
                    return new Pair<>(new Label("TODO"), new ReadOnlyObjectWrapper<>(""));
                }
            });
        }
        catch (InternalException e)
        {
            Utility.log(e);
            return new Pair<>(new Label("Internal Error"), new ReadOnlyObjectWrapper<@Nullable @Value Object>(null));
        }
    }
}
