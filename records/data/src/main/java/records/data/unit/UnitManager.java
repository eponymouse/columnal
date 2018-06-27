package records.data.unit;

import annotation.identifier.qual.UnitIdentifier;
import org.apache.commons.io.IOUtils;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.UnitLexer;
import records.grammar.UnitParser;
import records.grammar.UnitParser.AliasDeclarationContext;
import records.grammar.UnitParser.DeclarationContext;
import records.grammar.UnitParser.DisplayContext;
import records.grammar.UnitParser.SingleContext;
import records.grammar.UnitParser.TimesByContext;
import records.grammar.UnitParser.UnbracketedUnitContext;
import records.grammar.UnitParser.UnitContext;
import records.grammar.UnitParser.UnitDeclarationContext;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

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
            String builtInUnits = IOUtils.toString(stream, StandardCharsets.UTF_8);
            stream.close();
            List<DeclarationContext> decls = Utility.parseAsOne(builtInUnits, UnitLexer::new, UnitParser::new, p -> p.file().declaration());
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
        @UnitIdentifier String defined = IdentifierUtility.fromParsed(decl.singleUnit());
        String description = decl.STRING() != null ? decl.STRING().getText() : "";
        String suffix = "";
        String prefix = "";
        @Nullable Pair<Rational, Unit> equiv = null;
        for (DisplayContext displayContext : decl.display())
        {
            if (displayContext.PREFIX() != null)
                prefix = displayContext.STRING().getText();
            else
                suffix = displayContext.STRING().getText();
        }
        if (decl.unit() != null)
        {
            Rational scale;
            if (decl.scale() == null)
                scale = Rational.ONE;
            else
            {
                scale = Utility.parseRational(decl.scale().NUMBER(0).getText());
                if (decl.scale().NUMBER().size() > 1)
                    scale = Utility.rationalToPower(scale, Integer.parseInt(decl.scale().NUMBER(1).toString()));
            }
            equiv = new Pair<>(scale, loadUnit(decl.unit()));
        }
        return new UnitDeclaration(new SingleUnit(defined, description, prefix, suffix), equiv);
    }

    // Like loadUse, but any UserException is treated as an InternalException
    // since we expect the unit to be present.
    public Unit loadBuiltIn(String text) throws InternalException
    {
        try
        {
            return loadUse(text);
        }
        catch (UserException e)
        {
            throw new InternalException(e.getMessage() == null ? "Unknown unit error" : e.getMessage());
        }
    }

    public Unit loadUse(String text) throws UserException, InternalException
    {
        if (text.startsWith("{") && text.endsWith("}"))
            text = text.substring(1, text.length() - 1);
        if (text.isEmpty() || text.equals("1"))
            return Unit.SCALAR;
        UnbracketedUnitContext ctx = Utility.parseAsOne(text, UnitLexer::new, UnitParser::new, p -> p.unitUse().unbracketedUnit());
        return loadUnbracketedUnit(ctx);
    }

    private Unit loadUnbracketedUnit(UnbracketedUnitContext ctx) throws UserException
    {
        Unit u = loadUnit(ctx.unit());
        if (ctx.divideBy() != null)
            u = u.divideBy(loadUnit(ctx.divideBy().unit()));
        else
        {
            for (TimesByContext rhs : ctx.timesBy())
            {
                u = u.times(loadUnit(rhs.unit()));
            }
        }

        return u;
    }

    private Unit loadUnit(UnitContext unit) throws UserException
    {
        if (unit.single() != null)
            return loadSingle(unit.single());
        else
            return loadUnbracketedUnit(unit.unbracketedUnit());
    }

    private Unit loadSingle(SingleContext singleOrScaleContext) throws UserException
    {
        Unit base;
        if (singleOrScaleContext.singleUnit() != null)
        {
            if (singleOrScaleContext.singleUnit().UNITVAR() != null)
            {
                throw new UserException("Unit variables not allowed here");
            }
            else
            {
                @Nullable UnitDeclaration lookedUp = knownUnits.get(singleOrScaleContext.singleUnit().getText());
                if (lookedUp == null)
                    throw new UserException("Unknown unit: \"" + singleOrScaleContext.singleUnit().getText() + "\"");
                base = lookedUp.getUnit();
            }
        }
        else if (singleOrScaleContext.NUMBER() != null && singleOrScaleContext.NUMBER().getText().equals("1"))
        {
            return Unit.SCALAR;
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

    public Unit guessUnit(@Nullable String commonPrefix)
    {
        if (commonPrefix == null)
            return Unit.SCALAR;

        UnitDeclaration possUnit = knownUnits.get(commonPrefix.trim());
        if (possUnit == null)
            return Unit.SCALAR;
        else
            return possUnit.getUnit();
    }

    private Pair<Rational, Unit> canonicalise(SingleUnit original) throws UserException
    {
        UnitDeclaration decl = knownUnits.get(original.getName());
        if (decl == null)
            throw new UserException("Unknown unit: {" + original.getName() + "}");
        @Nullable Pair<Rational, Unit> equiv = decl.getEquivalentTo();
        if (equiv == null)
            return new Pair<>(Rational.ONE, decl.getUnit());
        else
            return canonicalise(equiv);
    }

    /**
     * Takes a unit U, and gets the canonical unit C, and what number you have to multiply U by to get C.
     */
    public Pair<Rational, Unit> canonicalise(Unit original) throws UserException
    {
        return canonicalise(new Pair<>(Rational.ONE, original));
    }

    private Pair<Rational, Unit> canonicalise(Pair<Rational, Unit> original) throws UserException
    {
        Rational accumScale = original.getFirst();
        Unit accumUnit = Unit.SCALAR;
        Map<SingleUnit, Integer> details = original.getSecond().getDetails();
        for (Entry<@KeyFor("details") SingleUnit, Integer> entry : details.entrySet())
        {
            Pair<Rational, Unit> canonicalised = entry.getKey() instanceof SingleUnit ?
                canonicalise((SingleUnit)entry.getKey()) : new Pair<>(Rational.ONE, new Unit(entry.getKey()));
            accumScale = accumScale.times(Utility.rationalToPower(canonicalised.getFirst(), entry.getValue()));
            accumUnit = accumUnit.times(canonicalised.getSecond().raisedTo(entry.getValue()));
        }
        return new Pair<>(accumScale, accumUnit);
    }

    public SingleUnit getDeclared(String m) throws InternalException
    {
        UnitDeclaration unitDeclaration = knownUnits.get(m);
        if (unitDeclaration == null)
            throw new InternalException("Unknown unit: " + m);
        return unitDeclaration.getDefined();
    }

    public List<SingleUnit> getAllDeclared()
    {
        return knownUnits.values().stream().map(d -> d.getDefined()).collect(Collectors.<@NonNull SingleUnit>toList());
    }

    public boolean isUnit(String unitName)
    {
        return knownUnits.containsKey(unitName);
    }
}
