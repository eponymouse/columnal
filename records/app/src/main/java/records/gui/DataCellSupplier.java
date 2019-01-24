package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.effect.GaussianBlur;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.CharacterHit;
import org.fxmisc.richtext.model.NavigationActions.SelectionPolicy;
import records.data.CellPosition;
import records.data.Column;
import records.gui.DataCellSupplier.CellStyle;
import records.gui.DataCellSupplier.VersionedSTF;
import records.gui.flex.EditorKit;
import records.gui.flex.EditorKitInterface;
import records.gui.flex.EditorKitSimpleLabel;
import records.gui.flex.FlexibleTextField;
import records.gui.grid.VirtualGridSupplierIndividual;
import records.gui.grid.VirtualGridSupplierIndividual.GridCellInfo;
import records.gui.kit.Document;
import records.gui.kit.DocumentTextField;
import records.gui.kit.ReadOnlyDocument;
import records.gui.stable.ColumnDetails;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

public class DataCellSupplier extends VirtualGridSupplierIndividual<VersionedSTF, CellStyle, GridCellInfo<VersionedSTF, CellStyle>>
{
    private static ArrayBlockingQueue<VersionedSTF> newItems = new ArrayBlockingQueue<>(500);
    
    static {
        for (int i = 0; i < 3; i++)
        {
            Thread t = new Thread("STF creator" + i)
            {
                @Override
                @OnThread(value = Tag.FX, ignoreParent = true)
                public void run()
                {
                    while (true)
                    {
                        try
                        {
                            newItems.put(new VersionedSTF());
                        }
                        catch (InterruptedException e)
                        {
                            // So what?  Go round again.
                        }
                    }
                }
            };
            t.setDaemon(true);
            t.start();
        }
    }
    
    public DataCellSupplier()
    {
        super(ViewOrder.STANDARD_CELLS, Arrays.asList(CellStyle.values()));
    }
    
    @Override
    protected VersionedSTF makeNewItem()
    {
        VersionedSTF stf = null;
        while (stf == null)
        {
            try
            {
                stf = newItems.take();
            }
            catch (InterruptedException e)
            {
                // Just go again
            }
        } 
        stf.getStyleClass().add("table-data-cell");
        return stf;
    }

    @Override
    protected ItemState getItemState(VersionedSTF stf, Point2D screenPos)
    {
        if (stf.isFocused())
            return ItemState.EDITING;
        else
            return ItemState.NOT_CLICKABLE;
    }

    @Override
    protected void keyboardActivate(CellPosition cellPosition)
    {
        startEditing(null, cellPosition);
    }

    @Override
    protected void startEditing(@Nullable Point2D screenPosition, CellPosition cellPosition)
    {
        @Nullable VersionedSTF stf = getItemAt(cellPosition);
        if (stf != null && stf.isEditable())
        {
            if (screenPosition != null)
            {
                Point2D localPos = stf.screenToLocal(screenPosition);
                stf.moveTo(localPos);
            }
            else
            {
                stf.home();
            }
            stf.requestFocus();
        }
    }
    
    public static enum CellStyle
    {
        TABLE_DRAG_SOURCE
        {
            @Override
            public void applyStyle(Node item, boolean on)
            {
                item.setEffect(on ? new GaussianBlur() : null);
            }
        },
        HOVERING_EXPAND_DOWN
        {
            @Override
            public void applyStyle(Node item, boolean on)
            {
                FXUtility.setPseudoclass(item, "hovering-expand-down", on);
            }
        },
        HOVERING_EXPAND_RIGHT
        {
            @Override
            public void applyStyle(Node item, boolean on)
            {
                FXUtility.setPseudoclass(item, "hovering-expand-right", on);
            }
        };

        @OnThread(Tag.FX)
        public abstract void applyStyle(Node item, boolean on);
    }

    @Override
    protected @OnThread(Tag.FX) void adjustStyle(VersionedSTF item, CellStyle style, boolean on)
    {
        style.applyStyle(item, on);
    }

    @Override
    protected void hideItem(VersionedSTF spareCell)
    {
        super.hideItem(spareCell);
        // Clear EditorKit to avoid keeping it around while spare:
        spareCell.blank(new ReadOnlyDocument(TranslationUtility.getString("data.loading")));
    }

    @OnThread(Tag.FXPlatform)
    public @Nullable VersionedSTF _test_getCellAt(CellPosition position)
    {
        return getItemAt(position);
    }


    // A simple subclass of STF that holds a version param.  A version is a weak reference
    // to a list of column details
    public static class VersionedSTF extends DocumentTextField
    {
        @OnThread(Tag.FXPlatform)
        private @Nullable WeakReference<ImmutableList<ColumnDetails>> currentVersion = null;

        @OnThread(Tag.FXPlatform)
        public boolean isUsingColumns(ImmutableList<ColumnDetails> columns)
        {
            // Very important here we use reference equality not .equals()
            // It's not about the content of the columns, it's about using the reference to the
            // immutable list as a simple way of doing a version check
            return currentVersion != null && currentVersion.get() == columns;
        }

        @OnThread(Tag.FXPlatform)
        public void blank(Document editorKit)
        {
            super.setDocument(editorKit);
            currentVersion = null;
        }

        @OnThread(Tag.FXPlatform)
        public void setContent(Document editorKit, ImmutableList<ColumnDetails> columns)
        {
            super.setDocument(editorKit);
            currentVersion = new WeakReference<>(columns);
        }
    }
    
    public static void startPreload()
    {
        // All done in static initialiser, nothing else to do here.
    }
}
