package records.gui.kit;

import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

// Will become a replacement for FlexibleTextField
@OnThread(Tag.FXPlatform)
public class DynamicTextField extends Region
{
    private final TextFlow textFlow;
    
    public DynamicTextField()
    {
        textFlow = new TextFlow();
        getChildren().add(textFlow);
    }
    
    public void setDocument(Document document)
    {
        textFlow.getChildren().setAll(makeTextNodes(document.getStyledSpans()));
    }

    private static List<Text> makeTextNodes(Stream<Pair<Set<String>, String>> styledSpans)
    {
        return styledSpans.map(ss -> {
            Text text = new Text(ss.getSecond());
            text.getStyleClass().addAll(ss.getFirst());
            return text;
        }).collect(ImmutableList.<Text>toImmutableList());
    }

    @Override
    protected void layoutChildren()
    {
        textFlow.resizeRelocate(0, 0, textFlow.prefWidth(-1), getHeight());
    }
}
