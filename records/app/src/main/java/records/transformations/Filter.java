package records.transformations;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.SolverContextFactory.Solvers;
import org.sosy_lab.java_smt.api.BitvectorFormula;
import org.sosy_lab.java_smt.api.BitvectorFormulaManager;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext;
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions;
import org.sosy_lab.java_smt.api.SolverException;
import records.data.CellPosition;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.ColumnId;
import records.data.NumericColumnStorage;
import records.data.RecordSet;
import records.data.TableAndColumnRenames;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.errors.ExpressionErrorException;
import records.errors.ExpressionErrorException.EditableExpression;
import records.gui.View;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionEditor;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.MultipleTableLookup;
import records.transformations.expression.Expression.TableLookup;
import records.transformations.expression.TypeState;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.FXPlatformConsumer;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.SimulationFunction;
import utility.SimulationSupplier;
import utility.gui.TranslationUtility;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Created by neil on 23/11/2016.
 */
@OnThread(Tag.Simulation)
public class Filter extends Transformation
{
    private static final String PREFIX = "KEEPIF";
    public static final String NAME = "filter";
    private final TableId srcTableId;
    private final @Nullable Table src;
    // Not actually a column by itself, but holds a list of integers so reasonable to re-use:
    // Each item is a source index in the original list
    private final NumericColumnStorage indexMap;
    private final @Nullable RecordSet recordSet;
    @OnThread(Tag.Any)
    private String error;
    private int nextIndexToExamine = 0;
    @OnThread(Tag.Any)
    private final Expression filterExpression;
    private @MonotonicNonNull DataType type;
    private boolean typeChecked = false;

    @SuppressWarnings("initialization")
    public Filter(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, Expression filterExpression) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.srcTableId = srcTableId;
        this.src = mgr.getSingleTableOrNull(srcTableId);
        this.indexMap = new NumericColumnStorage();
        this.filterExpression = filterExpression;
        this.error = "Unknown error";

        @Nullable RecordSet theRecordSet = null;
        try
        {
            if (src != null)
            {
                List<SimulationFunction<RecordSet, Column>> columns = new ArrayList<>();
                RecordSet data = src.getData();
                TableLookup tableLookup = new MultipleTableLookup(mgr, src);
                for (Column c : data.getColumns())
                {
                    columns.add(rs -> new Column(rs, c.getName())
                    {
                        @Override
                        @SuppressWarnings({"nullness", "initialization"})
                        public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
                        {
                            return c.getType().copyReorder((i, prog) ->
                            {
                                fillIndexMapTo(i, tableLookup, data, prog);
                                return DataTypeUtility.value(indexMap.getInt(i));
                            });
                        }

                        @Override
                        public boolean isAltered()
                        {
                            return true;
                        }
                    });
                }

                theRecordSet = new RecordSet(columns)
                {
                    @Override
                    public boolean indexValid(int index) throws UserException, InternalException
                    {
                        if (index < indexMap.filled())
                            return true;

                        fillIndexMapTo(index, tableLookup, data,null);
                        return index < indexMap.filled();
                    }
                };
            }
        }
        catch (UserException e)
        {
            String msg = e.getLocalizedMessage();
            if (msg != null)
                this.error = msg;
        }
        this.recordSet = theRecordSet;
    }

    private void fillIndexMapTo(int index, TableLookup data, RecordSet recordSet, @Nullable ProgressListener prog) throws UserException, InternalException
    {
        if (type == null)
        {
            if (!typeChecked)
            {
                // Must set it before, in case it throws:
                typeChecked = true;
                ErrorAndTypeRecorderStorer errors = new ErrorAndTypeRecorderStorer();
                @Nullable TypeExp checked = filterExpression.check(data, new TypeState(getManager().getUnitManager(), getManager().getTypeManager()), errors);
                @Nullable DataType typeFinal = null;
                if (checked != null)
                    typeFinal = errors.recordLeftError(getManager().getUnitManager(), filterExpression, checked.toConcreteType(getManager().getTypeManager()));
                
                if (typeFinal == null)
                    throw new ExpressionErrorException(errors.getAllErrors().findFirst().orElse(StyledString.s("Unknown type error")), new EditableExpression(filterExpression, srcTableId, true, DataType.BOOLEAN)
                    {
                        @Override
                        @OnThread(Tag.Simulation)
                        public Table replaceExpression(Expression changed) throws InternalException
                        {
                            return new Filter(getManager(), getDetailsForCopy(), Filter.this.srcTableId, changed);
                        }
                    });
                
                type = typeFinal;
            }
            if (type == null)
                return;
        }

        int start = indexMap.filled();
        while (indexMap.filled() <= index && recordSet.indexValid(nextIndexToExamine))
        {
            boolean keep = filterExpression.getBoolean(nextIndexToExamine, new EvaluateState(getManager().getTypeManager()), prog);
            if (keep)
                indexMap.add(nextIndexToExamine);
            nextIndexToExamine += 1;

            if (prog != null)
                prog.progressUpdate((double)(indexMap.filled() - start) / (double)(index - start));
        }
    }
    
    @Override
    @OnThread(Tag.Any)
    public Stream<TableId> getPrimarySources()
    {
        return Stream.of(srcTableId);
    }

    @Override
    @OnThread(Tag.Any)
    public Stream<TableId> getSourcesFromExpressions()
    {
        return TransformationUtil.tablesFromExpression(filterExpression);
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return NAME;
    }

    @Override
    protected @OnThread(Tag.Any) List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        return Collections.singletonList(PREFIX + " " + filterExpression.save(BracketedStatus.MISC, renames));
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException
    {
        if (recordSet == null)
            throw new UserException(error);
        return recordSet;
    }

    @OnThread(Tag.Any)
    public Expression getFilterExpression()
    {
        return filterExpression;
    }

    @OnThread(Tag.Any)
    public TableId getSource()
    {
        return srcTableId;
    }

    public static class Info extends SingleSourceTransformationInfo
    {
        public Info()
        {
            super(NAME, "Filter rows", "preview-filter.png", "filter.explanation.short", Arrays.asList("remove", "delete"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation loadSingle(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, String detail) throws InternalException, UserException
        {
            return new Filter(mgr, initialLoadDetails, srcTableId, Expression.parse(PREFIX, detail, mgr.getTypeManager()));
        }

        @Override
        @OnThread(Tag.Simulation)
        public Transformation makeWithSource(View view, TableManager mgr, CellPosition destination, Table srcTable) throws InternalException
        {
            return new Filter(mgr, new InitialLoadDetails(null, destination, new Pair<>(Display.ALL, ImmutableList.of())), srcTable.getId(), new BooleanLiteral(true));
        }
    }

    @Override
    public boolean transformationEquals(Transformation o)
    {
        Filter filter = (Filter) o;

        if (!srcTableId.equals(filter.srcTableId)) return false;
        return filterExpression.equals(filter.filterExpression);
    }

    @Override
    public int transformationHashCode()
    {
        int result = srcTableId.hashCode();
        result = 31 * result + filterExpression.hashCode();
        return result;
    }
}
