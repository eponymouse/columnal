package records.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

/**
 * Created by neil on 18/10/2016.
 */
public class Main extends Application
{
    @Override
    public void start(Stage primaryStage) throws Exception
    {
        View v = new View();
        primaryStage.setScene(new Scene(new ScrollPane(v)));
        primaryStage.show();
    }
}
