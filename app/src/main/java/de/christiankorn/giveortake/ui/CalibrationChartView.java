package de.christiankorn.giveortake.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.christiankorn.giveortake.R;

/**
 * Draws observed confidence-interval coverage against the app's 90 percent target.
 *
 * <p>A {@link Canvas} uses pixels whose origin is the view's top-left corner. This view converts
 * each coverage value from the mathematical range {@code 0..1} into that pixel coordinate system:
 * zero is placed at the plot's bottom and one at its top. Points are spaced evenly from left to
 * right because each one represents the next observation in session order rather than an elapsed
 * duration.</p>
 *
 * <p>The chart is a single accessibility item. TalkBack receives a textual trend summary instead
 * of attempting to focus visual lines and markers that have no useful individual controls.</p>
 */
public final class CalibrationChartView extends View {
    /** The nominal coverage promised by confidence intervals in this app. */
    public static final double NOMINAL_COVERAGE = 0.90;

    private static final double STABLE_TREND_TOLERANCE = 0.02;
    private static final float[] GRID_VALUES = {0.0f, 0.25f, 0.50f, 0.75f, 1.0f};
    private static final String[] GRID_LABELS = {"0%", "25%", "50%", "75%", "100%"};

    private final Paint gridPaint;
    private final Paint axisPaint;
    private final Paint axisLabelPaint;
    private final Paint legendPaint;
    private final Paint dataPaint;
    private final Paint markerPaint;
    private final Paint referencePaint;
    private final Paint referenceLabelPaint;
    private final Paint emptyPaint;
    private final Path dataPath;
    private final Path referencePath;

    private final float density;
    private final float defaultWidth;
    private final float defaultHeight;
    private final float markerRadius;
    private final float smallGap;
    private final float mediumGap;
    private final float legendSwatchWidth;

    private List<DataPoint> dataPoints = Collections.emptyList();
    private float plotLeft;
    private float plotTop;
    private float plotRight;
    private float plotBottom;
    private float legendBaseline;
    private float xTickBaseline;
    private float xAxisLabelBaseline;
    private String firstAxisLabel = "";
    private String middleAxisLabel = "";
    private String lastAxisLabel = "";

    /**
     * Creates a chart from application code.
     *
     * @param context context used to resolve dimensions, strings, and theme colours
     */
    public CalibrationChartView(Context context) {
        this(context, null);
    }

    /**
     * Creates a chart while Android inflates it from XML.
     *
     * @param context context used to resolve dimensions, strings, and theme colours
     * @param attributes attributes supplied on the XML element, or {@code null}
     */
    public CalibrationChartView(Context context, @Nullable AttributeSet attributes) {
        this(context, attributes, 0);
    }

    /**
     * Creates a chart with XML attributes and a default style attribute.
     *
     * @param context context used to resolve dimensions, strings, and theme colours
     * @param attributes attributes supplied on the XML element, or {@code null}
     * @param defaultStyleAttribute theme attribute containing default style values, or {@code 0}
     */
    public CalibrationChartView(
            Context context,
            @Nullable AttributeSet attributes,
            int defaultStyleAttribute
    ) {
        super(context, attributes, defaultStyleAttribute);
        density = getResources().getDisplayMetrics().density;
        defaultWidth = getResources().getDimension(
                R.dimen.calibration_chart_default_width
        );
        defaultHeight = getResources().getDimension(
                R.dimen.calibration_chart_default_height
        );
        markerRadius = getResources().getDimension(
                R.dimen.calibration_chart_marker_radius
        );
        smallGap = getResources().getDimension(
                R.dimen.calibration_chart_small_gap
        );
        mediumGap = getResources().getDimension(
                R.dimen.calibration_chart_medium_gap
        );
        legendSwatchWidth = getResources().getDimension(
                R.dimen.calibration_chart_legend_swatch_width
        );

        int primary = resolveThemeColour(
                com.google.android.material.R.attr.colorPrimary,
                Color.rgb(47, 99, 75)
        );
        int tertiary = resolveThemeColour(
                com.google.android.material.R.attr.colorTertiary,
                Color.rgb(125, 82, 96)
        );
        int onSurface = resolveThemeColour(
                com.google.android.material.R.attr.colorOnSurface,
                Color.rgb(28, 28, 24)
        );
        int outline = resolveThemeColour(
                com.google.android.material.R.attr.colorOutline,
                Color.rgb(116, 121, 111)
        );
        int onSurfaceVariant = resolveThemeColour(
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                Color.rgb(91, 97, 88)
        );

        float labelTextSize = getResources().getDimension(
                R.dimen.calibration_chart_label_text_size
        );
        float legendTextSize = getResources().getDimension(
                R.dimen.calibration_chart_legend_text_size
        );
        float lineWidth = getResources().getDimension(
                R.dimen.calibration_chart_line_width
        );
        float referenceWidth = getResources().getDimension(
                R.dimen.calibration_chart_reference_width
        );

        // Paint and Path retain drawing configuration. Allocating them once avoids adding garbage
        // collection work to onDraw(), which Android may call repeatedly while the screen moves.
        gridPaint = strokePaint(withAlpha(outline, 0.32f), density);
        axisPaint = strokePaint(onSurfaceVariant, density);
        axisLabelPaint = textPaint(onSurfaceVariant, labelTextSize, Paint.Align.RIGHT);
        legendPaint = textPaint(onSurface, legendTextSize, Paint.Align.LEFT);
        dataPaint = strokePaint(primary, lineWidth);
        markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        markerPaint.setColor(primary);
        markerPaint.setStyle(Paint.Style.FILL);
        referencePaint = strokePaint(tertiary, referenceWidth);
        referencePaint.setPathEffect(new DashPathEffect(
                new float[]{6.0f * density, 4.0f * density},
                0.0f
        ));
        referenceLabelPaint = textPaint(tertiary, legendTextSize, Paint.Align.RIGHT);
        referenceLabelPaint.setFakeBoldText(true);
        emptyPaint = textPaint(onSurfaceVariant, legendTextSize, Paint.Align.CENTER);
        dataPath = new Path();
        referencePath = new Path();

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateContentDescription();
    }

    /**
     * Replaces the chart's observations and redraws it.
     *
     * <p>Points must be supplied in chronological session order. The view makes a defensive copy,
     * so later changes to the caller's list cannot silently change what is drawn. At least two
     * points are needed for a trend line; fewer points produce the explicit empty state.</p>
     *
     * @param points labelled observations in chronological session order
     * @throws NullPointerException if the list or one of its points is {@code null}
     */
    public void setDataPoints(List<DataPoint> points) {
        if (points == null) {
            throw new NullPointerException("points must not be null");
        }
        ArrayList<DataPoint> copy = new ArrayList<>(points.size());
        for (DataPoint point : points) {
            if (point == null) {
                throw new NullPointerException("points must not contain null");
            }
            copy.add(point);
        }
        dataPoints = Collections.unmodifiableList(copy);
        updateAxisLabels();
        updateContentDescription();
        invalidate();
    }

    /**
     * Chooses the XML-requested size when constrained and otherwise uses a readable default size.
     *
     * @param widthMeasureSpec horizontal constraint supplied by the parent
     * @param heightMeasureSpec vertical constraint supplied by the parent
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.max(
                getSuggestedMinimumWidth(),
                getPaddingLeft() + Math.round(defaultWidth) + getPaddingRight()
        );
        int desiredHeight = Math.max(
                getSuggestedMinimumHeight(),
                getPaddingTop() + Math.round(defaultHeight) + getPaddingBottom()
        );
        setMeasuredDimension(
                resolveSizeAndState(desiredWidth, widthMeasureSpec, 0),
                resolveSizeAndState(desiredHeight, heightMeasureSpec, 0)
        );
    }

    /**
     * Recalculates the plot rectangle when Android assigns this view a new pixel size.
     *
     * @param width new width in pixels
     * @param height new height in pixels
     * @param oldWidth previous width in pixels
     * @param oldHeight previous height in pixels
     */
    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        Paint.FontMetrics labelMetrics = axisLabelPaint.getFontMetrics();
        Paint.FontMetrics legendMetrics = legendPaint.getFontMetrics();
        float largestYLabel = axisLabelPaint.measureText("100%");
        float labelHeight = labelMetrics.descent - labelMetrics.ascent;

        legendBaseline = getPaddingTop() - legendMetrics.ascent;
        plotLeft = getPaddingLeft() + labelHeight + mediumGap + largestYLabel + smallGap;
        plotTop = legendBaseline + legendMetrics.descent + mediumGap;
        plotRight = width - getPaddingRight() - markerRadius;
        plotBottom = height - getPaddingBottom() - (2.0f * labelHeight) - mediumGap;
        xTickBaseline = plotBottom + smallGap - labelMetrics.ascent;
        xAxisLabelBaseline = xTickBaseline + labelMetrics.descent + smallGap
                - labelMetrics.ascent;
        updateAxisLabels();
    }

    /**
     * Draws the grid, target, axes, and observations into the rectangle left after padding.
     *
     * @param canvas destination supplied by Android or by a bitmap export operation
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (dataPoints.size() < 2 || plotRight <= plotLeft || plotBottom <= plotTop) {
            canvas.drawText(
                    getResources().getString(
                            R.string.calibration_chart_empty
                    ),
                    getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) / 2.0f,
                    centredTextBaseline(emptyPaint, getPaddingTop(), getHeight() - getPaddingBottom()),
                    emptyPaint
            );
            return;
        }

        drawLegend(canvas);
        drawGridAndAxes(canvas);
        drawData(canvas);
        drawReferenceLine(canvas);
    }

    private void drawLegend(Canvas canvas) {
        float swatchStart = plotLeft;
        float swatchEnd = swatchStart + legendSwatchWidth;
        float swatchY = legendBaseline + legendPaint.ascent() / 2.0f;
        canvas.drawLine(swatchStart, swatchY, swatchEnd, swatchY, dataPaint);
        canvas.drawCircle((swatchStart + swatchEnd) / 2.0f, swatchY, markerRadius, markerPaint);
        canvas.drawText(
                getResources().getString(
                        R.string.calibration_chart_observed_legend
                ),
                swatchEnd + smallGap,
                legendBaseline,
                legendPaint
        );

        float referenceStart = plotLeft + (plotRight - plotLeft) * 0.57f;
        float referenceEnd = referenceStart + legendSwatchWidth;
        drawDashedLine(canvas, referenceStart, swatchY, referenceEnd);
        canvas.drawText(
                getResources().getString(
                        R.string.calibration_chart_target_legend
                ),
                referenceEnd + smallGap,
                legendBaseline,
                legendPaint
        );
    }

    private void drawGridAndAxes(Canvas canvas) {
        for (int index = 0; index < GRID_VALUES.length; index++) {
            float y = coverageToY(GRID_VALUES[index]);
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint);
            canvas.drawText(
                    GRID_LABELS[index],
                    plotLeft - smallGap,
                    y - (axisLabelPaint.ascent() + axisLabelPaint.descent()) / 2.0f,
                    axisLabelPaint
            );
        }
        canvas.drawLine(plotLeft, plotTop, plotLeft, plotBottom, axisPaint);
        canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, axisPaint);

        float verticalAxisCentre = (plotTop + plotBottom) / 2.0f;
        float verticalAxisLabelX = getPaddingLeft() - axisLabelPaint.ascent();
        axisLabelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.save();
        canvas.rotate(-90.0f, verticalAxisLabelX, verticalAxisCentre);
        canvas.drawText(
                getResources().getString(
                        R.string.calibration_chart_y_axis
                ),
                verticalAxisLabelX,
                verticalAxisCentre
                        - (axisLabelPaint.ascent() + axisLabelPaint.descent()) / 2.0f,
                axisLabelPaint
        );
        canvas.restore();
        axisLabelPaint.setTextAlign(Paint.Align.RIGHT);

        axisLabelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(firstAxisLabel, plotLeft, xTickBaseline, axisLabelPaint);
        axisLabelPaint.setTextAlign(Paint.Align.CENTER);
        if (!middleAxisLabel.isEmpty()) {
            canvas.drawText(
                    middleAxisLabel,
                    (plotLeft + plotRight) / 2.0f,
                    xTickBaseline,
                    axisLabelPaint
            );
        }
        axisLabelPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(lastAxisLabel, plotRight, xTickBaseline, axisLabelPaint);
        axisLabelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(
                getResources().getString(
                        R.string.calibration_chart_x_axis
                ),
                (plotLeft + plotRight) / 2.0f,
                xAxisLabelBaseline,
                axisLabelPaint
        );
        axisLabelPaint.setTextAlign(Paint.Align.RIGHT);
    }

    private void drawReferenceLine(Canvas canvas) {
        float y = coverageToY((float) NOMINAL_COVERAGE);
        String label = getResources().getString(
                R.string.calibration_chart_target_label
        );
        float labelWidth = referenceLabelPaint.measureText(label);
        float lineEnd = Math.max(plotLeft, plotRight - labelWidth - smallGap);
        drawDashedLine(canvas, plotLeft, y, lineEnd);
        canvas.drawText(label, plotRight, y - smallGap, referenceLabelPaint);
    }

    private void drawDashedLine(Canvas canvas, float startX, float y, float endX) {
        referencePath.reset();
        referencePath.moveTo(startX, y);
        referencePath.lineTo(endX, y);
        canvas.drawPath(referencePath, referencePaint);
    }

    private void drawData(Canvas canvas) {
        dataPath.reset();
        float step = (plotRight - plotLeft) / (dataPoints.size() - 1.0f);
        for (int index = 0; index < dataPoints.size(); index++) {
            float x = plotLeft + index * step;
            float y = coverageToY((float) dataPoints.get(index).getCoverage());
            if (index == 0) {
                dataPath.moveTo(x, y);
            } else {
                dataPath.lineTo(x, y);
            }
        }
        canvas.drawPath(dataPath, dataPaint);
        for (int index = 0; index < dataPoints.size(); index++) {
            float x = plotLeft + index * step;
            float y = coverageToY((float) dataPoints.get(index).getCoverage());
            canvas.drawCircle(x, y, markerRadius, markerPaint);
        }
    }

    private float coverageToY(float coverage) {
        return plotBottom - coverage * (plotBottom - plotTop);
    }

    private void updateAxisLabels() {
        if (dataPoints.size() < 2 || plotRight <= plotLeft) {
            firstAxisLabel = "";
            middleAxisLabel = "";
            lastAxisLabel = "";
            return;
        }
        float maximumWidth = (plotRight - plotLeft) / 3.2f;
        firstAxisLabel = ellipsise(dataPoints.get(0).getLabel(), maximumWidth);
        lastAxisLabel = ellipsise(
                dataPoints.get(dataPoints.size() - 1).getLabel(),
                maximumWidth
        );
        middleAxisLabel = dataPoints.size() > 2
                ? ellipsise(dataPoints.get(dataPoints.size() / 2).getLabel(), maximumWidth)
                : "";
    }

    private String ellipsise(String text, float maximumWidth) {
        if (axisLabelPaint.measureText(text) <= maximumWidth) {
            return text;
        }
        String ellipsis = "…";
        float availableWidth = maximumWidth - axisLabelPaint.measureText(ellipsis);
        int end = axisLabelPaint.breakText(text, true, Math.max(0.0f, availableWidth), null);
        return text.substring(0, end) + ellipsis;
    }

    private void updateContentDescription() {
        if (dataPoints.size() < 2) {
            setContentDescription(getResources().getString(
                    R.string.calibration_chart_accessibility_empty
            ));
            return;
        }

        DataPoint first = dataPoints.get(0);
        DataPoint latest = dataPoints.get(dataPoints.size() - 1);
        double change = latest.getCoverage() - first.getCoverage();
        int trendResource;
        if (change > STABLE_TREND_TOLERANCE) {
            trendResource = R.string.calibration_chart_trend_rising;
        } else if (change < -STABLE_TREND_TOLERANCE) {
            trendResource = R.string.calibration_chart_trend_falling;
        } else {
            trendResource = R.string.calibration_chart_trend_stable;
        }

        int targetResource;
        if (latest.getCoverage() > NOMINAL_COVERAGE) {
            targetResource = R.string.calibration_chart_above_target;
        } else if (latest.getCoverage() < NOMINAL_COVERAGE) {
            targetResource = R.string.calibration_chart_below_target;
        } else {
            targetResource = R.string.calibration_chart_at_target;
        }

        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        percentFormat.setMaximumFractionDigits(0);
        setContentDescription(getResources().getString(
                R.string.calibration_chart_accessibility_summary,
                dataPoints.size(),
                first.getLabel(),
                percentFormat.format(first.getCoverage()),
                latest.getLabel(),
                percentFormat.format(latest.getCoverage()),
                getResources().getString(trendResource),
                getResources().getString(targetResource)
        ));
    }

    private int resolveThemeColour(int attribute, int fallback) {
        TypedValue value = new TypedValue();
        if (!getContext().getTheme().resolveAttribute(attribute, value, true)) {
            return fallback;
        }
        if (value.resourceId != 0) {
            return getContext().getColor(value.resourceId);
        }
        return value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT
                ? value.data
                : fallback;
    }

    private static Paint strokePaint(int colour, float width) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(colour);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        return paint;
    }

    private static Paint textPaint(int colour, float size, Paint.Align alignment) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(colour);
        paint.setTextSize(size);
        paint.setTextAlign(alignment);
        return paint;
    }

    private static int withAlpha(int colour, float proportion) {
        return Color.argb(
                Math.round(Color.alpha(colour) * proportion),
                Color.red(colour),
                Color.green(colour),
                Color.blue(colour)
        );
    }

    private static float centredTextBaseline(Paint paint, float top, float bottom) {
        return (top + bottom - paint.ascent() - paint.descent()) / 2.0f;
    }

    /** Represents one labelled coverage observation on the chart's session axis. */
    public static final class DataPoint {
        private final String label;
        private final double coverage;

        /**
         * Creates an immutable chart point.
         *
         * <p>The label may be a session index such as {@code "Session 12"} or a date already
         * formatted for the user's locale. Formatting stays outside the view so the caller decides
         * which session identity is meaningful.</p>
         *
         * @param label non-empty text displayed on the horizontal axis when selected as a tick
         * @param coverage observed proportion from zero through one, inclusive
         * @throws IllegalArgumentException if the label is blank or coverage is outside 0..1
         */
        public DataPoint(String label, double coverage) {
            if (label == null || label.trim().isEmpty()) {
                throw new IllegalArgumentException("label must not be blank");
            }
            if (!Double.isFinite(coverage) || coverage < 0.0 || coverage > 1.0) {
                throw new IllegalArgumentException("coverage must be finite and within 0..1");
            }
            this.label = label;
            this.coverage = coverage;
        }

        /** Returns the session or date text associated with this observation. */
        public String getLabel() {
            return label;
        }

        /** Returns the observed coverage as a proportion from zero through one. */
        public double getCoverage() {
            return coverage;
        }
    }
}
