package records.data;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.grammar.MainLexer;
import records.loadsave.OutputBuilder;
import records.loadsave.OutputBuilder.QuoteBehaviour;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
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
        return Stream.concat(getPrimarySources(), getSourcesFromExpressions()).collect(ImmutableSet.toImmutableSet());
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
        // transformation : TRANSFORMATION tableId transformationName NEWLINE transformationDetail+;
        b.t(MainLexer.TRANSFORMATION).id(renames.tableId(getId())).id(getTransformationName(), QuoteBehaviour.QUOTE_SPACES).nl();
        b.t(MainLexer.SOURCE);
        for (TableId src : getSources())
            b.id(renames.tableId(src));
        b.nl();
        b.inner(() -> saveDetail(destination, renames));
        savePosition(b);
        b.end().id(renames.tableId(getId())).nl();
        then.saveTable(b.toString());
    }

    // The name as used when saving:
    @OnThread(Tag.Any)
    protected abstract String getTransformationName();

    @OnThread(Tag.Any)
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
        }, TableAndColumnRenames.EMPTY);
        return b.toString();
    }

    // Should be overridden by any transformation where any of these are possible.
    @Override
    public @OnThread(Tag.Any) TableOperations getOperations()
    {
        return new TableOperations(getManager().getRenameTableOperation(this), c -> null, null, null, null);
    }
}
