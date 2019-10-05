package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.beans.binding.ObjectExpression;
import javafx.scene.Scene;
import javafx.scene.text.Text;
import log.Log;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.common.rationals.Rational;
import records.data.Column;
import records.data.Column.AlteredState;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.grammar.ExpressionParser.*;
import records.grammar.ExpressionParserBaseVisitor;
import records.grammar.GrammarUtility;
import records.grammar.Versions.ExpressionVersion;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.DefineExpression.Definition;
import records.transformations.expression.Expression.ColumnLookup.FoundTable;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.QuickFix.QuickFixAction;
import records.transformations.expression.explanation.Explanation;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import records.transformations.expression.explanation.ExplanationLocation;
import records.transformations.expression.function.FunctionLookup;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.transformations.expression.function.ValueFunction;
import records.transformations.expression.function.ValueFunction.RecordedFunctionResult;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.transformations.expression.visitor.ExpressionVisitorStream;
import records.typeExp.ExpressionBase;
import records.typeExp.TypeExp;
import styled.StyledCSS;
import styled.StyledShowable;
import styled.StyledString;
import styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.ExFunction;
import utility.ExSupplier;
import utility.IdentifierUtility;
import utility.Pair;
import utility.SimulationConsumer;
import utility.Utility;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Created by neil on 24/11/2016.
 */
public abstract class Expression extends ExpressionBase implements StyledShowable, Replaceable<Expression>, Explanation.ExplanationSource
{
    public static final int MAX_STRING_SOLVER_LENGTH = 8;

    public static interface ColumnLookup
    {
        public static final class FoundColumn
        {
            public final TableId tableId;
            public final DataTypeValue dataTypeValue;
            public final @Nullable Pair<StyledString, @Nullable QuickFix<Expression>> information;

            public FoundColumn(TableId tableId, DataTypeValue dataTypeValue, @Nullable Pair<StyledString, @Nullable QuickFix<Expression>> information)
            {
                this.tableId = tableId;
                this.dataTypeValue = dataTypeValue;
                this.information = information;
            }
        }
        
        public static interface FoundTable
        {
            public TableId getTableId();

            public ImmutableMap<ColumnId, DataTypeValue> getColumnTypes() throws InternalException, UserException;

            public int getRowCount() throws InternalException, UserException;
        }
        
        // If you pass null for table, you get the default table (or null if none)
        // If no such table/column is found, null is returned
        // Calling getCollapsed  with row number on .dataTypeValue should get corresponding value.
        public @Nullable FoundColumn getColumn(@Recorded ColumnReference columnReference);

        // If no such table is found, null is returned.
        // If null is passed, uses the current table (if applicable; null return if not -- used for converting from column references)
        public @Nullable FoundTable getTable(@Nullable TableId tableName) throws UserException, InternalException;
        
        // This is really for the editor autocomplete, but it doesn't rely on any GUI
        // functionality so can be here:
        public Stream<ColumnReference> getAvailableColumnReferences();

        // This is really for the editor autocomplete, but it doesn't rely on any GUI
        // functionality so can be here:
        public Stream<TableReference> getAvailableTableReferences();

        public static abstract class ClickedReference
        {
            private final TableId tableId;
            private final ColumnId columnId;

            public ClickedReference(TableId tableId, ColumnId columnId)
            {
                this.tableId = tableId;
                this.columnId = columnId;
            }

            public abstract Expression getExpression();

            public TableId getTableId()
            {
                return tableId;
            }

            public ColumnId getColumnId()
            {
                return columnId;
            }
        }
        
        /**
         * Called when the column is clicked, to find out
         * which column reference to insert into the editor.  If the column is not clickable, will return empty stream.
         */
        public Stream<ClickedReference> getPossibleColumnReferences(TableId tableId, ColumnId columnId);


        public default @Nullable QuickFix<Expression> getFixForIdent(@ExpressionIdentifier String ident, @Recorded Expression target)
        {
            return null;
        }
    }

    // Default implementation of FoundTable
    public static final class FoundTableActual implements FoundTable
    {
        private final Table table;

        public FoundTableActual(Table table)
        {
            this.table = table;
        }

        public TableId getTableId()
        {
            return table.getId();
        }

        public ImmutableMap<ColumnId, DataTypeValue> getColumnTypes() throws InternalException, UserException
        {
            ImmutableMap.Builder<ColumnId, DataTypeValue> columns = ImmutableMap.builder();
            for (Column column : table.getData().getColumns())
            {
                columns.put(column.getName(), column.getType());
            }
            return columns.build();
        }

        public int getRowCount() throws InternalException, UserException
        {
            return table.getData().getLength();
        }
    }
    
    // PATTERN infects EXPRESSION: any bit of PATTERN in an inner expression
    // requires the outer expression to either throw an error, or be a PATTERN itself
    public static enum ExpressionKind { EXPRESSION, PATTERN; }

    /**
     * If something is a plain expression, then all we need to know is the TypeExp.
     * 
     * If something is a pattern, we then need to know:
     *  - Its type (as above)
     *  - The resulting type state (since a pattern may modify it)
     *  - The types for which we need Equatable when this is use in a pattern match.
     *  
     *  For example, if you have the tuple:
     *    (3, @anything, existingVar, $newVar)
     *  
     *  This is a pattern.  Its type is (Num, Any, existingVar's type, Any),
     *  its resulting state assigns a type for newVar, and we require Equatable
     *  on Num and existingVar's type.
     *  
     *  This can then validly be matched against:
     *    (3, (? + 1), value of existingVar's type, (? - 1))
     *  Provided existingVar's type is Equatable, without needing Equatable for the functions.
     */

    public static class CheckedExp
    {
        public final @Recorded TypeExp typeExp;
        public final TypeState typeState;
        // We could actually apply these immediately, because it's disallowed
        // to have a pattern outside a pattern match.  But then any creation
        // of a pattern would get that error, plus a whole load of
        // Equatable failures without any equality check in sight.
        // So we store the type that need Equatable here, and apply them once we see the equality match.
        // Always empty if type is EXPRESSION
        private final ImmutableList<TypeExp> equalityRequirements;

        private CheckedExp(@Recorded TypeExp typeExp, TypeState typeState, ImmutableList<TypeExp> equalityRequirements)
        {
            this.typeExp = typeExp;
            this.typeState = typeState;
            this.equalityRequirements = equalityRequirements;
        }

        public CheckedExp(@Recorded TypeExp typeExp, TypeState typeState)
        {
            this(typeExp, typeState, ImmutableList.of());
        }
        
        // Used for things like tuple members, list members.
        // If any of them are patterns, equality constraints are applied to
        // any non-pattern items.  The type state used is the given argument.
        /*
        public static CheckedExp combineStructural(@Recorded TypeExp typeExp, TypeState typeState, ImmutableList<CheckedExp> items)
        {
            boolean anyArePattern = items.stream().anyMatch(c -> c.expressionKind == ExpressionKind.PATTERN);
            ExpressionKind kind = anyArePattern ? ExpressionKind.PATTERN : ExpressionKind.EXPRESSION;
            ImmutableList<TypeExp> reqs;
            if (anyArePattern)
                reqs = items.stream().filter(c -> c.expressionKind != ExpressionKind.PATTERN)
                        .map(c -> c.typeExp)
                        .collect(ImmutableList.<TypeExp>toImmutableList());
            else
                reqs = ImmutableList.of();
            return new CheckedExp(typeExp, typeState, kind, reqs);
        }
        */

        /**
         * Make sure this item is equatable.
         */
        public void requireEquatable()
        {
            /*TODO!
            TypeClassRequirements equatable = TypeClassRequirements.require("Equatable", "<match>");
            if (expressionKind == ExpressionKind.PATTERN)
            {
                for (TypeExp t : equalityRequirements)
                {
                    t.requireTypeClasses(equatable);
                }
            }
            else if (expressionKind == ExpressionKind.EXPRESSION)
            {
                typeExp.requireTypeClasses(equatable);
            }
             */
        }

        // If the argument is null, just return this.
        // If non-null, check the item is an expression, and then apply the operator to our type
        public CheckedExp applyToType(@Nullable UnaryOperator<@Recorded TypeExp> changeType)
        {
            if (changeType == null)
                return this;
            return new CheckedExp(changeType.apply(typeExp), typeState, equalityRequirements);
        }
    }
    
    public static enum LocationInfo
    {
        // Multiply or divide:
        UNIT_MODIFYING,
        // Comparison, add or compare
        UNIT_CONSTRAINED,
        // All the rest:
        UNIT_DEFAULT
    }
    
    // Checks that all used variable names (unless this is a pattern) and column references are defined,
    // and that types check.  Return null if any problems.
    public abstract @Nullable CheckedExp check(@Recorded Expression this, ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException;

    // Calls check with EXPRESSION kind, and returns just the type, discarding the state.
    public final @Nullable TypeExp checkExpression(@Recorded Expression this, ColumnLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable CheckedExp check = check(dataLookup, typeState, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
        if (check == null)
            return null;
        return check.typeExp;
    }

    @OnThread(Tag.Simulation)
    protected final ValueResult result(@Value Object value, EvaluateState state)
    {
        return result(value, state, ImmutableList.of());
    }

    @OnThread(Tag.Simulation)
    protected final ValueResult result(@Value Object value, EvaluateState state, ImmutableList<ValueResult> childrenForExplanations)
    {
        return result(value, state, childrenForExplanations, ImmutableList.of(), false);
    }

    @OnThread(Tag.Simulation)
    protected final ValueResult result(@Value Object value, EvaluateState state, ImmutableList<ValueResult> childrenForExplanations, ImmutableList<ExplanationLocation> usedLocations, boolean skipChildrenIfTrivial)
    {
        return explanation(value, ExecutionType.VALUE, state, childrenForExplanations, usedLocations, skipChildrenIfTrivial);
    }

    @OnThread(Tag.Simulation)
    protected final ValueResult explanation(@Value Object value, ExecutionType executionType, EvaluateState state, ImmutableList<ValueResult> childrenForExplanations, ImmutableList<ExplanationLocation> usedLocations, boolean skipChildrenIfTrivial)
    {
        if (!state.recordExplanation())
        {
            return new ValueResult(value, state)
            {
                @Override
                public Explanation makeExplanation(@Nullable ExecutionType overrideExecutionType) throws InternalException
                {
                    throw new InternalException("Fetching explanation but did not record explanation");
                }
            };
        }
        
        return new ValueResult(value, state)
        {
            @Override
            public Explanation makeExplanation(@Nullable ExecutionType overrideExecutionType)
            {
                return new Explanation(Expression.this, overrideExecutionType != null ? overrideExecutionType : executionType, evaluateState, value, usedLocations)
                {
                    @Override
                    @OnThread(Tag.Simulation)
                    public @Nullable StyledString describe(Set<Explanation> alreadyDescribed, Function<ExplanationLocation, StyledString> hyperlinkLocation, ExpressionStyler expressionStyler, ImmutableList<ExplanationLocation> extraLocations, boolean skipIfTrivial) throws InternalException, UserException
                    {
                        return Expression.this.describe(value,this.executionType, evaluateState, hyperlinkLocation, expressionStyler, Utility.concatI(usedLocations, extraLocations), skipIfTrivial);
                    }

                    @Override
                    @OnThread(Tag.Simulation)
                    public ImmutableList<Explanation> getDirectSubExplanations() throws InternalException
                    {
                        return Utility.mapListInt(childrenForExplanations, e -> e.makeExplanation(null));
                    }

                    @Override
                    public boolean excludeChildrenIfTrivial()
                    {
                        return skipChildrenIfTrivial;
                    }
                };
            }

            @Override
            public ImmutableList<ExplanationLocation> getDirectlyUsedLocations()
            {
                return usedLocations;
            }
        };
    }

    @OnThread(Tag.Simulation)
    @Nullable
    private StyledString describe(@Value Object value, ExecutionType executionType, EvaluateState evaluateState,  Function<ExplanationLocation, StyledString> hyperlinkLocation, ExpressionStyler expressionStyler, ImmutableList<ExplanationLocation> usedLocations, boolean skipIfTrivial) throws UserException, InternalException
    {
        // Don't bother explaining literals, or trivial if we are skipping trivial:
        if (Expression.this.hideFromExplanation(skipIfTrivial))
            return null;
        
        // Or things which result in functions, as output won't be useful:
        if (value instanceof ValueFunction)
            return null;

        StyledString using = usedLocations.isEmpty() ? StyledString.s("") : StyledString.concat(StyledString.s(", using "), usedLocations.stream().filter(l -> l.rowIndex.isPresent()).map(hyperlinkLocation).collect(StyledString.joining(", ")));

        if (executionType == ExecutionType.MATCH && value instanceof Boolean)
        {
            return StyledString.concat(Expression.this.toDisplay(BracketedStatus.DONT_NEED_BRACKETS, expressionStyler), StyledString.s(((Boolean)value) ? " matched" : " did not match"), using);
        }
        else
        {
            return StyledString.concat(Expression.this.toDisplay(BracketedStatus.DONT_NEED_BRACKETS, expressionStyler), StyledString.s(" was "), StyledString.s(DataTypeUtility.valueToString(evaluateState.getTypeFor(Expression.this, executionType), value, null)), using);
        }
    }

    @OnThread(Tag.Simulation)
    protected ValueResult result(EvaluateState state, RecordedFunctionResult recordedFunctionResult)
    {
        return new ValueResult(recordedFunctionResult.result, state)
        {
            @Override
            public Explanation makeExplanation(@Nullable ExecutionType overrideExecutionType)
            {
                return new Explanation(Expression.this, overrideExecutionType != null ? overrideExecutionType : ExecutionType.VALUE, evaluateState, value, recordedFunctionResult.usedLocations)
                {
                    @Override
                    @OnThread(Tag.Simulation)
                    public @Nullable StyledString describe(Set<Explanation> alreadyDescribed, Function<ExplanationLocation, StyledString> hyperlinkLocation, ExpressionStyler expressionStyler, ImmutableList<ExplanationLocation> extraLocations, boolean skipIfTrivial) throws InternalException, UserException
                    {
                        return Expression.this.describe(value, this.executionType, evaluateState, hyperlinkLocation, expressionStyler, Utility.concatI(recordedFunctionResult.usedLocations, extraLocations), skipIfTrivial);
                    }

                    @Override
                    @OnThread(Tag.Simulation)
                    public ImmutableList<Explanation> getDirectSubExplanations() throws InternalException
                    {
                        return recordedFunctionResult.childExplanations;
                    }
                };
            }

            @Override
            public ImmutableList<ExplanationLocation> getDirectlyUsedLocations()
            {
                return recordedFunctionResult.usedLocations;
            }
        };
    }

    /**
     * For convenience this is a non-static class, as the Expression reference
     * is used by the default implementation of makeExplanation.
     * If you override makeExplanation, it doesn't matter which Expression
     * instance is used to construct the object.
     */
    @OnThread(Tag.Simulation)
    public abstract static class ValueResult
    {
        public final @Value Object value;
        // State after execution:
        public final EvaluateState evaluateState;
        
        protected ValueResult(@Value Object value, EvaluateState state)
        {
            this.value = value;
            this.evaluateState = state;
        }

        public abstract Explanation makeExplanation(@Nullable ExecutionType overrideExecutionType) throws InternalException;

        // Locations used directly by this result, not including
        // locations from child explanations
        public ImmutableList<ExplanationLocation> getDirectlyUsedLocations()
        {
            return ImmutableList.of();
        }
    }
    
    /**
     * Gets the value for this expression at the given evaluation state
     */
    @OnThread(Tag.Simulation)
    public abstract ValueResult calculateValue(EvaluateState state) throws UserException, InternalException;

    // Note that there will be duplicates if referred to multiple times
    public final Stream<ColumnReference> allColumnReferences(@Recorded Expression this)
    {
        return visit(new ExpressionVisitorStream<ColumnReference>() {
            @Override
            public Stream<ColumnReference> column(ColumnReference self, @Nullable TableId tableName, ColumnId columnName)
            {
                return Stream.of(self);
            }
        });
    }

    // Note that there will be duplicates if referred to multiple times
    public final Stream<TableReference> allTableReferences(@Recorded Expression this)
    {
        return visit(new ExpressionVisitorStream<TableReference>() {
            @Override
            public Stream<TableReference> table(TableReference self, TableId tableName)
            {
                return Stream.of(self);
            }
        });
    }

    // Note that there will be duplicates if referred to multiple times
    @Override
    @SuppressWarnings("recorded")
    public final Stream<String> allVariableReferences()
    {
        return visit(new ExpressionVisitorStream<String>() {
            @Override
            public Stream<String> ident(IdentExpression self, @ExpressionIdentifier String ident)
            {
                return Stream.of(ident);
            }

            @Override
            public Stream<String> implicitLambdaArg(ImplicitLambdaArg self)
            {
                return Stream.of(self.getVarName());
            }
        });
    }
    
    public abstract <T> T visit(@Recorded Expression this, ExpressionVisitor<T> visitor);

    public static enum SaveDestination
    {
        // Include things like @invalid, @call for saving to disk, clipboard, etc
        SAVE_EXTERNAL,
        // Load into editor, so leave out keywords, don't scope things which don't need scoping:
        EDITOR
    }
    
    /**
     * 
     * @param saveDestination If SAVE_EXTERNAL, include full keywords for things like invalid, function calls etc.
     *                   If EDITOR, give back string which could be entered direct in the GUI.
     * @param surround
     * @param typeManager
     * @param renames
     * @return
     */
    public abstract String save(SaveDestination saveDestination, BracketedStatus surround, @Nullable TypeManager typeManager, TableAndColumnRenames renames);

    @Pure
    public Optional<Rational> constantFold()
    {
        return Optional.empty();
    }
    
    public boolean hideFromExplanation(boolean skipIfTrivial)
    {
        return false;
    }
    
    // Vaguely similar to getValue, but instead checks if the expression matches the given value
    // For many expressions, matching means equality, but if a new-variable item is involved
    // it's not necessarily plain equality.
    // Given that the expression has type-checked, you can assume the value is of the same type
    // as the current expression (and throw an InternalException if not)
    // If you override this, you should also override checkAsPattern
    // If there is a match, returns result with true value.  If no match, returns a result with false value.
    @OnThread(Tag.Simulation)
    public ValueResult matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        ValueResult ourValue = calculateValue(state);
        return explanation(DataTypeUtility.value(Utility.compareValues(value, ourValue.value) == 0), ExecutionType.MATCH, state, ImmutableList.of(ourValue), ourValue.getDirectlyUsedLocations(), false);
    }

    @Override
    public String toString()
    {
        return save(SaveDestination.SAVE_EXTERNAL, BracketedStatus.DONT_NEED_BRACKETS, null, TableAndColumnRenames.EMPTY);
    }

    // This is like a zipper.  It gets a list of all expressions in the tree (i.e. all nodes)
    // and returns them, along with a function.  If you pass that function a replacement,
    // it will build you a new copy of the entire expression with that one node replaced.
    // Used for testing
    public final Stream<Pair<Expression, Function<Expression, Expression>>> _test_allMutationPoints()
    {
        return Stream.<Pair<Expression, Function<Expression, Expression>>>concat(Stream.<Pair<Expression, Function<Expression, Expression>>>of(new Pair<Expression, Function<Expression, Expression>>(this, e -> e)), _test_childMutationPoints());
    }

    public abstract Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints();

    // If this item can't make a type failure by itself (e.g. a literal) then returns null
    // Otherwise, uses the given generator to make a copy of itself which contains a type failure
    // in this node.  E.g. an equals expression might replace the lhs or rhs with a different type
    public abstract @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException;

    // Force sub-expressions to implement equals and hashCode:
    @Override
    public abstract boolean equals(@Nullable Object o);
    @Override
    public abstract int hashCode();

    @Override
    public final StyledString toStyledString()
    {
        return toDisplay(BracketedStatus.DONT_NEED_BRACKETS, (s, e) -> s);
    }

    public static interface ExpressionStyler
    {
        public StyledString styleExpression(StyledString display, Expression src);
    }
    
    protected abstract StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler);

    // Only for testing:
    public static interface _test_TypeVary
    {
        public Expression getDifferentType(@Nullable TypeExp type) throws InternalException, UserException;
        public Expression getAnyType() throws UserException, InternalException;
        public Expression getNonNumericType() throws InternalException, UserException;

        public Expression getType(Predicate<DataType> mustMatch) throws InternalException, UserException;
        public List<Expression> getTypes(int amount, ExFunction<List<DataType>, Boolean> mustMatch) throws InternalException, UserException;

        public Expression makeArrayExpression(ImmutableList<Expression> items);

        public TypeManager getTypeManager();
    }

    /**
     * Lookup for filter and calculate.  Available columns are:
     *  - per-row columns (with null table id) from
     *    the source table (equivalent to  our table if the column
     *    is unaltered)
     *  - @entire columns from the source table (equivalent to our table if the column
     *     is unaltered) 
     *  -  @entire columns from all tables that
     *     are behind this in the dependency tree,
     *     with non-null table id.
     */
    public static class MultipleTableLookup implements ColumnLookup
    {
        // Only null during testing
        private final @Nullable TableId us;
        private final TableManager tableManager;
        private final @Nullable Table srcTable;
        private final @Nullable CalculationEditor editing;
        
        public static interface CalculationEditor
        {
            public ColumnId getCurrentlyEditingColumn();
            
            // Gives back an action which will make a new Calculate depending on the current Calculate,
            // with the currently editing expression moved there.
            @OnThread(Tag.FXPlatform)
            public SimulationConsumer<Pair<@Nullable ColumnId, Expression>> moveExpressionToNewCalculation();
        }

        public MultipleTableLookup(@Nullable TableId us, TableManager tableManager, @Nullable TableId srcTableId, @Nullable CalculationEditor editing)
        {
            this.us = us;
            this.tableManager = tableManager;
            this.srcTable = srcTableId == null ? null : tableManager.getSingleTableOrNull(srcTableId);
            this.editing = editing;
        }

        @Override
        public @Nullable QuickFix<Expression> getFixForIdent(@ExpressionIdentifier String ident, @Recorded Expression target)
        {
            if (editing == null)
                return null;
            
            final ImmutableList<ColumnId> columnsFromSrc;
            final ImmutableList<ColumnId> columnsInUs;
            
            try
            {
                if (us != null)
                {
                    Table ourTable = tableManager.getSingleTableOrNull(us);
                    if (ourTable != null)
                        columnsInUs = ourTable.getData().getColumnIds();
                    else
                        columnsInUs = ImmutableList.of();
                }
                else
                    columnsInUs = ImmutableList.of();

                columnsFromSrc = srcTable == null ? ImmutableList.of() : srcTable.getData().getColumnIds();

                if (columnsInUs.contains(new ColumnId(ident)) && !columnsFromSrc.contains(new ColumnId(ident)))
                {
                    return new QuickFix<>(StyledString.s("Make a new calculation that can use this table's " + ident), ImmutableList.of(), target, new QuickFixAction()
                    {
                        @Override
                        public @OnThread(Tag.FXPlatform) @Nullable SimulationConsumer<Pair<@Nullable ColumnId, Expression>> doAction(TypeManager typeManager, ObjectExpression<Scene> editorSceneProperty)
                        {
                            return editing.moveExpressionToNewCalculation();
                        }
                    });
                }
            }
            catch (InternalException | UserException e)
            {
                if (e instanceof InternalException)
                    Log.log(e);
            }
            return null;
        }

        @Override
        public Stream<ClickedReference> getPossibleColumnReferences(TableId tableId, ColumnId columnId)
        {
            // Handle us and source table specially:
            if (us != null && tableId.equals(us))
            {
                // Can't refer to an edited column
                if (editing != null && columnId.equals(editing.getCurrentlyEditingColumn()))
                    return Stream.empty();
                
                Table usTable = tableManager.getSingleTableOrNull(us);
                if (usTable == null)
                    return Stream.empty();
                try
                {
                    Column column = usTable.getData().getColumnOrNull(columnId);
                    if (column == null || column.getAlteredState() == AlteredState.OVERWRITTEN)
                        return Stream.empty();
                    return Stream.of(new ClickedReference(tableId, columnId)
                    {
                        @Override
                        public Expression getExpression()
                        {
                            return new ColumnReference(getColumnId());
                        }
                    });
                }
                catch (InternalException | UserException e)
                {
                    if (e instanceof InternalException)
                        Log.log(e);
                    return Stream.empty();
                }
            }
            if (srcTable != null && tableId.equals(srcTable.getId()))
            {
                try
                {
                    Column column = srcTable.getData().getColumnOrNull(columnId);
                    if (column == null)
                        return Stream.empty();
                    return Stream.of(new ClickedReference(tableId, columnId)
                    {
                        @Override
                        public Expression getExpression()
                        {
                            return new ColumnReference(getColumnId());
                        }
                    });
                }
                catch (InternalException | UserException e)
                {
                    if (e instanceof InternalException)
                        Log.log(e);
                    return Stream.empty();
                }
            }
            // For everything else fall back to usual:
            return getAvailableColumnReferences().filter(c -> tableId.equals(c.getTableId()) && columnId.equals(c.getColumnId())).map(c -> new ClickedReference(tableId, columnId)
            {
                @Override
                public Expression getExpression()
                {
                    return c;
                }
            });
        }

        @Override
        public Stream<TableReference> getAvailableTableReferences()
        {
            return tableManager.getAllTablesAvailableTo(us).stream().map(t -> new TableReference(t.getId()));
        }

        public Stream<ColumnReference> getAvailableColumnReferences()
        {
            return tableManager.getAllTablesAvailableTo(us).stream().flatMap(t -> {
                try
                {
                    boolean isUsOrSrc = Objects.equals(us, t.getId()) || (srcTable != null && Objects.equals(t.getId(), srcTable.getId()));
                    return t.getData().getColumns().stream().map(c -> new ColumnReference(isUsOrSrc ? null : t.getId(), c.getName()));
                }
                catch (UserException e)
                {
                    return Stream.empty();
                }
                catch (InternalException e)
                {
                    Log.log(e);
                    return Stream.empty();
                }
            }).distinct();
        }

        @Override
        public @Nullable FoundTable getTable(@Nullable TableId tableName) throws UserException, InternalException
        {
            ImmutableList<Table> available = tableManager.getAllTablesAvailableTo(us);
            Table t = available.stream().filter(table -> table.getId().equals(tableName)).findFirst().orElse(null);
            if (t == null)
                return null;
            
            return new FoundTableActual(t);
        }

        @Override
        public @Nullable FoundColumn getColumn(@Recorded ColumnReference columnReference)
        {
            try
            {
                @Nullable Pair<TableId, RecordSet> rs = null;
                if (columnReference.getTableId() == null)
                {
                    if (srcTable != null)
                        rs = new Pair<>(srcTable.getId(), srcTable.getData());
                }
                else
                {
                    Table table = tableManager.getSingleTableOrNull(columnReference.getTableId());
                    if (table != null)
                        rs = new Pair<>(table.getId(), table.getData());
                }
                
                if (rs != null)
                {
                    Column column = rs.getSecond().getColumn(columnReference.getColumnId());
                    DataTypeValue columnType = column.getType();
                    return new FoundColumn(rs.getFirst(), columnType, checkRedefined(columnReference));
                }
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
            }
            return null;
        }

        // If column is redefined in this table, issue a warning
        private @Nullable Pair<StyledString, @Nullable QuickFix<Expression>> checkRedefined(@Recorded ColumnReference columnReference)
        {
            if (columnReference.getTableId() == null && us != null)
            {
                try
                {
                    Table ourTable = tableManager.getSingleTableOrNull(us);
                    if (ourTable == null)
                        return null;
                    RecordSet rs = ourTable.getData();
                    Column c = rs.getColumnOrNull(columnReference.getColumnId());
                    if (c != null && editing != null && !Objects.equals(columnReference.getColumnId(), editing.getCurrentlyEditingColumn()) && c.getAlteredState() == AlteredState.OVERWRITTEN
                        )
                    {
                        return new Pair<>(StyledString.concat(StyledString.s("Note: column "), StyledString.styled(c.getName().getRaw(), new StyledCSS("column-reference")), StyledString.s(" is re-calculated in this table, but this reference will use the value from the source table.")), getFixForIdent(columnReference.getColumnId().getRaw(), columnReference));
                    }
                }
                catch (InternalException | UserException e)
                {
                    if (e instanceof InternalException)
                        Log.log(e);
                }
            }
            return null;
        }
    }
    
    // Styles the string to look like a user-typed part of the expression
    protected static StyledString styledExpressionInput(String s)
    {
        return StyledString.fancyQuote(StyledString.styled(s, new ExpressionInputStyle()));
    }

    private static class ExpressionInputStyle extends Style<ExpressionInputStyle>
    {
        protected ExpressionInputStyle()
        {
            super(ExpressionInputStyle.class);
        }

        @Override
        protected @OnThread(Tag.FXPlatform) void style(Text t)
        {
            t.getStyleClass().add("expression-input");
        }

        @Override
        protected ExpressionInputStyle combine(ExpressionInputStyle with)
        {
            return this;
        }

        @Override
        protected boolean equalsStyle(ExpressionInputStyle item)
        {
            return true;
        }
    }
}
