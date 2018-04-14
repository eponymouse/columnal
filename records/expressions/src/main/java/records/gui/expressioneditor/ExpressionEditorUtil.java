package records.gui.expressioneditor;

import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.BooleanExpression;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.error.InternalException;
import records.gui.TypeDialog;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix.QuickFixParams;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix.ReplacementTarget;
import records.transformations.expression.Expression;
import records.transformations.expression.FixedTypeExpression;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.NaryOpExpression.TypeProblemDetails;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.TypeState;
import records.transformations.expression.UnitExpression;
import records.types.NumTypeExp;
import records.types.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
import utility.FXPlatformFunctionInt;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static records.transformations.expression.ErrorAndTypeRecorder.QuickFix.ReplacementTarget.CURRENT;

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
    }
    
    /**
     * Returns
     * @param textField
     * @param cssClass
     * @param label
     * @param surrounding
     * @param parentStyles
     * @return A pair of the VBox to display, and an action which can be used to show/clear an error on it (clear by passing null)
     */
    @NonNull
    protected static <E extends StyledShowable, P> Pair<ErrorTop, ErrorDisplayer<E, P>> withLabelAbove(TextField textField, String cssClass, String label, @Nullable @UnknownInitialization ConsecutiveChild<?, ?> surrounding, TopLevelEditor<?, ?> editor, FXPlatformConsumer<Pair<ReplacementTarget, @UnknownIfRecorded LoadableExpression<E, P>>> replaceWithFixed, Stream<String> parentStyles)
    {
        FXUtility.sizeToFit(textField, 10.0, 10.0);
        textField.getStyleClass().addAll(cssClass + "-name", "labelled-name");
        Label typeLabel = new Label(label);
        typeLabel.getStyleClass().addAll(cssClass + "-top", "labelled-top");
        if (surrounding != null)
        {
            enableSelection(typeLabel, surrounding);
            enableDragFrom(typeLabel, surrounding);
        }
        setStyles(typeLabel, parentStyles);
        ErrorTop vBox = new ErrorTop(typeLabel, textField);
        vBox.getStyleClass().add(cssClass);
        ExpressionInfoDisplay errorShower = installErrorShower(vBox, typeLabel, textField);
        return new Pair<>(vBox, new ErrorDisplayer<E, P>()
        {
            @Override
            public boolean isShowingError()
            {
                return errorShower.isShowingError();
            }

            @SuppressWarnings("recorded") // Damned if I can work out the right annotation
            @Override
            public void addErrorAndFixes(StyledString s, List<QuickFix<E, P>> q)
            {
                vBox.setError(true);
                errorShower.addMessageAndFixes(s, q, editor.getWindow(), editor.getTableManager(), replaceWithFixed);
            }

            @Override
            public void clearAllErrors()
            {
                vBox.setError(false);
                errorShower.clearError();
            }

            @Override
            public void showType(String type)
            {
                errorShower.setType(type);
            }

            @Override
            public void cleanup()
            {
                errorShower.hideImmediately();
            }
        });
    }

    public static ExpressionInfoDisplay installErrorShower(ErrorTop vBox, Label topLabel, TextField textField)
    {
        ExpressionInfoDisplay expressionInfoDisplay = new ExpressionInfoDisplay(vBox, topLabel, textField);
        vBox.bindErrorMasking(expressionInfoDisplay.maskingErrors());
        return expressionInfoDisplay;
    }

    @NonNull
    protected static <E extends LoadableExpression<E, P>, P> Pair<ErrorTop, ErrorDisplayer<E, P>> keyword(String keyword, String cssClass, @Nullable @UnknownInitialization OperandNode<?, ?> surrounding, TopLevelEditor<?, ?> expressionEditor, FXPlatformConsumer<Pair<ReplacementTarget, @UnknownIfRecorded LoadableExpression<E, P>>> replace, Stream<String> parentStyles)
    {
        TextField t = new TextField(keyword);
        t.setEditable(false);
        t.setDisable(true);
        return ExpressionEditorUtil.<E, P>withLabelAbove(t, cssClass, "", surrounding, expressionEditor, replace, parentStyles);
    }

    public static void setStyles(Label topLabel, Stream<String> parentStyles)
    {
        topLabel.getStyleClass().add(parentStyles.collect(Collectors.joining("-")) + "-child");
    }

    @SuppressWarnings("recorded")
    @OnThread(Tag.Any)
    public static List<QuickFix<Expression,ExpressionNodeParent>> quickFixesForTypeError(Expression src, @Nullable DataType fix)
    {
        List<QuickFix<Expression,ExpressionNodeParent>> quickFixes = new ArrayList<>();
        FXPlatformFunctionInt<QuickFixParams, Pair<ReplacementTarget, LoadableExpression<Expression, ExpressionNodeParent>>> makeTypeFix = params -> {
            TypeDialog typeDialog = new TypeDialog(params.parentWindow, params.tableManager.getTypeManager(), false);
            @Nullable DataType dataType = typeDialog.showAndWait().orElse(Optional.empty()).orElse(null);
            if (dataType != null)
            {
                return new Pair<>(CURRENT, FixedTypeExpression.fixType(dataType, src));
            } else
            {
                return new Pair<>(CURRENT, src);
            }
        };
        quickFixes.add(new QuickFix<Expression, ExpressionNodeParent>(StyledString.s(TranslationUtility.getString("fix.setType")), ImmutableList.<String>of(), makeTypeFix));
        if (fix != null)
        {
            @NonNull DataType fixFinal = fix;
            quickFixes.add(new QuickFix<Expression, ExpressionNodeParent>(StyledString.s(TranslationUtility.getString("fix.setTypeTo", fix.toString())), ImmutableList.of(), p -> new Pair<>(CURRENT, FixedTypeExpression.fixType(fixFinal, src))));
        }
        return quickFixes;
    }

    @OnThread(Tag.Any)
    public static List<QuickFix<Expression,ExpressionNodeParent>> getFixesForMatchingNumericUnits(TypeState state, TypeProblemDetails p)
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
            } else
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
        //Log.debug(">>> literals: " + Utility.listToString(literals));
        //Log.debug(">>> non-literals: " + nonLiteralUnits.size());
        
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
                    return Collections.singletonList(new QuickFix<Expression,ExpressionNodeParent>(StyledString.s(TranslationUtility.getString("fix.changeUnit", uniqueNonLiteralUnits.get(0).toString())), ImmutableList.of(), params -> {
                        return new Pair<>(CURRENT, literal.getFirst().withUnit(uniqueNonLiteralUnits.get(0)));
                    }));
                }
            }
        }
        return Collections.emptyList();
    }

    public static class CopiedItems implements Serializable
    {
        private static final long serialVersionUID = 3245083225504039668L;
        /**
         * Expressions are saved to string, operators are there as the raw string
         * They strictly alternate (operand-operator-operand etc) and the boolean
         * tracks whether first one was an operator (otherwise: operand)
         */
        public final List<String> items;
        public final boolean startsOnOperator;

        public CopiedItems(List<String> items, boolean startsOnOperator)
        {
            this.items = items;
            this.startsOnOperator = startsOnOperator;
        }
    }

    @SuppressWarnings("initialization")
    public static <E extends StyledShowable, P> void enableDragFrom(Label dragSource, @UnknownInitialization ConsecutiveChild<E, P> src)
    {
        TopLevelEditor<?, ?> editor = src.getParent().getEditor();
        dragSource.setOnDragDetected(e -> {
            editor.ensureSelectionIncludes(src);
            @Nullable CopiedItems selection = editor.getSelection();
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
