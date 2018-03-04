package test.gui;

import annotation.units.GridAreaRowIndex;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import log.Log;
import org.antlr.v4.runtime.atn.SemanticContext.OR;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.CellPosition;
import records.data.Table.MessageWhenEmpty;
import records.gui.grid.CellSelection;
import records.gui.grid.GridArea;
import records.gui.grid.VirtualGrid;
import records.gui.stable.SmoothScroller;
import styled.StyledString;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Pair;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(JUnitQuickcheck.class)
public class TestVirtualGridScroll extends ApplicationTest
{
    private final double ORIGINAL_SCROLL = 5001.0;
    @SuppressWarnings("nullness")
    private VirtualGrid virtualGrid;

    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    @Override
    public void start(Stage stage) throws Exception
    {
        virtualGrid = new VirtualGrid((c, p) -> {});
        stage.setScene(new Scene(new BorderPane(virtualGrid.getNode())));
        stage.setWidth(800);
        stage.setHeight(600);
        stage.show();
        
        // Add a huge table which will be roughly 10,000 pixels square:
        // That's about 400 rows, and 100 columns
        virtualGrid.addGridAreas(ImmutableList.of(new GridArea(new MessageWhenEmpty(StyledString.s("")))
        {
            @Override
            protected @OnThread(Tag.FXPlatform) void updateKnownRows(@GridAreaRowIndex int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions)
            {
            }

            @Override
            protected CellPosition recalculateBottomRightIncl()
            {
                return new CellPosition(CellPosition.row(400), CellPosition.col(100));
            }

            @Override
            public @Nullable CellSelection getSelectionForSingleCell(CellPosition cellPosition)
            {
                return null;
            }
        }));
        
        virtualGrid._test_setColumnWidth(48, 37.0);
        virtualGrid._test_setColumnWidth(50, 106.0);
        virtualGrid._test_setColumnWidth(51, 52.0);
        virtualGrid._test_setColumnWidth(53, 1.0);
        
        // Scroll to the middle to begin with so we don't get clamped:
        virtualGrid.getScrollGroup().requestScrollBy(-ORIGINAL_SCROLL, -ORIGINAL_SCROLL);
        TestUtil.sleep(300);
    }

    @OnThread(Tag.Any)
    public static class ScrollAmounts
    {
        final double[] amounts;

        public ScrollAmounts(double[] amounts)
        {
            this.amounts = amounts;
        }
    }
    
    @OnThread(Tag.Any)
    public static class GenScrollAmounts extends Generator<ScrollAmounts>
    {
        public GenScrollAmounts()
        {
            super(ScrollAmounts.class);
        }

        @Override
        public ScrollAmounts generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
        {
            int length = 6;
            double[] amounts = new double[length]; 
            for (int i = 0; i < length; i++)
            {
                amounts[i] = sourceOfRandomness.nextInt(10) == 0 ? 0.0 : sourceOfRandomness.nextDouble(-600.0, 600.0);
            }
            return new ScrollAmounts(amounts);
        }
    }
    
    @SuppressWarnings("deprecation")
    @Property(trials = 5)
    public void scrollXYBy(@When(seed=1L) @From(GenScrollAmounts.class) ScrollAmounts scrollX, @When(seed=1L) @From(GenScrollAmounts.class) ScrollAmounts scrollY)
    {
        // Check window has sized properly:
        assertThat(TestUtil.fx(() -> virtualGrid.getNode().getHeight()), Matchers.greaterThanOrEqualTo(500.0));
        assertThat(TestUtil.fx(() -> virtualGrid.getNode().getWidth()), Matchers.greaterThanOrEqualTo(700.0));

        checkOffsetsNegative();
      
        // We check with and without delay to try with/without smooth scrolling and jumping:
        for (int delay : new int[]{0, (int) (SmoothScroller.SCROLL_TIME_NANOS / 1_000_000L)})
        {
            assertThat(TestUtil.fx(() -> virtualGrid._test_getScrollXPos()), Matchers.closeTo(ORIGINAL_SCROLL, 0.1));
            // Test that scroll and scroll back works:
            for (double amount : scrollX.amounts)
            {
                TestUtil.fx_(() -> virtualGrid.getScrollGroup().requestScrollBy(amount, 0.0));
                TestUtil.sleep(delay);
                checkOffsetsNegative();
                TestUtil.fx_(() -> virtualGrid.getScrollGroup().requestScrollBy(-amount, 0.0));
                assertThat(TestUtil.fx(() -> virtualGrid._test_getScrollXPos()), Matchers.closeTo(ORIGINAL_SCROLL, 0.1));
                TestUtil.sleep(delay);
                checkOffsetsNegative();
            }

            assertThat(TestUtil.fx(() -> virtualGrid._test_getScrollYPos()), Matchers.closeTo(ORIGINAL_SCROLL, 0.1));
            // Test that scroll and scroll back works:
            for (double amount : scrollY.amounts)
            {
                TestUtil.fx_(() -> virtualGrid.getScrollGroup().requestScrollBy(amount, 0.0));
                TestUtil.sleep(delay);
                checkOffsetsNegative();
                TestUtil.fx_(() -> virtualGrid.getScrollGroup().requestScrollBy(-amount, 0.0));
                assertThat(TestUtil.fx(() -> virtualGrid._test_getScrollYPos()), Matchers.closeTo(ORIGINAL_SCROLL, 0.1));
                TestUtil.sleep(delay);
                checkOffsetsNegative();
            }
        }
        
        

    }

    @SuppressWarnings("deprecation")
    private void checkOffsetsNegative()
    {
        double[][] offsetsAndLimits = virtualGrid._test_getOffsetsAndLimits();
        for (int i = 0; i < offsetsAndLimits.length; i++)
        {
            assertThat("Offset index " + i, offsetsAndLimits[i][1], Matchers.greaterThanOrEqualTo(offsetsAndLimits[i][0]));
            assertThat("Offset index " + i, offsetsAndLimits[i][1], Matchers.lessThanOrEqualTo(0.0));
        }
    }

}
