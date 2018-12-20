package test;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.scene.input.KeyCode;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.model.NavigationActions.SelectionPolicy;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.gui.stf.StructuredTextField;
import test.gui.trait.FocusOwnerTrait;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.UnitType;
import utility.Utility;
import utility.Utility.ListEx;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Random;

public class DataEntryUtil
{
    private static final int DELAY = 1;

    @OnThread(Tag.Any)
    public static void enterValue(FocusOwnerTrait robot, Random random, DataType dataType, @Value Object value, boolean nested) throws UserException, InternalException
    {
        if (!nested)
        {
            //robot.push(TestUtil.ctrlCmd(), KeyCode.A);
            //robot.push(KeyCode.DELETE);
            robot.push(KeyCode.HOME);
        }
        dataType.apply(new DataTypeVisitor<UnitType>()
        {
            @Override
            public UnitType number(NumberInfo numberInfo) throws InternalException, UserException
            {
                // Delete the current value:
                deleteWord();
                
                String num = Utility.toBigDecimal(Utility.cast(value, Number.class)).toPlainString();
                robot.write(num, DELAY);
                return UnitType.UNIT;
            }

            private void deleteWord()
            {
                // Doesn't seem to work:
                //robot.push(TestUtil.ctrlCmd(), KeyCode.DELETE);
                // Do it manually instead:                
                StructuredTextField view = robot.getFocusOwner(StructuredTextField.class);
                
                TestUtil.fx_(() -> {
                    // Taken from StyledTextAreaBehavior.deleteNextWord()
                    int start = view.getCaretPosition();

                    if (start < view.getLength())
                    {
                        view.wordBreaksForwards(2, SelectionPolicy.CLEAR);
                        int sel = view.getCaretPosition();
                        if (sel < view.getLength() && view.getText(sel, sel + 1).equals("."))
                        {
                            view.wordBreaksForwards(1, SelectionPolicy.EXTEND);
                            view.wordBreaksForwards(1, SelectionPolicy.EXTEND);
                        }
                        int end = view.getCaretPosition();
                        Log.debug("Deleting {{{" + view.getText(start, end) + "}}} from {{{" + view.getText() + "}}}");
                        view.replaceText(start, end, "");
                    }
                });
            }

            @Override
            public UnitType text() throws InternalException, UserException
            {
                robot.write("\"" + GrammarUtility.escapeChars(Utility.cast(value, String.class)) + (nested || random.nextBoolean() ? "\"" : ""), DELAY);
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                TemporalAccessor t = (TemporalAccessor)value;
                if (dateTimeInfo.getType().hasYearMonth())
                {
                    delete(4);
                    robot.write(String.format("%04d", t.get(ChronoField.YEAR)) + "-", DELAY);
                    delete(2);
                    robot.write(String.format("%02d", t.get(ChronoField.MONTH_OF_YEAR)), DELAY);
                    if (dateTimeInfo.getType().hasDay())
                    {
                        robot.write("-");
                        delete(2);
                        robot.write(String.format("%02d", t.get(ChronoField.DAY_OF_MONTH)));
                    }
                    if (dateTimeInfo.getType().hasTime())
                        robot.write(" ");
                }
                if (dateTimeInfo.getType().hasTime())
                {
                    delete(2);
                    robot.write(String.format("%02d", t.get(ChronoField.HOUR_OF_DAY)) + ":", DELAY);
                    delete(2);
                    robot.write(String.format("%02d", t.get(ChronoField.MINUTE_OF_HOUR)) + ":", DELAY);
                    delete(2);
                    int nano = t.get(ChronoField.NANO_OF_SECOND);
                    robot.write(String.format("%02d", t.get(ChronoField.SECOND_OF_MINUTE)) + (nano == 0 ? "" : "."), DELAY);
                    delete(9);
                    robot.write(String.format("%09d", nano).replaceAll("0*$", ""), DELAY);
                }
                if (dateTimeInfo.getType().hasZoneId())
                {
                    ZoneId zone = ((ZonedDateTime) t).getZone();
                    Log.debug("Zone: {{{" + zone + "}}} is " + zone.getId());
                    robot.write(" ");
                    delete(3);
                    robot.write(zone.getId(), DELAY);
                }
                
                return UnitType.UNIT;
            }

            @Override
            public UnitType bool() throws InternalException, UserException
            {
                // Delete the false which is a placeholder:
                delete(5);

                robot.write(Boolean.toString(Utility.cast(value, Boolean.class)), DELAY);
                return UnitType.UNIT;
            }

            private void delete(int amount)
            {
                for (int i = 0; i < amount; i++)
                    robot.push(KeyCode.DELETE);
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                delete(tags.stream().mapToInt(t -> t.getName().length()).max().orElse(0));
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
