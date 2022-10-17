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

package xyz.columnal.transformations;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.RenameOnEdit;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.Versions.ExpressionVersion;
import xyz.columnal.rinterop.ConvertFromR;
import xyz.columnal.rinterop.RExecution;
import xyz.columnal.rinterop.RValue;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunctionInt;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.function.simulation.SimulationSupplier;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@OnThread(Tag.Simulation)
public class RTransformation extends VisitableTransformation
{
    public static final String NAME = "runr";
    // Each table id maps to an R variable by replacing space with dot.
    @OnThread(Tag.Any)
    private final ImmutableList<TableId> srcTableIds;
    // The packages to load first.
    @OnThread(Tag.Any)
    private final ImmutableList<String> packagesToLoad;
    // This is the expression to calculate in R:
    @OnThread(Tag.Any)
    private final String rExpression;
    
    @OnThread(Tag.Any)
    private final Either<StyledString, RecordSet> result;
    
    public RTransformation(TableManager tableManager, InitialLoadDetails initialLoadDetails, ImmutableList<TableId> srcTableIds, ImmutableList<String> packagesToLoad, String rExpression) throws InternalException
    {
        super(tableManager, initialLoadDetails);
        this.srcTableIds = srcTableIds;
        this.packagesToLoad = packagesToLoad;
        this.rExpression = rExpression;
        this.result = runR();
    }

    @Override
    @OnThread(Tag.Any)
    protected Stream<TableId> getSourcesFromExpressions()
    {
        return Stream.of();
    }

    @Override
    @OnThread(Tag.Any)
    protected Stream<TableId> getPrimarySources()
    {
        return srcTableIds.stream();
    }

    @Override
    @OnThread(Tag.Any)
    protected String getTransformationName()
    {
        return NAME;
    }

    @Override
    protected List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        return Stream.<String>concat(
            packagesToLoad.stream().<String>map(pkg -> "@PACKAGE " + pkg),
            Arrays.stream(Utility.splitLines(rExpression)).<String>map(l -> "@R " + l))
            .collect(ImmutableList.<String>toImmutableList());
    }

    @Override
    protected int transformationHashCode()
    {
        return Objects.hash(packagesToLoad, srcTableIds, rExpression);
    }

    @Override
    protected boolean transformationEquals(@Nullable Transformation obj)
    {
        if (!(obj instanceof RTransformation))
            return false;
        RTransformation that = (RTransformation)obj;
        
        return srcTableIds.equals(that.srcTableIds) && packagesToLoad.equals(that.packagesToLoad) && rExpression.equals(that.rExpression);
    }

    @Override
    @OnThread(Tag.Any)
    public RecordSet getData() throws UserException, InternalException
    {
        return result.eitherEx(err -> {throw new UserException(err);}, r -> r);
    }
    
    @RequiresNonNull({"srcTableIds", "rExpression", "packagesToLoad"})
    @OnThread(Tag.Simulation)
    private Either<StyledString, RecordSet> runR(@UnknownInitialization(Transformation.class) RTransformation this) throws InternalException
    {
        try
        {
            // Throws an exception if not OK:
            getManager().checkROKToRun(rExpression);
            
            HashMap<String, RecordSet> tablesToPass = new HashMap<>();

            for (TableId srcTableId : srcTableIds)
            {
                Table t = getManager().getSingleTableOrThrow(srcTableId);
                tablesToPass.put(ConvertFromR.usToRTable(t.getId()), t.getData());
            }

            RValue rResult = RExecution.runRExpression(rExpression, packagesToLoad, ImmutableMap.copyOf(tablesToPass));

            ImmutableList<Pair<String, EditableRecordSet>> tables = ConvertFromR.convertRToTable(getManager().getTypeManager(), rResult, false);

            if (tables.isEmpty())
                return Either.left(StyledString.s("R result empty"));
            else if (tables.size() > 1)
                return Either.left(StyledString.s("R result has multiple tables"));
            else
                return Either.right(tables.get(0).getSecond());
        }
        catch (UserException e)
        {
            return Either.left(e.getStyledMessage());
        }
    }

    @Override
    @OnThread(Tag.Any)
    public <T> T visit(TransformationVisitor<T> visitor)
    {
        return visitor.runR(this);
    }

    @Override
    @OnThread(Tag.Any)
    public @Nullable SimulationRunnable getReevaluateOperation()
    {
        return () -> {
            getManager().unban(rExpression);
            getManager().edit(RTransformation.this, new SimulationFunctionInt<TableId, RTransformation>()
            {
                @Override
                public RTransformation apply(TableId id) throws InternalException
                {
                    return new RTransformation(RTransformation.this.getManager(), RTransformation.this.getDetailsForCopy(id), srcTableIds, packagesToLoad, rExpression);
                }
            }, RenameOnEdit.UNNEEDED /* Name is unchanged so don't need to worry about that */);
        };
    }

    @Pure
    @OnThread(Tag.Any)
    public String getRExpression()
    {
        return rExpression;
    }
    
    @Pure
    @OnThread(Tag.Any)
    public ImmutableList<String> getPackagesToLoad()
    {
        return packagesToLoad;
    }

    @Pure
    @OnThread(Tag.Any)
    public ImmutableList<TableId> getInputTables()
    {
        return srcTableIds;
    }

    public static class Info extends TransformationInfo
    {
        public Info()
        {
            super(NAME, "transform.runr", "preview-runr.png", "runr.explanation.short", ImmutableList.of("R"));
        }

        @Override
        public Transformation load(TableManager mgr, InitialLoadDetails initialLoadDetails, List<TableId> source, String detail, ExpressionVersion expressionVersion) throws InternalException, UserException
        {
            String[] lines = Utility.splitLines(detail);
            ImmutableList<String> pkgs = Arrays.stream(lines).filter(s -> s.startsWith("@PACKAGE ")).map(s -> s.substring("@PACKAGE ".length())).collect(ImmutableList.<String>toImmutableList());
            
            String rExpression = Arrays.stream(lines).filter(s -> s.startsWith("@R ")).map(s -> s.substring("@R ".length())).collect(Collectors.joining("\n"));
            
            return new RTransformation(mgr, initialLoadDetails, ImmutableList.copyOf(source), pkgs, rExpression);
        }

        @Override
        public @Nullable SimulationSupplier<Transformation> make(TableManager mgr, CellPosition destination, FXPlatformSupplier<Optional<Table>> askForSingleSrcTable)
        {
            return () -> new RTransformation(mgr, new InitialLoadDetails(destination), ImmutableList.of(), ImmutableList.of(), "1+2");
        }
    }

    @Override
    public TableId getSuggestedName()
    {
        return suggestedName(rExpression);
    }

    public static TableId suggestedName(String rExpression)
    {
        return new TableId(IdentifierUtility.spaceSeparated("R", IdentifierUtility.shorten(IdentifierUtility.fixExpressionIdentifier(rExpression, "expr"))));
    }
}
