package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeManager;
import records.data.datatype.TypeManager.TagInfo;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.jellytype.JellyType;
import records.loadsave.OutputBuilder;
import records.transformations.expression.Expression.ColumnLookup.FoundTable;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import records.transformations.expression.explanation.ExplanationLocation;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.transformations.expression.function.ValueFunction;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.Record;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A plain identifier.  If it resolves to a variable, it's a variable-use.  If not, it's an unresolved identifier.
 * 
 * IdentExpression differs from InvalidIdentExpression
 * in that IdentExpression is always *syntactically* valid,
 * whereas InvalidIdentExpression is always syntactically
 * *invalid*, because it does not parse as an ident
 * and given its position, it cannot be treated as an
 * operator part of the expression (e.g. a trailing +
 * with no following operand)
 */
public class IdentExpression extends NonOperatorExpression
{
    // The name of the field in a table reference with the list of rows
    public static final @ExpressionIdentifier String ROWS = "rows";
    
    // TODO add resolver listener
    private final @Nullable @ExpressionIdentifier String namespace;
    private final ImmutableList<@ExpressionIdentifier String> idents;
    private @MonotonicNonNull Resolution resolution;

    private IdentExpression(@Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents)
    {
        this.namespace = namespace;
        this.idents = idents;
    }

    public static IdentExpression load(@Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents)
    {
        return new IdentExpression(namespace, idents);
    }

    public static IdentExpression load(@ExpressionIdentifier String ident)
    {
        return new IdentExpression(null, ImmutableList.<@ExpressionIdentifier String>of(ident));
    }

    public static IdentExpression tag(@ExpressionIdentifier String typeName, @ExpressionIdentifier String tagName)
    {
        return new IdentExpression("tag", ImmutableList.<@ExpressionIdentifier String>of(typeName, tagName));
    }
    
    public static IdentExpression tag(@ExpressionIdentifier String tagName)
    {
        return new IdentExpression("tag", ImmutableList.<@ExpressionIdentifier String>of(tagName));
    }

    public static IdentExpression function(ImmutableList<@ExpressionIdentifier String> functionFullName)
    {
        return new IdentExpression("function", functionFullName);
    }

    public static IdentExpression table(@ExpressionIdentifier String tableName)
    {
        return new IdentExpression("table", ImmutableList.<@ExpressionIdentifier String>of(tableName));
    }

    public static IdentExpression column(@Nullable TableId tableName, ColumnId columnName)
    {
        if (tableName == null)
            return column(columnName);
        else
            return new IdentExpression("column", ImmutableList.<@ExpressionIdentifier String>of(tableName.getRaw(), columnName.getRaw()));
    }

    public static IdentExpression column(ColumnId columnName)
    {
        return new IdentExpression("column", ImmutableList.<@ExpressionIdentifier String>of(columnName.getRaw()));
    }

    @SuppressWarnings("recorded") // Only used for items which will be reloaded anyway
    public static @Recorded Expression makeEntireColumnReference(TableId tableId, ColumnId columnId)
    {
        return new FieldAccessExpression(IdentExpression.table(tableId.getRaw()), load(columnId.getRaw()));
    }


    @Override
    public @Nullable CheckedExp check(@Recorded IdentExpression this, ColumnLookup dataLookup, TypeState original, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // I think should now be impossible:
        String invalid = streamAllParts().filter(n -> !GrammarUtility.validIdentifier(n)).findFirst().orElse(null);
        if (invalid != null)
        {
            onError.recordError(this, StyledString.s("Invalid identifier: \"" + invalid + "\""));
            return null;
        }
        // Possible lookup destinations, in order of preference:
        // (Preference is roughly how likely it is that the user defined it and wants it)
        // - Local variable (cannot be scoped)
        // - Column name (Scope: "column")
        // - Table name (Scope: "table")
        // - Tag name (Scope: "tag")
        // - Standard function name (Scope: "function")
        
        @Nullable TypeState state = original;
        boolean singleUnscoped = namespace == null && idents.size() == 1;
        List<TypeExp> varType = singleUnscoped ? state.findVarType(idents.get(0)) : null;
        
        if (varType != null)
        {
            resolution = new Resolution()
            {
                @Override
                public boolean isDeclarationInMatch()
                {
                    return false;
                }

                @Override
                public boolean isVariable()
                {
                    return true;
                }

                @Override
                public ValueResult getValue(EvaluateState state) throws InternalException
                {
                    return result(state.get(idents.get(0)), state);
                }
            };
            // If they're trying to use a variable with many types, it justifies us trying to unify all the types:
            return onError.recordTypeAndError(this, TypeExp.unifyTypes(varType), state);
        }
        
        if (singleUnscoped && kind == ExpressionKind.PATTERN)
        {
            MutVar patternType = new MutVar(this);
            state = state.add(idents.get(0), patternType, s -> onError.recordError(this, s));
            if (state == null)
                return null;
            resolution = new Resolution()
            {
                @Override
                public boolean isDeclarationInMatch()
                {
                    return true;
                }

                @Override
                public boolean isVariable()
                {
                    return true;
                }

                @Override
                public ValueResult getValue(EvaluateState state) throws InternalException
                {
                    throw new InternalException("Calling getValue on variable declaration (should only call matchAsPattern)");
                }
            };
            return onError.recordType(this, state, patternType);
        }
        
        if (namespace != null)
        {
            switch (namespace)
            {
                case "column":
                    Expression.ColumnLookup.@Nullable FoundColumn col;
                    final ColumnId columnName;
                    if (idents.size() == 1)
                    {
                        columnName = new ColumnId(idents.get(0));
                        col = dataLookup.getColumn(this, null, columnName);
                    }
                    else
                    {
                        columnName = new ColumnId(idents.get(1));
                        col = dataLookup.getColumn(this, new TableId(idents.get(0)), columnName);
                    }
                    if (col == null)
                    {
                        onError.recordError(this, StyledString.s("Could not find source column " + toText(idents)));
                        return null;
                    }
                    if (col.information != null)
                    {
                        onError.recordInformation(this, col.information);
                    }
                    TableId resolvedTableName = col.tableId;
                    DataTypeValue column = col.dataTypeValue;
                    resolution = new Resolution()
                    {
                        @Override
                        public ValueResult getValue(EvaluateState state) throws InternalException, UserException
                        {
                            if (column == null || resolvedTableName == null)
                                throw new InternalException("Attempting to fetch value despite type check failure");
                            return result(column.getCollapsed(state.getRowIndex()), state, ImmutableList.of(), ImmutableList.of(new ExplanationLocation(resolvedTableName, columnName, state.getRowIndex())), false);
                        }

                        @Override
                        public boolean isVariable()
                        {
                            return false;
                        }

                        @Override
                        public ImmutableList<@ExpressionIdentifier String> save(SaveDestination saveDestination, ImmutableList<@ExpressionIdentifier String> original, TableAndColumnRenames renames)
                        {
                            final Pair<@Nullable TableId, ColumnId> renamed = renames.columnId(resolvedTableName, columnName, null);

                            final @Nullable TableId renamedTableId = renamed.getFirst();
                            ImmutableList<@ExpressionIdentifier String> tablePlusColumn = renamedTableId != null ? ImmutableList.of(renamedTableId.getRaw(), renamed.getSecond().getRaw()) : ImmutableList.of(renamed.getSecond().getRaw());

                            return tablePlusColumn;
                        }
                    };
                    return onError.recordType(this, state, TypeExp.fromDataType(this, column.getType()));
                case "table":
                    FoundTable table = dataLookup.getTable(new TableId(idents.get(0)));
                    if (table == null)
                    {
                        onError.recordError(this, StyledString.s("Unknown table: " + idents.get(0)));
                        return null;
                    }
                    HashMap<@ExpressionIdentifier String, TypeExp> fieldsAsSingle = new HashMap<>();
                    HashMap<@ExpressionIdentifier String, TypeExp> fieldsAsList = new HashMap<>();

                    for (Entry<ColumnId, DataTypeValue> entry : table.getColumnTypes().entrySet())
                    {
                        fieldsAsList.put(entry.getKey().getRaw(), TypeExp.list(this, TypeExp.fromDataType(this, entry.getValue().getType())));
                        fieldsAsSingle.put(entry.getKey().getRaw(), TypeExp.fromDataType(this, entry.getValue().getType()));
                    }

                    boolean includeRows;
                    if (!fieldsAsList.containsKey(ROWS))
                    {
                        includeRows = true;
                        fieldsAsList.put(ROWS, TypeExp.list(this, TypeExp.record(this, fieldsAsSingle, true)));
                    }
                    else
                        includeRows = false;
                    FoundTable resolvedTable = table;
                    resolution = new Resolution()
                    {
                        @Override
                        public ImmutableList<@ExpressionIdentifier String> save(SaveDestination saveDestination, ImmutableList<@ExpressionIdentifier String> original, TableAndColumnRenames renames)
                        {
                            return ImmutableList.of(renames.tableId(new TableId(idents.get(0))).getRaw());
                        }

                        @Override
                        public ValueResult getValue(EvaluateState state) throws InternalException, UserException
                        {
                            if (resolvedTable == null)
                                throw new InternalException("Attempting to fetch value despite type check failure");
                            final FoundTable resolvedTableNN = resolvedTable;
                            final ImmutableMap<ColumnId, DataTypeValue> columnTypes = resolvedTableNN.getColumnTypes();
                            @Value Record result = DataTypeUtility.value(new Record()
                            {
                                @Override
                                public @Value Object getField(@ExpressionIdentifier String name) throws InternalException
                                {
                                    if (includeRows && name.equals(ROWS))
                                        return DataTypeUtility.value(new RowsAsList());
                                    return DataTypeUtility.value(new ColumnAsList(Utility.getOrThrow(columnTypes, new ColumnId(name), () -> new InternalException("Cannot find column " + name))));
                                }

                                class RowsAsList extends ListEx
                                {
                                    @Override
                                    public int size() throws InternalException, UserException
                                    {
                                        return resolvedTableNN.getRowCount();
                                    }

                                    @Override
                                    public @Value Object get(int index) throws InternalException, UserException
                                    {
                                        ImmutableMap.Builder<@ExpressionIdentifier String, @Value Object> rowValuesBuilder = ImmutableMap.builder();
                                        for (Entry<ColumnId, DataTypeValue> entry : columnTypes.entrySet())
                                        {
                                            rowValuesBuilder.put(entry.getKey().getRaw(), entry.getValue().getCollapsed(index));
                                        }
                                        ImmutableMap<@ExpressionIdentifier String, @Value Object> rowValues = rowValuesBuilder.build();

                                        return DataTypeUtility.value(new Record()
                                        {
                                            @Override
                                            public @Value Object getField(@ExpressionIdentifier String name) throws InternalException
                                            {
                                                return Utility.getOrThrow(rowValues, name, () -> new InternalException("Cannot find column " + name));
                                            }

                                            @Override
                                            public ImmutableMap<@ExpressionIdentifier String, @Value Object> getFullContent() throws InternalException
                                            {
                                                return rowValues;
                                            }
                                        });
                                    }
                                }

                                class ColumnAsList extends ListEx
                                {
                                    private final DataTypeValue dataTypeValue;

                                    ColumnAsList(DataTypeValue dataTypeValue)
                                    {
                                        this.dataTypeValue = dataTypeValue;
                                    }

                                    @Override
                                    public int size() throws InternalException, UserException
                                    {
                                        return resolvedTableNN.getRowCount();
                                    }

                                    @Override
                                    public @Value Object get(int index) throws InternalException, UserException
                                    {
                                        return dataTypeValue.getCollapsed(index);
                                    }
                                }

                                @Override
                                public ImmutableMap<@ExpressionIdentifier String, @Value Object> getFullContent() throws InternalException
                                {
                                    return columnTypes.entrySet().stream().collect(ImmutableMap.<Entry<ColumnId, DataTypeValue>, @ExpressionIdentifier String, @Value Object>toImmutableMap(e -> e.getKey().getRaw(), e -> DataTypeUtility.value(new ColumnAsList(e.getValue()))));
                                }
                            });

                            return result(result, state, ImmutableList.of(), ImmutableList.of(/*new ExplanationLocation(resolvedTableName, columnName)*/), false);
                        }

                        @Override
                        public boolean isVariable()
                        {
                            return false;
                        }
                    };
                    return new CheckedExp(onError.recordType(this, TypeExp.record(this, fieldsAsList, true)), state);
                case "tag":
                    Either<String, TagInfo> tag;
                    switch (idents.size())
                    {
                        case 1:
                            tag = state.getTypeManager().lookupTag(null, idents.get(0));
                            break;
                        case 2:
                            tag = state.getTypeManager().lookupTag(idents.get(0), idents.get(1));
                            break;
                        default:
                            onError.recordError(this, StyledString.s("Found " + idents.size() + " identifiers but tags should either have one (tag name) or two (type name, tag name)"));
                            return null;
                    }
                    resolution = new Resolution()
                    {
                        @Override
                        public boolean isVariable()
                        {
                            return false;
                        }

                        @Override
                        public @Nullable TagInfo getResolvedConstructor()
                        {
                            return tag.leftToNull();
                        }

                        @Override
                        public ValueResult getValue(EvaluateState state) throws InternalException, UserException
                        {
                            return result(tag.<@Value Object>eitherEx(s -> {
                                throw new InternalException("Attempting to fetch function despite failing type check");
                            }, t -> {
                                TagType<?> tag1 = t.getTagInfo();
                                if (tag1.getInner() == null)
                                    return new TaggedValue(t.tagIndex, null);
                                else
                                    return ValueFunction.value(new ValueFunction()
                                    {
                                        @Override
                                        public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
                                        {
                                            return new TaggedValue(t.tagIndex, arg(0));
                                        }
                                    });
                            }), state, ImmutableList.of());
                        }
                    };
                    return onError.recordType(this, state, tag.<@Nullable TypeExp>eitherEx(s -> null, t -> makeTagType(t)));
                case "function":
                    StandardFunctionDefinition functionDefinition = state.getFunctionLookup().lookup(idents.stream().collect(Collectors.joining("\\")));
                    if (functionDefinition != null)
                    {
                        Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> type = functionDefinition.getType(state.getTypeManager());
                        resolution = new Resolution()
                        {
                            @Override
                            public boolean isVariable()
                            {
                                return false;
                            }

                            @Override
                            public @Nullable StandardFunctionDefinition getResolvedFunctionDefinition()
                            {
                                return functionDefinition;
                            }

                            @Override
                            public ValueResult getValue(EvaluateState state) throws InternalException, UserException
                            {
                                return result(ValueFunction.value(functionDefinition.getInstance(state.getTypeManager(), s -> {
                                    Either<MutUnitVar, MutVar> typeExp = type.getSecond().get(s);
                                    if (typeExp == null)
                                        throw new InternalException("Type " + s + " cannot be found for function " + functionDefinition.getName());
                                    return typeExp.<Unit, DataType>mapBothEx(u -> {
                                        Unit concrete = u.toConcreteUnit();
                                        if (concrete == null)
                                            throw new UserException("Could not resolve unit " + s + " to a concrete unit from " + u);
                                        return concrete;
                                    }, t -> t.toConcreteType(state.getTypeManager(), true).eitherEx(
                                            l -> {throw new UserException(StyledString.concat(StyledString.s("Ambiguous type for call to " + functionDefinition.getName() + " "),  l.getErrorText()));},
                                            t2 -> t2
                                    ));
                                })), state);
                            }
                        };
                        return onError.recordType(this, state, functionDefinition.getType(state.getTypeManager()).getFirst());
                    }
                    break;
                default:
                    onError.recordError(this, StyledString.s("Unknown namespace: \"" + namespace + "\".  Known namespaces: column, table, tag, function."));
                    return null;
            }
        }
        
        // Didn't find it anywhere:
        onError.recordError(this, StyledString.s("Unknown name: \"" + idents.stream().collect(Collectors.joining("\\")) + "\""));
        @Nullable QuickFix<Expression> fix = dataLookup.getFixForIdent(namespace, idents, this);
        if (fix != null)
        {
            onError.recordQuickFixes(this, ImmutableList.<QuickFix<Expression>>of(fix));
        }
        return null;
        
    }

    private Stream<@ExpressionIdentifier String> streamAllParts()
    {
        return Stream.<@ExpressionIdentifier String>concat(Utility.<@ExpressionIdentifier String>streamNullable(namespace), idents.stream());
    }

    private TypeExp makeTagType(TagInfo t) throws InternalException
    {
        TagType<JellyType> tt = t.getTagInfo();
        Pair<TypeExp, ImmutableList<TypeExp>> taggedType = TypeExp.fromTagged(this, t.wholeType);
        return taggedType.getSecond().get(t.tagIndex);
    }

    @Override
    public ValueResult matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        if (resolution == null)
            throw new InternalException("Calling matchAsPattern on variable without typecheck");
        else if (resolution.isDeclarationInMatch())
            return explanation(DataTypeUtility.value(true), ExecutionType.MATCH, state.add(idents.get(0), value), ImmutableList.of(), ImmutableList.of(), false);
        else
            return super.matchAsPattern(value, state);
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        if (resolution == null)
            throw new InternalException("Calling getValue on variable without typecheck");
        else
            return resolution.getValue(state);
    }

    @Override
    public boolean hideFromExplanation(boolean skipIfTrivial)
    {
        // We are a trivial match, no point saying _foo matched successfully if
        // we appear inside a tuple, etc.
        if (resolution != null && resolution.isDeclarationInMatch())
            return skipIfTrivial;
        else
            return super.hideFromExplanation(skipIfTrivial);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, @Nullable TypeManager typeManager, TableAndColumnRenames renames)
    {
        // TODO not sure this should rely on type-checking for the renames
        return toText(resolution == null ? idents : resolution.save(saveDestination, idents, renames));
    }

    private String toText(ImmutableList<@ExpressionIdentifier String> idents)
    {
        StringBuilder stringBuilder = new StringBuilder();
        if (namespace != null)
            stringBuilder.append(namespace).append("\\\\");
        for (int i = 0; i < idents.size(); i++)
        {
            if (i > 0)
                stringBuilder.append("\\");
            stringBuilder.append(idents.get(i));
        }
        return stringBuilder.toString();
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.s(toText(idents)), this);
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentExpression that = (IdentExpression) o;
        return Objects.equals(namespace, that.namespace) &&
                idents.equals(that.idents);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(namespace, idents);
    }

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.ident(this, namespace, idents, resolution != null && resolution.isVariable());
    }

    /**
     * Only valid to call after type-checking!  Before that we can't know.
     */
    public boolean isVarDeclaration()
    {
        return resolution != null && resolution.isDeclarationInMatch();
    }

    /**
     * Only valid to call after type-checking!  Before that we can't know.
     * Bit hacky to provide this, but useful elsewhere...
     */
    public @Nullable TagInfo getResolvedConstructor()
    {
        return resolution != null ? resolution.getResolvedConstructor() : null;
    }

    /**
     * Only valid to call after type-checking!  Before that we can't know.
     * Bit hacky to provide this, but useful elsewhere...
     */
    public @Nullable StandardFunctionDefinition getFunctionDefinition()
    {
        return resolution != null ? resolution.getResolvedFunctionDefinition() : null;
    }

    private static interface Resolution
    {
        public default boolean isDeclarationInMatch()
        {
            return false;
        }

        public ValueResult getValue(EvaluateState state) throws InternalException, UserException;
        
        public default ImmutableList<@ExpressionIdentifier String> save(SaveDestination saveDestination, ImmutableList<@ExpressionIdentifier String> original, TableAndColumnRenames renames)
        {
            return original;
        }

        public boolean isVariable();
        
        public default @Nullable TagInfo getResolvedConstructor()
        {
            return null;
        }

        public default @Nullable StandardFunctionDefinition getResolvedFunctionDefinition()
        {
            return null;
        }
    }
    
    // If parameter is IdentExpression with single ident and no namespace, return the ident
    public static @Nullable @ExpressionIdentifier String getSingleIdent(Expression expression)
    {
        if (expression instanceof IdentExpression)
        {
            IdentExpression identExpression = (IdentExpression) expression;
            if (identExpression.namespace == null && identExpression.idents.size() == 1)
                return identExpression.idents.get(0);
        }
        return null;
    }
}
