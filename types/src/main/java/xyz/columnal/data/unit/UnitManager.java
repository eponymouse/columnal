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

package xyz.columnal.data.unit;

import annotation.identifier.qual.UnitIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.IOUtils;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.UnitLexer;
import xyz.columnal.grammar.UnitParser;
import xyz.columnal.grammar.UnitParser.AliasDeclarationContext;
import xyz.columnal.grammar.UnitParser.DisplayContext;
import xyz.columnal.grammar.UnitParser.FileItemContext;
import xyz.columnal.grammar.UnitParser.ScaleContext;
import xyz.columnal.grammar.UnitParser.ScalePowerContext;
import xyz.columnal.grammar.UnitParser.SingleContext;
import xyz.columnal.grammar.UnitParser.TimesByContext;
import xyz.columnal.grammar.UnitParser.UnbracketedUnitContext;
import xyz.columnal.grammar.UnitParser.UnitContext;
import xyz.columnal.grammar.UnitParser.UnitDeclarationContext;
import xyz.columnal.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.ResourceUtility;
import xyz.columnal.utility.Utility;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 09/12/2016.
 */
public final class UnitManager
{
    // Left means it's an alias, Right means full-unit
    private final Map<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> builtInUnits = new HashMap<>();
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private final Map<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> userUnits = new HashMap<>();
    
    // This map is the merger of builtInUnits and userUnits.
    // In case of clashes, builtInUnits is preferred.
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private final Map<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> knownUnits = new HashMap<>();
    
    public UnitManager() throws InternalException, UserException
    {
        this("builtin_units.txt");
    }
    
    @SuppressWarnings("initialization")
    public UnitManager(@Nullable String builtInName) throws InternalException, UserException
    {
        if (builtInName == null)
            return;
        try
        {
            @Nullable InputStream stream = ResourceUtility.getResourceAsStream(builtInName);
            if (stream == null)
                throw new InternalException("Could not find data file");
            String builtInUnits = IOUtils.toString(stream, StandardCharsets.UTF_8);
            stream.close();
            List<FileItemContext> decls = Utility.parseAsOne(builtInUnits, UnitLexer::new, UnitParser::new, p -> p.file().fileItem());
            String curCategory = "";
            for (FileItemContext item : decls)
            {
                if (item.declaration() != null && item.declaration().unitDeclaration() != null)
                {
                    UnitDeclaration unit = loadDeclaration(item.declaration().unitDeclaration(), curCategory);
                    @UnitIdentifier String name = unit.getDefined().getName();
                    knownUnits.put(name, Either.right(unit));
                    this.builtInUnits.put(name, Either.right(unit));
                }
                else if (item.declaration() != null && item.declaration().aliasDeclaration() != null)
                {
                    AliasDeclarationContext aliasDeclaration = item.declaration().aliasDeclaration();
                    @UnitIdentifier String newName = IdentifierUtility.fromParsed(aliasDeclaration.singleUnit(0));
                    @UnitIdentifier String origName = IdentifierUtility.fromParsed(aliasDeclaration.singleUnit(1));
                    knownUnits.put(newName, Either.left(origName));
                    this.builtInUnits.put(newName, Either.left(origName));
                }
                else if (item.category() != null)
                {
                    curCategory = item.category().STRING().getText();
                }
            }
        }
        catch (IOException e)
        {
            throw new InternalException("Error reading data file", e);
        }
    }

    private synchronized @Nullable UnitDeclaration getKnownUnit(@UnitIdentifier String name)
    {
        Either<@UnitIdentifier String, UnitDeclaration> target = knownUnits.get(name);
        if (target == null)
            return null;
        else
            return target.<@Nullable UnitDeclaration>either(this::getKnownUnit, d -> d);
    }

    private UnitDeclaration loadDeclaration(UnitDeclarationContext decl, String category) throws UserException
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
        return new UnitDeclaration(new SingleUnit(defined, description, prefix, suffix), equiv, category);
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
                @UnitIdentifier String unitName = IdentifierUtility.fromParsed(singleOrScaleContext.singleUnit());
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

    public synchronized List<SingleUnit> getAllDeclared()
    {
        return knownUnits.values().stream().flatMap(e -> e.<Stream<SingleUnit>>either(a -> Stream.<SingleUnit>empty(), d -> Stream.of(d.getDefined()))).collect(Collectors.<@NonNull SingleUnit>toList());
    }

    public synchronized void loadUserUnits(String unitsSrc) throws UserException, InternalException
    {
        List<FileItemContext> unitDecls = Utility.parseAsOne(unitsSrc, UnitLexer::new, UnitParser::new, p -> p.file().fileItem());

        for (FileItemContext decl : unitDecls)
        {
            if (decl.declaration().unitDeclaration() != null)
            {
                UnitDeclaration unit = loadDeclaration(decl.declaration().unitDeclaration(), "");
                @UnitIdentifier String name = unit.getDefined().getName();
                userUnits.putIfAbsent(name, Either.right(unit));
                // Don't overwrite existing binding:
                knownUnits.putIfAbsent(name, Either.right(unit));
                
            }
            else if (decl.declaration().aliasDeclaration() != null)
            {
                AliasDeclarationContext aliasDeclaration = decl.declaration().aliasDeclaration();
                @UnitIdentifier String newName = IdentifierUtility.fromParsed(aliasDeclaration.singleUnit(0));
                @UnitIdentifier String origName = IdentifierUtility.fromParsed(aliasDeclaration.singleUnit(1));
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

    public synchronized void removeUserUnit(String name)
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
    
    public synchronized void addUserUnit(Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> unit)
    {
        userUnits.putIfAbsent(unit.getFirst(), unit.getSecond());
        knownUnits.putIfAbsent(unit.getFirst(), unit.getSecond());
    }

    public List<String> save()
    {
        return save(u -> true);
    }

    public synchronized List<String> save(Predicate<@UnitIdentifier String> saveUnit)
    {
        return save(userUnits.keySet().stream().<@UnitIdentifier String>map(x -> x).filter(saveUnit));
    }

    public synchronized List<String> save(Stream<@UnitIdentifier String> unitsToSaveStream)
    {
        List<@UnitIdentifier String> toProcess = new ArrayList<>(unitsToSaveStream.collect(Collectors.<@UnitIdentifier String>toList()));
        HashSet<@UnitIdentifier String> unitsToSave = new HashSet<>();
        // Now save any units aliased or used in definition of those units:
        // Note: toProcess may grow while we execute!
        for (int i = 0; i < toProcess.size(); i++)
        {
            @UnitIdentifier String u = toProcess.get(i);
            unitsToSave.add(u);
            Either<@UnitIdentifier String, UnitDeclaration> def = knownUnits.get(u);
            if (def != null)
            {
                def.either_(t -> {
                    if (!unitsToSave.contains(t))
                        toProcess.add(t);
                }, decl -> {
                    if (decl.getEquivalentTo() != null)
                    {
                        for (SingleUnit v : decl.getEquivalentTo().getSecond().getDetails().keySet())
                        {
                            if (!unitsToSave.contains(v.getName()))
                                toProcess.add(v.getName());
                        }
                    }
                });
            }
        }
        
        return unitsToSave.stream().<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>>flatMap(u -> {
            Either<@UnitIdentifier String, UnitDeclaration> v = userUnits.get(u);
            return v == null ? Stream.of() : Stream.of(new Pair<>(u, v));
        }).map((Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> e) -> e.getSecond().either(alias -> {
            OutputBuilder b = new OutputBuilder();
            b.t(UnitParser.ALIAS, UnitParser.VOCABULARY);
            b.raw(e.getFirst());
            b.t(UnitParser.EQUALS, UnitParser.VOCABULARY);
            b.raw(alias);
            return b.toString();
        }, decl -> {
            OutputBuilder b = new OutputBuilder();
            b.t(UnitParser.UNIT, UnitParser.VOCABULARY);
            b.raw(e.getFirst());
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
        })).collect(ImmutableList.<String>toImmutableList());
    }

    public synchronized void clearAllUser()
    {
        userUnits.clear();
        knownUnits.clear();
        knownUnits.putAll(builtInUnits);
    }
}
