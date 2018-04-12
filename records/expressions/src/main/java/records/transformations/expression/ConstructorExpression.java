package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TypeManager;
import records.data.datatype.TypeManager.TagInfo;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Status;
import records.gui.expressioneditor.OperandNode;
import records.loadsave.OutputBuilder;
import records.types.TypeCons;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;

import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

public class ConstructorExpression extends NonOperatorExpression
{
    /**
     * If unknown type or tag, Left(tagName).  If known type and
     * tag, Right(tagInfo).
     */
    private final Either<String, TagInfo> tag;
    
    // null if type check fails for some reason:
    //private @MonotonicNonNull FunctionTypes type;

    public ConstructorExpression(TypeManager typeManager,  @Nullable String typeName, String tagName)
    {
        tag = typeName == null ? Either.left(tagName) : typeManager.lookupTag(typeName, tagName);
    }
    
    // Used for testing, or if you know the tag directly:
    public ConstructorExpression(Either<String, TagInfo> tag)
    {
        this.tag = tag;
    }

    @Override
    public @Recorded @Nullable TypeExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        return onError.recordType(this, tag.<@Nullable TypeExp>eitherEx(s -> null, t -> makeTagType(t)));
    }

    private TypeExp makeTagType(TagInfo t) throws InternalException
    {
        TagType<DataType> tt = t.getTagInfo();
        TypeExp taggedType = TypeExp.fromTagged(this, t.wholeType).getFirst();
        if (tt.getInner() == null)
        {
            return taggedType;
        }
        else
        {
            // TODO
            return taggedType;
        }
        
    }

    @Override
    @OnThread(Tag.Simulation)
    public @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return tag.eitherEx(s -> {
            throw new InternalException("Attempting to fetch function despite failing type check");
        }, t -> t.makeValue());
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.empty();
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return tag.either(s -> "@unknowntag " + s, t -> "@tag " + OutputBuilder.quotedIfNecessary(t.getTypeName().getRaw()) + "\\" + OutputBuilder.quotedIfNecessary(t.getTagInfo().getName()));
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        // TODO need to also pass across the type name some how
        return (p, s) -> new GeneralExpressionEntry(new GeneralExpressionEntry.TagName(tag.either(str -> str, t -> t.getTagInfo().getName())), p, s);
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
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.s(tag.either(s -> s, t -> t.getTypeName().getRaw() + "\\" + t.getTagInfo().getName()));
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

    public String _test_getName()
    {
        return tag.either(s -> s, t -> t.getTagInfo().getName());
    }

    public boolean _test_hasInner()
    {
        return tag.either(s -> false, t -> t.getTagInfo().getInner() != null);
    }
}
