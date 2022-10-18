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

package xyz.columnal.importers;

import annotation.help.qual.HelpKey;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.utility.adt.Either;

import java.util.function.Function;

/**
 * Contains information about a given choice, e.g. is free entry allowed,
 * get help on the item, etc).
 */
public class ChoiceDetails<C>
{
    private final @LocalizableKey String labelKey;
    private final @HelpKey String helpKey;
    public final ImmutableList<@NonNull C> quickPicks;
    // If null, quick picks are the only options.  If non-null, offer an "Other" pick
    // in the combo box which shows a text field:
    public final @Nullable Function<String, Either<@Localized String, @NonNull C>> stringEntry;

    public ChoiceDetails(@LocalizableKey String labelKey, @HelpKey String helpKey, ImmutableList<@NonNull C> quickPicks, @Nullable Function<String, Either<@Localized String, @NonNull C>> stringEntry)
    {
        this.labelKey = labelKey;
        this.helpKey = helpKey;
        this.quickPicks = quickPicks;
        this.stringEntry = stringEntry;
    }

    public @LocalizableKey String getLabelKey()
    {
        return labelKey;
    }

    public @HelpKey String getHelpId()
    {
        return helpKey;
    }

    // Does this item have no possible choices?
    public boolean isEmpty()
    {
        return quickPicks.isEmpty() && stringEntry == null;
    }
}
