package test.gen;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.MemoryArrayColumn;
import records.data.MemoryBooleanColumn;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.MemoryTaggedColumn;
import records.data.MemoryTemporalColumn;
import records.data.MemoryTupleColumn;
import records.data.RecordSet;
import records.data.datatype.DataType.ConcreteDataTypeVisitor;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeManager;
import records.data.datatype.TypeManager.TagInfo;
import records.jellytype.JellyType;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.TemporalLiteral;
import records.transformations.expression.TypeLiteralExpression;
import records.transformations.expression.StringConcatExpression;
import records.transformations.expression.type.TypeExpression;
import test.gen.backwards.*;
import utility.Either;
import utility.SimulationFunction;
import utility.TaggedValue;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.AddSubtractExpression;
import records.transformations.expression.AddSubtractExpression.Op;
import records.transformations.expression.AndExpression;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.DivideExpression;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.OrExpression;
import records.transformations.expression.StringLiteral;
import records.transformations.expression.TupleExpression;
import records.transformations.expression.VarDeclExpression;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.ExSupplier;
import utility.Pair;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.ListExList;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates expressions and resulting values by working backwards.
 * At each step, we generate a target value and then make an expression which will
 * produce that value.  This tends to create better tests for things like pattern
 * matches or equality expressions because we make sure to create non-matching and matching guards (or passing equalities).
 * But it prevents using numeric expressions because we cannot be sure of exact
 * results due to precision (e.g. make me a function which returns 0.3333333; 1/3 might
 * not quite crack it).
 */
@SuppressWarnings("recorded")
@OnThread(Tag.Simulation)
public class GenExpressionValueBackwards extends GenValueBase<ExpressionValue> implements RequestBackwardsExpression
{

    @SuppressWarnings("initialization")
    public GenExpressionValueBackwards()
    {
        super(ExpressionValue.class);
    }

    private BackwardsColumnRef columnProvider;
    private ImmutableList<BackwardsProvider> providers;

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public ExpressionValue generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        this.r = r;
        this.gs = generationStatus;
        this.numberOnlyInt = true;
        columnProvider = new BackwardsColumnRef(r, this);
        providers = ImmutableList.of(
            columnProvider,
            new BackwardsBooleans(r, this),
            new BackwardsFixType(r, this),
            new BackwardsFromText(r, this),
            new BackwardsLiteral(r, this),
            new BackwardsMatch(r, this),
            new BackwardsNumbers(r, this),
            new BackwardsTemporal(r, this),
            new BackwardsText(r, this)
        );
        try
        {
            DataType type = makeType(r);
            Pair<@Value Object, Expression> p = makeOfType(type);
            return new ExpressionValue(type, Collections.singletonList(p.getFirst()), DummyManager.INSTANCE.getTypeManager(), getRecordSet(), p.getSecond());
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TypeManager getTypeManager()
    {
        return DummyManager.INSTANCE.getTypeManager();
    }

    @NonNull
    @OnThread(Tag.Simulation)
    public KnownLengthRecordSet getRecordSet() throws InternalException, UserException
    {
        return new KnownLengthRecordSet(columnProvider.getColumns(), 1);
    }

    // Only valid after calling generate
    @NonNull
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Pair<@Value Object, Expression> makeOfType(DataType type) throws UserException, InternalException
    {
        @Value Object value = makeValue(type);
        Expression expression = make(type, value, 4);
        return new Pair<>(value, expression);
    }

    @SuppressWarnings("valuetype")
    public Expression make(DataType type, Object targetValue, int maxLevels) throws UserException, InternalException
    {
        ImmutableList.Builder<ExpressionMaker> terminals = ImmutableList.builder();
        ImmutableList.Builder<ExpressionMaker> deep = ImmutableList.builder();
        for (BackwardsProvider provider : providers)
        {
            terminals.addAll(provider.terminals(type, targetValue));
            deep.addAll(provider.terminals(type, targetValue));
        }

        return termDeep(maxLevels, type, terminals.build(), deep.build());
    }
    
    private List<ExpressionMaker> l(ExpressionMaker... suppliers)
    {
        return Arrays.asList(suppliers);
    }

    @OnThread(Tag.Simulation)
    private Expression termDeep(int maxLevels, DataType type, List<ExpressionMaker> terminals, List<ExpressionMaker> deeper) throws UserException, InternalException
    {
        if (!terminals.isEmpty() && (maxLevels <= 1 || deeper.isEmpty() || r.nextInt(0, 2) == 0))
            return r.choose(terminals).make();
        else
            return r.choose(deeper).make();
    }
    
    // Turn makeValue public:
    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public @Value Object makeValue(DataType t) throws UserException, InternalException
    {
        return super.makeValue(t);
    }

    @Override
    public DataType makeType() throws InternalException, UserException
    {
        return makeType(r);
    }
    
    // Make public:

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public @Value long genInt()
    {
        return super.genInt();
    }
}
