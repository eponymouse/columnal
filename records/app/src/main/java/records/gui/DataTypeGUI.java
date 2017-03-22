package records.gui;

import annotation.qual.Value;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TypeId;
import records.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.List;

/**
 * Created by neil on 21/03/2017.
 */
@OnThread(Tag.FXPlatform)
public class DataTypeGUI
{
    public static Pair<Node, ObservableValue<? extends @Value @Nullable Object>> getEditorFor(DataType dataType) throws InternalException
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
                return new Pair<>(new Label("TODO"), new ReadOnlyObjectWrapper<>(""));
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Pair<Node, ObservableValue<? extends @Nullable @Value Object>> tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException
            {
                return new Pair<>(new Label("TODO"), new ReadOnlyObjectWrapper<>(""));
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
}
