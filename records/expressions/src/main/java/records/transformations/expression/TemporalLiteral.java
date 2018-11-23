package records.transformations.expression;

import annotation.qual.Value;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeUtility.StringView;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.TemporalLiteralNode;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;

import java.time.temporal.TemporalAccessor;
import java.util.Objects;
import java.util.stream.Stream;

public class TemporalLiteral extends Literal
{
    private final String content;
    // The parsed version of content, if it parsed successfully, otherwise an error
    private final Either<StyledString, @Value TemporalAccessor> value;
    private final DateTimeType literalType;

    public TemporalLiteral(DateTimeType literalType, String content)
    {
        this.content = content.trim();
        this.literalType = literalType;
        StringView stringView = new StringView(content);
        Either<StyledString, @Value TemporalAccessor> v;
        try
        {
            v = Either.right(DataTypeUtility.parseTemporalFlexible(new DateTimeInfo(literalType), stringView));
            stringView.skipSpaces();
            if (stringView.charStart != content.length())
                v = Either.left(StyledString.s("Unrecognised content: " + stringView.snippet()));
        }
        catch (UserException e)
        {
            v = Either.left(e.getStyledMessage());
        }
        this.value = v;
    }

    @Override
    public String editString()
    {
        return content;
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(p -> new TemporalLiteralNode(p, literalType, editString()));
    }
    
    
    @Override
    protected Either<StyledString, TypeExp> checkType(TypeState typeState) throws InternalException
    {
        if (value.isLeft())
        {
            return Either.left(StyledString.concat(StyledString.s("Value "), styledExpressionInput(content), StyledString.s(" not recognised as "), DataType.date(new DateTimeInfo(literalType)).toStyledString(), StyledString.s(" because "), value.getLeft("Impossible")));
        }
        
        return Either.right(TypeExp.fromDataType(this, DataType.date(new DateTimeInfo(literalType))));
    }

    @Override
    public Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        return new Pair<>(value.<@Value Object>eitherInt(err -> {
            throw new InternalException("Executing with unrecognised date/time literal: " + content + " " + err);
        }, v -> v), state);
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String prefix;
        switch (literalType)
        {
            case YEARMONTHDAY:
                prefix = "date";
                break;
            case YEARMONTH:
                prefix = "dateym";
                break;
            case TIMEOFDAY:
                prefix = "time";
                break;
            case DATETIME:
                prefix = "datetime";
                break;
            case DATETIMEZONED:
                prefix = "datetimezoned";
                break;
            default:
                prefix = "date";
                break;
        }
        
        return prefix + "{" + content + "}";
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.s(save(true, bracketedStatus, new TableAndColumnRenames(ImmutableMap.of())));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemporalLiteral that = (TemporalLiteral) o;
        return Objects.equals(content, that.content) &&
                Objects.equals(value, that.value) &&
                literalType == that.literalType;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(content, value, literalType);
    }
}
