package records.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.MapMaker;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import log.Log;
import records.data.DataSource;
import records.data.GridComment;
import records.data.Table;
import records.data.TableManager;
import records.data.TableManager.TableManagerListener;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Check;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.WeakHashMap;

public final class CheckSummaryLabel extends BorderPane
{
    // Note that weakKeys makes an identity hash map, deliberately: 
    @OnThread(Tag.Simulation)
    private final Map<Check, Optional<Boolean>> currentResults = new MapMaker().weakKeys().makeMap();
    private final Label counts = GUI.labelRaw("No checks", "check-summary-counts");
    private final BooleanProperty hasChecksProperty = new SimpleBooleanProperty(false);
    private final ArrayList<ChecksStateListener> checkListeners = new ArrayList<>();

    // Should be called before any tables are added
    public CheckSummaryLabel(TableManager tableManager, FXPlatformRunnable onClick)
    {
        getStyleClass().add("check-summary");
        setCenter(counts);
        counts.setFocusTraversable(false);
        counts.setOnMouseClicked(e -> onClick.run());
        tableManager.addListener(new TableManagerListener()
        {
            @Override
            public void removeTable(Table t, int tablesRemaining)
            {
                // No harm removing it:
                currentResults.remove(t);
                Utility.later(CheckSummaryLabel.this).update();
            }

            @Override
            public void addSource(DataSource dataSource)
            {
            }

            @Override
            public void addTransformation(Transformation transformation)
            {
                if (transformation instanceof Check)
                {
                    Check check = (Check) transformation;
                    currentResults.put(check, Optional.empty());
                    // Do a run later to not hold things up now:
                    Workers.onWorkerThread("Check display", Priority.FETCH, () -> {
                        
                        try
                        {
                            @Value Boolean result = check.getResult();
                            // Only replace if still in the map:
                            currentResults.replace(check, Optional.empty(), Optional.of(result));
                            Utility.later(CheckSummaryLabel.this).update();
                        }
                        catch (InternalException | UserException e)
                        {
                            Log.log(e);
                        }
                    });
                }
            }

            @Override
            public void addComment(GridComment gridComment)
            {

            }

            @Override
            public void removeComment(GridComment gridComment)
            {

            }
        });
    }
    
    @OnThread(Tag.Simulation)
    private void update()
    {
        int total = currentResults.size();
        if (total == 0)
        {
            FXUtility.runFX(() -> {
                this.counts.setText("No checks");
                hasChecksProperty.setValue(false);
                FXUtility.setPseudoclass(this.counts, "failing", false);
                for (ChecksStateListener checkListener : checkListeners)
                {
                    checkListener.checksChanged();
                }
            });
        }
        else
        {
            // If any items are currently missing a result, we display a question mark: 
            OptionalInt passing = currentResults.values().stream().reduce(OptionalInt.of(0), (OptionalInt count, Optional<Boolean> result) -> count.isPresent() && result.isPresent() ? OptionalInt.of(count.getAsInt() + (result.get() ? 1 : 0)) : OptionalInt.empty(), (a, b) -> a.isPresent() && b.isPresent() ? OptionalInt.of(a.getAsInt() + b.getAsInt()) : OptionalInt.empty());
            FXUtility.runFX(() -> {
                this.counts.setText("Checks: " + (passing.isPresent() ? passing.getAsInt() + "/" + total : "?/" + total) + " OK");
                FXUtility.setPseudoclass(this.counts, "failing", !passing.isPresent() || passing.getAsInt() != total);
                hasChecksProperty.set(true);
                for (ChecksStateListener checkListener : checkListeners)
                {
                    checkListener.checksChanged();
                }
            });
        }    
    }
    
    public BooleanExpression hasChecksProperty()
    {
        return this.hasChecksProperty;
    }

    public ImmutableList<Check> getFailingChecks()
    {
        return currentResults.entrySet().stream().filter(e -> e.getValue().isPresent() && !e.getValue().get()).map(e -> e.getKey()).sorted(Comparator.comparing(c -> c.getId().getRaw())).collect(ImmutableList.<Check>toImmutableList());
    }

    public ImmutableList<Check> getPassingChecks()
    {
        return currentResults.entrySet().stream().filter(e -> e.getValue().isPresent() && e.getValue().get()).map(e -> e.getKey()).sorted(Comparator.comparing(c -> c.getId().getRaw())).collect(ImmutableList.<Check>toImmutableList());
    }
    
    public static interface ChecksStateListener
    {
        @OnThread(Tag.FXPlatform)
        public void checksChanged();
    }

    public void addListener(ChecksStateListener listener)
    {
        checkListeners.add(listener);
    }
    
    public void removeListener(ChecksStateListener listener)
    {
        checkListeners.remove(listener);
    }
}
