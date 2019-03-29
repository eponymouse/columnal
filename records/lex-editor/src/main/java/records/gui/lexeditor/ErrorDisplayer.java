package records.gui.lexeditor;

import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
import styled.StyledString;

import java.util.List;

public interface ErrorDisplayer
{
    public void addErrorAndFixes(Span location, StyledString error, List<TextQuickFix> quickFixes);
}
