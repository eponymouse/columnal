package records.transformations.expression;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.beans.binding.BooleanExpression;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.jellytype.JellyType;
import records.transformations.expression.ConstructorExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.NaryOpExpression.TypeProblemDetails;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.QuickFix;
import records.transformations.expression.StandardFunction;
import records.transformations.expression.TypeLiteralExpression;
import records.transformations.expression.TypeState;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.function.FunctionLookup;
import records.transformations.expression.type.IdentTypeExpression;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.TypeExpression;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.FXPlatformSupplierInt;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 21/01/2017.
 */
public class ExpressionUtil
{
    // Parameter should be Expression/UnitExpression/etc
    @OnThread(Tag.Any)
    public static String makeCssClass(StyledShowable replacement)
    {
        return "id-munged-" + replacement.toString().codePoints().mapToObj(i -> Integer.toString(i)).collect(Collectors.joining("-"));
    }

    @OnThread(Tag.Any)
    public static String makeCssClass(String replacement)
    {
        return "id-munged-" + replacement.codePoints().mapToObj(i -> Integer.toString(i)).collect(Collectors.joining("-"));
    }

    public static boolean isCallTarget(Expression expression)
    {
        // callTarget : varRef | standardFunction | constructor | unfinished;
        return expression instanceof IdentExpression
                || expression instanceof StandardFunction
                || expression instanceof ConstructorExpression;
    }

    @SuppressWarnings("recorded")
    @OnThread(Tag.Any)
    public static List<QuickFix<Expression>> quickFixesForTypeError(TypeManager typeManager, FunctionLookup functionLookup, Expression src, @Nullable DataType fix)
    {
        List<QuickFix<Expression>> quickFixes = new ArrayList<>();
        FXPlatformSupplierInt<Expression> makeTypeFix = () -> {
            return TypeLiteralExpression.fixType(functionLookup, fix == null ? new InvalidIdentTypeExpression("") : TypeExpression.fromDataType(fix), src);
        };
        quickFixes.add(new QuickFix<Expression>(StyledString.s(TranslationUtility.getString("fix.setType")), ImmutableList.<String>of(), src, makeTypeFix));
        if (fix != null)
        {
            @NonNull DataType fixFinal = fix;
            quickFixes.add(new QuickFix<Expression>(StyledString.s(TranslationUtility.getString("fix.setTypeTo", fix.toString())), ImmutableList.of(), src, () -> TypeLiteralExpression.fixType(typeManager, functionLookup, JellyType.fromConcrete(fixFinal), src)));
        }
        return quickFixes;
    }

    @OnThread(Tag.Any)
    public static List<QuickFix<Expression>> getFixesForMatchingNumericUnits(TypeState state, TypeProblemDetails p)
    {
        // Must be a units issue.  Check if fixing a numeric literal involved would make
        // the units match all non-literal units:
        List<Pair<NumericLiteral, @Nullable Unit>> literals = new ArrayList<>();
        List<@Nullable Unit> nonLiteralUnits = new ArrayList<>();
        for (int i = 0; i < p.expressions.size(); i++)
        {
            Expression expression = p.expressions.get(i);
            if (expression instanceof NumericLiteral)
            {
                NumericLiteral n = (NumericLiteral) expression;
                @Nullable UnitExpression unitExpression = n.getUnitExpression();
                @Nullable Unit unit;
                if (unitExpression == null)
                    unit = Unit.SCALAR;
                else
                    unit = unitExpression.asUnit(state.getUnitManager()).<@Nullable Unit>either(_err -> null, u -> {
                        try
                        {
                            return u.makeUnit(ImmutableMap.of());
                        }
                        catch (InternalException e)
                        {
                            Log.log(e);
                            return null;
                        }
                    });
                literals.add(new Pair<NumericLiteral, @Nullable Unit>(n, unit));
            }
            else
            {
                @Nullable TypeExp type = p.getType(i);
                if (type != null && !(type instanceof NumTypeExp))
                {
                    // Non-numeric type; definitely can't offer a sensible fix:
                    return Collections.emptyList();
                }
                nonLiteralUnits.add(type == null ? null : ((NumTypeExp) type).unit.toConcreteUnit());
            }
        }
        Log.debug(">>> literals: " + Utility.listToString(literals));
        Log.debug(">>> non-literals: " + Utility.listToString(nonLiteralUnits));
        
        // For us to offer the quick fix, we need the following conditions: all non-literals
        // have the same known unit (and there is at least one non-literal).
        List<Unit> uniqueNonLiteralUnits = Utility.filterOutNulls(nonLiteralUnits.stream()).distinct().collect(Collectors.<Unit>toList());
        if (uniqueNonLiteralUnits.size() == 1)
        {
            for (Pair<NumericLiteral, @Nullable Unit> literal : literals)
            {
                Log.debug("Us: " + p.getOurExpression() + " literal: " + literal.getFirst() + " match: " + (literal.getFirst() == p.getOurExpression()));
                Log.debug("Non-literal unit: " + uniqueNonLiteralUnits.get(0) + " us: " + literal.getSecond());
                if (literal.getFirst() == p.getOurExpression() && !uniqueNonLiteralUnits.get(0).equals(literal.getSecond()))
                {
                    return Collections.singletonList(new QuickFix<Expression>(StyledString.s(TranslationUtility.getString("fix.changeUnit", uniqueNonLiteralUnits.get(0).toString())), ImmutableList.of(), p.getOurExpression(), () -> {
                        return literal.getFirst().withUnit(uniqueNonLiteralUnits.get(0));
                    }));
                }
            }
        }
        return Collections.emptyList();
    }
}
