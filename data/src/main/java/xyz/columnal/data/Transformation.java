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

package xyz.columnal.data;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javafx.application.Platform;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.DataTypeValue.OverrideSet;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.MainLexer;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.id.TableId;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.loadsave.OutputBuilder.QuoteBehaviour;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 21/10/2016.
 */
public abstract class Transformation extends Table
{
    public Transformation(TableManager mgr, InitialLoadDetails initialLoadDetails)
    {
        super(mgr, initialLoadDetails);
    }

    @OnThread(Tag.Any)
    public final ImmutableSet<TableId> getSources()
    {
        return Stream.concat(getPrimarySources(), getSourcesFromExpressions()).collect(ImmutableSet.<TableId>toImmutableSet());
    }

    // Which tables are used in expressions?
    @OnThread(Tag.Any)
    protected abstract Stream<TableId> getSourcesFromExpressions();

    // Which tables are declared as our primary source, if any (for filter, transform, concat, etc)
    @OnThread(Tag.Any)
    protected abstract Stream<TableId> getPrimarySources();

    @Override
    @OnThread(Tag.Simulation)
    public final void save(@Nullable File destination, Saver then, TableAndColumnRenames renames)
    {
        OutputBuilder b = new OutputBuilder();
        b.t(MainLexer.TRANSFORMATION).begin().raw(saveTag.getTag()).nl();
        b.pushPrefix(saveTag);
        b.id(renames.tableId(getId())).nl();
        b.id(getTransformationName(), QuoteBehaviour.DEFAULT).nl();
        b.t(MainLexer.SOURCE);
        for (@NonNull TableId src : getPrimarySources().collect(ImmutableList.<@NonNull TableId>toImmutableList()))
            b.id(renames.tableId(src));
        b.nl();
        b.begin().nl();
        for (String line : saveDetail(destination, renames))
        {
            b.raw(line).nl();
        }
        b.end().nl();
        savePosition(b);
        b.pop();
        b.end().raw(saveTag.getTag()).t(MainLexer.TRANSFORMATION).nl();
        then.saveTable(b.toString());
    }

    // The name as used when saving:
    @OnThread(Tag.Any)
    protected abstract String getTransformationName();

    @OnThread(Tag.Simulation)
    protected abstract List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames);

    // hashCode and equals must be implemented properly (used for testing).
    // To make sure we don't forget, we introduce abstract methods which must
    // be overridden.  (We don't make hashCode and equals themselves abstract
    // because subclasses would then lose access to Table.hashCode which they'd need to implement their hash code).
    @Override
    public final int hashCode()
    {
        return 31 * super.hashCode() + transformationHashCode();
    }

    protected abstract int transformationHashCode();

    @Override
    public final boolean equals(@Nullable Object obj)
    {
        if (!super.equals(obj))
            return false;
        return transformationEquals((Transformation)obj);
    }

    protected abstract boolean transformationEquals(Transformation obj);

    // Mainly for testing:
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public String toString()
    {
        StringBuilder b = new StringBuilder();
        save(null, new Saver()
        {
            @Override
            public @OnThread(Tag.Simulation) void saveTable(String tableSrc)
            {
                b.append(tableSrc);
            }

            @Override
            public @OnThread(Tag.Simulation) void saveUnit(String unitSrc)
            {
                b.append(unitSrc);
            }

            @Override
            public @OnThread(Tag.Simulation) void saveType(String typeSrc)
            {
                b.append(typeSrc);
            }

            @Override
            public @OnThread(Tag.Simulation) void saveComment(String commentSrc)
            {
                b.append(commentSrc);
            }
        }, TableAndColumnRenames.EMPTY);
        return b.toString();
    }

    // Should be overridden by any transformation where any of these are possible.
    @Override
    public @OnThread(Tag.Any) TableOperations getOperations()
    {
        return new TableOperations(getManager().getRenameTableOperation(this), c -> null, null, null, null);
    }
    
    protected final void ensureBoolean(DataType dataType) throws UserException
    {
        if (!dataType.equals(DataType.BOOLEAN))
        {
            throw new UserException("Required Boolean type, but found: " + dataType);
        }
    }
    
    @OnThread(Tag.Any)
    protected final DataTypeValue addManualEditSet(@UnknownInitialization(Transformation.class) Transformation this, ColumnId columnId, DataTypeValue original) throws InternalException
    {
        return original.withSet(new OverrideSet()
        {
            @Override
            public void set(int index, Either<String, @Value Object> value) throws UserException
            {
                // Need to ask the user if they want a manual edit
                Platform.runLater(() -> {
                    TableDisplayBase display = Utility.later(Transformation.this).getDisplay();
                    if (display != null)
                        display.promptForTransformationEdit(index, new Pair<>(columnId, original.getType()), value);
                });
                
                throw new SilentCancelEditException("Can't edit a transformation's data.");
            }
        });
    }

    public static class SilentCancelEditException extends UserException
    {
        public SilentCancelEditException(String message)
        {
            super(message);
        }
    }
}
