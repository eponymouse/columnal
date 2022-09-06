package xyz.columnal.utility.gui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

/**
 * Created by neil on 16/04/2017.
 */
public class HBoxRow extends HBox
{
    public HBoxRow(Node... contents)
    {
        super(contents);
        getStyleClass().add("hbox-row");
    }
}
