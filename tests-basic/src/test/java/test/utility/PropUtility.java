/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.utility;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import xyz.columnal.error.UserException;
import test.utility.gen.GenNumber;
import xyz.columnal.utility.Utility;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;


/**
 * Created by neil on 14/05/2017.
 */
@RunWith(JUnitQuickcheck.class)
public class PropUtility
{
    @Property(trials = 2000)
    public void testNumberFracUtilities(@From(GenNumber.class) @Value Number n) throws UserException
    {
        /* Too hard to test
        assertEquals(Utility.toBigDecimal(n),
            Utility.toBigDecimal(Utility.addSubtractNumbers(
                Utility.getIntegerPart(n),
                Utility.multiplyNumbers(Utility.getFracPart(n, 35), Utility.rationalToPower(Rational.of(10), -35))
                , true)));
        */
        for (int minDP = 0; minDP < 15; minDP++)
        {
            for (int maxDP = Math.max(1, minDP); maxDP <= 20; maxDP++)
            {
                String fracPartAsString = Utility.getFracPartAsString(n, minDP, maxDP);
                assertThat(fracPartAsString.length(), Matchers.<Integer>greaterThanOrEqualTo(minDP));
                assertThat(fracPartAsString.length(), Matchers.<Integer>lessThanOrEqualTo(maxDP));
                if (!fracPartAsString.isEmpty())
                    fracPartAsString = "." + fracPartAsString;
                if (fracPartAsString.endsWith("\u2026"))
                {
                    // Check that it's a prefix of full string:
                    assertThat(Utility.toBigDecimal(n).toPlainString(), Matchers.startsWith(Utility.getIntegerPart(n).toString() + fracPartAsString.substring(0, fracPartAsString.length() - 1)));
                }
                else
                {
                    assertThat(Utility.toBigDecimal(n), Matchers.comparesEqualTo(Utility.toBigDecimal(Utility.parseNumber(Utility.getIntegerPart(n).toString() + fracPartAsString)).stripTrailingZeros()));
                }
            }
        }
    }
}
