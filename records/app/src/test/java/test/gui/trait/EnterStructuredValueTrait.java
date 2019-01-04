package test.gui.trait;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import org.testfx.util.WaitForAsyncUtils;
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
import records.gui.flex.FlexibleTextField;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformRunnable;
import utility.UnitType;
import utility.Utility;
import utility.Utility.ListEx;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public interface EnterStructuredValueTrait extends FxRobotInterface, FocusOwnerTrait
{
    @OnThread(Tag.Any)
    default public void enterStructuredValue(DataType dataType, @Value Object value, Random r, boolean nested) throws InternalException, UserException
    {
        final int DELAY = 1;
        
        // Should we inline this there, or vice versa?
        if (!nested)
        {
            //FlexibleTextField view = robot.getFocusOwner(FlexibleTextField.class);
            push(TestUtil.ctrlCmd(), KeyCode.A);
            push(KeyCode.DELETE);
            push(KeyCode.HOME);
        }
        dataType.apply(new DataTypeVisitor<UnitType>()
        {
            @Override
            public UnitType number(NumberInfo numberInfo) throws InternalException, UserException
            {                
                String num = Utility.toBigDecimal(Utility.cast(value, Number.class)).toPlainString();
                write(num, DELAY);
                return UnitType.UNIT;
            }

            @Override
            public UnitType text() throws InternalException, UserException
            {
                write("\"" + GrammarUtility.escapeChars(Utility.cast(value, String.class)) + "\"", DELAY);
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                TemporalAccessor t = (TemporalAccessor) value;
                if (dateTimeInfo.getType().hasYearMonth())
                {
                    write(String.format("%04d", t.get(ChronoField.YEAR)) + "-", DELAY);
                    write(String.format("%02d", t.get(ChronoField.MONTH_OF_YEAR)), DELAY);
                    if (dateTimeInfo.getType().hasDay())
                    {
                        write("-");
                        write(String.format("%02d", t.get(ChronoField.DAY_OF_MONTH)));
                    }
                    if (dateTimeInfo.getType().hasTime())
                        write(" ");
                }
                if (dateTimeInfo.getType().hasTime())
                {
                    write(String.format("%02d", t.get(ChronoField.HOUR_OF_DAY)) + ":", DELAY);
                    write(String.format("%02d", t.get(ChronoField.MINUTE_OF_HOUR)) + ":", DELAY);
                    int nano = t.get(ChronoField.NANO_OF_SECOND);
                    write(String.format("%02d", t.get(ChronoField.SECOND_OF_MINUTE)) + (nano == 0 ? "" : "."), DELAY);
                    write(String.format("%09d", nano).replaceAll("0*$", ""), DELAY);
                }
                if (dateTimeInfo.getType().hasZoneId())
                {
                    ZoneId zone = ((ZonedDateTime) t).getZone();
                    Log.debug("Zone: {{{" + zone + "}}} is " + zone.getId());
                    write(" ");
                    write(zone.getId(), DELAY);
                }
                
                return UnitType.UNIT;
            }

            @Override
            public UnitType bool() throws InternalException, UserException
            {
                // Delete the false which is a placeholder:
                write(Boolean.toString(Utility.cast(value, Boolean.class)), DELAY);
                return UnitType.UNIT;
            }
            
            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                write(DataTypeUtility.valueToString(dataType, value, null));
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                write("(");
                @Value Object[] tuple = Utility.castTuple(value, inner.size());
                for (int i = 0; i < tuple.length; i++)
                {
                    if (i > 0)
                    {
                        write(",");
                        if (r.nextBoolean())
                            write(" ");
                    }
                    enterStructuredValue(inner.get(i), tuple[i], r, true);
                }

                write(")");
                return UnitType.UNIT;
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public UnitType array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner != null)
                {
                    write("[");
                    ListEx listEx = Utility.cast(value, ListEx.class);
                    for (int i = 0; i < listEx.size(); i++)
                    {
                        if (i > 0)
                        {
                            write(",");
                            if (r.nextBoolean())
                                write(" ");
                        }
                        enterStructuredValue(inner, listEx.get(i), r, true);
                    }
                    write("]");
                }
                return UnitType.UNIT;
            }
        });
    }
    
    // Checks STF has same content after running defocus
    @OnThread(Tag.Any)
    default public void defocusSTFAndCheck(boolean checkContentSame, FXPlatformRunnable defocus)
    {
        Window window = TestUtil.fx(() -> getRealFocusedWindow());
        Node node = TestUtil.fx(() -> window.getScene().getFocusOwner());
        assertTrue("" + node, node instanceof FlexibleTextField);
        FlexibleTextField field = (FlexibleTextField) node;
        String content = TestUtil.fx(() -> field.getText());
        ChangeListener<String> logTextChange = (a, oldVal, newVal) -> Log.logStackTrace("Text changed on defocus from : \"" + oldVal + "\" to \"" + newVal + "\"");
        if (checkContentSame)
        {
            TestUtil.fx_(() -> field.textProperty().addListener(logTextChange));
        }
        TestUtil.fx_(defocus);
        WaitForAsyncUtils.waitForFxEvents();
        assertNotEquals(node, TestUtil.fx(() -> window.getScene().getFocusOwner()));
        if (checkContentSame)
        {
            assertEquals(content, TestUtil.fx(() -> field.getText()));
            TestUtil.fx_(() -> field.textProperty().removeListener(logTextChange));
        }
    }
}
