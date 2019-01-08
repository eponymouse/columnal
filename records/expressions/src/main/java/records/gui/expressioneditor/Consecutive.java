package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.LoadableExpression.SingleLoader;
import styled.StyledShowable;
import utility.FXPlatformRunnable;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 19/02/2017.
 */
public abstract class Consecutive<EXPRESSION extends StyledShowable, SAVER extends ClipboardSaver> extends ConsecutiveBase<EXPRESSION, SAVER>
{
    protected final EEDisplayNodeParent parent;
    
    public Consecutive(OperandOps<EXPRESSION, SAVER> operations, @UnknownInitialization(Object.class) EEDisplayNodeParent parent, @Nullable PrefixSuffix prefixSuffix, String style, @Nullable Stream<SingleLoader<EXPRESSION, SAVER>> content)
    {
        super(operations, prefixSuffix, style);
        this.parent = Utility.later(parent);
        if (content != null)
        {
            atomicEdit.set(true);
            children.addAll(content.map(f -> f.load(Utility.later(this))).collect(Collectors.<ConsecutiveChild<EXPRESSION, SAVER>>toList()));
            if (children.isEmpty())
                children.add(Utility.later(this).makeBlankChild(false));
            atomicEdit.set(false);
            // Get rid of anything which would go if you got focus and lost it again:
            Utility.later(this).focusChanged();
        }
        else
        {
            atomicEdit.set(true);
            children.add(Utility.later(this).makeBlankChild(false));
            atomicEdit.set(false);
        }
    }

    protected void selfChanged(@UnknownInitialization(ConsecutiveBase.class) Consecutive<EXPRESSION, SAVER> this)
    {
        if (parent != null)
            parent.changed(this);
    }

    @Override
    protected void parentFocusRightOfThis(Focus side, boolean becauseOfTab)
    {
        parent.focusRightOf(this, side, becauseOfTab);
    }

    @Override
    protected void parentFocusLeftOfThis()
    {
        parent.focusLeftOf(this);
    }

    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.<String>concat(parent.getParentStyles(), Stream.<String>of(style));
    }

    @Override
    public TopLevelEditor<?, ?> getEditor()
    {
        return parent.getEditor();
    }

    public void setEntireSelected(boolean selected, boolean focus, @Nullable FXPlatformRunnable onFocusLost)
    {
        ImmutableList<ConsecutiveChild<EXPRESSION, SAVER>> ourChildren = getAllChildren();
        if (!ourChildren.isEmpty())
            markSelection(ourChildren.get(0), ourChildren.get(ourChildren.size() - 1), selected, null);
        if (prefixNode != null)
        {
            FXUtility.setPseudoclass(prefixNode.getFirst(), "exp-selected", selected);
            if (focus)
            {
                prefixNode.getSecond().requestFocus();
                if (onFocusLost != null)
                    FXUtility.onFocusLostOnce(prefixNode.getSecond(), onFocusLost);
            }
        }
        if (suffixNode != null)
            FXUtility.setPseudoclass(suffixNode.getFirst(), "exp-selected", selected);
    }
    
    public boolean isSelectionFocused()
    {
        return prefixNode == null ? false : prefixNode.getSecond().isFocused();
    }

    public abstract @Recorded EXPRESSION save(boolean showErrors);
}
