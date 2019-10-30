package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import records.transformations.expression.function.FunctionLookup;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.TaggedValue;

import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A type{...} expression. 
 */
public class TypeLiteralExpression extends NonOperatorExpression
{
    // This may a type expression that doesn't save to a valid type:
    private final @Recorded TypeExpression type;
    
    public TypeLiteralExpression(@Recorded TypeExpression type)
    {
        this.type = type;
    }

    public static Expression fixType(TypeManager typeManager, FunctionLookup functionLookup, JellyType fix, @Recorded Expression expression) throws InternalException
    {
        try
        {
            return fixType(functionLookup, TypeExpression.fromJellyType(fix, typeManager), expression);
        }
        catch (UserException e)
        {
            // If we have a user exception, we are trying to fix to a non-existent type.
            // Probably means we fucked up, but just use blank type:
            Log.log(e);
            return fixType(functionLookup, new InvalidIdentTypeExpression(""), expression);
        }
    }
    
    @SuppressWarnings("recorded") // Don't need to record when making a fix
    public static Expression fixType(FunctionLookup functionLookup, TypeExpression fixTo, @Recorded Expression expression) throws InternalException
    {
        // Special case -- if it's "from text", switch to "from text to":
        if (expression instanceof CallExpression)
        {
            CallExpression call = (CallExpression) expression;
            if (call.getFunction() instanceof IdentExpression)
            {
                IdentExpression func = (IdentExpression) call.getFunction();
                if (Objects.equals(func.getFunctionDefinition(), functionLookup.lookup("from text")))
                {
                    StandardFunctionDefinition fromTextTo = functionLookup.lookup("from text to");
                    if (fromTextTo != null)
                    {
                        return new CallExpression(IdentExpression.function(fromTextTo.getFullName()), ImmutableList.of(new TypeLiteralExpression(fixTo),
                            call.getParams().get(0)));
                    }
                }
            }
        }
        
        StandardFunctionDefinition asType = functionLookup.lookup( "as type");
        if (asType == null)
            throw new InternalException("Missing as type function");
        if (expression instanceof CallExpression
            && ((CallExpression) expression).getFunction() instanceof  IdentExpression &&
                Objects.equals(((IdentExpression)((CallExpression) expression).getFunction()).getFunctionDefinition(), asType)
            )
        {
            expression = ((CallExpression) expression).getParams().get(1);
        }

        return new CallExpression(IdentExpression.function(asType.getFullName()), ImmutableList.of(
            new TypeLiteralExpression(fixTo),
            expression
        ));
    }
        
    @Override
    public @Nullable CheckedExp check(@Recorded TypeLiteralExpression this, ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable DataType dataType = type.toDataType(typeState.getTypeManager());
        if (dataType == null)
        {
            // Error should already have been given in the editor check
            //onError.recordError(this, StyledString.concat(StyledString.s("Invalid type: "), type.toStyledString()));
            return null;
        }
        return onError.recordTypeAndError(this, Either.right(TypeExp.dataTypeToTypeGADT(this, dataType)), typeState);
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws InternalException
    {
        // TODO return the actual type literal once we define the GADT
        return result(new TaggedValue(0, null, DataTypeUtility.fromTags(ImmutableList.<TagType<Object>>of(new TagType<Object>("Type", null)))), state);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "type{" + type.save(saveDestination, renames) + "}";
    }

    @Override
    public StyledString toDisplay(DisplayType displayType, BracketedStatus surround, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(StyledString.s("type{"), type.toStyledString(), StyledString.s("}")), this);
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        // TODO
        return Stream.empty();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        // TODO
        return null;
    }


    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeLiteralExpression that = (TypeLiteralExpression) o;
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(type);
    }

    public @Recorded TypeExpression getType()
    {
        return type;
    }

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.litType(this, type);
    }
}
