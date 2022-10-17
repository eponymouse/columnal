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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.text.Text;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.common.rationals.Rational;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeUtility.Truncater;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.id.TableId;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.log.Log;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import xyz.columnal.styled.StyledString.Style;
import xyz.columnal.transformations.expression.explanation.Explanation;
import xyz.columnal.transformations.expression.explanation.Explanation.ExecutionType;
import xyz.columnal.transformations.expression.explanation.ExplanationLocation;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.transformations.expression.function.StandardFunctionDefinition;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.transformations.expression.function.ValueFunction.RecordedFunctionResult;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitorStream;
import xyz.columnal.typeExp.ExpressionBase;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.utility.function.ExFunction;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
            public final boolean tableCanBeOmitted;
            public final DataTypeValue dataTypeValue;
            public final @Nullable Pair<StyledString, @Nullable QuickFix<Expression>> information;

            public FoundColumn(TableId tableId, boolean tableCanBeOmitted, DataTypeValue dataTypeValue, @Nullable Pair<StyledString, @Nullable QuickFix<Expression>> information)
            {
                this.tableId = tableId;
                this.tableCanBeOmitted = tableCanBeOmitted;
                this.dataTypeValue = dataTypeValue;
                this.information = information;
            }
        }
        
        public static interface FoundTable
        {
            public TableId getTableId();

            public ImmutableMap<ColumnId, DataTypeValue> getColumnTypes() throws InternalException, UserException;

            @OnThread(Tag.Simulation)
            public int getRowCount() throws InternalException, UserException;
        }
        
        // If you pass null for table, you get the default table (or null if none)
        // If no such table/column is found, null is returned
        // Calling getCollapsed  with row number on .dataTypeValue should get corresponding value.
        public @Nullable FoundColumn getColumn(@Recorded Expression expression, @Nullable TableId tableId, ColumnId columnId);

        // If no such table is found, null is returned.
        // If null is passed, uses the current table (if applicable; null return if not -- used for converting from column references)
        public @Nullable FoundTable getTable(@Nullable TableId tableName) throws UserException, InternalException;
        
        // This is really for the editor autocomplete, but it doesn't rely on any GUI
        // functionality so can be here:
        public Stream<Pair<@Nullable TableId, ColumnId>> getAvailableColumnReferences();

        // This is really for the editor autocomplete, but it doesn't rely on any GUI
        // functionality so can be here:
        public Stream<TableId> getAvailableTableReferences();

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


        public default @Nullable QuickFix<Expression> getFixForIdent(@Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, @Recorded Expression target)
        {
            return null;
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
        IdentExpression.resolveThroughout(this, dataLookup, typeState.getFunctionLookup(), typeState.getTypeManager());        
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
        return explanation(value, ExecutionType.VALUE, state, childrenForExplanations, ImmutableList.of(), false, null);
    }

    @OnThread(Tag.Simulation)
    protected final ValueResult resultIsLocation(@Value Object value, EvaluateState state, ImmutableList<ValueResult> childrenForExplanations, ExplanationLocation resultLocation, boolean skipChildrenIfTrivial)
    {
        return explanation(value, ExecutionType.VALUE, state, childrenForExplanations, ImmutableList.of(resultLocation), skipChildrenIfTrivial, resultLocation);
    }

    @OnThread(Tag.Simulation)
    protected final ValueResult explanation(@Value Object value, ExecutionType executionType, EvaluateState state, ImmutableList<ValueResult> childrenForExplanations, ImmutableList<ExplanationLocation> usedLocations, boolean skipChildrenIfTrivial)
    {
        return explanation(value, executionType, state, childrenForExplanations, usedLocations, skipChildrenIfTrivial, null);
    }

    @OnThread(Tag.Simulation)
    protected final ValueResult explanation(@Value Object value, ExecutionType executionType, EvaluateState state, ImmutableList<ValueResult> childrenForExplanations, ImmutableList<ExplanationLocation> usedLocations, boolean skipChildrenIfTrivial, @Nullable ExplanationLocation resultIsLocation)
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
                return new Explanation(Expression.this, overrideExecutionType != null ? overrideExecutionType : executionType, evaluateState, value, usedLocations, resultIsLocation)
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
    protected final StyledString describe(@Value Object value, ExecutionType executionType, EvaluateState evaluateState,  Function<ExplanationLocation, StyledString> hyperlinkLocation, ExpressionStyler expressionStyler, ImmutableList<ExplanationLocation> usedLocations, boolean skipIfTrivial) throws UserException, InternalException
    {
        // Don't bother explaining literals, or trivial if we are skipping trivial:
        if (Expression.this.hideFromExplanation(skipIfTrivial))
            return null;
        
        // Or things which result in functions, as output won't be useful:
        if (value instanceof ValueFunction)
            return null;

        StyledString using = usedLocations.isEmpty() ? StyledString.s("") : StyledString.concat(StyledString.s(", using "), usedLocations.stream().filter(l -> l.rowIndex != null).map(hyperlinkLocation).collect(StyledString.joining(", ")));

        if (executionType == ExecutionType.MATCH && value instanceof Boolean)
        {
            return StyledString.concat(Expression.this.toDisplay(DisplayType.SIMPLE, BracketedStatus.DONT_NEED_BRACKETS, expressionStyler), StyledString.s(((Boolean)value) ? " matched" : " did not match"), using);
        }
        else
        {
            // TODO hyperlink truncated items to allow displaying their full value
            return StyledString.concat(Expression.this.toDisplay(DisplayType.SIMPLE, BracketedStatus.DONT_NEED_BRACKETS, expressionStyler), StyledString.s(" was "), StyledString.s(DataTypeUtility.valueToString(value, null, false, new Truncater()
            {
                @Override
                public String truncateNumber(@ImmediateValue Number number) throws InternalException, UserException
                {
                    if (!Utility.isIntegral(number))
                    {
                        // From https://stackoverflow.com/questions/7572309/any-neat-way-to-limit-significant-figures-with-bigdecimal
                        BigDecimal bd = Utility.toBigDecimal(number);
                        int targetSF = 6;
                        int newScale = targetSF - bd.precision() + bd.scale();

                        bd = bd.setScale(newScale, RoundingMode.HALF_UP);
                        return DataTypeUtility.value(bd).toPlainString() + "\u2026";
                    }
                    // Use default behaviour:
                    return DataTypeUtility.valueToStringFX(number);
                }
            })), using);
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
                return new Explanation(Expression.this, overrideExecutionType != null ? overrideExecutionType : ExecutionType.VALUE, evaluateState, value, recordedFunctionResult.usedLocations, recordedFunctionResult.resultIsLocation)
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
    public abstract ValueResult calculateValue(EvaluateState state) throws EvaluationException, InternalException;
    
    // Fetches a sub-expression and adjusts stack trace and explanation if there is an exception.  If not, adds to passed builder and returns
    @OnThread(Tag.Simulation)
    protected final ValueResult fetchSubExpression(Expression subExpression, EvaluateState state, ImmutableList.Builder<ValueResult> subExpressionsSoFar) throws EvaluationException, InternalException
    {
        try
        {
            ValueResult result = subExpression.calculateValue(state);
            subExpressionsSoFar.add(result);
            return result;
        }
        catch (EvaluationException e)
        {
            throw new EvaluationException(e, this, ExecutionType.VALUE, state, subExpressionsSoFar.build());
        }
    }

    @OnThread(Tag.Simulation)
    protected final ValueResult matchSubExpressionAsPattern(Expression subExpression, @Value Object matchAgainst, EvaluateState state, ImmutableList.Builder<ValueResult> subExpressionsSoFar) throws EvaluationException, InternalException
    {
        try
        {
            ValueResult result = subExpression.matchAsPattern(matchAgainst, state);
            subExpressionsSoFar.add(result);
            return result;
        }
        catch (EvaluationException e)
        {
            throw new EvaluationException(e, this, ExecutionType.MATCH, state, subExpressionsSoFar.build());
        }
    }

    // Note that there will be duplicates if referred to multiple times
    @Override
    @SuppressWarnings("recorded")
    public final Stream<String> allVariableReferences()
    {
        return visit(new ExpressionVisitorStream<String>() {
            @Override
            public Stream<String> ident(IdentExpression self, @Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, boolean isVariable)
            {
                if (isVariable)
                    return Stream.of(idents.get(0));
                else
                    return Stream.of();
            }

            @Override
            public Stream<String> implicitLambdaArg(ImplicitLambdaArg self)
            {
                return Utility.streamNullable(self.getVarNameOrNull());
            }
        });
    }
    
    public abstract <T> T visit(@Recorded Expression this, ExpressionVisitor<T> visitor);

    public static interface SaveDestination
    {
        /**
         * Disambiguates namespace plus idents for the purpose of saving.
         * @param namespace
         * @param idents
         * @return
         */
        Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>> disambiguate(@Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents);

        /**
         * Makes a new save destination with the given names defined (namespace plus idents for each entry) 
         * @param names
         * @return
         */
        SaveDestination withNames(ImmutableList<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>> names);

        /**
         * Get all the defined names in the given namespace
         */
        ImmutableList<ImmutableList<String>> definedNames(String namespace);

        /**
         * Do we need keywords like @call, @unfinished, etc?
         * @return
         */
        boolean needKeywords();
        
        public static final SaveDestination TO_STRING = new SaveDestination()
        {
            @Override
            public Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>> disambiguate(@Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents)
            {
                // Give everything in full:
                return new Pair<>(namespace, idents);
            }

            @Override
            public SaveDestination withNames(ImmutableList<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>> names)
            {
                return this;
            }

            @Override
            public ImmutableList<ImmutableList<String>> definedNames(String namespace)
            {
                return ImmutableList.of();
            }

            @Override
            public boolean needKeywords()
            {
                return false;
            }
        };
        // Same thing so can just re-use:
        public static final SaveDestination TO_EDITOR_FULL_NAME = TO_STRING;
        
        public static final SaveDestination TO_FILE = new SaveDestination()
        {
            @Override
            public Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>> disambiguate(@Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents)
            {
                // Give everything in full:
                return new Pair<>(namespace, idents);
            }

            @Override
            public SaveDestination withNames(ImmutableList<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>> names)
            {
                return this;
            }

            @Override
            public ImmutableList<ImmutableList<String>> definedNames(String namespace)
            {
                return ImmutableList.of();
            }

            @Override
            public boolean needKeywords()
            {
                return true;
            }
        };
        
        static final class ToEditor implements SaveDestination
        {
            private final ImmutableList<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>> names;

            public ToEditor(ImmutableList<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>> names)
            {
                this.names = names;
            }

            @Override
            public Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>> disambiguate(@Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents)
            {
                // Need to keep adding idents backwards from end until it is not ambigious:
                boolean usingNamespace = false;
                int firstIdentUsed = idents.size() - 1;
                // Can only increase scoping if we're not using the namespace or are not yet using all idents
                increaseScoping: while (!usingNamespace || firstIdentUsed > 0)
                {
                    ImmutableList<@ExpressionIdentifier String> inUse = idents.subList(firstIdentUsed, idents.size());

                    int matchCount = 0;
                    for (Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>> name : names)
                    {
                        // We need to make sure that name resolving to its intended destination is not mistaken for a conflict.
                        // Our first step of resolution is to add the namespace, and if that isn't enough, we scope name further and further.
                        // Conflicts arise only when:
                        // - The namespace for the target is non-null AND not in use AND there is another name from another namespace which would match.  In this case, use namespace
                        // - (The namespace for the target is null OR in use) AND there are *more than one* names from that namespace (or any namespaceif target has null) which would match.  In this case, increase scoping. 
                        
                        if (!usingNamespace && namespace != null && !namespace.equals(name.getFirst()) && couldMatch(inUse, name.getSecond()))
                        {
                            usingNamespace = true;
                            continue increaseScoping;
                        }
                        
                        if ((!usingNamespace || namespace == null || namespace.equals(name.getFirst())) && couldMatch(inUse, name.getSecond()))
                        {
                            matchCount += 1;
                        }
                    }
                    if (matchCount > 1 && firstIdentUsed > 0)
                    {
                        firstIdentUsed -= 1;
                    }
                    else if (matchCount == 1)
                    {
                        // Match just one name
                        break increaseScoping;
                    }
                    else if (matchCount == 0)
                    {
                        // Match nothing; preserve fully as-is:
                        usingNamespace = !Objects.equals(namespace, "var");
                        firstIdentUsed = 0;
                        break increaseScoping;
                    }
                    else
                    {
                        break increaseScoping;
                    }
                }
                return new Pair<>(usingNamespace ? namespace : null, idents.subList(firstIdentUsed, idents.size()));
            }

            private static boolean couldMatch(ImmutableList<@ExpressionIdentifier String> currentCandidateScoping, ImmutableList<@ExpressionIdentifier String> fullName)
            {
                if (currentCandidateScoping.size() > fullName.size())
                    return false; // Already scoped enough to avoid the confusion
                // We go backwards:
                for (int i = 1; i <= currentCandidateScoping.size(); i++)
                {
                    if (!currentCandidateScoping.get(currentCandidateScoping.size() - i).equals(fullName.get(fullName.size() - i)))
                    {
                        return false; 
                    }
                }
                // All match up to the length we've scoped to:
                return true;
            }

            @Override
            public SaveDestination withNames(ImmutableList<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>> names)
            {
                return new ToEditor(Utility.concatI(this.names, names));
            }

            @Override
            @SuppressWarnings("identifier")
            public ImmutableList<ImmutableList<String>> definedNames(String namespace)
            {
                return names.stream().filter(p -> namespace.equals(p.getFirst())).<ImmutableList<String>>map(p -> p.getSecond()).collect(ImmutableList.<ImmutableList<String>>toImmutableList());
            }

            @Override
            public boolean needKeywords()
            {
                return false;
            }
        }
        
        // We need names of tables, columns, functions, tags:
        public static SaveDestination toExpressionEditor(TypeManager typeManager, ColumnLookup columnLookup, FunctionLookup functionLookup)
        {
            ImmutableList.Builder<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>> names = ImmutableList.builder();

            for (TaggedTypeDefinition ttd : typeManager.getKnownTaggedTypes().values())
            {
                for (TagType<JellyType> tag : ttd.getTags())
                {
                    names.add(new Pair<>("tag", ImmutableList.<@ExpressionIdentifier String>of(ttd.getTaggedTypeName().getRaw(), tag.getName())));
                }
            }
            try
            {
                for (StandardFunctionDefinition function : functionLookup.getAllFunctions())
                {
                    names.add(new Pair<>("function", function.getFullName()));
                }
            }
            catch (InternalException e)
            {
                Log.log(e);
            }

            for (Pair<@Nullable TableId, ColumnId> column : Utility.iterableStream(columnLookup.getAvailableColumnReferences()))
            {
                names.add(new Pair<>("column", column.getFirst() == null ? ImmutableList.<@ExpressionIdentifier String>of(column.getSecond().getRaw()) : ImmutableList.<@ExpressionIdentifier String>of(column.getFirst().getRaw(), column.getSecond().getRaw())));
            }
            for (TableId table : Utility.iterableStream(columnLookup.getAvailableTableReferences()))
            {
                names.add(new Pair<>("table", ImmutableList.<@ExpressionIdentifier String>of(table.getRaw())));
            }
            
            return new ToEditor(names.build());
        }

        public static SaveDestination toTypeEditor(TypeManager typeManager)
        {
            // There is no scoping on type names at the moment anyway:
            return TO_EDITOR_FULL_NAME;
        }

        public static SaveDestination toUnitEditor(UnitManager unitManager)
        {
            // There is no scoping on unit names at the moment anyway:
            return TO_EDITOR_FULL_NAME;
        }
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
    public abstract String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames);

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
    public ValueResult matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, EvaluationException
    {
        ValueResult ourValue = calculateValue(state);
        try
        {
            return explanation(DataTypeUtility.value(Utility.compareValues(value, ourValue.value) == 0), ExecutionType.MATCH, state, ImmutableList.of(ourValue), ourValue.getDirectlyUsedLocations(), false);
        }
        catch (UserException e)
        {
            throw new EvaluationException(e, this, ExecutionType.MATCH, state, ImmutableList.of(ourValue));
        }
    }

    @Override
    public final String toString()
    {
        return save(SaveDestination.TO_STRING, BracketedStatus.DONT_NEED_BRACKETS, TableAndColumnRenames.EMPTY);
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
        return toDisplay(DisplayType.FULL, BracketedStatus.DONT_NEED_BRACKETS, (s, e) -> s);
    }

    public final StyledString toSimpleStyledString()
    {
        return toDisplay(DisplayType.SIMPLE, BracketedStatus.DONT_NEED_BRACKETS, (s, e) -> s);
    }

    public static interface ExpressionStyler
    {
        public StyledString styleExpression(StyledString display, Expression src);
    }
    
    public static enum DisplayType
    {
        SIMPLE, FULL;
    }
    
    protected abstract StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler);

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
