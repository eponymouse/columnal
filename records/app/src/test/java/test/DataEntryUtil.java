package test;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.UnitType;
import utility.Utility;
import utility.Utility.ListEx;

import java.util.Random;

public class DataEntryUtil
{
    private static final int DELAY = 1;

    @OnThread(Tag.Simulation)
    public static void enterValue(FxRobotInterface robot, Random random, DataType dataType, @Value Object value, boolean nested) throws UserException, InternalException
    {
        if (!nested)
        {
            robot.push(TestUtil.ctrlCmd(), KeyCode.A);
            robot.push(KeyCode.DELETE);
            robot.push(KeyCode.HOME);
        }
        dataType.apply(new DataTypeVisitor<UnitType>()
        {
            @Override
            public UnitType number(NumberInfo numberInfo) throws InternalException, UserException
            {
                String num = Utility.toBigDecimal(Utility.cast(value, Number.class)).toPlainString();
                robot.write(num, DELAY);
                return UnitType.UNIT;
            }

            @Override
            public UnitType text() throws InternalException, UserException
            {
                robot.write("\"" + Utility.cast(value, String.class) + (nested || random.nextBoolean() ? "\"" : ""), DELAY);
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                robot.write(DataTypeUtility.valueToString(dataType, value, null));
                return UnitType.UNIT;
            }

            @Override
            public UnitType bool() throws InternalException, UserException
            {
                robot.write(Boolean.toString(Utility.cast(value, Boolean.class)), DELAY);
                return UnitType.UNIT;
            }
            
            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType tagged(TypeId typeName, ImmutableList<DataType> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                robot.write(DataTypeUtility.valueToString(dataType, value, null));
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                robot.write("(");
                @Value Object[] tuple = Utility.castTuple(value, inner.size());
                for (int i = 0; i < tuple.length; i++)
                {
                    if (i > 0)
                    {
                        robot.write(",");
                        if (random.nextBoolean())
                            robot.write(" ");
                    }
                    enterValue(robot, random, inner.get(i), tuple[i], true);
                }

                if (nested || random.nextBoolean())
                    robot.write(")");
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner != null)
                {
                    robot.write("[");
                    ListEx listEx = Utility.cast(value, ListEx.class);
                    for (int i = 0; i < listEx.size(); i++)
                    {
                        if (i > 0)
                        {
                            robot.write(",");
                            if (random.nextBoolean())
                                robot.write(" ");
                        }
                        enterValue(robot, random, inner, listEx.get(i), true);
                    }
                    if (nested || random.nextBoolean())
                        robot.write("]");
                }
                return UnitType.UNIT;
            }
        });
    }
}
