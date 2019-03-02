package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.TableAndColumnRenames;
import records.transformations.expression.function.ValueFunction;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.datatype.TypeManager.TagInfo;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.jellytype.JellyType;
import records.loadsave.OutputBuilder;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.TaggedValue;

import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * This just provides the constructor.  If it has an inner argument,
 * that is specified by a CallExpression that has this ConstructorExpression
 * as the first argument.
 */
public class ConstructorExpression extends NonOperatorExpression
{
    /**
     * If unknown type or tag, Left(tagName).  If known type and
     * tag, Right(tagInfo).
     */
    private final Either<String, TagInfo> tag;
    
    // null if type check fails for some reason:
    //private @MonotonicNonNull FunctionTypes type;

    public ConstructorExpression(TypeManager typeManager, @Nullable @ExpressionIdentifier String typeName, String tagName)
    {
        tag = typeName == null ? Either.left(tagName) : typeManager.lookupTag(typeName, tagName);
    }
    
    // Used for testing, or if you know the tag directly:
    public ConstructorExpression(TagInfo tag)
    {
        this.tag = Either.right(tag);
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        return onError.recordType(this, ExpressionKind.EXPRESSION, typeState, tag.<@Nullable TypeExp>eitherEx(s -> null, t -> makeTagType(t)));
    }

    private TypeExp makeTagType(TagInfo t) throws InternalException
    {
        TagType<JellyType> tt = t.getTagInfo();
        Pair<TypeExp, ImmutableList<TypeExp>> taggedType = TypeExp.fromTagged(this, t.wholeType);
        return taggedType.getSecond().get(t.tagIndex);
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
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

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.empty();
    }

    @Override
    public Stream<String> allVariableReferences()
    {
        return Stream.empty();
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        if (structured)
            return tag.either(s -> "@unfinished " + OutputBuilder.quoted(s), t -> "@tag " + t.getTypeName().getRaw() + ":" + t.getTagInfo().getName());
        else
            return tag.either(s -> s, t -> t.getTypeName().getRaw() + ":" + t.getTagInfo().getName());
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(GeneralExpressionEntry.load(this));
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
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.s(tag.either(s -> s, t -> t.getTypeName().getRaw() + "\\" + t.getTagInfo().getName())), this);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConstructorExpression that = (ConstructorExpression) o;
        return Objects.equals(tag, that.tag);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(tag);
    }

    // For use at run-time.  Throws an exception if didn't type check
    public int getTagIndex() throws InternalException
    {
        return tag.eitherInt(s -> {throw new InternalException("Could not find tag " + s);}, t -> t.tagIndex);        
    }
    
    public String getName()
    {
        return tag.either(s -> s, t -> t.getTagInfo().getName());
    }
    
    @Pure
    public @Nullable TypeId getTypeName()
    {
        return tag.<@Nullable TypeId>either(s -> null, t -> t.getTypeName());
    }

    public boolean _test_hasInner()
    {
        return tag.either(s -> false, t -> t.getTagInfo().getInner() != null);
    }

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }
}
