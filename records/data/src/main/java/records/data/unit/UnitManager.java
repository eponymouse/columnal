package records.data.unit;

import annotation.identifier.qual.UnitIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.IOUtils;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.grammar.MainParser.UnitsContext;
import records.grammar.UnitLexer;
import records.grammar.UnitParser;
import records.grammar.UnitParser.*;
import records.loadsave.OutputBuilder;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 09/12/2016.
 */
public class UnitManager
{
    // Left means it's an alias, Right means full-unit
    private final Map<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> builtInUnits = new HashMap<>();
    private final Map<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> userUnits = new HashMap<>();
    
    // This map is the merger of builtInUnits and userUnits.
    // In case of clashes, builtInUnits is preferred.
    private final Map<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> knownUnits = new HashMap<>();

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
                    @UnitIdentifier String name = unit.getDefined().getName();
                    knownUnits.put(name, Either.right(unit));
                    this.builtInUnits.put(name, Either.right(unit));
                }
                else if (decl.aliasDeclaration() != null)
                {
                    AliasDeclarationContext aliasDeclaration = decl.aliasDeclaration();
                    @SuppressWarnings("identifier")
                    @UnitIdentifier String newName = aliasDeclaration.singleUnit(0).IDENT().getText();
                    @SuppressWarnings("identifier")
                    @UnitIdentifier String origName = aliasDeclaration.singleUnit(1).IDENT().getText();
                    knownUnits.put(newName, Either.left(origName));
                    this.builtInUnits.put(newName, Either.left(origName));
                }
            }
        }
        catch (IOException e)
        {
            throw new InternalException("Error reading data file", e);
        }
    }
    
    private @Nullable UnitDeclaration getKnownUnit(@UnitIdentifier String name)
    {
        Either<@UnitIdentifier String, UnitDeclaration> target = knownUnits.get(name);
        if (target == null)
            return null;
        else
            return target.<@Nullable UnitDeclaration>either(this::getKnownUnit, d -> d);
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
        if (decl.unbracketedUnit() != null)
        {
            ScaleContext scaleContext = decl.scale();
            Rational scale = loadScale(scaleContext);
            equiv = new Pair<>(scale, loadUnbracketedUnit(decl.unbracketedUnit()));
        }
        return new UnitDeclaration(new SingleUnit(defined, description, prefix, suffix), equiv);
    }

    public static Rational loadScale(ScaleContext scaleContext)
    {
        Rational scale;
        if (scaleContext == null)
            scale = Rational.ONE;
        else
        {
            ScalePowerContext scalePowerContext = scaleContext.scalePower(0);
            scale = loadScalePower(scalePowerContext);
            if (scaleContext.scalePower().size() > 1)
                scale = scale.divides(loadScalePower(scaleContext.scalePower(1)));
        }
        return scale;
    }

    public static Rational loadScalePower(ScalePowerContext scalePowerContext)
    {
        Rational rational = Utility.parseRational(scalePowerContext.NUMBER(0).getText());
        if (scalePowerContext.NUMBER().size() > 1)
            rational = Utility.rationalToPower(rational, Integer.parseInt(scalePowerContext.NUMBER(1).toString()));
        return rational;
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
                @SuppressWarnings("identifier")
                @UnitIdentifier String unitName = singleOrScaleContext.singleUnit().getText();
                @Nullable UnitDeclaration lookedUp = getKnownUnit(unitName);
                if (lookedUp == null)
                    throw new UserException("Unknown unit: \"" + unitName + "\"");
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

        @UnitIdentifier String possName = IdentifierUtility.asUnitIdentifier(commonPrefix.trim());
        if (possName == null)
            return Unit.SCALAR;
        UnitDeclaration possUnit = getKnownUnit(possName);
        if (possUnit == null)
            return Unit.SCALAR;
        else
            return possUnit.getUnit();
    }

    private Pair<Rational, Unit> canonicalise(SingleUnit original) throws UserException
    {
        UnitDeclaration decl = getKnownUnit(original.getName());
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

    public SingleUnit getDeclared(@UnitIdentifier String m) throws InternalException
    {
        UnitDeclaration unitDeclaration = getKnownUnit(m);
        if (unitDeclaration == null)
            throw new InternalException("Unknown unit: " + m);
        return unitDeclaration.getDefined();
    }

    public List<SingleUnit> getAllDeclared()
    {
        return knownUnits.values().stream().flatMap(e -> e.<Stream<SingleUnit>>either(a -> Stream.empty(), d -> Stream.of(d.getDefined()))).collect(Collectors.<@NonNull SingleUnit>toList());
    }

    public boolean isUnit(String unitName)
    {
        return knownUnits.containsKey(unitName);
    }

    public void loadUserUnits(UnitsContext units) throws UserException, InternalException
    {
        List<DeclarationContext> unitDecls = Utility.parseAsOne(units.detail().DETAIL_LINE().stream().<String>map(l -> l.getText()).filter(s -> !s.trim().isEmpty()).collect(Collectors.joining("\n")), UnitLexer::new, UnitParser::new, p -> p.file().declaration());

        for (DeclarationContext decl : unitDecls)
        {
            if (decl.unitDeclaration() != null)
            {
                UnitDeclaration unit = loadDeclaration(decl.unitDeclaration());
                @UnitIdentifier String name = unit.getDefined().getName();
                userUnits.putIfAbsent(name, Either.right(unit));
                // Don't overwrite existing binding:
                knownUnits.putIfAbsent(name, Either.right(unit));
                
            }
            else if (decl.aliasDeclaration() != null)
            {
                AliasDeclarationContext aliasDeclaration = decl.aliasDeclaration();
                @SuppressWarnings("identifier")
                @UnitIdentifier String newName = aliasDeclaration.singleUnit(0).IDENT().getText();
                @SuppressWarnings("identifier")
                @UnitIdentifier String origName = aliasDeclaration.singleUnit(1).IDENT().getText();
                knownUnits.putIfAbsent(newName, Either.left(origName));
                this.userUnits.putIfAbsent(newName, Either.left(origName));
            }
        }

    }

    public ImmutableMap<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> getAllBuiltIn()
    {
        return ImmutableMap.copyOf(this.builtInUnits);
    }

    public ImmutableMap<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> getAllUserDeclared()
    {
        return ImmutableMap.copyOf(this.userUnits);
    }
    
    // Gets the name of the canonical unit at the end of the conversion chain
    // for the given unit name.  In case of problems (broken links etc), null is returned
    public @Nullable ImmutableSet<String> getCanonicalBaseUnit(@UnitIdentifier String unitName)
    {
        UnitDeclaration declaration = getKnownUnit(unitName);
        if (declaration == null)
            return null;
        
        if (declaration.getEquivalentTo() != null)
        {
            HashSet<String> baseUnits = new HashSet<>();

            for (SingleUnit singleUnit : declaration.getEquivalentTo().getSecond().getDetails().keySet())
            {
                ImmutableSet<String> canonBase = getCanonicalBaseUnit(singleUnit.getName());
                if (canonBase == null)
                    return null;
                baseUnits.addAll(canonBase);
            }
                    
            return ImmutableSet.copyOf(baseUnits);
        }
        
        return ImmutableSet.of(unitName);
    }

    public void removeUserUnit(String name)
    {
        // We only remove from knownUnits if it's the same;
        // it's possible that we did not successfully override the built-in unit:
        Either<String, UnitDeclaration> removed = userUnits.remove(name);
        // Deliberate use of ==
        if (knownUnits.get(name) == removed)
        {
            knownUnits.remove(name);
        }
    }
    
    public void addUserUnit(Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> unit)
    {
        userUnits.putIfAbsent(unit.getFirst(), unit.getSecond());
        knownUnits.putIfAbsent(unit.getFirst(), unit.getSecond());
    }

    public String save()
    {
    return userUnits.entrySet().stream().map((Entry<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> e) -> e.getValue().either(alias -> {
            OutputBuilder b = new OutputBuilder();
            b.t(UnitParser.ALIAS, UnitParser.VOCABULARY);
            b.raw(e.getKey());
            b.t(UnitParser.EQUALS, UnitParser.VOCABULARY);
            b.raw(alias);
            return b.toString();
        }, decl -> {
            OutputBuilder b = new OutputBuilder();
            b.t(UnitParser.UNIT, UnitParser.VOCABULARY);
            b.raw(e.getKey());
            b.s(decl.getDefined().getDescription());
            @Nullable Pair<Rational, Unit> equiv = decl.getEquivalentTo();
            if (equiv != null)
            {
                b.t(UnitParser.EQUALS, UnitParser.VOCABULARY);
                if (!equiv.getFirst().equals(Rational.of(1)))
                {
                    // TODO save string source, not just canonical rational:
                    b.raw(equiv.getFirst().toString());
                    b.t(UnitParser.TIMES, UnitParser.VOCABULARY);
                }
                
                b.raw(equiv.getSecond().toString());
            }
            return b.toString();
        })).collect(Collectors.joining("\n"));
    }

    public void clearAllUser()
    {
        userUnits.clear();
        knownUnits.clear();
        knownUnits.putAll(builtInUnits);
    }
}
