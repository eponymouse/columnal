package utility.gui;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A CSS theme, implemented by providing a way to fetch a stylesheet URL for a given file name (e.g. by selecting from a directory specific to that theme).
 */
public interface Theme
{
    @OnThread(Tag.FXPlatform)
    public String getStylesheetURL(String stylesheetFileName) throws Throwable;
    
    @OnThread(Tag.FXPlatform)
    public String getName();
}
