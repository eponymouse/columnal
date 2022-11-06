package test.gui.trait;

import org.testjavafx.node.NodeQuery;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.testjavafx.FxRobotInterface;
import test.gui.TFXUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Collection;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public interface QueryTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    public default <T extends Node> @NonNull T waitForOne(String query)
    {
        Optional<T> r;
        int count = 80;
        do
        {
            r = TFXUtil.fx(() -> lookup(query).tryQuery());
            TFXUtil.sleep(100);
            count--;
        }
        while (r.isEmpty() && count >= 0);
        return r.orElseThrow(() -> new RuntimeException("Nothing found for \"" + query + "\""));
    }
    
    @OnThread(Tag.Any)
    public default void waitClickOn(String query)
    {
        clickOn(this.<Node>waitForOne(query));
    }

    @OnThread(Tag.Any)
    public default void assertShowing(String message, String query)
    {
        Optional<Node> r;
        int count = 50;
        do
        {
            r = TFXUtil.fx(() -> lookup(query).tryQuery());
            TFXUtil.sleep(100);
            count--;
        }
        while (r.isEmpty() && count >= 0);
        assertTrue(message, r.isPresent());
    }

    @OnThread(Tag.Any)
    public default void assertNotShowing(String message, String query)
    {
        Optional<Node> r;
        int count = 50;
        do
        {
            r = TFXUtil.fx(() -> lookup(query).tryQuery());
            TFXUtil.sleep(100);
            count--;
        }
        while (r.isPresent() && count >= 0);
        assertFalse(message, r.isPresent());
    }
    
    @OnThread(Tag.Any)
    @SuppressWarnings("threadchecker") // The from method is actually thread-safe, having looked at the source code
    public default NodeQuery fromNode(Node node)
    {
        return from(node);
    }

    @OnThread(Tag.Any)
    @SuppressWarnings("threadchecker") // The from method is actually thread-safe, having looked at the source code
    public default NodeQuery fromNodes(Collection<Node> nodes)
    {
        return from(nodes.toArray(Node[]::new));
    }
    
    @OnThread(Tag.Any)
    public default int count(String nodeQuery)
    {
        return TFXUtil.fx(() -> lookup(nodeQuery).queryAll().size());
    }    
}
