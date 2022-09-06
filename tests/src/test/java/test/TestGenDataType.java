package test;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.internal.GeometricDistribution;
import com.pholser.junit.quickcheck.internal.generator.SimpleGenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import test.gen.type.GenDataType;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

// Tests that the GenDataType generator is outputting all classes
public class TestGenDataType
{
    private static enum Container
    {
        TAGGED, RECORD, ARRAY;
    }

    @Test
    public void testVarieties()
    {
        // We check that each nested pair occurs.  We start by putting them all in a set, remove if we find them,
        // and any left at the end did not occur:
        Set<Pair<@Nullable Container, @Nullable Container>> nestings = new HashSet<>();
        nestings.add(new Pair<@Nullable Container, @Nullable Container>(null, null));
        for (Container a : Container.values())
        {
            for (Container b : Container.values())
            {
                nestings.add(new Pair<@Nullable Container, @Nullable Container>(a, b));
            }
            nestings.add(new Pair<@Nullable Container, @Nullable Container>(a, null));
        }

        Random r = new Random(1L);
        GenDataType genDataType = new GenDataType();
        for (int i = 0; i < 1000; i++)
        {
            SourceOfRandomness sourceOfRandomness = new SourceOfRandomness(r);
            DataType t = genDataType.generate(sourceOfRandomness, new SimpleGenerationStatus(new GeometricDistribution(), sourceOfRandomness, 10));
            nestings.removeAll(calculateNesting(t).collect(Collectors.toList()));
        }

        assertEquals(Collections.emptySet(), nestings);
    }

    private static Stream<Pair<@Nullable Container, @Nullable Container>> calculateNesting(DataType t)
    {
        try
        {
            return t.apply(new DataTypeVisitor<Stream<Pair<@Nullable Container, @Nullable Container>>>()
            {
                @Override
                public Stream<Pair<@Nullable Container, @Nullable Container>> number(NumberInfo numberInfo) throws InternalException, UserException
                {
                    return Stream.of(new Pair<@Nullable Container, @Nullable Container>(null, null));
                }

                @Override
                public Stream<Pair<@Nullable Container, @Nullable Container>> text() throws InternalException, UserException
                {
                    return Stream.of(new Pair<@Nullable Container, @Nullable Container>(null, null));
                }

                @Override
                public Stream<Pair<@Nullable Container, @Nullable Container>> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                {
                    return Stream.of(new Pair<@Nullable Container, @Nullable Container>(null, null));
                }

                @Override
                public Stream<Pair<@Nullable Container, @Nullable Container>> bool() throws InternalException, UserException
                {
                    return Stream.of(new Pair<@Nullable Container, @Nullable Container>(null, null));
                }

                @Override
                public Stream<Pair<@Nullable Container, @Nullable Container>> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                {
                    return tags.stream().flatMap(t -> Utility.streamNullable(t.getInner())).<Pair<@Nullable Container, @Nullable Container>>flatMap((Function<DataType, Stream<Pair<@Nullable Container, @Nullable Container>>>)(t -> TestGenDataType.calculateNesting(t))).map(wrap(Container.TAGGED));
                }

                @Override
                public Stream<Pair<@Nullable Container, @Nullable Container>> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
                {
                    return fields.values().stream().<Pair<@Nullable Container, @Nullable Container>>flatMap((Function<DataType, Stream<Pair<@Nullable Container, @Nullable Container>>>)TestGenDataType::calculateNesting).map(wrap(Container.RECORD));
                }

                @Override
                public Stream<Pair<@Nullable Container, @Nullable Container>> array(@Nullable DataType inner) throws InternalException, UserException
                {
                    if (inner == null)
                        throw new RuntimeException("Should not generate null inside array");
                    return calculateNesting(inner).map(wrap(Container.ARRAY));
                }
            });
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static Function<Pair<@Nullable Container, @Nullable Container>, Pair<@Nullable Container, @Nullable Container>> wrap(Container outer)
    {
        return (Pair<@Nullable Container, @Nullable Container> p) -> new Pair<@Nullable Container, @Nullable Container>(outer, p.getFirst());
    }
}
