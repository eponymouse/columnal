package records.gui.lexeditor;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import records.error.InternalException;
import utility.ResourceUtility;

import java.net.URL;

public final class DocWindow extends Stage
{
    public DocWindow(String title, String docURL, Node toRightOf) throws InternalException
    {
        setTitle(title);
        setAlwaysOnTop(true);
        WebView webView = new WebView();
        URL url = ResourceUtility.getResource(docURL);
        if (url != null)
        {
            webView.getEngine().load(url.toExternalForm());
        }
        else
            throw new InternalException("Could not find resource: " + docURL);
        webView.setPrefWidth(400);
        webView.setPrefHeight(300);
        Bounds screenBounds = toRightOf.localToScreen(toRightOf.getBoundsInLocal()); 
        setX(screenBounds.getMaxX() + 10.0);
        setY(screenBounds.getMinY());
        setScene(new Scene(new BorderPane(webView)));
    }
}
