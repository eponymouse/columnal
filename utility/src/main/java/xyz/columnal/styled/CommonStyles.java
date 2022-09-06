package styled;

import javafx.scene.text.Text;
import styled.StyledString.Style;

public class CommonStyles
{
    private abstract static class BasicStyle<S extends Style<S>> extends Style<S>
    {
        public BasicStyle(Class<S> thisClass)
        {
            super(thisClass);
        }

        @Override
        protected S combine(S with)
        {
            return with;
        }

        @Override
        protected boolean equalsStyle(S item)
        {
            return true;
        }
    }
    
    private static class Italic extends BasicStyle<Italic>
    {
        private Italic()
        {
            super(Italic.class);
        }

        @Override
        protected void style(Text t)
        {
            t.setStyle("-fx-font-style: italic;");
        }
    }
    
    public static final Italic ITALIC = new Italic();

    private static class Monospace extends BasicStyle<Monospace>
    {
        private Monospace()
        {
            super(Monospace.class);
        }

        @Override
        protected void style(Text t)
        {
            // TODO
        }
    }

    public static final Monospace MONOSPACE = new Monospace();
}
