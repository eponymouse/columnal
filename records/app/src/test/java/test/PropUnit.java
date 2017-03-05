package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenUnit;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 24/01/2017.
 */
@RunWith(JUnitQuickcheck.class)
public class PropUnit
{
    @Property
    public void propUnits(@From(GenUnit.class) Unit a, @From(GenUnit.class) Unit b) throws UserException, InternalException
    {
        UnitManager mgr = new UnitManager();
        assertEquals(a.equals(b), b.equals(a));
        assertEquals(a.canScaleTo(b, mgr), b.canScaleTo(a, mgr).map(Rational::reciprocal));
        assertEquals(a, a.reciprocal().reciprocal());
        assertEquals(b, b.reciprocal().reciprocal());
        assertEquals(a.divide(b), b.divide(a).reciprocal());
        assertEquals(a.times(b), a.divide(b.reciprocal()));
        assertEquals(a, a.divide(b).times(b));
        assertEquals(a, a.times(b).divide(b));
        for (int i = 1; i < 10; i++)
            assertEquals(a, a.raisedTo(i).rootedBy(i));
    }
}
