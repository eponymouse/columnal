package records.gui;

import javafx.application.Application;
import javafx.stage.Stage;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 18/10/2016.
 */
public class Main extends Application
{
    @Override
    @OnThread(value = Tag.FXPlatform,ignoreParent = true)
    public void start(final Stage primaryStage) throws Exception
    {
        InitialWindow.show(primaryStage);
    }


    // TODO pass -XX:AutoBoxCacheMax= parameter on execution
    public static void main(String[] args)
    {
        Application.launch(Main.class);
    }
}
