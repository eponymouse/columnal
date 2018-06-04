package test.gui;

import com.google.common.collect.ImmutableList;
import org.testfx.api.FxRobotInterface;
import records.error.InternalException;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.InvalidOpTypeExpression;
import records.transformations.expression.type.ListTypeExpression;
import records.transformations.expression.type.NumberTypeExpression;
import records.transformations.expression.type.TaggedTypeNameExpression;
import records.transformations.expression.type.TupleTypeExpression;
import records.transformations.expression.type.TypeApplyExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypePrimitiveLiteral;
import records.transformations.expression.type.UnfinishedTypeExpression;
import test.DataEntryUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;

public interface EnterTypeTrait extends FxRobotInterface
{
    static final int DELAY = 1;
    
    @OnThread(Tag.Any)
    public default void enterType(TypeExpression typeExpression, Random r) throws InternalException
    {
        if (typeExpression instanceof TypePrimitiveLiteral)
        {
            TypePrimitiveLiteral lit = (TypePrimitiveLiteral) typeExpression;
            write(lit._test_getType().toString(), DELAY);
        }
        else if (typeExpression instanceof UnfinishedTypeExpression)
        {
            UnfinishedTypeExpression un = (UnfinishedTypeExpression) typeExpression;
            write(un._test_getContent(), DELAY);
        }
        else if (typeExpression instanceof TypeApplyExpression)
        {
            TypeApplyExpression appl = (TypeApplyExpression) typeExpression;
            for (int i = 0; i < appl._test_getOperands().size(); i++)
            {
                TypeExpression item = appl._test_getOperands().get(i).getRight("");
                enterType(item, r);
                if (i < appl._test_getOperands().size() - 1)
                    write(r.nextBoolean() ? "-" : " - ", DELAY);
            }
        }
        else if (typeExpression instanceof TupleTypeExpression)
        {
            TupleTypeExpression tuple = (TupleTypeExpression) typeExpression;
            write("(");
            for (int i = 0; i < tuple._test_getItems().size(); i++)
            {
                TypeExpression item = tuple._test_getItems().get(i);
                enterType(item, r);
                if (i < tuple._test_getItems().size() - 1)
                    write(r.nextBoolean() ? "," : " , ", DELAY);
            }
            write(")");
        }
        else if (typeExpression instanceof ListTypeExpression)
        {
            ListTypeExpression list = (ListTypeExpression) typeExpression;
            write("[");
            enterType(list._test_getContent(), r);
            write("]");
        }
        else if (typeExpression instanceof NumberTypeExpression)
        {
            NumberTypeExpression number = (NumberTypeExpression) typeExpression;
            write("Number", DELAY);
            UnitExpression units = number._test_getUnits();
            if (units != null)
            {
                write("{");
                write(units.save(true), DELAY);
                write("}");
            }
        }
        else if (typeExpression instanceof TaggedTypeNameExpression)
        {
            TaggedTypeNameExpression tag = (TaggedTypeNameExpression) typeExpression;
            write(tag.getTypeName().getRaw(), DELAY);
        }
        /*
        else if (typeExpression instanceof InvalidOpTypeExpression)
        {
            InvalidOpTypeExpression e = (InvalidOpTypeExpression)typeExpression;
            ImmutableList<TypeExpression> operands = e._test_getOperands();
            ImmutableList<String> operators = e._test_getOperators();
            for (int i = 0; i < Math.max(operands.size(), operators.size()); i++)
            {
                if (i < operands.size())
                    enterType(operands.get(i), r);
                if (i < operators.size())
                    write(operators.get(i), DELAY);
            }
        }
        */
        else
        {
            throw new RuntimeException("Unknown TypeExpression sub type: " + typeExpression.getClass());
        }
    }
}
