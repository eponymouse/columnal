package records.data.unit;

import de.uni_freiburg.informatik.ultimate.logic.Rational;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.UnitLexer;
import records.grammar.UnitParser;
import records.grammar.UnitParser.AliasDeclarationContext;
import records.grammar.UnitParser.DeclarationContext;
import records.grammar.UnitParser.DisplayContext;
import records.grammar.UnitParser.SingleOrScaleContext;
import records.grammar.UnitParser.UnitContext;
import records.grammar.UnitParser.UnitDeclarationContext;
import utility.Utility;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by neil on 09/12/2016.
 */
public class UnitManager
{
    // Note that because of aliasing, it is not necessarily the case
    // that knownUnits.get("foo") has the canonical name "foo"
    private final Map<String, UnitDeclaration> knownUnits = new HashMap<>();

    @SuppressWarnings("initialization")
    public UnitManager() throws InternalException, UserException
    {
        try
        {
            @Nullable ClassLoader classLoader = getClass().getClassLoader();
            if (classLoader == null)
                throw new InternalException("Could not find class loader");
            @Nullable InputStream stream = classLoader.getResourceAsStream("builtin_units.txt");
            if (stream == null)
                throw new InternalException("Could not find data file");
            List<DeclarationContext> decls = Utility.parseAsOne(stream, UnitLexer::new, UnitParser::new, p -> p.file().declaration());
            for (DeclarationContext decl : decls)
            {
                if (decl.unitDeclaration() != null)
                {
                    UnitDeclaration unit = loadDeclaration(decl.unitDeclaration());
                    knownUnits.put(unit.getDefined().getName(), unit);
                }
                else if (decl.aliasDeclaration() != null)
                {
                    AliasDeclarationContext aliasDeclaration = decl.aliasDeclaration();
                    String newName = aliasDeclaration.singleUnit(0).getText();
                    String origName = aliasDeclaration.singleUnit(1).getText();
                    UnitDeclaration origUnit = knownUnits.get(origName);
                    if (origUnit == null)
                        throw new UserException("Attempting to alias to unknown unit: \"" + origName + "\"");
                    knownUnits.put(newName, origUnit);
                    origUnit.addAlias(newName);
                }
            }
        }
        catch (IOException e)
        {
            throw new InternalException("Error reading data file", e);
        }
    }

    private UnitDeclaration loadDeclaration(UnitDeclarationContext decl) throws UserException
    {
        String defined = decl.singleUnit().getText();
        String description = decl.STRING() != null ? decl.STRING().getText() : "";
        String suffix = "";
        String prefix = "";
        @Nullable Unit equiv = null;
        for (DisplayContext displayContext : decl.display())
        {
            if (displayContext.PREFIX() != null)
                prefix = displayContext.STRING().getText();
            else
                suffix = displayContext.STRING().getText();
        }
        if (decl.unit() != null)
        {
            equiv = loadUnit(decl.unit());
        }
        return new UnitDeclaration(new SingleUnit(defined, description, prefix, suffix), equiv);
    }

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
            @Nullable UnitDeclaration lookedUp = knownUnits.get(singleOrScaleContext.singleUnit().getText());
            if (lookedUp == null)
                throw new UserException("Unknown unit: \"" + singleOrScaleContext.singleUnit().getText() + "\"");
            base = lookedUp.getUnit();
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
        UnitDeclaration possUnit = knownUnits.get(commonPrefix.trim());
        if (possUnit == null)
            return Unit.SCALAR;
        else
            return possUnit.getUnit();
    }
}
