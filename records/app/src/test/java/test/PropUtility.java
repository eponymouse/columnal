package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import records.error.UserException;
import test.gen.GenNumber;
import utility.Utility;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;


/**
 * Created by neil on 14/05/2017.
 */
@RunWith(JUnitQuickcheck.class)
public class PropUtility
{
    @Property(trials = 10000)
    public void testNumberFracUtilities(@From(GenNumber.class) Number n) throws UserException
    {
        /* Too hard to test
        assertEquals(Utility.toBigDecimal(n),
            Utility.toBigDecimal(Utility.addSubtractNumbers(
                Utility.getIntegerPart(n),
                Utility.multiplyNumbers(Utility.getFracPart(n, 35), Utility.rationalToPower(Rational.of(10), -35))
                , true)));
        */
        for (int minDP = 0; minDP < 30; minDP++)
        {
            String fracPartAsString = Utility.getFracPartAsString(n, minDP, 9999);
            assertThat(fracPartAsString.length(), Matchers.greaterThanOrEqualTo(minDP));
            if (!fracPartAsString.isEmpty())
                fracPartAsString = "." + fracPartAsString;
            // TODO test maxDisplayDP
            // TODO also add a param to function which adds ellipsis or not, then tests that
            assertThat(Utility.toBigDecimal(n), Matchers.comparesEqualTo(Utility.toBigDecimal(Utility.parseNumber(Utility.getIntegerPart(n).toString() + fracPartAsString)).stripTrailingZeros()));
        }
    }
}
