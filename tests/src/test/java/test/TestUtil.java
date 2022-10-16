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
import javafx.css.Styleable;
import test.gui.TFXUtil;
import xyz.columnal.data.TableManager;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.utility.*;
import xyz.columnal.data.datatype.DataType;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

/**
 * Created by neil on 05/11/2016.
 */
public class TestUtil
{
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

}
