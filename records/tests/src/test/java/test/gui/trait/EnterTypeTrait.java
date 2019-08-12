package test.gui.trait;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import javafx.scene.input.KeyCode;
import org.testfx.api.FxRobotInterface;
import records.error.InternalException;
import records.transformations.expression.Expression.SaveDestination;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.IdentTypeExpression;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.ListTypeExpression;
import records.transformations.expression.type.NumberTypeExpression;
import records.transformations.expression.type.RecordTypeExpression;
import records.transformations.expression.type.TypeApplyExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypePrimitiveLiteral;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.UnitType;

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
        else if (typeExpression instanceof IdentTypeExpression)
        {
            IdentTypeExpression un = (IdentTypeExpression) typeExpression;
            write(un.asIdent(), DELAY);
        }
        else if (typeExpression instanceof InvalidIdentTypeExpression)
        {
            InvalidIdentTypeExpression un = (InvalidIdentTypeExpression) typeExpression;
            write(un._test_getContent(), DELAY);
        }
        else if (typeExpression instanceof TypeApplyExpression)
        {
            TypeApplyExpression appl = (TypeApplyExpression) typeExpression;
            write(appl.getTypeName(), 1);
            for (Either<UnitExpression, TypeExpression> arg : appl.getArgumentsOnly())
            {
                write("(");
                push(KeyCode.DELETE);
                arg.eitherInt(unit -> {
                    write("{");
                    push(KeyCode.DELETE);
                    enterUnit(unit, r);
                    write("}");
                    return UnitType.UNIT;
                }, type -> {
                    enterType(type, r);
                    return UnitType.UNIT;
                });
                write(")");
            }
        }
        else if (typeExpression instanceof RecordTypeExpression)
        {
            RecordTypeExpression record = (RecordTypeExpression) typeExpression;
            write("(");
            push(KeyCode.DELETE);
            for (int i = 0; i < record._test_getItems().size(); i++)
            {
                Pair<@ExpressionIdentifier String, @Recorded TypeExpression> item = record._test_getItems().get(i);
                write(item.getFirst() + ": ");
                enterType(item.getSecond(), r);
                if (i < record._test_getItems().size() - 1)
                    write(r.nextBoolean() ? "," : " , ", DELAY);
            }
            write(")");
        }
        else if (typeExpression instanceof ListTypeExpression)
        {
            ListTypeExpression list = (ListTypeExpression) typeExpression;
            write("[");
            push(KeyCode.DELETE);
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
                push(KeyCode.DELETE);
                enterUnit(units, r);
                write("}");
            }
        }
        /*
        else if (typeExpression instanceof InvalidOpTypeExpression)
        {
            InvalidOpTypeExpression e = (InvalidOpTypeExpression)typeExpression;
            ImmutableList<TypeExpression> operands = e.getOperands();
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

    @OnThread(Tag.Any)
    public default void enterUnit(UnitExpression unitExpression, Random r) throws InternalException
    {
        // Bit of a hack...
        for (char c : unitExpression.save(SaveDestination.EDITOR, true).toCharArray())
        {
            write(c);
            if (c == '(')
                push(KeyCode.DELETE);
        }
    }

    public default void enterAndDeleteSmartBrackets(String internalContent)
    {
        for (char c : internalContent.toCharArray())
        {
            write(c);
            if ("({[\"".contains("" + c))
                push(KeyCode.DELETE);
        }
    }
}
