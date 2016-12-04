package records.transformations;

import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
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
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.ColumnId;
import records.data.NumericColumnStorage;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DisplayValue;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.TypeState;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Created by neil on 23/11/2016.
 */
@OnThread(Tag.Simulation)
public class Filter extends Transformation
{
    private static final String PREFIX = "KEEPIF";
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
    public Filter(TableManager mgr, @Nullable TableId tableId, TableId srcTableId, Expression filterExpression) throws InternalException
    {
        super(mgr, tableId);
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
                List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
                RecordSet data = src.getData();
                for (Column c : data.getColumns())
                {
                    columns.add(rs -> new Column(rs)
                    {
                        @Override
                        public @OnThread(Tag.Any) ColumnId getName()
                        {
                            return c.getName();
                        }

                        @Override
                        @SuppressWarnings({"nullness", "initialization"})
                        public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
                        {
                            return c.getType().copyReorder((i, prog) ->
                            {
                                fillIndexMapTo(i, data, prog);
                                return indexMap.get(i).intValue();
                            });
                        }
                    });
                }

                theRecordSet = new RecordSet("Filtered", columns)
                {
                    @Override
                    public boolean indexValid(int index) throws UserException, InternalException
                    {
                        if (index < indexMap.filled())
                            return true;

                        fillIndexMapTo(index, data, null);
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

    private void fillIndexMapTo(int index, RecordSet data, @Nullable ProgressListener prog) throws UserException, InternalException
    {
        if (type == null)
        {
            if (!typeChecked)
            {
                // Must set it before, in case it throws:
                typeChecked = true;
                @Nullable DataType checked = filterExpression.check(data, new TypeState(), (e, s) -> {throw new UserException(s);});
                if (checked != null)
                    type = checked;

            }
            if (type == null)
                return;
        }

        int start = indexMap.filled();
        while (indexMap.filled() <= index && data.indexValid(nextIndexToExamine))
        {
            boolean keep = filterExpression.getBoolean(nextIndexToExamine, new EvaluateState(), prog);
            if (keep)
                indexMap.add(nextIndexToExamine);
            nextIndexToExamine += 1;

            if (prog != null)
                prog.progressUpdate((double)(indexMap.filled() - start) / (double)(index - start));
        }
    }

    @Override
    public @OnThread(Tag.FXPlatform) String getTransformationLabel()
    {
        return "Filter";
    }

    @Override
    public @OnThread(Tag.FXPlatform) List<TableId> getSources()
    {
        return Collections.singletonList(srcTableId);
    }

    @Override
    public @OnThread(Tag.FXPlatform) TransformationEditor edit()
    {
        return new Editor(getId(), srcTableId, src);
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return "filter";
    }

    @Override
    protected @OnThread(Tag.FXPlatform) List<String> saveDetail(@Nullable File destination)
    {
        return Collections.singletonList(PREFIX + " " + filterExpression.save(true));
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException
    {
        if (recordSet == null)
            throw new UserException(error);
        return recordSet;
    }

    private static class Editor extends TransformationEditor
    {
        private final @Nullable TableId thisTableId;
        private final TableId srcTableId;
        private final @Nullable Table src;
        private final List<ColumnId> allColumns = new ArrayList<>();
        private final TextField rawField;
        private final ObservableList<List<DisplayValue>> srcHeaderAndData;
        private final ObservableList<List<DisplayValue>> destHeaderAndData;

        @OnThread(Tag.FXPlatform)
        public Editor(@Nullable TableId thisTableId, TableId srcTableId, @Nullable Table src)
        {
            this.thisTableId = thisTableId;
            this.srcTableId = srcTableId;
            this.src = src;
            this.rawField = new TextField("");
            this.srcHeaderAndData = FXCollections.observableArrayList();
            this.destHeaderAndData = FXCollections.observableArrayList();
        }

        private void updateExample(Expression expression) throws UserException, InternalException
        {
            if (src == null)
                return;
            if (expression.check(src.getData(), new TypeState(), (e, s) -> {}) == null)
                return;

            if (allColumns.isEmpty())
            {
                allColumns.addAll(src.getData().getColumnIds());
            }
            /*
            Map<ColumnId, Long> itemToFreq = expression.allColumnNames().collect(Collectors.groupingBy(Function.<ColumnId>identity(), Collectors.<ColumnId>counting()));
            List<ColumnId> sortedByFreq = itemToFreq.entrySet().stream().sorted(Entry.<ColumnId, Long>comparingByValue(Comparator.<Long>reverseOrder())).<ColumnId>map(Entry::getKey).limit(3).collect(Collectors.toList());
            if (sortedByFreq.size() < 3)
            {
                // Add any other columns to make it up:
                for (ColumnId c : allColumns)
                {
                    if (!sortedByFreq.contains(c))
                    {
                        sortedByFreq.add(c);
                        if (sortedByFreq.size() == 3)
                            break;
                    }
                }
            }
*/
            Configuration config = Configuration.defaultConfiguration();
            try
            {
                LogManager logger = BasicLogManager.create(config);
                ShutdownManager shutdown = ShutdownManager.create();
                SolverContext ctx = SolverContextFactory.createSolverContext(
                    config, logger, shutdown.getNotifier(), Solvers.Z3);

                Map<Pair<@Nullable TableId, ColumnId>, Formula> vars = new HashMap<>();
                BooleanFormula f = (BooleanFormula)expression.toSolver(ctx.getFormulaManager(), src.getData(), vars);

                System.out.println("Example: " + f.toString());

                try (ProverEnvironment prover = ctx.newProverEnvironment(ProverOptions.GENERATE_MODELS)) {
                    prover.addConstraint(f);
                    for (Formula var : vars.values())
                    {
                        if (var instanceof BitvectorFormula)
                        {
                            prover.addConstraint(asciiRange(ctx.getFormulaManager().getBooleanFormulaManager(), ctx.getFormulaManager().getBitvectorFormulaManager(), (BitvectorFormula)var, 0));
                        }
                    }


                    boolean isUnsat = prover.isUnsat();
                    System.out.println("Satisfiable: " + !isUnsat);
                    if (!isUnsat) {
                        Model model = prover.getModel();
                        for (Entry<Pair<@Nullable TableId, ColumnId>, Formula> var : vars.entrySet())
                        {
                            System.out.println("Variable " + var.getKey() + " = " + show(model.evaluate(var.getValue())));
                        }
                    }
                }
                try (ProverEnvironment prover = ctx.newProverEnvironment(ProverOptions.GENERATE_MODELS)) {
                    prover.addConstraint(ctx.getFormulaManager().getBooleanFormulaManager().not(f));
                    /*for (Formula var : vars.values())
                    {
                        if (var instanceof BitvectorFormula)
                        {
                            prover.addConstraint(asciiRange(ctx.getFormulaManager().getBooleanFormulaManager(), ctx.getFormulaManager().getBitvectorFormulaManager(), (BitvectorFormula)var, 0));
                        }
                    }*/

                    boolean isUnsat = prover.isUnsat();
                    System.out.println("Satisfiable negation: " + !isUnsat);
                    if (!isUnsat) {
                        Model model = prover.getModel();
                        for (Entry<Pair<@Nullable TableId, ColumnId>, Formula> var : vars.entrySet())
                        {
                            System.out.println("Variable " + var.getKey() + " = " + show(model.evaluate(var.getValue())));
                        }
                    }
                }
            }
            catch (UserException | InternalException e)
            {
                e.printStackTrace();
            }
            catch (ClassCastException e)
            {
                // Types issue
                e.printStackTrace();
            }
            catch (InvalidConfigurationException | SolverException | InterruptedException e)
            {
                // Never mind then...
                e.printStackTrace();
            }

        }

        private String show(@Nullable Object v)
        {
            if (v == null)
                return "null";
            if (v instanceof BigInteger)
            {
                BigInteger i = (BigInteger) v;
                StringBuilder sb = new StringBuilder();
                for (int nextCodePoint = i.intValue(); nextCodePoint != 0; i = i.shiftRight(32), nextCodePoint = i.intValue())
                {
                    sb.appendCodePoint(nextCodePoint);
                }
                return sb.reverse().toString();
            }
            return v.getClass().toString();
        }

        private BooleanFormula asciiRange(BooleanFormulaManager b, BitvectorFormulaManager v, BitvectorFormula cur, int index)
        {
            if (index >= Expression.MAX_STRING_SOLVER_LENGTH)
                return b.makeBoolean(true);

            // A valid option is string is blank from here on
            BooleanFormula isZero = v.equal(cur, v.makeBitvector(32 * Expression.MAX_STRING_SOLVER_LENGTH, 0));
            // If it's not all blank, we look at next character and check it is in ASCII range
            BitvectorFormula nextChar = v.and(cur, v.makeBitvector(32 * Expression.MAX_STRING_SOLVER_LENGTH, 127));
            BooleanFormula nextCharASCII = b.and(v.lessThan(v.makeBitvector(32 * Expression.MAX_STRING_SOLVER_LENGTH, 32), nextChar, false), v.lessThan(nextChar, v.makeBitvector(32 * Expression.MAX_STRING_SOLVER_LENGTH, 122), false));
            BooleanFormula thereafter = asciiRange(b, v, v.shiftRight(cur, v.makeBitvector(32 * Expression.MAX_STRING_SOLVER_LENGTH, 32), false), index + 1);
            return b.or(isZero, b.and(nextCharASCII, thereafter));
        }

        @Override
        public @OnThread(Tag.FX) StringExpression displayTitle()
        {
            return new ReadOnlyStringWrapper("Filter");
        }

        @Override
        public Pane getParameterDisplay(FXPlatformConsumer<Exception> reportError)
        {
            Utility.addChangeListenerPlatform(rawField.textProperty(), text -> {
                try
                {
                    if (text != null)
                        updateExample(Expression.parse(null, text));
                }
                catch (Exception e)
                {
                    // Never mind, don't update
                    reportError.consume(e);
                }
            });
            try
            {
                updateExample(new BooleanLiteral(true));
            }
            catch (Exception e)
            {
                reportError.consume(e);
            }

            Node example = createExplanation(srcHeaderAndData, destHeaderAndData, "Filter examines each row separately.  It evaluates the given expression for that row.  If the expression is true, the row is kept; if it's false, the row is removed.  For example, if you have a column called price, and you write\n@price >= 100\nas your expression then only rows where the price is 100 or higher will be kept.");
            return new VBox(rawField, example);
        }

        @Override
        public BooleanExpression canPressOk()
        {
            return new ReadOnlyBooleanWrapper(true);
        }

        @Override
        public SimulationSupplier<Transformation> getTransformation(TableManager mgr)
        {
            String expr = rawField.getText();
            if (expr.isEmpty())
                expr = "true";
            String exprFinal = expr;
            return () -> new Filter(mgr, thisTableId, srcTableId, Expression.parse(null, exprFinal));
        }

        @Override
        public @Nullable Table getSource()
        {
            return src;
        }

        @Override
        public TableId getSourceId()
        {
            return srcTableId;
        }
    }

    public static class Info extends SingleSourceTransformationInfo
    {
        public Info()
        {
            super("filter", Arrays.asList("remove", "delete"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation loadSingle(TableManager mgr, TableId tableId, TableId srcTableId, String detail) throws InternalException, UserException
        {
            return new Filter(mgr, tableId, srcTableId, Expression.parse(PREFIX, detail));
        }

        @Override
        public @OnThread(Tag.FXPlatform) TransformationEditor editNew(TableId srcTableId, @Nullable Table src)
        {
            return new Editor(null, srcTableId, src);
        }
    }
}
