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

package xyz.columnal.transformations.expression;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.scene.Scene;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationConsumer;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.TranslationUtility;

/**
 * A quick fix for an error.  Has a title to display, and a thunk to run
 * to get a replacement expression for the error source.
 * 
 * This works on a tree basis, not text-based.
 */
public final class QuickFix<EXPRESSION extends StyledShowable>
{
    private final StyledString title;
    // Identified by reference, not by contents/hashCode.
    private final @Recorded EXPRESSION replacementTarget;
    private final Either<QuickFixAction, QuickFixReplace<@UnknownIfRecorded EXPRESSION>> actionOrMakeReplacement;
    private final FXPlatformSupplier<ImmutableList<String>> cssClasses;

    public QuickFix(@LocalizableKey String titleKey, @Recorded EXPRESSION replacementTarget, QuickFixReplace<@NonNull @UnknownIfRecorded EXPRESSION> makeReplacement)
    {
        this(StyledString.s(TranslationUtility.getString(titleKey)),
            ImmutableList.of(),
            replacementTarget, makeReplacement);
    }

    public QuickFix(StyledString title, ImmutableList<String> cssClasses, @Recorded EXPRESSION replacementTarget, QuickFixReplace<@UnknownIfRecorded EXPRESSION> makeReplacement)
    {
        this(title, cssClasses, replacementTarget, Either.right(makeReplacement));
    }

    public QuickFix(StyledString title, ImmutableList<String> cssClasses, @Recorded EXPRESSION replacementTarget, QuickFixAction action)
    {
        this(title, Utility.prependToList("quick-fix-action", cssClasses), replacementTarget, Either.left(action));
    }

    private QuickFix(StyledString title, ImmutableList<String> cssClasses, @Recorded EXPRESSION replacementTarget, Either<QuickFixAction, QuickFixReplace<@UnknownIfRecorded EXPRESSION>> actionOrMakeReplacement)
    {
        this.title = title;
        this.cssClasses = new FXPlatformSupplier<ImmutableList<String>>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public ImmutableList<String> get()
            {
                try
                {
                    return Utility.<String>concatI(cssClasses, actionOrMakeReplacement.<ImmutableList<String>>eitherInt(a -> ImmutableList.<String>of(), r -> ImmutableList.<String>of(ExpressionUtil.makeCssClass(r.makeReplacement()))));
                }
                catch (InternalException e)
                {
                    Log.log(e);
                    return cssClasses;
                }
            }
        };
        this.replacementTarget = replacementTarget;
        this.actionOrMakeReplacement = actionOrMakeReplacement;
    }

    @OnThread(Tag.Any)
    public StyledString getTitle()
    {
        return title;
    }
    
    @OnThread(Tag.FXPlatform)
    // Gets the replacement, or action to perform
    public Either<QuickFixAction, QuickFixReplace<@UnknownIfRecorded EXPRESSION>> getActionOrReplacement()
    {
        return actionOrMakeReplacement;
    }

    public @Recorded EXPRESSION getReplacementTarget()
    {
        return replacementTarget;
    }

    @OnThread(Tag.FXPlatform)
    public ImmutableList<String> getCssClasses()
    {
        return cssClasses.get();
    }
    
    public static interface QuickFixAction
    {
        // Will only be called once.  If non-null return, force-close expression editor
        // and then run the item on the simulation thread.
        @OnThread(Tag.FXPlatform)
        public @Nullable SimulationConsumer<Pair<@Nullable ColumnId, Expression>> doAction(TypeManager typeManager, ObjectExpression<Scene> editorSceneProperty);
    }
    
    public static interface QuickFixReplace<EXPRESSION>
    {
        // Note -- may be called multiple times
        public EXPRESSION makeReplacement() throws InternalException;
    }
}
