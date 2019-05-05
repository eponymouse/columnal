package records.transformations.expression;

import annotation.identifier.qual.UnitIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import javafx.scene.Scene;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyUnit;
import records.transformations.expression.QuickFix.QuickFixAction;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

// Same distinction as IdentExpression/InvalidIdentExpression
public class SingleUnitExpression extends UnitExpression
{
    private final @UnitIdentifier String name;

    public SingleUnitExpression(@UnitIdentifier String text)
    {
        this.name = text;
    }

    @Override
    public JellyUnit asUnit(@Recorded SingleUnitExpression this, UnitManager unitManager) throws UnitLookupException
    {
        try
        {
            return JellyUnit.fromConcrete(unitManager.loadUse(name));
        }
        catch (InternalException | UserException e)
        {
            Stream<QuickFix<@Recorded UnitExpression>> similarNames = Utility.findAlternatives(name, unitManager.getAllDeclared().stream(), su -> Stream.of(su.getName(), su.getDescription()))
                .<QuickFix<@Recorded UnitExpression>>map(su -> new QuickFix<@Recorded UnitExpression>(StyledString.s("Correct"), ImmutableList.of(), this, () -> new SingleUnitExpression(su.getName())));
            QuickFix<@Recorded UnitExpression> makeNew = new QuickFix<@Recorded UnitExpression>(StyledString.s("Create unit \"" + name + "\""), ImmutableList.<String>of(), this, new QuickFixAction()
            {
                @Override
                public @OnThread(Tag.FXPlatform) void doAction(FixHelper fixHelper, Scene editorScene)
                {
                    fixHelper.createNewUnit(name, editorScene);
                }
            });
            ImmutableList<QuickFix<@Recorded UnitExpression>> fixes = Stream.<QuickFix<@Recorded UnitExpression>>concat(similarNames, Stream.<QuickFix<@Recorded UnitExpression>>of(makeNew)).collect(ImmutableList.<QuickFix<@Recorded UnitExpression>>toImmutableList());
            throw new UnitLookupException(StyledString.s(e.getLocalizedMessage()), this, fixes);
        }
    }

    @Override
    public String save(boolean structured, boolean topLevel)
    {
        return name;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SingleUnitExpression that = (SingleUnitExpression) o;

        return name.equals(that.name);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public boolean isScalar()
    {
        return false;
    }

    @Override
    public int hashCode()
    {
        return name.hashCode();
    }

    @Override
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }
}
