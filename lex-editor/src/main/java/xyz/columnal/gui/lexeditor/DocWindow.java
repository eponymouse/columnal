/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui.lexeditor;

import javafx.beans.binding.ObjectExpression;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.error.InternalException;
import xyz.columnal.gui.lexeditor.completion.InsertListener;
import xyz.columnal.gui.lexeditor.completion.LexAutoCompleteWindow;
import xyz.columnal.utility.ResourceUtility;
import xyz.columnal.utility.gui.FXUtility;

import java.net.URL;

public final class DocWindow extends Stage
{
    public DocWindow(String title, String docURL, @Nullable Node toRightOf, InsertListener insertListener, ObjectExpression<Scene> lastAsLongAsSceneIsNotNull) throws InternalException
    {
        setTitle(title);
        setAlwaysOnTop(true);
        FXUtility.setIcon(this);
        WebView webView = new WebView();
        FXUtility.addChangeListenerPlatform(webView.getEngine().documentProperty(), webViewDoc -> LexAutoCompleteWindow.enableInsertLinks(webViewDoc, insertListener, () -> null));
        URL url = ResourceUtility.getResource(docURL);
        if (url != null)
        {
            webView.getEngine().load(url.toExternalForm());
        }
        else
            throw new InternalException("Could not find resource: " + docURL);
        webView.setPrefWidth(400);
        webView.setPrefHeight(300);
        if (toRightOf != null)
        {
            Bounds screenBounds = toRightOf.localToScreen(toRightOf.getBoundsInLocal());
            setX(screenBounds.getMaxX() + 10.0);
            setY(screenBounds.getMinY());
        }
        setScene(new Scene(new BorderPane(webView)));
        FXUtility.addChangeListenerPlatform(lastAsLongAsSceneIsNotNull, scene -> {
            if (scene == null)
                close();
        });
    }
}
