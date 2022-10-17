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

package test.gui.expressionEditor;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import org.junit.Test;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.ColumnUtility;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.ImmediateDataSource;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import test.DummyManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationSupplier;
import xyz.columnal.utility.gui.FXUtility;

import static org.junit.Assert.assertNotNull;

@OnThread(Tag.Simulation)
public class TestTypeEditorCompletion extends BaseTestEditorCompletion
{
    @SuppressWarnings("nullness")
    private MainWindowActions mainWindowActions;

    private void loadTypeExpression(String typeExpressionSrc, TaggedTypeDefinition... taggedTypes) throws Exception
    {
        TableManager toLoad = new DummyManager();
        for (TaggedTypeDefinition taggedTypeDefinition : taggedTypes)
        {
            toLoad.getTypeManager().registerTaggedType(taggedTypeDefinition.getTaggedTypeName().getRaw(), taggedTypeDefinition.getTypeArguments(), taggedTypeDefinition.getTags());
        }
        toLoad.record(
                new ImmediateDataSource(toLoad,
                        new InitialLoadDetails(new TableId("IDS"), null, new CellPosition(CellPosition.row(1), CellPosition.col(1)), null),
                        new EditableRecordSet(
                                ImmutableList.of(rs -> ColumnUtility.makeImmediateColumn(DataType.NUMBER, new ColumnId("My Number"), DataTypeUtility.value(0)).apply(rs)),
                                (SimulationSupplier<Integer>)() -> 0)));
        
        mainWindowActions = TAppUtil.openDataAsTable(windowToUse, toLoad).get();
        sleep(1000);
        // Start creating column 
        correctTargetWindow();
        Node expandRight = TFXUtil.fx(() -> lookup(".expand-arrow").match(n -> FXUtility.hasPseudoclass(n, "expand-right")).<Node>query());
        assertNotNull(expandRight);
        // Won't happen, assertion will fail:
        if (expandRight == null) return;
        clickOn(expandRight);
        write("Col");
        push(KeyCode.TAB);
    }
    
    @Test
    public void testCore() throws Exception
    {
        loadTypeExpression("");
        checkCompletions(
            c("Number", 0, 0),
            c("Number{}", 0, 0),
            c("Text", 0, 0),
            c("Date", 0, 0),
            c("DateTime", 0, 0),
            c("DateYM", 0, 0)
        );
        write("Dat");

        checkCompletions(
                c("Number", 0, 0),
                c("Number{}", 0, 0),
                c("Text", 0, 0),
                c("Date", 0, 3),
                c("DateTime", 0, 3),
                c("DateYM", 0, 3)
        );
    }

    @Test
    public void testBuiltInTagged() throws Exception
    {
        loadTypeExpression("",
            new TaggedTypeDefinition(new TypeId("Opsicle"), ImmutableList.of(), ImmutableList.of(new TagType<>("Single", null))),
            new TaggedTypeDefinition(new TypeId("Either"), ImmutableList.<Pair<TypeVariableKind, @ExpressionIdentifier String>>of(new Pair<>(TypeVariableKind.TYPE, "a"), new Pair<>(TypeVariableKind.TYPE, "b")), ImmutableList.of(new TagType<>("Left", null))));
        // Don't want to offer Void or Type as they are considered internal
        checkCompletions(
                c("Optional()", 0, 0),
                c("Opsicle", 0, 0),
                c("Either()()", 0, 0),
                c("Void"),
                c("Type")
        );
        write("Opst");

        checkCompletions(
                c("Optional()", 0, 2),
                c("Opsicle", 0, 3),
                c("Either()()", 0, 0),
                c("Void"),
                c("Type")
        );
    }
}
