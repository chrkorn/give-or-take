package de.christiankorn.giveortake.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;

import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import de.christiankorn.giveortake.R;
import de.christiankorn.giveortake.core.UncertaintyScale;

/**
 * Displays a single-thumb logarithmic dial for choosing a multiplicative uncertainty factor.
 *
 * <p>The dial's factor is independent of the user's best guess; another component combines both
 * values to derive the interval {@code [guess / factor, guess * factor]}. Pointer input remains
 * continuous, while keyboard and accessibility actions move between labelled reference factors.</p>
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
    private float touchHitRadius;
    private float horizontalTouchSlop;
    private float labelAscent;
    private float labelDescent;
    private float widestLabelWidth;

    private Paint trackPaint;
    private Paint thumbPaint;
    private Paint labelPaint;
    private float[] tickFactors;
    private String[] tickLabels;
    private DecimalFormat factorFormat;
    private CharSequence derivedRangeText;
    @Nullable
    private OnFactorChangeListener onFactorChangeListener;
    private boolean dragging;

    /**
     * Receives factor changes so the containing screen can update its derived range immediately.
     */
    public interface OnFactorChangeListener {

        /**
         * Called after the dial's factor changes.
         *
         * @param dial the dial whose value changed
         * @param factor the new multiplicative uncertainty factor
         * @param fromUser {@code true} for touch, keyboard, or accessibility input; {@code false}
         *         when application code called {@link #setFactor(double)}
         */
        void onFactorChanged(UncertaintyDial dial, double factor, boolean fromUser);
    }

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
        float minimumTouchTarget = getResources().getDimension(
                R.dimen.uncertainty_dial_minimum_touch_target
        );
        float systemTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        touchHitRadius = Math.max(minimumTouchTarget / 2.0f, thumbRadius + systemTouchSlop);
        horizontalTouchSlop = touchHitRadius;
        initialisePaints(trackColor, thumbColor, labelTextSize);
        initialiseTicks();

        // A representative value makes the thumb visible away from an endpoint in Layout Editor.
        // Runtime starts at the configured minimum until application code supplies a saved value.
        factor = isInEditMode()
                ? Math.max(minimumFactor, Math.min(PREVIEW_FACTOR, maximumFactor))
                : minimumFactor;
        derivedRangeText = getResources().getString(
                R.string.uncertainty_dial_range_unavailable
        );
        setFocusable(true);
        setClickable(true);
        updateContentDescription();
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
        factorFormat = new DecimalFormat(
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
            tickLabels[index] = "×" + factorFormat.format(tickFactors[index]);
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

        float trackStartX = getTrackStartX();
        float trackEndX = getTrackEndX();
        float trackY = getTrackY();
        float markerExtent = getMarkerExtent();
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

    private float getHorizontalInset() {
        return Math.max(thumbRadius, widestLabelWidth / 2.0f);
    }

    private float getTrackStartX() {
        return getPaddingLeft() + getHorizontalInset();
    }

    private float getTrackEndX() {
        return Math.max(
                getTrackStartX(),
                getWidth() - getPaddingRight() - getHorizontalInset()
        );
    }

    private float getMarkerExtent() {
        return Math.max(thumbRadius, tickLength / 2.0f);
    }

    private float getTrackY() {
        float markerExtent = getMarkerExtent();
        float contentHeight = markerExtent * 2.0f
                + labelSpacing
                + labelDescent
                - labelAscent;
        float availableHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        float contentTop = getPaddingTop()
                + Math.max(0.0f, (availableHeight - contentHeight) / 2.0f);
        return contentTop + markerExtent;
    }

    private float positionToX(double positionFraction, float trackStartX, float trackEndX) {
        double visualFraction = getLayoutDirection() == LAYOUT_DIRECTION_RTL
                ? 1.0 - positionFraction
                : positionFraction;
        return (float) (trackStartX + visualFraction * (trackEndX - trackStartX));
    }

    private boolean isNearTrack(float x, float y) {
        float trackStartX = getTrackStartX();
        float trackEndX = getTrackEndX();
        return trackEndX > trackStartX
                && x >= trackStartX - horizontalTouchSlop
                && x <= trackEndX + horizontalTouchSlop
                && Math.abs(y - getTrackY()) <= touchHitRadius;
    }

    private void updateFactorFromX(float x) {
        float trackStartX = getTrackStartX();
        float trackWidth = getTrackEndX() - trackStartX;
        if (trackWidth <= 0.0f) {
            return;
        }

        double visualPosition = (x - trackStartX) / trackWidth;
        double logicalPosition = getLayoutDirection() == LAYOUT_DIRECTION_RTL
                ? 1.0 - visualPosition
                : visualPosition;
        setFactorInternal(
                UncertaintyScale.positionFractionToFactor(
                        logicalPosition,
                        minimumFactor,
                        maximumFactor
                ),
                true
        );
    }

    /**
     * Handles track taps and thumb drags with a finger-sized hit area around the thin track.
     *
     * <p>A completed touch calls {@link #performClick()} so click listeners and accessibility
     * services receive the semantic click that a raw touch sequence would otherwise hide.</p>
     *
     * @param event the current pointer event
     * @return {@code true} while this view owns a recognised dial gesture
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isNearTrack(event.getX(), event.getY())) {
                    return false;
                }
                dragging = true;
                setPressed(true);
                ViewParent parent = getParent();
                if (parent != null) {
                    // A ScrollView may otherwise intercept MOVE events after this view accepted
                    // DOWN, stealing the drag before the user can finish setting the answer.
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                updateFactorFromX(event.getX());
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!dragging) {
                    return false;
                }
                updateFactorFromX(event.getX());
                return true;

            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    return false;
                }
                updateFactorFromX(event.getX());
                // ADR 0014 keeps pointer input continuous; labelled factors are not snap points.
                finishDragging();
                performClick();
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (!dragging) {
                    return false;
                }
                finishDragging();
                return true;

            default:
                return dragging;
        }
    }

    private void finishDragging() {
        dragging = false;
        setPressed(false);
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(false);
        }
    }

    /**
     * Performs the dial's semantic click after a tap or drag completes.
     *
     * <p>Android Lint requires touch-driven custom views to delegate here because {@link
     * View#performClick()} invokes registered click listeners and emits the accessibility event
     * used by non-touch input and assistive technology.</p>
     *
     * @return {@code true}, because this clickable view handled the action
     */
    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    /**
     * Moves between labelled reference factors in response to keyboard or D-pad arrows.
     *
     * @param keyCode the pressed key
     * @param event information about the key event
     * @return {@code true} if the key is an arrow handled by this dial
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int direction;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                direction = 1;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                direction = -1;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                direction = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? -1 : 1;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                direction = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 1 : -1;
                break;
            default:
                return super.onKeyDown(keyCode, event);
        }

        stepToAdjacentReference(direction);
        return true;
    }

    private boolean stepToAdjacentReference(int direction) {
        if (direction > 0) {
            for (float tickFactor : tickFactors) {
                if (tickFactor > factor) {
                    setFactorInternal(tickFactor, true);
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
                    return true;
                }
            }
        } else {
            for (int index = tickFactors.length - 1; index >= 0; index--) {
                if (tickFactors[index] < factor) {
                    setFactorInternal(tickFactors[index], true);
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Describes this custom control as one adjustable range to accessibility services.
     *
     * @param info the node populated for this view
     */
    @SuppressWarnings("deprecation") // The non-deprecated constructor requires API 30; min SDK is 26.
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(SeekBar.class.getName());
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,
                minimumFactor,
                maximumFactor,
                (float) factor
        ));
        if (isEnabled()) {
            info.setScrollable(true);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }

    /**
     * Lets assistive technology adjust the factor through standard forward/backward actions.
     *
     * @param action the requested accessibility action
     * @param arguments optional action arguments supplied by Android
     * @return {@code true} when this dial handled the action
     */
    @Override
    public boolean performAccessibilityAction(int action, @Nullable Bundle arguments) {
        if (isEnabled()
                && action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            return stepToAdjacentReference(1);
        }
        if (isEnabled()
                && action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            return stepToAdjacentReference(-1);
        }
        return super.performAccessibilityAction(action, arguments);
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
        setFactorInternal(factor, false);
    }

    private void setFactorInternal(double factor, boolean fromUser) {
        if (Double.compare(this.factor, factor) == 0) {
            return;
        }
        this.factor = factor;
        // Only the thumb position changes, so a redraw is enough; remeasurement and layout would
        // waste work on every MOVE event.
        invalidate();
        if (onFactorChangeListener != null) {
            onFactorChangeListener.onFactorChanged(this, factor, fromUser);
        }
        updateContentDescription();
    }

    /**
     * Registers the listener that updates the screen's live derived-range preview.
     *
     * @param listener the listener to notify, or {@code null} to stop notifications
     */
    public void setOnFactorChangeListener(@Nullable OnFactorChangeListener listener) {
        onFactorChangeListener = listener;
    }

    /**
     * Supplies the same human-readable range text shown by the containing screen.
     *
     * <p>Keeping formatting in the Activity ensures sighted and accessibility users hear exactly
     * the interval that will be submitted, including its unit and display rounding.</p>
     *
     * @param derivedRangeText the formatted lower-to-upper range, or {@code null} while no valid
     *         best guess is available
     */
    public void setDerivedRangeText(@Nullable CharSequence derivedRangeText) {
        CharSequence newText = derivedRangeText == null
                ? getResources().getString(R.string.uncertainty_dial_range_unavailable)
                : derivedRangeText;
        if (!TextUtils.equals(this.derivedRangeText, newText)) {
            this.derivedRangeText = newText.toString();
            updateContentDescription();
        }
    }

    private void updateContentDescription() {
        setContentDescription(getResources().getString(
                R.string.uncertainty_dial_content_description,
                factorFormat.format(factor),
                derivedRangeText
        ));
    }
}
