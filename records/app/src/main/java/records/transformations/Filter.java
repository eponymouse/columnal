package records.transformations;

import annotation.qual.Value;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.i18n.qual.Localized;
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
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.gui.SingleSourceControl;
import records.gui.View;
import records.gui.expressioneditor.ExpressionEditor;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.ErrorRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.TypeState;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;
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

/**
 * Created by neil on 23/11/2016.
 */
@OnThread(Tag.Simulation)
public class Filter extends TransformationEditable
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
                List<ExFunction<RecordSet, Column>> columns = new ArrayList<>();
                RecordSet data = src.getData();
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
                                fillIndexMapTo(i, data, prog);
                                return DataTypeUtility.value(indexMap.getInt(i));
                            });
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
                ErrorRecorderStorer errors = new ErrorRecorderStorer();
                @Nullable DataType checked = filterExpression.check(data, new TypeState(getManager().getUnitManager(), getManager().getTypeManager()), errors);
                if (checked != null)
                    type = checked;
                else
                    throw new UserException((@NonNull String)errors.getAllErrors().findFirst().orElse("Unknown type error"));

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
    @OnThread(Tag.Any)
    public List<TableId> getSources()
    {
        return Collections.singletonList(srcTableId);
    }

    @Override
    public @OnThread(Tag.FXPlatform) TransformationEditor edit(View view)
    {
        return new Editor(view, getManager(), getId(), srcTableId, src, filterExpression);
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return "filter";
    }

    @Override
    protected @OnThread(Tag.Any) List<String> saveDetail(@Nullable File destination)
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

    public Expression getFilterExpression()
    {
        return filterExpression;
    }

    private static class Editor extends TransformationEditor
    {
        private final @Nullable TableId thisTableId;
        private final SingleSourceControl srcControl;
        private final List<ColumnId> allColumns = new ArrayList<>();
        //private final ObservableList<Pair<String, List<DisplayValue>>> srcHeaderAndData;
        //private final ObservableList<Pair<String, List<DisplayValue>>> destHeaderAndData;
        private final TableManager mgr;
        private Expression expression;
        private final ExpressionEditor expressionEditor;

        @SuppressWarnings("initialization")
        @OnThread(Tag.FXPlatform)
        public Editor(View view, TableManager mgr, @Nullable TableId thisTableId, @Nullable TableId srcTableId, @Nullable Table src, Expression expression)
        {
            this.mgr = mgr;
            this.thisTableId = thisTableId;
            this.srcControl = new SingleSourceControl(view, mgr, srcTableId);
            //this.srcHeaderAndData = FXCollections.observableArrayList();
            //this.destHeaderAndData = FXCollections.observableArrayList();
            this.expression = expression;
            this.expressionEditor = new ExpressionEditor(expression, srcControl.getTableOrNull(), new ReadOnlyObjectWrapper<>(DataType.BOOLEAN), mgr, e -> {
                try
                {
                    updateExample(e);
                    this.expression = e;
                }
                catch (InternalException | UserException ex)
                {
                    Utility.log(ex);
                    // TODO what should we show in interface?
                }
            });
        }

        @OnThread(Tag.FXPlatform)
        private void updateExample(Expression expression) throws UserException, InternalException
        {
            @Nullable Table src = srcControl.getTableOrNull();
            if (src == null)
                return;
            if (expression.check(src.getData(), new TypeState(mgr.getUnitManager(), mgr.getTypeManager()), (e, s, q) -> {}) == null)
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
            // Skip the solver:
            if (true) return;

            Configuration config = Configuration.defaultConfiguration();
            try
            {
                LogManager logger = BasicLogManager.create(config);
                ShutdownManager shutdown = ShutdownManager.create();
                SolverContext ctx = SolverContextFactory.createSolverContext(
                    config, logger, shutdown.getNotifier(), Solvers.Z3);

                Map<Pair<@Nullable TableId, ColumnId>, @NonNull Formula> vars = new HashMap<>();
                BooleanFormula f = (BooleanFormula)expression.toSolver(ctx.getFormulaManager(), src.getData(), vars);

                //System.out.println("Example: " + f.toString());

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
                    //System.out.println("Satisfiable: " + !isUnsat);
                    if (!isUnsat) {
                        //srcHeaderAndData.clear();
                        Model model = prover.getModel();
                        for (Entry<Pair<@Nullable TableId, ColumnId>, @NonNull Formula> var : vars.entrySet())
                        {
                            Object evaluated = model.evaluate(var.getValue());
                            if (evaluated != null)
                            {
                                //System.out.println("Variable " + var.getKey() + " = " + show(evaluated));
                                //srcHeaderAndData.add(new Pair<String, List<DisplayValue>>(var.getKey().getSecond().getOutput(), Arrays.asList(DataTypeUtility.toDisplayValue(-1, show(evaluated)))));
                                //destHeaderAndData.add(new Pair<String, List<DisplayValue>>(var.getKey().getSecond().getOutput(), Arrays.asList(DataTypeUtility.toDisplayValue(-1, show(evaluated)))));
                            }
                        }
                    }
                }
                try (ProverEnvironment prover = ctx.newProverEnvironment(ProverOptions.GENERATE_MODELS)) {
                    prover.addConstraint(ctx.getFormulaManager().getBooleanFormulaManager().not(f));
                    for (Formula var : vars.values())
                    {
                        if (var instanceof BitvectorFormula)
                        {
                            prover.addConstraint(asciiRange(ctx.getFormulaManager().getBooleanFormulaManager(), ctx.getFormulaManager().getBitvectorFormulaManager(), (BitvectorFormula)var, 0));
                        }
                    }

                    boolean isUnsat = prover.isUnsat();
                    //System.out.println("Satisfiable negation: " + !isUnsat);
                    if (!isUnsat) {
                        Model model = prover.getModel();
                        for (Entry<Pair<@Nullable TableId, ColumnId>, Formula> var : vars.entrySet())
                        {
                            Object evaluated = model.evaluate(var.getValue());
                            if (evaluated != null)
                            {
                                //System.out.println("Variable " + var.getKey() + " = " + show(model.evaluate(var.getValue())));
                                //srcHeaderAndData.add(new Pair<String, List<DisplayValue>>(var.getKey().getSecond().getOutput(), Arrays.asList(DataTypeUtility.toDisplayValue(-1, show(evaluated)))));
                            }
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

        // Not really a @Value return, but satisfies type system:
        @SuppressWarnings("value")
        private @Value String show(@Nullable Object v)
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
        public String getDisplayTitle()
        {
            return "Filter";
        }

        @Override
        public Pair<@Localized String, @Localized String> getDescriptionKeys()
        {
            return new Pair<>("filter.description.short", "filter.description.rest");
        }

        @Override
        public Pane getParameterDisplay(FXPlatformConsumer<Exception> reportError)
        {
            try
            {
                updateExample(new BooleanLiteral(true));
            }
            catch (Exception e)
            {
                reportError.consume(e);
            }

            Node example = new Label("TODO"); //createExplanation(srcHeaderAndData, destHeaderAndData, "");
            FXPlatformConsumer<@Nullable Expression> updater = expression -> {
                if (expression != null)
                {
                    try
                    {
                        updateExample(expression);
                    }
                    catch (InternalException | UserException e)
                    {
                        // Never mind, don't update
                        reportError.consume(e);
                    }
                }
            };

            return new VBox(expressionEditor.getContainer(), example);
        }

        @Override
        public BooleanExpression canPressOk()
        {
            return new ReadOnlyBooleanWrapper(true);
        }

        @Override
        public SimulationSupplier<Transformation> getTransformation(TableManager mgr)
        {
            SimulationSupplier<TableId> srcId = srcControl.getTableIdSupplier();
            return () -> new Filter(mgr, thisTableId, srcId.get(), expression);
        }

        @Override
        public @Nullable TableId getSourceId()
        {
            return srcControl.getTableIdOrNull();
        }
    }

    public static class Info extends SingleSourceTransformationInfo
    {
        public Info()
        {
            super("keep", "Keep rows", Arrays.asList("remove", "delete"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation loadSingle(TableManager mgr, TableId tableId, TableId srcTableId, String detail) throws InternalException, UserException
        {
            return new Filter(mgr, tableId, srcTableId, Expression.parse(PREFIX, detail, mgr.getTypeManager()));
        }

        @Override
        public TransformationEditor editNew(View view, TableManager mgr, @Nullable TableId srcTableId, @Nullable Table src)
        {
            return new Editor(view, mgr, null, srcTableId, src, new BooleanLiteral(true));
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
