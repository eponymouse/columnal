/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.MapMaker;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import xyz.columnal.log.Log;
import xyz.columnal.data.DataSource;
import xyz.columnal.data.GridComment;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.TableManager.TableManagerListener;
import xyz.columnal.data.Transformation;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.Check;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

@OnThread(Tag.FXPlatform)
public final class CheckSummaryLabel extends BorderPane
{
    // Note that weakKeys makes an identity hash map, deliberately: 
    @OnThread(Tag.Simulation)
    private final Map<Check, Optional<Boolean>> currentResults = new MapMaker().weakKeys().makeMap();
    private final Label counts = GUI.label("checks.no.checks", "check-summary-counts");
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
                this.counts.setText(TranslationUtility.getString("checks.no.checks"));
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

    @OnThread(Tag.Simulation)
    public ImmutableList<Check> getFailingChecks()
    {
        return currentResults.entrySet().stream().filter(e -> e.getValue().isPresent() && !e.getValue().get()).map(e -> e.getKey()).sorted(Comparator.comparing(c -> c.getId().getRaw())).collect(ImmutableList.<Check>toImmutableList());
    }

    @OnThread(Tag.Simulation)
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
