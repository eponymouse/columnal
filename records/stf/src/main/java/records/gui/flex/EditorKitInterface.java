package records.gui.flex;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An interface that all FlexibleTextField editor kit implementations
 * must conform to.
 */
public interface EditorKitInterface
{
    /**
     * Can the field this kit is attached to receive focus and be edited?
     */
    public boolean isEditable();

    /**
     * Sets the field that this kit is attached to
     * @param field Non-null if newly attached to a field, null if being
     *              detached from a field we are currently attached to.
     */
    public void setField(@Nullable FlexibleTextField field);

    /**
     * Performs some action to take focus away from this field
     * (e.g. on to a cell selection instead).
     */
    public void relinquishFocus();

    /**
     * Called when the focus is gained or lost on the attached field
     * @param curContent The current text content of the field
     * @param focused Is the field gaining (true) or losing (false) focus?
     */
    public void focusChanged(String curContent, boolean focused);

    /**
     * If this is instanceof EditorKit&;lt;T&gt;, return this.  Otherwise
     * return null.
     */
    public <T> @Nullable EditorKit<T> asEditableKit(Class<T> itemClass);
}
