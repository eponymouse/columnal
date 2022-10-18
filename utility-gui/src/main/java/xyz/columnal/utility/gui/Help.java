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

package xyz.columnal.utility.gui;

import annotation.help.qual.HelpKey;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.grammar.GrammarUtility;
import xyz.columnal.utility.adt.Pair;

import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;

/**
 * Class for loading help information
 */
// package-visible
class Help
{
    // Maps file names (minus extension) to
    // help item names to help info
    private static LoadingCache<Pair<String, String>, HelpInfo> helpCache = CacheBuilder.newBuilder().build(
        new CacheLoader<Pair<String, String>, HelpInfo>()
        {
            @Override
            public HelpInfo load(Pair<String, String> s) throws Exception
            {
                return loadFile(s.getFirst(), s.getSecond());
            }
        }
    );

    private static HelpInfo loadFile(String fileStem, String id)
    {
        ResourceBundle resourceBundle = ResourceBundle.getBundle(fileStem);

        // Don't understand why I need all here.  Surely i18n should be enough?

        @SuppressWarnings("all") // Because we assert that the loaded XML is localized
        @LocalizableKey String titleKey = id + ".title";
        @SuppressWarnings("all")
        @LocalizableKey String shortKey = id;
        @SuppressWarnings("all")
        @LocalizableKey String fullKey = id + ".full";
        @SuppressWarnings("i18n")
        ImmutableList<@Localized String> split = ImmutableList.copyOf(GrammarUtility.collapseSpaces(resourceBundle.getString(fullKey)).split("£££££"));
        return new HelpInfo(resourceBundle.getString(titleKey), resourceBundle.getString(shortKey), split);
    }

    static class HelpInfo
    {
        final @Localized String title;
        final @Localized String shortText;
        final List<@Localized String> fullParas;

        private HelpInfo(@Localized String title, @Localized String shortText, List<@Localized String> fullParas)
        {
            this.title = title;
            this.shortText = shortText;
            this.fullParas = fullParas;
        }
    }

    static @Nullable HelpInfo getHelpInfo(@HelpKey String helpKey)
    {
        String[] rootAndEntry = helpKey.split("/");
        try
        {
            return helpCache.get(new Pair<>(rootAndEntry[0], rootAndEntry[1]));
        }
        catch (ExecutionException e)
        {
            Log.log(e);
            return null;
        }
    }
}
