package records.gui;

import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.ColumnId;
import records.data.DisplayValue;
import records.data.DisplayValueBase;
import records.data.RecordSet;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.stable.StableView;
import utility.gui.stable.StableView.ValueFetcher;

import java.util.List;

/**
 * Created by neil on 01/05/2017.
 */
public class TableDisplayUtility
{
    public static List<Pair<String, ValueFetcher>> makeStableViewColumn(RecordSet recordSet)
    {
        return Utility.mapList(recordSet.getColumns(), col -> {
            return new Pair<>(col.getName().getRaw(), (rowIndex, callback) ->
            {
                ObservableValue<DisplayValueBase> obs = col.getDisplay(rowIndex);
                callback.setValue(rowIndex, getNode((DisplayValue)obs.getValue()));
                FXUtility.addChangeListenerPlatformNN(obs, v -> callback.setValue(rowIndex, getNode((DisplayValue)v)));
            });
        });
    }

    @OnThread(Tag.FXPlatform)
    private static Region getNode(DisplayValue item)
    {
        if (item.getNumber() != null)
        {
            @NonNull Number n = item.getNumber();
            HBox container = new HBox();
            Utility.addStyleClass(container, "number-display");
            Text prefix = new Text(item.getUnit().getDisplayPrefix());
            Utility.addStyleClass(prefix, "number-display-prefix");
            String integerPart = Utility.getIntegerPart(n).toString();
            integerPart = integerPart.replace("-", "\u2012");
            Text whole = new Text(integerPart);
            Utility.addStyleClass(whole, "number-display-int");
            String fracPart = Utility.getFracPartAsString(n);
            while (fracPart.length() < item.getMinimumDecimalPlaces())
                fracPart += "0";
            Text frac = new Text(fracPart.isEmpty() ? "" : ("." + fracPart));
            Utility.addStyleClass(frac, "number-display-frac");
            Pane spacer = new Pane();
            spacer.setVisible(false);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            container.getChildren().addAll(prefix, spacer, whole, frac);
            return container;
        }
        else
        {
            StackPane stringWrapper = new StackPane();
            Label beginQuote = new Label("\u201C");
            Label endQuote = new Label("\u201D");
            beginQuote.getStyleClass().add("string-display-quote");
            endQuote.getStyleClass().add("string-display-quote");
            StackPane.setAlignment(beginQuote, Pos.TOP_LEFT);
            StackPane.setAlignment(endQuote, Pos.TOP_RIGHT);
            //StackPane.setMargin(beginQuote, new Insets(0, 0, 0, 3));
            //StackPane.setMargin(endQuote, new Insets(0, 3, 0, 0));
            Label label = new Label(item.toString());
            label.setTextOverrun(OverrunStyle.CLIP);
            stringWrapper.getChildren().addAll(beginQuote /*, endQuote*/, label);
            return stringWrapper;
        }
    }
}
