package records.gui.expressioneditor;

import javafx.scene.Node;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import utility.Pair;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 19/02/2017.
 */
public class Consecutive extends ConsecutiveBase
{
    protected final ExpressionParent parent;

    public Consecutive(ExpressionParent parent, @Nullable Node prefixNode, @Nullable Node suffixNode, String style)
    {
        super(prefixNode, suffixNode, style);
        this.parent = parent;
        initializeContent();
    }

    protected void selfChanged(@UnknownInitialization(ConsecutiveBase.class) Consecutive this)
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
}
