package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Status;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.UnitEntry;
import records.gui.expressioneditor.UnitNodeParent;
import records.loadsave.OutputBuilder;
import records.types.units.UnitExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 18/02/2017.
 */
public class UnfinishedUnitExpression extends UnitExpression
{
    private final String text;

    public UnfinishedUnitExpression(String text)
    {
        this.text = text;
    }

    @Override
    public Either<Pair<String, List<UnitExpression>>, UnitExp> asUnit(UnitManager unitManager)
    {
        // TODO add known similar units:
        return Either.left(new Pair<>("Unknown unit", Collections.emptyList()));
    }

    @Override
    public String save(boolean topLevel)
    {
        return "@unfinished " + OutputBuilder.quoted(text);
    }

    @Override
    public @OnThread(Tag.FXPlatform) OperandNode<UnitExpression> edit(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel)
    {
        return new UnitEntry(parent, text, false);
    }


    @Override
    public boolean equals(@Nullable Object o)
    {
        return o instanceof UnfinishedUnitExpression && text.equals(((UnfinishedUnitExpression)o).text);
    }

    @Override
    public int hashCode()
    {
        return text.hashCode();
    }

    public String getText()
    {
        return text;
    }
}
