package de.christiankorn.giveortake.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import de.christiankorn.giveortake.R;
import de.christiankorn.giveortake.core.UncertaintyScale;

/**
 * Displays a single-thumb logarithmic dial for choosing a multiplicative uncertainty factor.
 *
 * <p>This first implementation deliberately draws but does not yet respond to touch. The dial's
 * factor is independent of the user's best guess; another component can combine both values to
 * derive the interval {@code [guess / factor, guess * factor]}.</p>
 */
public class UncertaintyDial extends View {
    private static final float DEFAULT_MINIMUM_FACTOR = 1.2f;
    private static final float DEFAULT_MAXIMUM_FACTOR = 100.0f;
    private static final float PREVIEW_FACTOR = 4.0f;
    private static final float[] REFERENCE_FACTORS = {
            1.2f, 2.0f, 4.0f, 10.0f, 20.0f, 50.0f, 100.0f
    };

    private float minimumFactor;
    private float maximumFactor;
    private double factor;
    private float thumbRadius;
    private float trackThickness;
    private float tickLength;
    private float labelSpacing;
    private float defaultWidth;
    private float labelAscent;
    private float labelDescent;
    private float widestLabelWidth;

    private Paint trackPaint;
    private Paint thumbPaint;
    private Paint labelPaint;
    private float[] tickFactors;
    private String[] tickLabels;

    /**
     * Creates a dial from application code.
     *
     * <p>Android does not use this one-argument constructor for ordinary XML inflation. It is
     * used when code creates the view directly with {@code new UncertaintyDial(context)}.</p>
     *
     * @param context the context used to resolve resources and theme values
     */
    public UncertaintyDial(Context context) {
        this(context, null);
    }

    /**
     * Creates a dial while Android inflates it from an XML layout.
     *
     * <p>{@link android.view.LayoutInflater} calls this two-argument form for a custom view and
     * supplies the XML attributes. It delegates to the style-aware constructor so all setup has
     * one implementation.</p>
     *
     * @param context the context used to resolve resources and theme values
     * @param attributes the attributes written on the XML element, or {@code null}
     */
    public UncertaintyDial(Context context, @Nullable AttributeSet attributes) {
        this(context, attributes, 0);
    }

    /**
     * Creates a dial with XML attributes and a default style attribute from the current theme.
     *
     * <p>This constructor is useful to a style-aware subclass or to code that explicitly supplies
     * {@code defaultStyleAttribute}. Standard custom-view XML inflation enters through the
     * two-argument constructor above, which then calls this one with no default style attribute.</p>
     *
     * @param context the context used to resolve resources and theme values
     * @param attributes the attributes written on the XML element, or {@code null}
     * @param defaultStyleAttribute a theme attribute containing default style values, or {@code 0}
     */
    public UncertaintyDial(
            Context context,
            @Nullable AttributeSet attributes,
            int defaultStyleAttribute
    ) {
        super(context, attributes, defaultStyleAttribute);
        initialise(attributes, defaultStyleAttribute);
    }

    private void initialise(@Nullable AttributeSet attributes, int defaultStyleAttribute) {
        int defaultTrackColor = getContext().getColor(R.color.uncertainty_dial_track);
        int defaultThumbColor = getContext().getColor(R.color.uncertainty_dial_thumb);

        TypedArray styledAttributes = getContext().obtainStyledAttributes(
                attributes,
                R.styleable.UncertaintyDial,
                defaultStyleAttribute,
                0
        );
        int trackColor;
        int thumbColor;
        float labelTextSize;
        try {
            minimumFactor = styledAttributes.getFloat(
                    R.styleable.UncertaintyDial_minimumFactor,
                    DEFAULT_MINIMUM_FACTOR
            );
            maximumFactor = styledAttributes.getFloat(
                    R.styleable.UncertaintyDial_maximumFactor,
                    DEFAULT_MAXIMUM_FACTOR
            );
            trackColor = styledAttributes.getColor(
                    R.styleable.UncertaintyDial_trackColor,
                    defaultTrackColor
            );
            thumbColor = styledAttributes.getColor(
                    R.styleable.UncertaintyDial_thumbColor,
                    defaultThumbColor
            );
            thumbRadius = styledAttributes.getDimension(
                    R.styleable.UncertaintyDial_thumbRadius,
                    getResources().getDimension(R.dimen.uncertainty_dial_thumb_radius)
            );
            trackThickness = styledAttributes.getDimension(
                    R.styleable.UncertaintyDial_trackThickness,
                    getResources().getDimension(R.dimen.uncertainty_dial_track_thickness)
            );
            labelTextSize = styledAttributes.getDimension(
                    R.styleable.UncertaintyDial_tickLabelTextSize,
                    getResources().getDimension(R.dimen.uncertainty_dial_tick_label_text_size)
            );
        } finally {
            // TypedArray instances come from a shared pool. Recycling in finally returns this one
            // even if future attribute-reading code throws an exception.
            styledAttributes.recycle();
        }

        validateConfiguration(labelTextSize);
        tickLength = getResources().getDimension(R.dimen.uncertainty_dial_tick_length);
        labelSpacing = getResources().getDimension(R.dimen.uncertainty_dial_label_spacing);
        defaultWidth = getResources().getDimension(R.dimen.uncertainty_dial_default_width);
        initialisePaints(trackColor, thumbColor, labelTextSize);
        initialiseTicks();

        // A representative value makes the thumb visible away from an endpoint in Layout Editor.
        // Runtime starts at the configured minimum until application code supplies a saved value.
        factor = isInEditMode()
                ? Math.max(minimumFactor, Math.min(PREVIEW_FACTOR, maximumFactor))
                : minimumFactor;
    }

    private void validateConfiguration(float labelTextSize) {
        if (!Float.isFinite(minimumFactor)
                || !Float.isFinite(maximumFactor)
                || minimumFactor <= 0.0f
                || maximumFactor <= minimumFactor) {
            throw new IllegalArgumentException(
                    "minimumFactor and maximumFactor must define an increasing positive range"
            );
        }
        if (!Float.isFinite(thumbRadius)
                || !Float.isFinite(trackThickness)
                || !Float.isFinite(labelTextSize)
                || thumbRadius <= 0.0f
                || trackThickness <= 0.0f
                || labelTextSize <= 0.0f) {
            throw new IllegalArgumentException("dial dimensions must be finite and positive");
        }
    }

    private void initialisePaints(int trackColor, int thumbColor, float labelTextSize) {
        // Paint objects cache rendering configuration. Creating them here avoids allocating and
        // garbage-collecting objects during onDraw(), which may run once per animation frame.
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(trackColor);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeWidth(trackThickness);

        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setColor(thumbColor);
        thumbPaint.setStyle(Paint.Style.FILL);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(getContext().getColor(R.color.uncertainty_dial_label));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(labelTextSize);

        Paint.FontMetrics fontMetrics = labelPaint.getFontMetrics();
        labelAscent = fontMetrics.ascent;
        labelDescent = fontMetrics.descent;
    }

    private void initialiseTicks() {
        int referenceTickCount = 0;
        for (float referenceFactor : REFERENCE_FACTORS) {
            if (referenceFactor > minimumFactor && referenceFactor < maximumFactor) {
                referenceTickCount++;
            }
        }

        tickFactors = new float[referenceTickCount + 2];
        tickLabels = new String[referenceTickCount + 2];
        DecimalFormat labelFormat = new DecimalFormat(
                "0.##",
                DecimalFormatSymbols.getInstance()
        );

        int tickIndex = 0;
        tickFactors[tickIndex++] = minimumFactor;
        for (float referenceFactor : REFERENCE_FACTORS) {
            if (referenceFactor > minimumFactor && referenceFactor < maximumFactor) {
                tickFactors[tickIndex++] = referenceFactor;
            }
        }
        tickFactors[tickIndex] = maximumFactor;

        widestLabelWidth = 0.0f;
        for (int index = 0; index < tickFactors.length; index++) {
            tickLabels[index] = "×" + labelFormat.format(tickFactors[index]);
            widestLabelWidth = Math.max(
                    widestLabelWidth,
                    labelPaint.measureText(tickLabels[index])
            );
        }
    }

    /**
     * Measures the dial while respecting both its preferred content size and its parent's rules.
     *
     * <p>{@link MeasureSpec#EXACTLY} means the parent has fixed that dimension, so the view must
     * use the supplied size even when it differs from the preferred size. {@link
     * MeasureSpec#AT_MOST} supplies an upper bound, normally for {@code wrap_content}; the view
     * chooses its preferred size unless that would exceed the bound. {@link
     * MeasureSpec#UNSPECIFIED} imposes no bound, so the view reports its full preferred size.</p>
     *
     * @param widthMeasureSpec the horizontal mode and size supplied by the parent
     * @param heightMeasureSpec the vertical mode and size supplied by the parent
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float horizontalInset = Math.max(thumbRadius, widestLabelWidth / 2.0f);
        int desiredWidth = Math.max(
                getSuggestedMinimumWidth(),
                (int) Math.ceil(
                        getPaddingLeft()
                                + Math.max(defaultWidth, horizontalInset * 2.0f)
                                + getPaddingRight()
                )
        );

        float markerExtent = Math.max(thumbRadius, tickLength / 2.0f);
        float labelHeight = labelDescent - labelAscent;
        int desiredHeight = Math.max(
                getSuggestedMinimumHeight(),
                (int) Math.ceil(
                        getPaddingTop()
                                + markerExtent * 2.0f
                                + labelSpacing
                                + labelHeight
                                + getPaddingBottom()
                )
        );

        setMeasuredDimension(
                resolveMeasuredDimension(desiredWidth, widthMeasureSpec),
                resolveMeasuredDimension(desiredHeight, heightMeasureSpec)
        );
    }

    private static int resolveMeasuredDimension(int desiredSize, int measureSpec) {
        int mode = MeasureSpec.getMode(measureSpec);
        int suppliedSize = MeasureSpec.getSize(measureSpec);
        if (mode == MeasureSpec.EXACTLY) {
            return suppliedSize;
        }
        if (mode == MeasureSpec.AT_MOST) {
            return Math.min(desiredSize, suppliedSize);
        }
        return desiredSize;
    }

    /**
     * Draws the track, reference tick marks and labels, and the current factor's thumb.
     *
     * @param canvas the canvas supplied by Android for this drawing pass
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float horizontalInset = Math.max(thumbRadius, widestLabelWidth / 2.0f);
        float trackStartX = getPaddingLeft() + horizontalInset;
        float trackEndX = Math.max(
                trackStartX,
                getWidth() - getPaddingRight() - horizontalInset
        );

        float markerExtent = Math.max(thumbRadius, tickLength / 2.0f);
        float contentHeight = markerExtent * 2.0f
                + labelSpacing
                + labelDescent
                - labelAscent;
        float availableHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        float contentTop = getPaddingTop() + Math.max(0.0f, (availableHeight - contentHeight) / 2.0f);
        float trackY = contentTop + markerExtent;
        float labelBaseline = trackY + markerExtent + labelSpacing - labelAscent;

        canvas.drawLine(trackStartX, trackY, trackEndX, trackY, trackPaint);
        for (int index = 0; index < tickFactors.length; index++) {
            float tickX = positionToX(
                    UncertaintyScale.factorToPositionFraction(
                            tickFactors[index],
                            minimumFactor,
                            maximumFactor
                    ),
                    trackStartX,
                    trackEndX
            );
            canvas.drawLine(
                    tickX,
                    trackY - tickLength / 2.0f,
                    tickX,
                    trackY + tickLength / 2.0f,
                    trackPaint
            );
            canvas.drawText(tickLabels[index], tickX, labelBaseline, labelPaint);
        }

        float thumbX = positionToX(
                UncertaintyScale.factorToPositionFraction(
                        factor,
                        minimumFactor,
                        maximumFactor
                ),
                trackStartX,
                trackEndX
        );
        canvas.drawCircle(thumbX, trackY, thumbRadius, thumbPaint);
    }

    private float positionToX(double positionFraction, float trackStartX, float trackEndX) {
        double visualFraction = getLayoutDirection() == LAYOUT_DIRECTION_RTL
                ? 1.0 - positionFraction
                : positionFraction;
        return (float) (trackStartX + visualFraction * (trackEndX - trackStartX));
    }

    /**
     * Returns the uncertainty factor represented by the thumb.
     *
     * @return the current factor within the configured inclusive range
     */
    public double getFactor() {
        return factor;
    }

    /**
     * Moves the thumb to a factor within the configured range and schedules a redraw.
     *
     * @param factor the new finite factor, from the configured minimum through maximum
     * @throws IllegalArgumentException if the factor is non-finite or outside the configured range
     */
    public void setFactor(double factor) {
        if (!Double.isFinite(factor) || factor < minimumFactor || factor > maximumFactor) {
            throw new IllegalArgumentException("factor must be finite and within the factor range");
        }
        if (Double.compare(this.factor, factor) != 0) {
            this.factor = factor;
            invalidate();
        }
    }
}
