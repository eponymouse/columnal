package records.transformations.expression.function;

import annotation.funcdoc.qual.FuncDocKey;
import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.SimulationFunction;

import java.util.Map;

public interface StandardFunctionDefinition
{
    public @ExpressionIdentifier String getName();

    public ImmutableList<@ExpressionIdentifier String> getFullName();

    @OnThread(Tag.Simulation)
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException;

    public Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> getType(TypeManager typeManager) throws InternalException;

    public @FuncDocKey String getDocKey();

    public @Localized String getMiniDescription();
    
    public ImmutableList<String> getParamNames();
    
    public ImmutableList<String> getSynonyms();
}
