package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableMap;
import javafx.scene.input.DataFormat;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.InvalidSingleUnitExpression;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeSaver;
import utility.FXPlatformConsumer;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.stream.Stream;

public class UnitEditor extends TopLevelEditor<UnitExpression, UnitSaver>
{
    public static final DataFormat UNIT_CLIPBOARD_TYPE = FXUtility.getDataFormat("application/records-type");
    private final FXPlatformConsumer<@Nullable Unit> onChange;

    public UnitEditor(TypeManager typeManager, @Nullable UnitExpression startingValue, FXPlatformConsumer<@Nullable Unit> onChange)
    {
        super(new UnitExpressionOps(), typeManager, "unit-editor");
        
        this.onChange = onChange;
        onChange.consume(null);

        // Safe because at end of constructor:
        Utility.later(this).loadContent(
            startingValue != null ? startingValue : new InvalidSingleUnitExpression(""), true);
    }

    @Override
    protected void selfChanged()
    {
        super.selfChanged();
        clearAllErrors();
        @UnknownIfRecorded UnitExpression unitExpression = save();
        saved();
        @Nullable Unit unit = unitExpression.asUnit(getTypeManager().getUnitManager()).<@Nullable Unit>either(p -> null, u -> {
            try
            {
                return u.makeUnit(ImmutableMap.of());
            }
            catch (InternalException e)
            {
                Log.log(e);
                return null;
            }
        });
        //Log.debug("Latest type: " + dataType + " from expression: " + typeExpression.save(new TableAndColumnRenames(ImmutableMap.of())));
        onChange.consume(unit);
    }

    @Override
    protected boolean hasImplicitRoundBrackets()
    {
        return false;
    }

    @Override
    public @Recorded UnitExpression save()
    {
        UnitSaver saver = new UnitSaver(this, true);
        save(saver);
        @Recorded UnitExpression unitExpression = saver.finish(children.get(children.size() - 1));
        Log.debug("Saved as: " + unitExpression);
        return unitExpression;
    }

    @Override
    protected void parentFocusRightOfThis(Focus side, boolean becauseOfTab)
    {

    }

    @Override
    protected void parentFocusLeftOfThis()
    {

    }
    
    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.empty();
    }

    @Override
    public boolean showCompletionImmediately(@UnknownInitialization ConsecutiveChild<UnitExpression, UnitSaver> child)
    {
        // We show immediately if we are preceded by an operator:
        int index = Utility.indexOfRef(this.children, child);
        if (index == 0)
            return this.children.size() == 1; // Show if otherwise empty
        ConsecutiveChild<UnitExpression, UnitSaver> before = this.children.get(index - 1);
        return (before instanceof UnitEntry && ((UnitEntry)before).isOperator());
    }

    @Override
    public DataFormat getClipboardType()
    {
        return UNIT_CLIPBOARD_TYPE;
    }

    @Override
    protected @Nullable LoadableExpression<UnitExpression, UnitSaver> parse(String src) throws InternalException, UserException
    {
        return UnitExpression.load(src);
    }
}
