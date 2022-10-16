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

package test.expressions;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Booleans;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import test.TTableUtil;
import test.functions.TFunctionUtil;
import xyz.columnal.data.*;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.DataItemPosition;
import xyz.columnal.id.TableId;
import xyz.columnal.transformations.expression.BooleanLiteral;
import xyz.columnal.transformations.expression.EvaluationException;
import xyz.columnal.transformations.expression.Expression.ExpressionStyler;
import xyz.columnal.transformations.expression.MatchExpression;
import xyz.columnal.transformations.expression.MatchExpression.MatchClause;
import xyz.columnal.transformations.expression.MatchExpression.Pattern;
import xyz.columnal.transformations.expression.explanation.Explanation;
import xyz.columnal.transformations.expression.explanation.Explanation.ExecutionType;
import xyz.columnal.transformations.expression.explanation.ExplanationLocation;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.Check;
import xyz.columnal.transformations.Check.CheckType;
import xyz.columnal.transformations.expression.ErrorAndTypeRecorderStorer;
import xyz.columnal.transformations.expression.EvaluateState;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.MultipleTableLookup;
import xyz.columnal.transformations.function.FunctionList;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import test.DummyManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListExList;
import xyz.columnal.utility.Utility.RecordMap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@OnThread(Tag.Simulation)
public class TestExpressionExplanation
{
    private final TableManager tableManager;
    
    public TestExpressionExplanation() throws UserException, InternalException
    {
        tableManager = DummyManager.make();
        List<SimulationFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
        columns.add(bools("all true", true, true, true, true));
        columns.add(bools("half false", false, true, false, true));
        columns.add(bools("all false", false, false, false, false));
        tableManager.record(new ImmediateDataSource(tableManager, new InitialLoadDetails(new TableId("T1"), null, null, null), new EditableRecordSet(columns, () -> 4)));
        
        columns.clear();
        columns.add(nums("asc", 1, 2, 3, 4));
        columns.add(text("alphabet animals", "Aardvark", "Bear", "Cat", "Deer"));
        tableManager.record(new ImmediateDataSource(tableManager, new InitialLoadDetails(new TableId("T2"), null, null, null), new EditableRecordSet(columns, () -> 4)));
    }

    private static SimulationFunction<RecordSet, EditableColumn> bools(@ExpressionIdentifier String name, boolean... values)
    {
        return rs -> new MemoryBooleanColumn(rs, new ColumnId(name), Utility.<@ImmediateValue Boolean, Either<String, Boolean>>mapList(Booleans.asList(values), Either::right), false);
    }

    private static SimulationFunction<RecordSet, EditableColumn> nums(@ExpressionIdentifier String name, Number... values)
    {
        return rs -> new MemoryNumericColumn(rs, new ColumnId(name), new NumberInfo(Unit.SCALAR), Utility.<Number, Either<String, Number>>mapList(Arrays.asList(values), Either::right), DataTypeUtility.value(0));
    }

    private static SimulationFunction<RecordSet, EditableColumn> text(@ExpressionIdentifier String name, String... values)
    {
        return rs -> new MemoryStringColumn(rs, new ColumnId(name), Utility.<String, Either<String, String>>mapList(Arrays.asList(values), Either::right), "");
    }
    
    @Test
    public void testException() throws Exception
    {
        testExplanation("(1 / 2) + 3",
            e("(1 / 2) + 3", null, null, null,
                e("1 / 2", null, null, null,
                    lit(1), lit(2)),
                lit(3)
        ));

        testExplanation("(1 / 0) + 3",
                e("(1 / 0) + 3", null, null, null,
                        e("1 / 0", null, null, null,
                                lit(1), lit(0))
                        // Should not have lit(3) child because of the exception
                ));
    }

    @Test
    public void testLiterals() throws Exception
    {
        testExplanation("1", e("1", null, 1, null));
        testExplanation("true", e("true", null, true, null));
    }
    
    @Test
    public void testExplainedElement() throws Exception
    {
        testExplanation("@call function\\\\list\\element(table\\\\T1#all true, 3)", e("@call function\\\\list\\element(table\\\\T1#all true, 3)", null, true, l("T1", "all true", 2), entire("T1", "all true"), lit(3)));
        testExplanation("@call function\\\\list\\element(table\\\\T2#asc, 2) > 5", e("@call function\\\\list\\element(table\\\\T2#asc, 2) > 5", null, false, null, e("@call function\\\\list\\element(table\\\\T2#asc, 2)", null, 2, l("T2", "asc", 1), entire("T2", "asc"), lit(2)), lit(5)));
        testExplanation("(@call function\\\\list\\element(table\\\\T2#asc, 1) < 5) & (@call function\\\\list\\element(table\\\\T2#asc, 2) = 5)",
            e("(@call function\\\\list\\element(table\\\\T2#asc, 1) < 5) & (@call function\\\\list\\element(table\\\\T2#asc, 2) = 5)", null, false, null,
                e("@call function\\\\list\\element(table\\\\T2#asc, 1) < 5", null, true, null,
                    e("@call function\\\\list\\element(table\\\\T2#asc, 1)", null, 1, l("T2", "asc", 0), entire("T2", "asc"), lit(1)),
                    lit(5)),
                e("@call function\\\\list\\element(table\\\\T2#asc, 2) = 5", null, false, null,
                        e("@call function\\\\list\\element(table\\\\T2#asc, 2)", null, 2, l("T2", "asc", 1), entire("T2", "asc"), lit(2)),
                        lit(5))
            )
        );
    }

    protected Explanation entire(@ExpressionIdentifier String table, @ExpressionIdentifier String column, Object... values) throws InternalException, UserException
    {
        return e("table\\\\" + table + "#" + column, null, new ListExList(TTableUtil.streamFlattened(tableManager.getSingleTableOrThrow(new TableId(table)).getData().getColumn(new ColumnId(column))).collect(ImmutableList.toImmutableList())), l(table, column), e("table\\\\" + table, null, null, new ExplanationLocation(new TableId(table))));
    }

    @Test
    public void testExplainedAll() throws Exception
    {
        testExplanation("@call function\\\\listprocess\\all(table\\\\T1#all false, (? = true))", 
            explanation("@call function\\\\listprocess\\all(table\\\\T1#all false, (? = true))", ExecutionType.VALUE, null, false, l("T1", "all false", 0), false,
                entire("T1", "all false"),
                    // Once for the function, once for the function call:
                    e("? = true", null, null, null),
                    explanation("? = true", ExecutionType.CALL_IMPLICIT, q(false), false, null, false, e("?", q(false), false, null), lit(true))));
        testExplanation("@call function\\\\listprocess\\all(table\\\\T1#half false, function\\\\boolean\\not)",
            explanation("@call function\\\\listprocess\\all(table\\\\T1#half false, function\\\\boolean\\not)", ExecutionType.VALUE, null, false, l("T1", "half false", 1), false, entire("T1", "half false"), e("function\\\\boolean\\not", null, null, null)));
        testExplanation("@call function\\\\listprocess\\all(table\\\\T2#asc, (? < 3))",
            explanation("@call function\\\\listprocess\\all(table\\\\T2#asc, (? < 3))", ExecutionType.VALUE, null, false, l("T2", "asc", 2), false, entire("T2", "asc"),
                // Once for function, once for function call:
                e("? < 3", null, null, null),
                explanation("? < 3", ExecutionType.CALL_IMPLICIT, q(3), false, null, false, e("?", q(3), 3, null), lit(3))));
        testExplanation("@call function\\\\listprocess\\all(table\\\\T2#asc, (? =~ (1.8 \u00B1 1.2)))",
                explanation("@call function\\\\listprocess\\all(table\\\\T2#asc, (? =~ (1.8 \u00B1 1.2)))", ExecutionType.VALUE,  null, false, l("T2", "asc", 3), false, entire("T2", "asc"),
                    // Once for function, once for call:
                    e("? =~ (1.8 \u00B1 1.2)", null, null, null),
                    explanation("? =~ (1.8 \u00B1 1.2)", ExecutionType.CALL_IMPLICIT, q(4), false, null, false,
                        e("?", q(4), 4, null),
                        m("1.8 \u00B1 1.2", null, false, null, lit(new BigDecimal("1.8")), lit(new BigDecimal("1.2")))
        )));
        testExplanation("@call function\\\\listprocess\\none(table\\\\T2#asc, @function(var\\\\x) @then @call function\\\\boolean\\not(var\\\\x =~ (1.8 \u00B1 0.9)) @endfunction)",
                explanation("@call function\\\\listprocess\\none(table\\\\T2#asc, @function(var\\\\x) @then @call function\\\\boolean\\not(var\\\\x =~ (1.8 \u00B1 0.9)) @endfunction)", ExecutionType.VALUE, null, false, l("T2", "asc", 2), false, entire("T2", "asc"),
                    // Once for function, once for call:
                    e("@function(var\\\\x) @then @call function\\\\boolean\\not(var\\\\x =~ (1.8 \u00B1 0.9)) @endfunction", null, null, null),
                    e("@call function\\\\boolean\\not(var\\\\x =~ (1.8 \u00B1 0.9))", vv("x", 3), null, null,
                        explanation("var\\\\x =~ (1.8 \u00B1 0.9)", ExecutionType.VALUE, vv("x", 3), false, null, false, 
                        e("var\\\\x", vv("x", 3), 3, null),
                        m("1.8 \u00B1 0.9", null, false, null, lit(new BigDecimal("1.8")), lit(new BigDecimal("0.9")))
        ))));
        
        testCheckExplanation("T1", "column\\\\half false", CheckType.ALL_ROWS, e("column\\\\T1\\half false", r(0), false, l("T1", "half false", 0)));

        testCheckExplanation("T1", "@if column\\\\half false @then column\\\\all false @else column\\\\all true @endif", CheckType.ALL_ROWS, e("@if column\\\\T1\\half false @then column\\\\T1\\all false @else column\\\\T1\\all true @endif", r(1), false, null,
            e("column\\\\T1\\half false", r(1), true, l("T1", "half false", 1)),
            e("column\\\\T1\\all false", r(1), false, l("T1", "all false", 1))
                ));

        testCheckExplanation("T1", "@match column\\\\half false @case true @then column\\\\all false @case false @then column\\\\all true @endmatch", CheckType.ALL_ROWS, e("@match column\\\\T1\\half false @case true @then column\\\\T1\\all false @case false @then column\\\\T1\\all true @endmatch", r(1), false, null,
                e("column\\\\T1\\half false", r(1), true, l("T1", "half false", 1)),
                clause(ImmutableList.of(new MatchExpression.Pattern(new BooleanLiteral(true), null)), "column\\\\T1\\all false", r(1), true,
                    // Bit confusing; outer true is result of pattern match,
                    // inner true is the literal that it was matched against
                    m("true", r(1), true, null, e("true", r(1), true, null))),
                e("column\\\\T1\\all false", r(1), false, l("T1", "all false", 1)))
        );
        
        // This is a mega-match clause with multiple clauses that won't match.
        // The matching row is the third row (index 2).
        // The value being matched against is (asc, alphabet animals)
        
        // First clause is (n, a) @given n > text length(a)
        Explanation megaClause1Expl = clause(ImmutableList.of(pattern("(a: var\\\\n, b: var\\\\a)", "var\\\\n > @call function\\\\text\\text length(var\\\\a)")), "true", r(2), false,
                m("(a: var\\\\n, b: var\\\\a)", r(2, v("n", 3), v("a", "Cat")), true, null,
                    m("var\\\\n", r(2, v("n", 3)), true, null),
                    m("var\\\\a", r(2, v("a", "Cat")), true, null)),
                e("var\\\\n > @call function\\\\text\\text length(var\\\\a)", r(2, v("n", 3), v("a", "Cat")), false, null,
                    e("var\\\\n", r(2, v("n", 3)), 3, null),
                    e("@call function\\\\text\\text length(var\\\\a)", r(2, v("a", "Cat")), 3, null, e("a", r(2, v("a", "Cat")), "Cat", null))
                    )
                );
        // Second clause is (3,  _ ; "t") @given false @then 1 > 0
        Explanation megaClause2Expl = clause(ImmutableList.of(pattern("(a: 3, b: (_ ; \"t\"))", "false")), "1 > 0", r(2), false,
            m("(a:3, b:(_ ; \"t\"))", r(2), true, null,
                m("3", r(2), true, null,
                    e("3", r(2), 3, null)),
                m("_ ; \"t\"", r(2), true, null,
                    e("\"t\"", r(2), "t", null),
                    m("_", r(2), true, null))
            ),
            e("false", r(2), false, null)
        );
        
        // Third clause is @case (_n, "Cat") @then n > 2
        Explanation megaClause3Expl = clause(ImmutableList.of(pattern("(a: var\\\\n, b: \"Cat\")", null)), "n > 2", r(2, v("n", 3)), true,
            m("(a: var\\\\n, b: \"Cat\")", r(2, v("n", 3)), true, null, 
                    m("var\\\\n", r(2, v("n", 3)), true, null),
                    m("\"Cat\"", r(2), true, null, e("\"Cat\"", r(2), "Cat", null)))    
        );
        Explanation outcome = e("var\\\\n > 2", r(2, v("n", 3)), true, null, e("n", r(2, v("n", 3)), 3, null), e("2", r(2), 2, null));
        
        /* TODO put this back
        String fullMega = "@match (a:column\\\\T2\\asc, b:column\\\\T2\\alphabet animals) @case (a:n, b:a) @given n > @call function\\\\text length(a) @then true @case (a:3,  b:(_ ; \"t\")) @given false @then 1 > 0 @case (a:n, b:\"Cat\") @then n > 2 @case _ @then false @endmatch";
        testCheckExplanation("T2", fullMega, CheckType.NO_ROWS, e(fullMega, r(2), true, null, 
            e("(a:column\\\\T2\\asc, b:column\\\\T2\\alphabet animals)", r(2), new RecordMap(ImmutableMap.<@ExpressionIdentifier String, @Value Object>of("a", DataTypeUtility.value(3), "b", DataTypeUtility.value("Cat"))), null,
                e("column\\\\T2\\asc", r(2), 3, l("T2", "asc", 2)),
                e("column\\\\T2\\alphabet animals", r(2), "Cat", l("T2", "alphabet animals", 2))
                ),
            megaClause1Expl, megaClause2Expl, megaClause3Expl, outcome));
         */
    }

    private Pattern pattern(String patternSrc, @Nullable String guardSrc) throws InternalException, UserException
    {
        TypeManager typeManager = tableManager.getTypeManager();
        Expression pattern = TFunctionUtil.parseExpression(patternSrc, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));
        Expression guard = guardSrc == null ? null : TFunctionUtil.parseExpression(guardSrc, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));
        return new Pattern(pattern, guard);
    }

    // No row index, and a mapping from a single implicit lambda arg param to the given value
    @SuppressWarnings("valuetype")
    private Pair<OptionalInt, ImmutableMap<String, @Value Object>> q(Object value)
    {
        return new Pair<>(OptionalInt.empty(), ImmutableMap.of("?1", value));
    }

    // No row index, and a mapping from a single name to the given value
    @SuppressWarnings("valuetype")
    private Pair<OptionalInt, ImmutableMap<String, @Value Object>> vv(String name, Object value)
    {
        return new Pair<>(OptionalInt.empty(), ImmutableMap.of(name, value));
    }
    
    private static class VarValue
    {
        private final String name;
        private final @Value Object value;

        public VarValue(String name, @Value Object value)
        {
            this.name = name;
            this.value = value;
        }
    }

    @SuppressWarnings("valuetype")
    private VarValue v(String name, Object value)
    {
        return new VarValue(name, value);
    }
    
    // Just a row index, no variables
    private Pair<OptionalInt, ImmutableMap<String, @Value Object>> r(int rowIndex, VarValue... varValues)
    {
        ImmutableMap.Builder<String, @Value Object> vars = ImmutableMap.builderWithExpectedSize(varValues.length);
        for (VarValue varValue : varValues)
        {
            vars.put(varValue.name, varValue.value);
        }
        
        return new Pair<>(OptionalInt.of(rowIndex), vars.build());
    }
    
    private Explanation clause(ImmutableList<Pattern> patterns, String outcomeSrc, @Nullable Pair<OptionalInt, ImmutableMap<String, @Value Object>> rowIndexAndVars, boolean result, Explanation... children) throws InternalException, UserException
    {
        TypeManager typeManager = tableManager.getTypeManager();
        Expression outcomeExpression = TFunctionUtil.parseExpression(outcomeSrc, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));
        return new Explanation(MatchClause.unrecorded(patterns, outcomeExpression), ExecutionType.MATCH, makeEvaluateState(rowIndexAndVars, typeManager), DataTypeUtility.value(result), ImmutableList.of(), null)
        {
            @Override
            public @OnThread(Tag.Simulation) StyledString describe(Set<Explanation> alreadyDescribed, Function<ExplanationLocation, StyledString> hyperlinkLocation, ExpressionStyler expressionStyler, ImmutableList<ExplanationLocation> extraLocations, boolean skipIfTrivial) throws InternalException, UserException
            {
                return StyledString.s("No description in TestExpressionExplanation");
            }

            @Override
            public @OnThread(Tag.Simulation) ImmutableList<Explanation> getDirectSubExplanations() throws InternalException
            {
                return ImmutableList.copyOf(children);
            }
        };
    }

    private Explanation m(String expressionSrc, @Nullable Pair<OptionalInt, ImmutableMap<String, @Value Object>> rowIndexAndVars, @Nullable Object result, @Nullable ExplanationLocation location, Explanation... children) throws InternalException, UserException
    {
        return explanation(expressionSrc, ExecutionType.MATCH, rowIndexAndVars, result, location, false, children);
    }
    
    private Explanation e(String expressionSrc, @Nullable Pair<OptionalInt, ImmutableMap<String, @Value Object>> rowIndexAndVars, @Nullable Object result, @Nullable ExplanationLocation location, Explanation... children) throws InternalException, UserException
    {
        return explanation(expressionSrc, ExecutionType.VALUE, rowIndexAndVars, result, location, true, children);
    }
    
    @SuppressWarnings("valuetype")
    private Explanation explanation(String expressionSrc, ExecutionType executionType, @Nullable Pair<OptionalInt, ImmutableMap<String, @Value Object>> rowIndexAndVars, @Nullable Object result, @Nullable ExplanationLocation location, boolean locationIsResult, Explanation... children) throws InternalException, UserException
    {
        TypeManager typeManager = tableManager.getTypeManager();
        Expression expression = TFunctionUtil.parseExpression(expressionSrc, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));
        EvaluateState evaluateState = makeEvaluateState(rowIndexAndVars, typeManager);
        return new Explanation(expression, executionType, evaluateState, result, Utility.streamNullable(location).collect(ImmutableList.<ExplanationLocation>toImmutableList()), locationIsResult ? location : null)
        {
            @Override
            public @OnThread(Tag.Simulation) StyledString describe(Set<Explanation> alreadyDescribed, Function<ExplanationLocation, StyledString> hyperlinkLocation, ExpressionStyler expressionStyler, ImmutableList<ExplanationLocation> extraLocations, boolean skipIfTrivial) throws InternalException, UserException
            {
                return StyledString.s("No description in TestExpressionExplanation");
            }

            @Override
            public @OnThread(Tag.Simulation) ImmutableList<Explanation> getDirectSubExplanations() throws InternalException
            {
                return ImmutableList.copyOf(children);
            }
        };
    }

    private EvaluateState makeEvaluateState(@Nullable Pair<OptionalInt, ImmutableMap<String, @Value Object>> rowIndexAndVars, TypeManager typeManager) throws InternalException
    {
        EvaluateState evaluateState = new EvaluateState(typeManager, rowIndexAndVars == null ? OptionalInt.empty() : rowIndexAndVars.getFirst());
        if (rowIndexAndVars != null)
        {
            for (Entry<String, @Value Object> var : rowIndexAndVars.getSecond().entrySet())
            {
                evaluateState = evaluateState.add(var.getKey(), var.getValue());
            }
        }
        return evaluateState;
    }

    private Explanation lit(Object value) throws UserException, InternalException
    {
        return e(value.toString(), null, value, null);
    }

    private ExplanationLocation l(@ExpressionIdentifier String tableName, @ExpressionIdentifier String columnName)
    {
        return new ExplanationLocation(new TableId(tableName), new ColumnId(columnName));
    }

    private ExplanationLocation l(@ExpressionIdentifier String tableName, @ExpressionIdentifier String columnName, int rowIndex)
    {
        return new ExplanationLocation(new TableId(tableName), new ColumnId(columnName), DataItemPosition.row(rowIndex));
    }
    
    private void testExplanation(String src, Explanation expectedExplanation) throws Exception
    {
        TypeManager typeManager = tableManager.getTypeManager();
        Expression expression = TFunctionUtil.parseExpression(src, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));

        ErrorAndTypeRecorderStorer errorAndTypeRecorderStorer = new ErrorAndTypeRecorderStorer();
        TypeExp typeCheck = expression.checkExpression(new MultipleTableLookup(null, tableManager, null, null), TFunctionUtil.createTypeState(typeManager), errorAndTypeRecorderStorer);
        assertNotNull(errorAndTypeRecorderStorer.getAllErrors().collect(StyledString.joining("\n")).toPlain(), typeCheck);
        Explanation actual;
        try
        {
            actual = expression.calculateValue(new EvaluateState(typeManager, OptionalInt.empty(), true)).makeExplanation(null);
        }
        catch (EvaluationException e)
        {
            actual = e.makeExplanation();
        }
        assertEquals(expectedExplanation, actual);
    }

    private void testCheckExplanation(@ExpressionIdentifier String srcTable, String src, CheckType checkType, @Nullable Explanation expectedExplanation) throws Exception
    {
        TypeManager typeManager = tableManager.getTypeManager();
        Expression expression = TFunctionUtil.parseExpression(src, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));
        
        Check check = new Check(tableManager, TFunctionUtil.ILD, new TableId(srcTable), checkType, expression);
        boolean result = Utility.cast(check.getData().getColumns().get(0).getType().getCollapsed(0), Boolean.class);
        // null explanation means we expect a pass:
        assertEquals(expectedExplanation == null, result);
        assertEquals(expectedExplanation, check.getExplanation());
    }
}
