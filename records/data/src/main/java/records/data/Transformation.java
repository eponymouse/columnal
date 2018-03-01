package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.grammar.MainLexer;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.util.List;

/**
 * Created by neil on 21/10/2016.
 */
public abstract class Transformation extends Table
{
    public Transformation(TableManager mgr, InitialLoadDetails initialLoadDetails)
    {
        super(mgr, initialLoadDetails);
    }

    // Label to show between arrows:
    @OnThread(Tag.FXPlatform)
    public abstract String getTransformationLabel();

    @OnThread(Tag.Any)
    public abstract List<TableId> getSources();

    @Override
    @OnThread(Tag.Simulation)
    public final void save(@Nullable File destination, Saver then, TableAndColumnRenames renames)
    {
        OutputBuilder b = new OutputBuilder();
        // transformation : TRANSFORMATION tableId transformationName NEWLINE transformationDetail+;
        b.t(MainLexer.TRANSFORMATION).id(renames.tableId(getId())).id(getTransformationName()).nl();
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
    // because subclasses would then lose access to Table.hashCode which they'd need).
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
        return new TableOperations(getManager().getRenameTableOperation(this), null, c -> null, c -> null, null, null, null);
    }

    @Override
    public void addColumn(String newColumnName, DataType newColumnType, @Value Object newColumnValue) throws InternalException
    {
        throw new InternalException("Called addColumn despite showAddColumnButton returning false for type " + getClass());
    }

    @Override
    @OnThread(Tag.Any)
    public Table.MessageWhenEmpty getDisplayMessageWhenEmpty()
    {
        return new MessageWhenEmpty("table.transformation.noColumns", "table.transformation.noRows");
    }
}
