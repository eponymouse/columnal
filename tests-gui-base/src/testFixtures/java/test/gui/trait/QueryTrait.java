package test.gui.trait;

import javafx.scene.Node;
import org.testfx.api.FxRobotInterface;
import test.gui.TFXUtil;

import java.util.Optional;

public interface QueryTrait extends FxRobotInterface
{
    public default <T extends Node> T waitForOne(String query)
    {
        Optional<T> r;
        int count = 80;
        do
        {
            r = lookup(query).tryQuery();
            TFXUtil.sleep(100);
            count--;
        }
        while (r.isEmpty() && count >= 0);
        return r.orElseThrow(() -> new RuntimeException("Nothing found for \"" + query + "\""));
    }
}
