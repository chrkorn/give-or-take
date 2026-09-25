package de.christiankorn.giveortake.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.christiankorn.giveortake.R;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Exercises calibration-chart validation, accessibility, measurement, and Canvas rendering. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CalibrationChartViewTest {
    private Context themedContext;

    /** Gives every test the same application theme used by the real Activity. */
    @Before
    public void setUp() {
        themedContext = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(),
                R.style.Theme_GiveOrTake
        );
    }

    /** Verifies invalid values are rejected before undefined chart geometry reaches Canvas. */
    @Test
    public void dataPoint_withInvalidValues_rejectsArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CalibrationChartView.DataPoint("", 0.5)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CalibrationChartView.DataPoint("Session 1", -0.01)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CalibrationChartView.DataPoint("Session 1", Double.NaN)
        );
    }

    /** Verifies fewer than two observations are exposed as insufficient data, not as a trend. */
    @Test
    public void setDataPoints_withOnePoint_keepsAccessibleEmptyState() {
        CalibrationChartView view = new CalibrationChartView(themedContext);

        view.setDataPoints(Arrays.asList(
                new CalibrationChartView.DataPoint("Session 4", 0.8)
        ));

        assertEquals(
                themedContext.getString(R.string.calibration_chart_accessibility_empty),
                view.getContentDescription().toString()
        );
    }

    /** Verifies TalkBack receives direction, endpoint values, and comparison with the target. */
    @Test
    public void setDataPoints_withRisingTrend_summarisesMeaning() {
        CalibrationChartView view = new CalibrationChartView(themedContext);

        view.setDataPoints(Arrays.asList(
                new CalibrationChartView.DataPoint("Session 1", 0.5),
                new CalibrationChartView.DataPoint("Session 2", 0.7),
                new CalibrationChartView.DataPoint("Session 3", 0.8)
        ));

        String description = view.getContentDescription().toString();
        assertTrue(description.contains("Session 1 at 50%"));
        assertTrue(description.contains("Session 3 at 80%"));
        assertTrue(description.contains("trend is rising"));
        assertTrue(description.contains("below the nominal 90% target"));
    }

    /** Verifies the setter snapshots caller-owned lists instead of observing later mutations. */
    @Test
    public void setDataPoints_copiesCallerList() {
        CalibrationChartView view = new CalibrationChartView(themedContext);
        List<CalibrationChartView.DataPoint> points = new ArrayList<>(Arrays.asList(
                new CalibrationChartView.DataPoint("Session 1", 0.5),
                new CalibrationChartView.DataPoint("Session 2", 0.7)
        ));
        view.setDataPoints(points);
        String beforeMutation = view.getContentDescription().toString();

        points.add(new CalibrationChartView.DataPoint("Session 3", 0.9));

        assertEquals(beforeMutation, view.getContentDescription().toString());
    }

    /** Verifies unspecified parents receive the documented readable default dimensions. */
    @Test
    public void measure_withoutParentConstraints_usesDefaultDimensions() {
        CalibrationChartView view = new CalibrationChartView(themedContext);
        view.setPadding(10, 20, 30, 40);

        view.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        assertEquals(
                Math.round(themedContext.getResources().getDimension(
                        R.dimen.calibration_chart_default_width
                )) + 40,
                view.getMeasuredWidth()
        );
        assertEquals(
                Math.round(themedContext.getResources().getDimension(
                        R.dimen.calibration_chart_default_height
                )) + 60,
                view.getMeasuredHeight()
        );
    }

    /** Verifies a measured chart can render its lines and text into an off-screen bitmap. */
    @Test
    public void draw_withTrend_changesBitmapPixels() {
        CalibrationChartView view = new CalibrationChartView(themedContext);
        view.setDataPoints(Arrays.asList(
                new CalibrationChartView.DataPoint("Session 1", 0.6),
                new CalibrationChartView.DataPoint("Session 2", 0.75),
                new CalibrationChartView.DataPoint("Session 3", 0.85)
        ));
        int width = 640;
        int height = 464;
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        );
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));

        boolean changedPixelFound = false;
        for (int y = 0; y < height && !changedPixelFound; y++) {
            for (int x = 0; x < width; x++) {
                if (bitmap.getPixel(x, y) != 0) {
                    changedPixelFound = true;
                    break;
                }
            }
        }
        assertTrue(changedPixelFound);
    }
}
