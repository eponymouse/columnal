package test.gen;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.KnownLengthRecordSet;
import records.data.datatype.TypeManager;
import test.gen.backwards.*;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import test.DummyManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
public class GenExpressionValueBackwards extends GenExpressionValueBase implements RequestBackwardsExpression
{

    @SuppressWarnings("initialization")
    public GenExpressionValueBackwards()
    {
    }

    private BackwardsColumnRef columnProvider;
    private ImmutableList<BackwardsProvider> providers;

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public ExpressionValue generate()
    {
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
            new BackwardsText(r, this),
            new BackwardsTuple(r, this)
        );
        try
        {
            DataType type = makeType(r);
            Pair<@Value Object, Expression> p = makeOfType(type);
            return new ExpressionValue(type, Collections.singletonList(p.getFirst()), getTypeManager(), getRecordSet(), p.getSecond(), this);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TypeManager getTypeManager()
    {
        return dummyManager.getTypeManager();
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
            deep.addAll(provider.deep(maxLevels, type, targetValue));
        }

        return register(termDeep(maxLevels, type, terminals.build(), deep.build()), type, targetValue);
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
