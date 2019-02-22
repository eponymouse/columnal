package records.gui.expressioneditor;

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
import records.gui.expressioneditor.EEDisplayNode.Focus;
import records.gui.expressioneditor.TopLevelEditor.SelectExtremityTarget;
import records.gui.expressioneditor.TopLevelEditor.SelectionTarget;
import records.jellytype.JellyType;
import records.transformations.expression.Expression;
import records.transformations.expression.NaryOpExpression.TypeProblemDetails;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.QuickFix;
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
public class ExpressionEditorUtil
{
    // A VBox that has an error at the top.  Main reason this
    // is its own class not just VBox is to retain information which
    // we dig out for testing purposes
    @OnThread(Tag.FXPlatform)
    public static class ErrorTop extends VBox
    {
        private final Label topLabel;
        private final Pane errorPane;
        private boolean hasError;
        private boolean maskErrors = false;
        // This used to be static and shared, but that causes a memory leak.  It's so small,
        // just make one per pane:
        private final WritableImage SQUIGGLE = new WritableImage(4, 4);
        {
            SQUIGGLE.getPixelWriter().setColor(0, 0, Color.RED);
            SQUIGGLE.getPixelWriter().setColor(0, 1, Color.RED);
            SQUIGGLE.getPixelWriter().setColor(1, 1, Color.RED);
            SQUIGGLE.getPixelWriter().setColor(1, 2, Color.RED);
            SQUIGGLE.getPixelWriter().setColor(2, 2, Color.RED);
            SQUIGGLE.getPixelWriter().setColor(2, 3, Color.RED);
            SQUIGGLE.getPixelWriter().setColor(3, 1, Color.RED);
            SQUIGGLE.getPixelWriter().setColor(3, 2, Color.RED);
        }

        public ErrorTop(Label topLabel, Node content)
        {
            this.topLabel = topLabel;
            this.errorPane = new Pane();
            getChildren().setAll(topLabel, content, errorPane);
            setFillWidth(true);
            errorPane.setMinHeight(5);            
            this.errorPane.setBackground(new Background(new BackgroundImage(
                SQUIGGLE, BackgroundRepeat.REPEAT, BackgroundRepeat.NO_REPEAT,
                new BackgroundPosition(Side.LEFT, 0, false, Side.TOP, 0, false), 
                BackgroundSize.DEFAULT)));            
            // Correct the offset so that the squiggles line up on adjacent items:
            FXUtility.addChangeListenerPlatformNN(errorPane.localToSceneTransformProperty(), l2s -> {
                double sceneX = errorPane.localToScene(0, 0).getX();
                // Since Background is Immutable, we have to recreate the whole thing with new offset:
                this.errorPane.setBackground(new Background(new BackgroundImage(
                    SQUIGGLE, BackgroundRepeat.REPEAT, BackgroundRepeat.NO_REPEAT,
                    new BackgroundPosition(Side.LEFT, 4 - (sceneX % 4), false, Side.TOP, 0, false), 
                    BackgroundSize.DEFAULT)));
            });
            hasError = false;
            errorPane.setVisible(false);
        }
 
        @OnThread(Tag.FXPlatform)
        public Stream<Pair<Label, Boolean>> _test_getHeaderState()
        {
            return Stream.of(new Pair<>(topLabel, hasError && !maskErrors));
        }

        @OnThread(Tag.FXPlatform)
        public void setError(boolean error)
        {
            this.hasError = error;
            //FXUtility.setPseudoclass(this, "exp-error", hasError && !maskErrors);
            errorPane.setVisible(hasError && !maskErrors);
            //Log.logStackTrace("Error state: " + hasError + " && " + !maskErrors);
        }

        @OnThread(Tag.FXPlatform)
        void bindErrorMasking(BooleanExpression errorMasking)
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

    public static void setStyles(Label topLabel, Stream<String> parentStyles)
    {
        String joined = parentStyles.collect(Collectors.joining("-"));
        if (!joined.isEmpty())
            topLabel.getStyleClass().add(joined + "-child");
    }

    @SuppressWarnings("recorded")
    @OnThread(Tag.Any)
    public static List<QuickFix<Expression,ExpressionSaver>> quickFixesForTypeError(TypeManager typeManager, FunctionLookup functionLookup, Expression src, @Nullable DataType fix)
    {
        List<QuickFix<Expression,ExpressionSaver>> quickFixes = new ArrayList<>();
        FXPlatformSupplierInt<Expression> makeTypeFix = () -> {
            return TypeLiteralExpression.fixType(functionLookup, fix == null ? new InvalidIdentTypeExpression("") : TypeExpression.fromDataType(fix), src);
        };
        quickFixes.add(new QuickFix<Expression, ExpressionSaver>(StyledString.s(TranslationUtility.getString("fix.setType")), ImmutableList.<String>of(), src, makeTypeFix));
        if (fix != null)
        {
            @NonNull DataType fixFinal = fix;
            quickFixes.add(new QuickFix<Expression, ExpressionSaver>(StyledString.s(TranslationUtility.getString("fix.setTypeTo", fix.toString())), ImmutableList.of(), src, () -> TypeLiteralExpression.fixType(typeManager, functionLookup, JellyType.fromConcrete(fixFinal), src)));
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
                    return Collections.singletonList(new QuickFix<Expression,ExpressionSaver>(StyledString.s(TranslationUtility.getString("fix.changeUnit", uniqueNonLiteralUnits.get(0).toString())), ImmutableList.of(), p.getOurExpression(), () -> {
                        return literal.getFirst().withUnit(uniqueNonLiteralUnits.get(0));
                    }));
                }
            }
        }
        return Collections.emptyList();
    }
    
    public static <E extends StyledShowable, P extends ClipboardSaver> void enableDragFrom(Label dragSource, @UnknownInitialization ConsecutiveChild<E, P> src_)
    {
        ConsecutiveChild<E, P> src = FXUtility.mouse(src_);
        dragSource.setOnDragDetected(e -> {
            TopLevelEditor<?, ?> editor = src.getParent().getEditor();
            editor.ensureSelectionIncludes(src);
            @Nullable Map<DataFormat, Object> selection = editor.getSelection();
            if (selection != null)
            {
                editor.setSelectionLocked(true);
                Dragboard db = dragSource.startDragAndDrop(TransferMode.MOVE);
                db.setContent(selection);
            }
            e.consume();
        });
    }
    
    public static <E extends StyledShowable, P extends ClipboardSaver> void enableDragTo(Node container, @UnknownInitialization TopLevelEditor<E, P> editor_)
    {
        TopLevelEditor<E, P> editor = FXUtility.mouse(editor_);
        container.setOnDragOver(e -> {
            if (e.getDragboard().hasContent(editor.getClipboardType()))
                e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
        });
        container.setOnDragDropped(e -> {
            editor.setSelectionLocked(false);
            @Nullable Pair<ConsecutiveChild<?, ?>, FXPlatformRunnable> dropBefore = editor.findClosestDrop(new Point2D(e.getSceneX(), e.getSceneY()), f -> {
                Dragboard db = e.getDragboard();
                if (db.hasContent(f))
                {
                    Object content = e.getDragboard().getContent(f);
                    if (content != null)
                        return content.toString();
                }
                return null;
            });
            if (dropBefore != null && !editor.selectionContains(dropBefore.getFirst()))
            {
                if (e.getTransferMode() == TransferMode.MOVE)
                {
                    editor.removeSelectedItems();
                }
                  
                dropBefore.getSecond().run();
                //editor.addContent(dropBefore, content.toString());
                e.setDropCompleted(true);
                return;
            }
            e.consume();
        });
    }
    
    public static <E extends StyledShowable, P extends ClipboardSaver> void enableSelection(Label typeLabel, @UnknownInitialization ConsecutiveChild<E, P> node_, TextField textField)
    {
        ConsecutiveChild<E, P> node = FXUtility.mouse(node_);
        typeLabel.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.isPopupTrigger())
            {
                showContextMenu(typeLabel, node, e);
                e.consume();
            }
        });
        
        typeLabel.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (!e.isStillSincePress() || e.getClickCount() != 1)
                return;
            
            if (e.isPopupTrigger())
            {
                showContextMenu(typeLabel, node, e);
                e.consume();
            }
            else if (e.getButton() == MouseButton.PRIMARY)
            {

                if (e.isShiftDown())
                    node.getParent().getEditor().extendSelectionTo(node, SelectionTarget.AS_IS);
                else
                    node.getParent().getEditor().selectOnly(node);
                e.consume();
            }
        });
        
        typeLabel.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.LEFT)
            {
                if (!e.isShiftDown())
                    node.getParent().getEditor().clearSelection();
                node.getParent().getEditor().extendSelectionTo(node, SelectionTarget.LEFT);
            }
            else if (e.getCode() == KeyCode.RIGHT)
            {
                if (!e.isShiftDown())
                    node.getParent().getEditor().clearSelection();
                node.getParent().getEditor().extendSelectionTo(node, SelectionTarget.RIGHT);
            }
            else if (e.getCode() == KeyCode.HOME)
            {
                if (!e.isShiftDown())
                    node.getParent().getEditor().clearSelection();
                node.getParent().getEditor().extendSelectionToExtremity(node, SelectExtremityTarget.HOME);
            }
            else if (e.getCode() == KeyCode.END)
            {
                if (!e.isShiftDown())
                    node.getParent().getEditor().clearSelection();
                node.getParent().getEditor().extendSelectionToExtremity(node, SelectExtremityTarget.END);
            }
            else if (e.getCode() == KeyCode.BACK_SPACE || e.getCode() == KeyCode.DELETE)
            {
                node.getParent().getEditor().deleteSelection();
            }
            else if (e.getCode() == KeyCode.X && e.isShortcutDown())
            {
                node.getParent().getEditor().cutSelection();
            }
            else if (e.getCode() == KeyCode.C && e.isShortcutDown())
            {
                node.getParent().getEditor().copySelection();
            }
            else if (e.getCode() == KeyCode.A && e.isShortcutDown())
            {
                node.getParent().getEditor().selectAllSiblings(node);
            }
            else if (e.getCode() == KeyCode.DOWN && !e.isShiftDown())
            {
                node.focus(Focus.LEFT);
            }
            e.consume();
        });
        
        textField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if ((e.isShortcutDown() && e.getCode() == KeyCode.A) || e.getCode() == KeyCode.F9)
            {
                node.getParent().getEditor().selectOnly(node);
                node.getParent().getEditor().selectAllSiblings(node);
                e.consume();
            }
            // Important not to consume by default!
        });
    }

    private static <E extends StyledShowable, P extends ClipboardSaver> void showContextMenu(Label typeLabel, ConsecutiveChild<E, P> node, MouseEvent e)
    {
        ContextMenu popupMenu = new ContextMenu(
            GUI.menuItem("cut", () -> {
                node.getParent().getEditor().ensureSelectionIncludes(node);
                node.getParent().getEditor().cutSelection();
            }),
            GUI.menuItem("copy", () -> {
                node.getParent().getEditor().ensureSelectionIncludes(node);
                node.getParent().getEditor().copySelection();
            })
        );
        popupMenu.show(typeLabel, e.getScreenX(), e.getScreenY());
    }
}
