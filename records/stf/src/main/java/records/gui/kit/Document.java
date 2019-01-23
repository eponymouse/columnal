package records.gui.kit;

import javafx.scene.Node;
import utility.Pair;

import java.util.Set;
import java.util.stream.Stream;

public interface Document
{
    Stream<Pair<Set<String>, String>> getStyledSpans();
}
