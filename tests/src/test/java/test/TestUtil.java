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

package test;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.sun.javafx.PlatformUtil;
import com.sun.javafx.tk.Toolkit;
import javafx.application.Platform;
import javafx.css.Styleable;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;
import test.gui.TFXUtil;
import xyz.columnal.data.TableManager;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.testfx.api.FxRobotInterface;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.utility.*;
import xyz.columnal.data.datatype.DataType;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.gui.FXUtility;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

/**
 * Created by neil on 05/11/2016.
 */
public class TestUtil
{
    public static final @LocalizableKey String EMPTY_KEY = makeEmptyKey();

    private static @LocalizableKey String makeEmptyKey()
    {
        // I cannot seem to get the checker to suppress warnings, so instead give a key that is valid:
        return "menu.exit";
    }

    public static <K, V> Map<K, V> makeMap(SourceOfRandomness r, int minSizeIncl, int maxSizeIncl, Supplier<K> makeKey, Supplier<V> makeValue)
    {
        int size = r.nextInt(minSizeIncl, maxSizeIncl);
        HashMap<K, V> list = new HashMap<>();
        for (int i = 0; i < size; i++)
            list.put(makeKey.get(), makeValue.get());
        return list;
    }

    private static Pair<@Nullable String, DataType> t(DataType type)
    {
        return new Pair<>(null, type);
    }

    // WOuld be nice to get this working, but doesn't currently work
    public static void writePaste_doesntwork(FxRobotInterface robot, String string)
    {
        TFXUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, string)));
        robot.push(PlatformUtil.isMac() ? KeyCode.COMMAND : KeyCode.CONTROL, KeyCode.V);
    }

    /*public static interface TablesMaker
    {
        public List<Table>
    }*/

    @OnThread(Tag.FXPlatform)
    public static void copySnapshotToClipboard(Node node)
    {
        WritableImage img = node.snapshot(null, null);
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putImage(img);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    public static void fxYieldUntil(FXPlatformSupplier<Boolean> waitUntilTrue)
    {
        Object finish = new Object();
        FXPlatformRunnable repeat = new FXPlatformRunnable()
        {
            int attempts = 0;
            
            @Override
            public void run()
            {
                if (waitUntilTrue.get() || attempts >= 10)
                {
                    com.sun.javafx.tk.Toolkit.getToolkit().exitNestedEventLoop(finish, "");
                }
                else
                {
                    attempts += 1;
                    FXUtility.runAfterDelay(Duration.millis(300), this);
                }
            }
        };
        Platform.runLater(repeat::run);
        Toolkit.getToolkit().enterNestedEventLoop(finish);
    }

    // Applies Matcher to the result of an extraction function:
    public static <@NonNull S, @NonNull T> Matcher<S> matcherOn(Matcher<T> withExtracted, Function<S, @NonNull T> extract)
    {
        return new BaseMatcher<S>()
        {
            @Override
            public void describeTo(Description description)
            {
                withExtracted.describeTo(description);
            }

            @SuppressWarnings("unchecked")
            @Override
            public boolean matches(Object o)
            {
                return withExtracted.matches(extract.apply((S)o));
            }
        };
    }

    public static <T extends Styleable> Matcher<T> matcherHasStyleClass(String styleClass)
    {
        return TestUtil.<T, Iterable<? extends String>>matcherOn(Matchers.contains(styleClass), (T s) -> TFXUtil.fx(() -> ImmutableList.copyOf(s.getStyleClass())));
    }

    // Adds event filters on all nodes under the target location,
    // and tracks which if any receive the given event type
    // while executing during.
    @OnThread(Tag.Any)
    public static <E extends Event> void debugEventRecipient_(FxRobotInterface robot, @Nullable Point2D target, EventType<E> eventType, Runnable during)
    {
        Set<Node> allNodes = robot.lookup(n -> {
            Bounds screen = TFXUtil.fx(() -> n.localToScreen(n.getBoundsInLocal()));
            return target == null || screen.contains(target);
        }).queryAll();

        List<Pair<Node, EventType<?>>> received = new ArrayList<>();
        Map<Node, EventHandler<E>> listeners = new HashMap<>(); 
        for (Node node : allNodes)
        {
            EventHandler<E> eventHandler = e -> {
                received.add(new Pair<>(node, e.getEventType()));
            };
            TFXUtil.fx_(() -> node.addEventFilter(eventType, eventHandler));
            listeners.put(node, eventHandler);
        }
        
        during.run();

        listeners.forEach((n, l) ->
        {
            TFXUtil.fx_(() -> n.removeEventFilter(eventType, l));
        });
        
        Log.normal("Events received:\n" + received.stream().map(n -> "  " + n.toString()).collect(Collectors.joining("\n")));
    }

    public static class Expression_Mgr
    {
        public final TableManager mgr;
        public final Expression expression;

        public Expression_Mgr(TableManager mgr, Expression expression)
        {
            this.mgr = mgr;
            this.expression = expression;
        }
    }

}
