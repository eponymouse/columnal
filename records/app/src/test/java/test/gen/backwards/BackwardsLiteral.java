package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager.TagInfo;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.*;
import utility.Either;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;

import java.time.temporal.TemporalAccessor;
import java.util.List;

@SuppressWarnings("recorded")
public class BackwardsLiteral extends BackwardsProvider
{
    public BackwardsLiteral(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        super(r, parent);
    }

    @Override
    public List<ExpressionMaker> terminals(DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return targetType.apply(new DataTypeVisitor<List<ExpressionMaker>>()
        {
            @Override
            public List<ExpressionMaker> number(NumberInfo numberInfo) throws InternalException, UserException
            {
                return ImmutableList.of(
                    () -> new NumericLiteral((Number)targetValue, parent.makeUnitExpression(numberInfo.getUnit())),
                        () -> new TimesExpression(ImmutableList.of(new NumericLiteral((Number)targetValue, null), new NumericLiteral(1, parent.makeUnitExpression(numberInfo.getUnit()))))
                );
            }

            @Override
            public List<ExpressionMaker> text() throws InternalException, UserException
            {
                return ImmutableList.of(() -> new StringLiteral((String)targetValue));
            }

            @Override
            public List<ExpressionMaker> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return ImmutableList.of(
                    () -> {
                        return new TemporalLiteral(dateTimeInfo.getType(), dateTimeInfo.getStrictFormatter().format((TemporalAccessor)targetValue));
                    }
                );
            }

            @Override
            public List<ExpressionMaker> bool() throws InternalException, UserException
            {
                return ImmutableList.of(() -> new BooleanLiteral((Boolean)targetValue));
            }

            @Override
            public List<ExpressionMaker> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return ImmutableList.of(() -> {
                    TaggedValue taggedValue = (TaggedValue) targetValue;
                    TagType<DataType> tag = tags.get(taggedValue.getTagIndex());

                    ConstructorExpression constructor = new ConstructorExpression(parent.getTypeManager(), typeName.getRaw(), tag.getName());
                    if (tag.getInner() == null || taggedValue.getInner() == null)
                        return constructor;
                    else
                        return new CallExpression(constructor, r.choose(terminals(tag.getInner(), taggedValue.getInner())).make());
                });
            }

            @Override
            public List<ExpressionMaker> tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return ImmutableList.of(() -> {
                    @Value Object[] items = Utility.castTuple(targetValue, inner.size());
                    ImmutableList.Builder<Expression> exps = ImmutableList.builder();
                    for (int i = 0; i < inner.size(); i++)
                    {
                        exps.add(r.choose(terminals(inner.get(i), items[i])).make());
                    }
                    return new TupleExpression(exps.build());
                });
            }

            @Override
            public List<ExpressionMaker> array(DataType inner) throws InternalException, UserException
            {
                return ImmutableList.of(() -> {
                    ListEx list = (ListEx)targetValue; 
                    ImmutableList.Builder<Expression> exps = ImmutableList.builder();
                    for (int i = 0; i < list.size(); i++)
                    {
                        exps.add(r.choose(terminals(inner, list.get(i))).make());
                    }
                    return new ArrayExpression(exps.build());
                });
            }
        });
    }

    // Structural semi-literals: structural outer layer, then recurse for inner item.
    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return targetType.apply(new DataTypeVisitor<List<ExpressionMaker>>()
        {
            @Override
            public List<ExpressionMaker> number(NumberInfo numberInfo) throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> text() throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> bool() throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return ImmutableList.of(() -> {
                    TaggedValue taggedValue = (TaggedValue) targetValue;
                    TagType<DataType> tag = tags.get(taggedValue.getTagIndex());

                    ConstructorExpression constructor = new ConstructorExpression(parent.getTypeManager(), typeName.getRaw(), tag.getName());
                    if (tag.getInner() == null || taggedValue.getInner() == null)
                        return constructor;
                    else
                        return new CallExpression(constructor, parent.make(tag.getInner(), taggedValue.getInner(), maxLevels - 1));
                });
            }

            @Override
            public List<ExpressionMaker> tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return ImmutableList.of(() -> {
                    Object[] items = (Object[]) targetValue;
                    ImmutableList.Builder<Expression> exps = ImmutableList.builder();
                    for (int i = 0; i < inner.size(); i++)
                    {
                        exps.add(parent.make(inner.get(i), items[i], maxLevels - 1));
                    }
                    return new TupleExpression(exps.build());
                });
            }

            @Override
            public List<ExpressionMaker> array(DataType inner) throws InternalException, UserException
            {
                return ImmutableList.of(() -> {
                    ListEx list = (ListEx)targetValue;
                    ImmutableList.Builder<Expression> exps = ImmutableList.builder();
                    for (int i = 0; i < list.size(); i++)
                    {
                        exps.add(parent.make(inner, list.get(i), maxLevels - 1));
                    }
                    return new ArrayExpression(exps.build());
                });
            }
        });

    }
}
