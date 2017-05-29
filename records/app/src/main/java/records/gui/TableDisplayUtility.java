package records.gui;

import annotation.qual.Value;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxmisc.richtext.model.StyledText;
import records.data.Column;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.SimulationRunnable;
import utility.Utility;
import records.gui.stable.StableView.ColumnHandler;
import records.gui.stable.StableView.ValueReceiver;

import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 01/05/2017.
 */
@OnThread(Tag.FXPlatform)
public class TableDisplayUtility
{

    public static List<Pair<String, ColumnHandler>> makeStableViewColumns(RecordSet recordSet)
    {
        return Utility.mapList(recordSet.getColumns(), col -> {
            try
            {
                return getDisplay(col);
            }
            catch (InternalException | UserException e)
            {
                // Show a dummy column with an error message:
                return new Pair<>(col.getName().getRaw(), new ColumnHandler()
                {
                    @Override
                    public void fetchValue(int rowIndex, ValueReceiver receiver, int firstVisibleRowIndexIncl, int lastVisibleRowIndexIncl)
                    {
                        receiver.setValue(rowIndex, new Label("Error: " + e.getLocalizedMessage()));
                    }

                    @Override
                    public void columnResized(double width)
                    {

                    }

                    @Override
                    public void edit(int rowIndex, @Nullable Point2D scenePoint)
                    {
                        Utility.logStackTrace("Called edit when not editable");
                    }

                    @Override
                    public boolean isEditable()
                    {
                        return false;
                    }

                    @Override
                    public @OnThread(Tag.Simulation) SimulationRunnable appendRow(int newRowIndex) throws InternalException
                    {
                        throw new InternalException("Called appendRow when not editable");
                    }
                });
            }
        });
    }

    private static Pair<String, ColumnHandler> getDisplay(@NonNull Column column) throws UserException, InternalException
    {
        return new Pair<>(column.getName().getRaw(), column.getType().applyGet(new DataTypeVisitorGet<ColumnHandler>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public ColumnHandler number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                return NumberDisplay.makeDisplayCache(g, displayInfo.getDisplayInfo(), column);
            }

            @Override
            public ColumnHandler text(GetValue<@Value String> g) throws InternalException, UserException
            {
                class StringDisplay extends StackPane
                {
                    private final Label label;

                    public StringDisplay(String value)
                    {
                        Label beginQuote = new Label("\u201C");
                        Label endQuote = new Label("\u201D");
                        beginQuote.getStyleClass().add("string-display-quote");
                        endQuote.getStyleClass().add("string-display-quote");
                        StackPane.setAlignment(beginQuote, Pos.TOP_LEFT);
                        StackPane.setAlignment(endQuote, Pos.TOP_RIGHT);
                        //StackPane.setMargin(beginQuote, new Insets(0, 0, 0, 3));
                        //StackPane.setMargin(endQuote, new Insets(0, 3, 0, 0));
                        label = new Label(value);
                        label.setTextOverrun(OverrunStyle.CLIP);
                        getChildren().addAll(beginQuote, label); //endQuote, label);
                        // TODO allow editing, and call column.modified when it happens
                    }
                }

                return new DisplayCache<@Value String, StringDisplay>(g, null, s -> s) {
                    @Override
                    protected StringDisplay makeGraphical(int rowIndex, @Value String value)
                    {
                        return new StringDisplay(value);
                    }

                    @Override
                    public void edit(int rowIndex, @Nullable Point2D scenePoint)
                    {
                        //TODO
                    }

                    //TODO
                    @Override
                    public boolean isEditable()
                    {
                        return false;
                    }

                    @Override
                    public @OnThread(Tag.Simulation) SimulationRunnable appendRow(int newRowIndex) throws InternalException, UserException
                    {
                        //TODO
                        return g.set(newRowIndex, "");
                    }
                };
            }

            @Override
            public ColumnHandler bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public ColumnHandler date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public ColumnHandler tagged(TypeId typeName, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public ColumnHandler tuple(List<DataTypeValue> types) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public ColumnHandler array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }
        }));
    }

    // package-visible
    static StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> docFromSegments(StyledText<Collection<String>>... segments)
    {
        ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc = ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromSegment((StyledText<Collection<String>>)segments[0], Collections.emptyList(), Collections.emptyList(), StyledText.<Collection<String>>textOps());
        for (int i = 1; i < segments.length; i++)
        {
            doc = doc.concat(ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromSegment(segments[i], Collections.emptyList(), Collections.emptyList(), StyledText.<Collection<String>>textOps()));
        }
        return doc;
    }
}
