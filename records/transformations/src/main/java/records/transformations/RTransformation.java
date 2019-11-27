package records.transformations;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.CellPosition;
import records.data.EditableRecordSet;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.Versions.ExpressionVersion;
import records.rinterop.RData;
import records.rinterop.RData.RValue;
import records.rinterop.RExecution;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;

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
    private final ImmutableList<TableId> srcTableIds;
    // The packages to load first.
    private final ImmutableList<String> packagesToLoad;
    // This is the expression to calculate in R:
    private final String rExpression;
    
    private @MonotonicNonNull Either<StyledString, RecordSet> result;
    
    public RTransformation(TableManager tableManager, InitialLoadDetails initialLoadDetails, ImmutableList<TableId> srcTableIds, ImmutableList<String> packagesToLoad, String rExpression)
    {
        super(tableManager, initialLoadDetails);
        this.srcTableIds = srcTableIds;
        this.packagesToLoad = packagesToLoad;
        this.rExpression = rExpression;
    }

    @Override
    protected Stream<TableId> getSourcesFromExpressions()
    {
        return Stream.of();
    }

    @Override
    protected Stream<TableId> getPrimarySources()
    {
        return srcTableIds.stream();
    }

    @Override
    protected String getTransformationName()
    {
        return NAME;
    }

    @Override
    protected List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        return Arrays.stream(Utility.splitLines(rExpression)).map(l -> "@R " + l).collect(ImmutableList.<String>toImmutableList());
    }

    @Override
    protected int transformationHashCode()
    {
        return Objects.hash(srcTableIds, rExpression);
    }

    @Override
    protected boolean transformationEquals(@Nullable Transformation obj)
    {
        if (!(obj instanceof RTransformation))
            return false;
        RTransformation that = (RTransformation)obj;
        
        return srcTableIds.equals(that.srcTableIds) && rExpression.equals(that.rExpression);
    }

    @Override
    public RecordSet getData() throws UserException, InternalException
    {
        if (result == null)
        {
            HashMap<String, RecordSet> tablesToPass = new HashMap<>();

            for (TableId srcTableId : srcTableIds)
            {
                Table t = getManager().getSingleTableOrThrow(srcTableId);
                tablesToPass.put(RData.usToRTable(t.getId()), t.getData());
            }

            RValue rResult = RExecution.runRExpression(rExpression, packagesToLoad, ImmutableMap.copyOf(tablesToPass));

            ImmutableList<Pair<String, EditableRecordSet>> tables = RData.convertRToTable(getManager().getTypeManager(), rResult);
            
            if (tables.isEmpty())
                result = Either.left(StyledString.s("R result empty"));
            else if (tables.size() > 1)
                result = Either.left(StyledString.s("R result has multiple tables"));
            else
                result = Either.right(tables.get(0).getSecond());
        }
        return result.eitherEx(err -> {throw new UserException(err);}, r -> r);
    }

    @Override
    public <T> T visit(TransformationVisitor<T> visitor)
    {
        return visitor.runR(this);
    }

    @Pure
    public String getRExpression()
    {
        return rExpression;
    }
    
    public ImmutableList<String> getPackagesToLoad()
    {
        return packagesToLoad;
    }

    public ImmutableList<TableId> getInputTables()
    {
        return srcTableIds;
    }

    public static class Info extends TransformationInfo
    {
        public Info()
        {
            super(NAME, "transform.runr", "preview-run.png", "runr.explanation.short", ImmutableList.of("R"));
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
}
