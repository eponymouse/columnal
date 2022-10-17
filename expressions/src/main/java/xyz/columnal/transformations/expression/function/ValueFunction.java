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

package xyz.columnal.transformations.expression.function;

import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.transformations.expression.explanation.Explanation;
import xyz.columnal.transformations.expression.explanation.ExplanationLocation;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.FunctionInt;
import xyz.columnal.utility.function.simulation.SimulationSupplierInt;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ValueFunctionBase;

import java.util.ArrayList;
import java.util.stream.Stream;

// I want to label this class @Value but I don't seem able to.  Perhaps because it is abstract?
public abstract class ValueFunction extends ValueFunctionBase
{
    // All fields are used temporarily while _call() is executing.
    // This means that an individual ValueFunction is not re-entrant.
    
    // null if we are not recording explanationss
    private @Nullable ArrayList<ExplanationLocation> usedLocations;
    // null if we are not recording
    private @Nullable ImmutableList<ArgumentExplanation> argExplanations;
    // null if we are not recording
    private @Nullable ArrayList<Explanation> extraExplanations;
    
    private @Nullable ExplanationLocation resultIsLocation;
    
    // Non-null once we are executing:
    private @Value Object @MonotonicNonNull[] curArgs;
    

    @OnThread(Tag.Any)
    protected ValueFunction()
    {
    }

    // Not for external calling
    @OnThread(Tag.Simulation)
    protected abstract @Value Object _call() throws InternalException, UserException;

    // Call without recording an explanation
    @OnThread(Tag.Simulation)
    public final @Value Object call(@Value Object[] args) throws InternalException, UserException
    {
        this.curArgs = args;
        this.argExplanations = null;
        this.usedLocations = null;
        this.extraExplanations = null;
        return _call();
    }

    // Call and record an explanation
    @OnThread(Tag.Simulation)
    public final RecordedFunctionResult callRecord(@Value Object[] args, @Nullable ImmutableList<ArgumentExplanation> argumentExplanations) throws InternalException, UserException
    {
        this.curArgs = args;
        this.argExplanations = argumentExplanations;
        this.usedLocations = new ArrayList<>();
        ArrayList<Explanation> extra = this.extraExplanations = new ArrayList<>();
        @Value Object result = _call();
        return new RecordedFunctionResult(result, Utility.<Explanation>concatI(argumentExplanations == null ? ImmutableList.<Explanation>of() : Utility.<ArgumentExplanation, Explanation>mapListInt(argumentExplanations, e -> e.getValueExplanation()), extra), usedLocations != null ? ImmutableList.copyOf(usedLocations) : ImmutableList.of(), resultIsLocation);
    }

    protected final <T> @Value T arg(int index, Class<T> tClass) throws InternalException
    {
        return Utility.cast(arg(index), tClass);
    }
    
    protected final @Value Object arg(int index) throws InternalException
    {
        if (curArgs == null)
            throw new InternalException("Function accessing missing arguments");
        if (index < 0 || index >= curArgs.length)
            throw new InternalException("Function " + getClass() + " accessing argument " + index + " but only " + curArgs.length);
        return curArgs[index];
    }
    
    protected @Value int intArg(int index) throws InternalException, UserException
    {
        return DataTypeUtility.requireInteger(arg(index, Number.class));
    }
    
    @OnThread(Tag.Simulation)
    protected final @Value Object callArg(int index, @Value Object[] arguments) throws InternalException, UserException
    {
        ValueFunction function = arg(index, ValueFunction.class);
        if (extraExplanations == null)
        {
            // Not recording:
            return function.call(arguments);
        }
        else
        {
            // We are recording, so pass argument explanations to the chained call:
            RecordedFunctionResult r = function.callRecord(arguments, null);
            if (extraExplanations != null)
            {
                extraExplanations.clear();
                extraExplanations.addAll(r.childExplanations);
            }
            if (usedLocations != null)
            {
                usedLocations.clear();
                usedLocations.addAll(r.usedLocations);
            }
            return r.result;
        }
    }
    
    // Used by some function implementations to access explanations
    // of arguments, or of a list element of an argument.
    public static interface ArgumentExplanation
    {
        @OnThread(Tag.Simulation)
        Explanation getValueExplanation() throws InternalException;

        // If this argument is a column, give back the location
        // for the given zero-based index.
        @OnThread(Tag.Simulation)
        @Nullable ExplanationLocation getListElementLocation(int index) throws InternalException;
    }
    
    protected final void addUsedLocations(FunctionInt<ImmutableList<ArgumentExplanation>, Stream<ExplanationLocation>> extractLocations) throws InternalException
    {
        if (argExplanations != null && this.usedLocations != null)
            this.usedLocations.addAll(extractLocations.apply(argExplanations).collect(ImmutableList.<ExplanationLocation>toImmutableList()));
    }
    
    protected final void setResultIsLocation(ExplanationLocation explanationLocation)
    {
        if (this.usedLocations != null)
            this.resultIsLocation = explanationLocation;
    }
    
    @OnThread(Tag.Simulation)
    protected final void addExtraExplanation(SimulationSupplierInt<Explanation> makeExplanation) throws InternalException
    {
        if (extraExplanations != null)
        {
            extraExplanations.add(makeExplanation.get());
        }
    }

    @SuppressWarnings("valuetype")
    public static @Value ValueFunction value(@UnknownIfValue ValueFunction function)
    {
        return function;
    }

    public static class RecordedFunctionResult
    {
        public final @Value Object result;
        public final ImmutableList<Explanation> childExplanations;
        public final ImmutableList<ExplanationLocation> usedLocations;
        public final @Nullable ExplanationLocation resultIsLocation;

        public RecordedFunctionResult(@Value Object result, ImmutableList<Explanation> childExplanations, ImmutableList<ExplanationLocation> usedLocations, @Nullable ExplanationLocation resultIsLocation)
        {
            this.result = result;
            this.childExplanations = childExplanations;
            this.usedLocations = usedLocations;
            this.resultIsLocation = resultIsLocation;
        }
    }
}
