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

package xyz.columnal.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.TypeExp.TypeError;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.Utility;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

/**
 * The state used while type-checking expressions.
 *
 * It changes based on pattern matches, which introduce new variables.
 */
public final class TypeState
{
    // In-built variables:
    public static final @ExpressionIdentifier String GROUP_COUNT = "group count";
    public static final @ExpressionIdentifier String ROW_NUMBER = "row";
    
    // Doesn't change in modified TypeStates, but is passed through unchanged from initial TypeState.
    private final FunctionLookup functionLookup;

    private final ImmutableMap<String, TypeExp> variablePreTypes;
    // If variable is in there but > size 1, means it is known but it is defined by multiple guards
    // This is okay if they don't use it, but if they do use it, must attempt unification across all the types.
    // These are not @ExpressionIdentifier because there may be internal
    // names like ?1
    private final ImmutableMap<String, ImmutableList<TypeExp>> variables;
    private final TypeManager typeManager;
    private final UnitManager unitManager;
    private int nextLambdaId = 1;

    public TypeState(TypeManager typeManager, FunctionLookup functionLookup)
    {
        this(ImmutableMap.of(), ImmutableMap.of(), typeManager, typeManager.getUnitManager(), functionLookup);
    }

    private TypeState(ImmutableMap<String, TypeExp> variablePreTypes, ImmutableMap<String, ImmutableList<TypeExp>> variables, TypeManager typeManager, UnitManager unitManager, FunctionLookup functionLookup)
    {
        this.variablePreTypes = variablePreTypes;
        this.variables = variables;
        this.typeManager = typeManager;
        this.unitManager = unitManager;
        this.functionLookup = functionLookup;
    }

    public static TypeState withRowNumber(TypeManager typeManager, FunctionLookup functionLookup) throws InternalException
    {
        TypeState typeState = new TypeState(typeManager, functionLookup).add(ROW_NUMBER, TypeExp.plainNumber(null), ss -> {
        });
        if (typeState != null)
            return typeState;
        else
            throw new InternalException("Could not add row number variable");
    }

    public @Nullable TypeState add(@ExpressionIdentifier String varName, TypeExp type, Consumer<StyledString> error) throws InternalException
    {
        if (variables.containsKey(varName))
        {
            error.accept(StyledString.s("Duplicate variable name: " + varName));
            return null;
        }
        ImmutableMap.Builder<String, ImmutableList<TypeExp>> copy = ImmutableMap.builder();
        copy.putAll(variables);
        @Nullable TypeExp preType = variablePreTypes.get(varName);
        if (preType != null)
        {
            Either<TypeError, TypeExp> unified = TypeExp.unifyTypes(type, preType);
            unified.ifLeft(typeError -> error.accept(typeError.getMessage()));
            if (unified.isLeft())
                return null;
        }
        copy.put(varName, ImmutableList.of(type));
        return new TypeState(ImmutableMap.<String, TypeExp>copyOf(Maps.<String, TypeExp>filterEntries(variablePreTypes, (Entry<String, TypeExp> e) -> e != null && !e.getKey().equals(varName))), copy.build(), typeManager, unitManager, functionLookup);
    }

    public TypeState addImplicitLambdas(ImmutableList<@Recorded ImplicitLambdaArg> lambdaArgs, ImmutableList<TypeExp> argTypes) throws InternalException
    {
        TypeState typeState = this;
        for (int i = 0; i < lambdaArgs.size(); i++)
        {
            lambdaArgs.get(i).assignId(this);
            typeState = typeState.addAllowShadow(lambdaArgs.get(i).getVarName(), argTypes.get(i));
        }
        return typeState;
    }

    public TypeState addAllowShadow(String varName, TypeExp type)
    {
        // We allow name shadowing without complaint:
        HashMap<String, ImmutableList<TypeExp>> copy = new HashMap<>();
        copy.putAll(variables);
        copy.put(varName, ImmutableList.of(type));
        return new TypeState(variablePreTypes, ImmutableMap.copyOf(copy), typeManager, unitManager, functionLookup);
    }

    /**
     *  Merges a set of type states from different pattern guards.
     *
     *  The semantics here are that duplicate variables are allowed, if they refer to a variable
     *  with the same type. (e.g. @case (4, x) @orcase (6, x))  If they have a different type,
     *  it's an error.
     *
     *  This is different semantics to the union function, which does not permit variables with
     *  the same name (this may be a bit confusing: intersect does allow duplicates, but union
     *  does not!)
     */

    public static TypeState intersect(List<TypeState> typeStates)
    {
        Map<String, ImmutableList<TypeExp>> mergedVars = new HashMap<>(typeStates.get(0).variables);
        for (int i = 1; i < typeStates.size(); i++)
        {
            Map<String, ImmutableList<TypeExp>> variables = typeStates.get(i).variables;
            for (Entry<@KeyFor("variables") String, ImmutableList<TypeExp>> entry : variables.entrySet())
            {
                // If it's present in both sets, only keep if same type, otherwise mask:
                mergedVars.merge(entry.getKey(), entry.getValue(), (a, b) -> Utility.concatI(a, b));
            }
        }
        return new TypeState(ImmutableMap.of(), ImmutableMap.copyOf(mergedVars), typeStates.get(0).typeManager, typeStates.get(0).unitManager, typeStates.get(0).functionLookup);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeState typeState = (TypeState) o;

        return variables.equals(typeState.variables);
    }

    @Override
    public int hashCode()
    {
        return variables.hashCode();
    }

    // If it's null, it's totally unknown
    // If it's > size 1, it should count as masked because it has different types in different guards
    public @Nullable ImmutableList<TypeExp> findVarType(String varName)
    {
        return variables.get(varName);
    }

    /*
    public static class TypeAndTagInfo
    {
        public final DataType wholeType;
        public final int tagIndex;
        public final @Nullable DataType innerType;

        public TypeAndTagInfo(DataType wholeType, int tagIndex, @Nullable DataType innerType)
        {
            this.wholeType = wholeType;
            this.tagIndex = tagIndex;
            this.innerType = innerType;
        }
    }

    public @Nullable TypeAndTagInfo findTaggedType(Pair<String, String> tagName, Consumer<String> onError) throws InternalException
    {
        String typeName = tagName.getFirst();
        @Nullable DataType type;
        type = typeManager.lookupType(typeName);
        if (type == null)
        {
            onError.accept("Could not find tagged type: \"" + typeName + "\"");
            return null;
        }

        Pair<Integer, @Nullable DataType> tagDetail = type.unwrapTag(tagName.getSecond());
        if (tagDetail.getFirst() == -1)
        {
            onError.accept("Type \"" + typeName + "\" does not have tag: \"" + tagName + "\"");
            return null;
        }

        return new TypeAndTagInfo(type, tagDetail.getFirst(), tagDetail.getSecond());
    }
    */

    public UnitManager getUnitManager()
    {
        return unitManager;
    }

    public TypeManager getTypeManager()
    {
        return typeManager;
    }
    
    @Override
    public String toString()
    {
        return "TypeState{" +
                "variables=" + variables +
                '}';
    }

    public int getNextLambdaId()
    {
        return nextLambdaId++;
    }

    public ImmutableSet<String> getAvailableVariables()
    {
        return variables.keySet();
    }

    public FunctionLookup getFunctionLookup()
    {
        return functionLookup;
    }

    public @Nullable TypeState addPreType(String varName, TypeExp typeExp, Consumer<StyledString> onError)
    {
        if (variablePreTypes.containsKey(varName))
        {
            onError.accept(StyledString.s("Duplicate types given for variable " + varName));
            return null;
        }
        
        ImmutableMap.Builder<String, TypeExp> newPreTypes = ImmutableMap.builder();
        newPreTypes.putAll(variablePreTypes);
        newPreTypes.put(varName, typeExp);
        return new TypeState(newPreTypes.build(), variables, typeManager, unitManager, functionLookup);
    }
}
