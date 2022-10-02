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

package xyz.columnal.utility;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.log.Log;
import xyz.columnal.styled.StyledString;

import java.util.Arrays;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;

/**
 * Created by neil on 17/04/2017.
 */
@OnThread(Tag.Any)
public class TranslationUtility
{
    private static final LoadingCache<@LocalizableKey String, Optional<@Localized String>> cached = CacheBuilder.newBuilder().build(new CacheLoader<@LocalizableKey String, Optional<@Localized String>>()
    {
        @Override
        public Optional<@Localized String> load(@LocalizableKey String key) throws Exception
        {
            return Optional.ofNullable(loadString(key));
        }
    });
    
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private static @MonotonicNonNull List<ResourceBundle> resources;

    @OnThread(Tag.Any)
    private static synchronized @Nullable List<ResourceBundle> getResources()
    {
        if (resources == null)
        {
            try
            {
                // Each of these corresponds to a <name>_en.properties file in the resources directory
                resources = Arrays.asList(
                    ResourceBundle.getBundle("transformations"),
                    ResourceBundle.getBundle("expression"),
                    ResourceBundle.getBundle("function"),
                    ResourceBundle.getBundle("main"),
                    ResourceBundle.getBundle("newcolumn"),
                    ResourceBundle.getBundle("dataentry"),
                    ResourceBundle.getBundle("import")
                );
            }
            catch (MissingResourceException e)
            {
                Log.log(e);
                return null;
            }
        }
        return resources;
    }

    /**
     * Given a localization key (LHS in labels files), returns the localized value (RHS in labels files)
     *
     * If the key is not found, the key itself is returned as the string
     */
    @SuppressWarnings("i18n") // Because we return key if there's an issue
    @OnThread(Tag.Any)
    public static @Localized String getString(@LocalizableKey String key, String... values)
    {
        @Localized String lookedUp = null;
        try
        {
            lookedUp = cached.get(key).orElse(null);
        }
        catch (ExecutionException e)
        {
            // Leave lookedUp as null and log it
            Log.log(e);
        }
        if (lookedUp != null)
        {
            if (values.length == 0)
                return lookedUp;
            for (int i = 0; i < values.length; i++)
            {
                lookedUp = lookedUp.replace("$" + (i+1), values[i]);
            }
            return lookedUp;
        }
        else
        {
            Log.error("Did not find translated key: \"" + key + "\"");
            return key; // Best we can do, if we can't find the labels file.
        }
    }

    private static @Localized @Nullable String loadString(@LocalizableKey String key)
    {
        @Nullable String lookedUp = null;
        @Nullable List<ResourceBundle> res = getResources();
        if (res != null)
        {
            for (ResourceBundle r : res)
            {
                try
                {
                    @Nullable String local = r.getString(key);
                    if (local != null)
                    {
                        lookedUp = local;
                        break;
                    }
                }
                catch (MissingResourceException e)
                {
                    // This is fine; just try the next one.
                }
            }
        }
        return lookedUp;
    }

    @SuppressWarnings("i18n") // Because we return key if there's an issue
    @OnThread(Tag.Any)
    public static StyledString getStyledString(@LocalizableKey String key, String... values)
    {
        return StyledString.s(getString(key, values));
    }


}
