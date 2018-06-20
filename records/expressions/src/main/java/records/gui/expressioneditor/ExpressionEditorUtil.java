package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.BooleanExpression;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.jellytype.JellyType;
import records.transformations.expression.Expression;
import records.transformations.expression.NaryOpExpression.TypeProblemDetails;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.QuickFix;
import records.transformations.expression.TypeLiteralExpression;
import records.transformations.expression.TypeState;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.UnfinishedTypeExpression;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplierInt;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 21/01/2017.
 */
public class ExpressionEditorUtil
{
    // A VBox that has an error at the top.  Main reason this
    // is its own class not just VBox is to retain information which
    // we dig out for testing purposes
    public static class ErrorTop extends VBox
    {
        private final Label topLabel;
        private boolean hasError;
        private boolean maskErrors = false;

        public ErrorTop(Label topLabel, Node content)
        {
            super(topLabel, content);
            this.topLabel = topLabel;
        }
 
        @OnThread(Tag.FXPlatform)
        public Stream<Pair<String, Boolean>> _test_getHeaderState()
        {
            return Stream.of(new Pair<>(topLabel.getText(), hasError && !maskErrors));
        }

        @OnThread(Tag.FXPlatform)
        public void setError(boolean error)
        {
            this.hasError = error;
            FXUtility.setPseudoclass(this, "exp-error", hasError && !maskErrors);
            //Log.logStackTrace("Error state: " + hasError + " && " + !maskErrors);
        }

        @OnThread(Tag.FXPlatform)
        private void bindErrorMasking(BooleanExpression errorMasking)
        {
            maskErrors = errorMasking.get();
            FXUtility.addChangeListenerPlatformNN(errorMasking, maskErrors -> {
                this.maskErrors = maskErrors;
                setError(hasError);
            });
            setError(hasError);
        }

        // For debugging purposes:
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public String toString()
        {
            return "ErrorTop " + topLabel.getText() + ": " + (getChildren().size() > 1 ? getChildren().get(1) : "<empty>");
        }
    }
    
    public static ExpressionInfoDisplay installErrorShower(ErrorTop vBox, Label topLabel, TextField textField)
    {
        ExpressionInfoDisplay expressionInfoDisplay = new ExpressionInfoDisplay(vBox, topLabel, textField);
        vBox.bindErrorMasking(expressionInfoDisplay.maskingErrors());
        return expressionInfoDisplay;
    }

    public static void setStyles(Label topLabel, Stream<String> parentStyles)
    {
        topLabel.getStyleClass().add(parentStyles.collect(Collectors.joining("-")) + "-child");
    }

    @SuppressWarnings("recorded")
    @OnThread(Tag.Any)
    public static List<QuickFix<Expression,ExpressionSaver>> quickFixesForTypeError(TypeManager typeManager, Expression src, @Nullable DataType fix)
    {
        List<QuickFix<Expression,ExpressionSaver>> quickFixes = new ArrayList<>();
        FXPlatformSupplierInt<Expression> makeTypeFix = () -> {
            return TypeLiteralExpression.fixType(typeManager, fix == null ? new UnfinishedTypeExpression("") : TypeExpression.fromDataType(fix), src);
        };
        quickFixes.add(new QuickFix<Expression, ExpressionSaver>(StyledString.s(TranslationUtility.getString("fix.setType")), ImmutableList.<String>of(), src, makeTypeFix));
        if (fix != null)
        {
            @NonNull DataType fixFinal = fix;
            quickFixes.add(new QuickFix<Expression, ExpressionSaver>(StyledString.s(TranslationUtility.getString("fix.setTypeTo", fix.toString())), ImmutableList.of(), src, () -> TypeLiteralExpression.fixType(typeManager, JellyType.fromConcrete(fixFinal), src)));
        }
        return quickFixes;
    }

    @OnThread(Tag.Any)
    public static List<QuickFix<Expression,ExpressionSaver>> getFixesForMatchingNumericUnits(TypeState state, TypeProblemDetails p)
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
                    unit = unitExpression.asUnit(state.getUnitManager()).<@Nullable Unit>either(_err -> null, u -> u.toConcreteUnit());
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
        List<Unit> uniqueNonLiteralUnits = Utility.filterOutNulls(nonLiteralUnits.stream()).distinct().collect(Collectors.toList());
        if (uniqueNonLiteralUnits.size() == 1)
        {
            for (Pair<NumericLiteral, @Nullable Unit> literal : literals)
            {
                Log.debug("Us: " + p.getOurExpression() + " literal: " + literal.getFirst() + " match: " + (literal.getFirst() == p.getOurExpression()));
                Log.debug("Non-literal unit: " + uniqueNonLiteralUnits.get(0) + " us: " + literal.getSecond());
                if (literal.getFirst() == p.getOurExpression() && !uniqueNonLiteralUnits.get(0).equals(literal.getSecond()))
                {
                    return Collections.singletonList(new QuickFix<Expression,ExpressionSaver>(StyledString.s(TranslationUtility.getString("fix.changeUnit", uniqueNonLiteralUnits.get(0).toString())), ImmutableList.of(), p.getOurExpression(), () -> {
                        return literal.getFirst().withUnit(uniqueNonLiteralUnits.get(0));
                    }));
                }
            }
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("initialization")
    public static <E extends StyledShowable, P> void enableDragFrom(Label dragSource, @UnknownInitialization ConsecutiveChild<E, P> src)
    {
        TopLevelEditor<?, ?> editor = src.getParent().getEditor();
        dragSource.setOnDragDetected(e -> {
            editor.ensureSelectionIncludes(src);
            @Nullable String selection = editor.getSelection();
            if (selection != null)
            {
                editor.setSelectionLocked(true);
                Dragboard db = dragSource.startDragAndDrop(TransferMode.MOVE);
                db.setContent(Collections.singletonMap(FXUtility.getTextDataFormat("Expression"), selection));
            }
            e.consume();
        });
        dragSource.setOnDragDone(e -> {
            editor.setSelectionLocked(false);
            if (e.getTransferMode() != null)
            {
                editor.removeSelectedItems();
            }
            e.consume();
        });
    }

    @SuppressWarnings("initialization")
    public static <E extends StyledShowable, P> void enableSelection(Label typeLabel, @UnknownInitialization ConsecutiveChild<E, P> node)
    {
        typeLabel.setOnMouseClicked(e -> {
            if (!e.isStillSincePress())
                return;

            if (e.isShiftDown())
                node.getParent().getEditor().extendSelectionTo(node);
            else
                node.getParent().getEditor().selectOnly(node);
            e.consume();
        });
    }
}
