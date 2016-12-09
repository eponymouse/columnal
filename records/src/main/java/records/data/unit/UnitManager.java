package records.data.unit;

import de.uni_freiburg.informatik.ultimate.logic.Rational;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.UnitLexer;
import records.grammar.UnitParser;
import records.grammar.UnitParser.SingleOrScaleContext;
import records.grammar.UnitParser.UnitContext;
import utility.Utility;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by neil on 09/12/2016.
 */
public class UnitManager
{
    private final Map<String, Unit> knownUnits = new HashMap<>();

    public Unit loadUse(String text) throws UserException, InternalException
    {
        if (text.startsWith("{") && text.endsWith("}"))
            text = text.substring(1, text.length() - 1);
        if (text.isEmpty())
            return Unit.SCALAR;
        UnitContext ctx = Utility.parseAsOne(text, UnitLexer::new, UnitParser::new, p -> p.unit());
        return loadUnit(ctx);
    }

    private Unit loadUnit(UnitContext ctx) throws UserException
    {
        // Dig through brackets:
        while (ctx.singleOrScale() == null)
            ctx = ctx.unit();
        Unit lhs = loadSingle(ctx.singleOrScale());
        if (ctx.unit() != null)
        {
            Unit rhs = loadUnit(ctx.unit());
            if (ctx.DIVIDE() != null)
                return lhs.divide(rhs);
            else
                return lhs.times(rhs);
        }
        else
            return lhs;
    }

    private Unit loadSingle(SingleOrScaleContext singleOrScaleContext) throws UserException
    {
        Unit base;
        if (singleOrScaleContext.singleUnit() != null)
        {
            @Nullable Unit lookedUp = knownUnits.get(singleOrScaleContext.singleUnit().getText());
            if (lookedUp == null)
                throw new UserException("Unknown unit: \"" + singleOrScaleContext.singleUnit().getText() + "\"");
            base = lookedUp;
        }
        else if (singleOrScaleContext.scale() != null)
        {
            base = new Unit(Utility.parseRational(singleOrScaleContext.scale().getText()));
        }
        else
            throw new UserException("Error parsing unit: \"" + singleOrScaleContext.getText() + "\"");

        if (singleOrScaleContext.NUMBER() != null)
        {
            try
            {
                int power = Integer.valueOf(singleOrScaleContext.NUMBER().getText());
                return base.raisedTo(power);
            }
            catch (NumberFormatException e)
            {
                throw new UserException("Problem parsing integer power: \"" + singleOrScaleContext.NUMBER().getText() + "\"", e);
            }
        }
        else
            return base;
    }

    public Unit guessUnit(String commonPrefix)
    {
        // TODO
        return Unit.SCALAR;
    }
}
