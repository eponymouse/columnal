package records.gui.expressioneditor;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Chars;
import javafx.scene.Node;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 19/02/2017.
 */
public class Consecutive<EXPRESSION extends @NonNull Object> extends ConsecutiveBase<EXPRESSION>
{
    protected final ExpressionParent parent;
    private final ImmutableSet<Character> endCharacters;

    @SuppressWarnings("initialization") // Because of loading
    public Consecutive(OperandOps<EXPRESSION> operations, ExpressionParent parent, @Nullable Node prefixNode, @Nullable Node suffixNode, String style, @Nullable Pair<List<FXPlatformFunction<ConsecutiveBase<EXPRESSION>, OperandNode<EXPRESSION>>>, List<FXPlatformFunction<ConsecutiveBase<EXPRESSION>, OperatorEntry<EXPRESSION>>>> content, char... endCharacters)
    {
        super(operations, prefixNode, suffixNode, style);
        this.parent = parent;
        this.endCharacters = ImmutableSet.copyOf(Chars.asList(endCharacters));
        if (content != null)
        {
            atomicEdit.set(true);
            operands.addAll(Utility.mapList(content.getFirst(), f -> f.apply(this)));
            operators.addAll(Utility.mapList(content.getSecond(), f -> f.apply(this)));
            atomicEdit.set(false);
            // Get rid of anything which would go if you got focus and lost it again:
            focusChanged();
        }
        else
        {
            atomicEdit.set(true);
            operators.add(makeBlankOperator());
            operands.add(makeBlankOperand());
            atomicEdit.set(false);
        }
    }

    protected void selfChanged(@UnknownInitialization(ConsecutiveBase.class) Consecutive<EXPRESSION> this)
    {
        if (parent != null)
            parent.changed(this);
    }

    @Override
    protected List<Pair<DataType, List<String>>> getSuggestedParentContext() throws UserException, InternalException
    {
        return parent.getSuggestedContext(this);
    }

    @Override
    protected void parentFocusRightOfThis()
    {
        parent.focusRightOf(this);
    }

    @Override
    protected void parentFocusLeftOfThis()
    {
        parent.focusLeftOf(this);
    }

    @Override
    protected boolean isMatchNode()
    {
        return parent instanceof ClauseNode && ((ClauseNode)parent).isMatchNode(this);
    }

    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.<String>concat(parent.getParentStyles(), Stream.<String>of(style));
    }

    @Override
    public ExpressionEditor getEditor()
    {
        return parent.getEditor();
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(ExpressionNode child)
    {
        return parent.getAvailableVariables(this);
    }

    @Override
    public ImmutableSet<Character> terminatedByChars()
    {
        return endCharacters;
    }
}
