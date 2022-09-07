package records.data;

import com.google.common.collect.ImmutableMap;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.SimulationConsumer;

import java.util.function.Consumer;

public interface PluggedContentHandler
{
    @OnThread(Tag.Simulation)
    public abstract ImmutableMap<String, SimulationConsumer<Pair<SaveTag, String>>> getHandledContent(Consumer<StyledString> onError);
}
