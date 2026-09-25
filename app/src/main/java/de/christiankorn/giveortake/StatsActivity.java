package de.christiankorn.giveortake;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.christiankorn.giveortake.core.CalibrationTracker;
import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.data.CalibrationStatistics;
import de.christiankorn.giveortake.data.CoverageTrendPoint;
import de.christiankorn.giveortake.data.PersonalBestStatistics;
import de.christiankorn.giveortake.data.QuizStatisticsDao;
import de.christiankorn.giveortake.data.RecentSessionStatistics;
import de.christiankorn.giveortake.data.StatisticsOverview;
import de.christiankorn.giveortake.data.StoredSession;
import de.christiankorn.giveortake.ui.CalibrationChartView;

/**
 * Displays aggregate performance, confidence calibration, and past quiz sessions.
 *
 * <p>Database reads run on an Activity-owned worker. The resulting statistics are immutable and
 * are posted to the main thread only for rendering. A single {@link RecyclerView} owns both the
 * summary header and session rows, which lets the complete statistics screen scroll as one unit.</p>
 */
public final class StatsActivity extends AppCompatActivity {
    private static final int SESSION_PAGE_SIZE = Integer.MAX_VALUE;
    private static final long TEST_TIMEOUT_SECONDS = 5L;

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(runnable ->
            new Thread(runnable, "statistics-database")
    );
    private int loadGeneration;

    /** Creates the screen, installs its loading state, and begins an asynchronous history read. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_stats);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.stats_root), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.stats_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        findViewById(R.id.stats_start_session_button).setOnClickListener(view -> startActivity(
                QuizActivity.createIntent(StatsActivity.this, Level.POINT_ESTIMATES)
        ));
        findViewById(R.id.stats_retry_button).setOnClickListener(view -> loadStatistics());

        RecyclerView recyclerView = findViewById(R.id.stats_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(false);

        loadStatistics();
    }

    /** Stops accepting results from the screen's database worker when this Activity is destroyed. */
    @Override
    protected void onDestroy() {
        loadGeneration++;
        databaseExecutor.shutdownNow();
        super.onDestroy();
    }

    private void loadStatistics() {
        int generation = ++loadGeneration;
        showOnly(R.id.stats_loading_state);
        databaseExecutor.execute(() -> {
            try (QuizStatisticsDao statisticsDao = new QuizStatisticsDao(
                    getApplicationContext()
            )) {
                StatisticsOverview overview = statisticsDao.getOverview();
                List<RecentSessionStatistics> sessions = statisticsDao.getRecentSessions(
                        SESSION_PAGE_SIZE,
                        0
                );
                StatsData data = new StatsData(overview, sessions);
                runOnUiThread(() -> {
                    if (generation == loadGeneration && !isDestroyed()) {
                        render(data);
                    }
                });
            } catch (RuntimeException exception) {
                runOnUiThread(() -> {
                    if (generation == loadGeneration && !isDestroyed()) {
                        showOnly(R.id.stats_error_state);
                    }
                });
            }
        });
    }

    private void render(StatsData data) {
        if (data.overview.getEndedSessionCount() == 0) {
            NumberFormat integerFormat = NumberFormat.getIntegerInstance();
            ((TextView) findViewById(R.id.stats_empty_message)).setText(getString(
                    R.string.stats_empty_message,
                    integerFormat.format(CalibrationTracker.MINIMUM_MEANINGFUL_SAMPLE_SIZE)
            ));
            showOnly(R.id.stats_empty_state);
            return;
        }

        RecyclerView recyclerView = findViewById(R.id.stats_recycler);
        recyclerView.setAdapter(new StatisticsAdapter(data.overview, data.sessions));
        showOnly(R.id.stats_recycler);
    }

    private void showOnly(int visibleViewId) {
        int[] stateViewIds = {
                R.id.stats_loading_state,
                R.id.stats_empty_state,
                R.id.stats_error_state,
                R.id.stats_recycler
        };
        for (int viewId : stateViewIds) {
            findViewById(viewId).setVisibility(
                    viewId == visibleViewId ? View.VISIBLE : View.GONE
            );
        }
    }

    /**
     * Waits until earlier statistics reads leave the worker queue for instrumentation tests.
     *
     * <p>The method is package-private so application code cannot turn asynchronous loading into
     * a blocking call. Tests invoke it away from the main thread and then let the main looper
     * render the already-posted result.</p>
     */
    void awaitLoadForTest() {
        Future<?> marker = databaseExecutor.submit(() -> {
            // Reaching the marker proves every earlier read has completed or failed.
        });
        try {
            marker.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for statistics", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError("Statistics did not finish loading", exception);
        }
    }

    private static final class StatsData {
        private final StatisticsOverview overview;
        private final List<RecentSessionStatistics> sessions;

        private StatsData(
                StatisticsOverview overview,
                List<RecentSessionStatistics> sessions
        ) {
            this.overview = overview;
            this.sessions = new ArrayList<>(sessions);
        }
    }

    /** Adapts one summary header and many immutable session values into reusable row views. */
    private final class StatisticsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_SESSION = 1;

        private final StatisticsOverview overview;
        private final List<RecentSessionStatistics> sessions;
        private final NumberFormat integerFormat = NumberFormat.getIntegerInstance();
        private final NumberFormat decimalFormat = NumberFormat.getNumberInstance();
        private final NumberFormat percentFormat = NumberFormat.getPercentInstance();
        private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);

        private StatisticsAdapter(
                StatisticsOverview overview,
                List<RecentSessionStatistics> sessions
        ) {
            this.overview = overview;
            this.sessions = sessions;
            decimalFormat.setMinimumFractionDigits(0);
            decimalFormat.setMaximumFractionDigits(1);
            percentFormat.setMaximumFractionDigits(0);
            setHasStableIds(true);
        }

        /** Inflates either the one-off summary or a recyclable session row. */
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent,
                int viewType
        ) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderViewHolder(inflater.inflate(
                        R.layout.item_stats_header,
                        parent,
                        false
                ));
            }
            return new SessionViewHolder(inflater.inflate(
                    R.layout.item_stats_session,
                    parent,
                    false
            ));
        }

        /** Binds immutable statistics to a holder without creating another row view. */
        @Override
        public void onBindViewHolder(
                @NonNull RecyclerView.ViewHolder holder,
                int position
        ) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind(overview);
            } else {
                ((SessionViewHolder) holder).bind(sessions.get(position - 1));
            }
        }

        /** Returns the summary plus one row for every loaded ended session. */
        @Override
        public int getItemCount() {
            return sessions.size() + 1;
        }

        /** Distinguishes the summary holder from reusable session holders. */
        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_HEADER : TYPE_SESSION;
        }

        /** Gives each persisted session a stable identity across RecyclerView updates. */
        @Override
        public long getItemId(int position) {
            return position == 0 ? Long.MIN_VALUE : sessions.get(position - 1).getSessionId();
        }

        private final class HeaderViewHolder extends RecyclerView.ViewHolder {
            private final TextView sessionsValue;
            private final TextView questionsValue;
            private final TextView highScoreValue;
            private final TextView meanClosenessValue;
            private final View noRangesGroup;
            private final View coverageGroup;
            private final View insufficientGroup;
            private final View widthDivider;
            private final View widthGroup;
            private final TextView coverageSummary;
            private final TextView calibrationVerdict;
            private final TextView insufficientMessage;
            private final TextView rangesRemaining;
            private final TextView meanWidthValue;
            private final CalibrationChartView calibrationChart;

            private HeaderViewHolder(View itemView) {
                super(itemView);
                sessionsValue = itemView.findViewById(R.id.stats_sessions_value);
                questionsValue = itemView.findViewById(R.id.stats_questions_value);
                highScoreValue = itemView.findViewById(R.id.stats_high_score_value);
                meanClosenessValue = itemView.findViewById(
                        R.id.stats_mean_closeness_value
                );
                noRangesGroup = itemView.findViewById(R.id.stats_no_ranges_group);
                coverageGroup = itemView.findViewById(R.id.stats_coverage_group);
                insufficientGroup = itemView.findViewById(R.id.stats_insufficient_group);
                widthDivider = itemView.findViewById(R.id.stats_width_divider);
                widthGroup = itemView.findViewById(R.id.stats_width_group);
                coverageSummary = itemView.findViewById(R.id.stats_coverage_summary);
                calibrationVerdict = itemView.findViewById(R.id.stats_calibration_verdict);
                insufficientMessage = itemView.findViewById(R.id.stats_insufficient_message);
                rangesRemaining = itemView.findViewById(R.id.stats_ranges_remaining);
                meanWidthValue = itemView.findViewById(R.id.stats_mean_width_value);
                calibrationChart = itemView.findViewById(R.id.stats_calibration_chart);
            }

            private void bind(StatisticsOverview value) {
                sessionsValue.setText(integerFormat.format(value.getEndedSessionCount()));
                questionsValue.setText(integerFormat.format(value.getAnswerCount()));
                bindPointHeadlines(value);
                bindCalibration(value.getCalibration());
                bindCalibrationChart(value.getCoverageTrend());
            }

            private void bindCalibrationChart(List<CoverageTrendPoint> trend) {
                List<CalibrationChartView.DataPoint> points = new ArrayList<>(trend.size());
                long previousSessionId = Long.MIN_VALUE;
                for (CoverageTrendPoint point : trend) {
                    String sessionNumber = integerFormat.format(point.getEndingSessionId());
                    CalibrationChartView.DataPoint chartPoint =
                            new CalibrationChartView.DataPoint(
                                    getString(
                                            R.string.calibration_chart_session_label,
                                            sessionNumber
                                    ),
                                    point.getCoverage()
                            );
                    int lastIndex = points.size() - 1;
                    if (lastIndex >= 0 && previousSessionId == point.getEndingSessionId()) {
                        // Coverage uses overlapping ten-answer windows. Keeping the last window in
                        // each session makes the horizontal axis genuinely session-based rather
                        // than drawing many identically labelled points for a long quiz.
                        points.set(lastIndex, chartPoint);
                    } else {
                        points.add(chartPoint);
                    }
                    previousSessionId = point.getEndingSessionId();
                }
                calibrationChart.setDataPoints(points);
            }

            private void bindPointHeadlines(StatisticsOverview value) {
                OptionalDouble meanError = value.getMeanPointLogRelativeError();
                if (meanError.isPresent()) {
                    meanClosenessValue.setText(getString(
                            R.string.stats_factor,
                            decimalFormat.format(StatsPresentation.factorFromLogScale(
                                    meanError.getAsDouble()
                            ))
                    ));
                } else {
                    meanClosenessValue.setText(R.string.stats_unavailable);
                }

                PersonalBestStatistics pointBest = null;
                for (PersonalBestStatistics personalBest : value.getPersonalBests()) {
                    if (personalBest.getLevel() == Level.POINT_ESTIMATES) {
                        pointBest = personalBest;
                        break;
                    }
                }
                if (pointBest == null) {
                    highScoreValue.setText(R.string.stats_unavailable);
                } else {
                    highScoreValue.setText(getString(
                            R.string.stats_point_score,
                            decimalFormat.format(pointBest.getValue())
                    ));
                }
            }

            private void bindCalibration(CalibrationStatistics calibration) {
                long sampleSize = calibration.getSampleSize();
                boolean hasRanges = sampleSize > 0L;
                noRangesGroup.setVisibility(hasRanges ? View.GONE : View.VISIBLE);
                widthDivider.setVisibility(hasRanges ? View.VISIBLE : View.GONE);
                widthGroup.setVisibility(hasRanges ? View.VISIBLE : View.GONE);
                if (!hasRanges) {
                    coverageGroup.setVisibility(View.GONE);
                    insufficientGroup.setVisibility(View.GONE);
                    return;
                }

                meanWidthValue.setText(getString(
                        R.string.stats_factor,
                        decimalFormat.format(StatsPresentation.factorFromLogScale(
                                calibration.getMeanLogScaleWidth().getAsDouble()
                        ))
                ));
                if (calibration.hasMeaningfulSampleSize()) {
                    bindMeaningfulCalibration(calibration);
                } else {
                    bindInsufficientCalibration(sampleSize);
                }
            }

            private void bindMeaningfulCalibration(CalibrationStatistics calibration) {
                coverageGroup.setVisibility(View.VISIBLE);
                insufficientGroup.setVisibility(View.GONE);
                coverageSummary.setText(getString(
                        R.string.stats_coverage_summary,
                        percentFormat.format(calibration.getEmpiricalCoverage().getAsDouble())
                ));

                StatsPresentation.CalibrationVerdict verdict =
                        StatsPresentation.assessCalibration(
                                calibration.getNominalCoverageGap().getAsDouble()
                        );
                int verdictText;
                switch (verdict) {
                    case OVERCONFIDENT:
                        verdictText = R.string.stats_verdict_overconfident;
                        break;
                    case UNDERCONFIDENT:
                        verdictText = R.string.stats_verdict_underconfident;
                        break;
                    case WELL_CALIBRATED:
                    default:
                        verdictText = R.string.stats_verdict_well_calibrated;
                        break;
                }
                calibrationVerdict.setText(verdictText);
            }

            private void bindInsufficientCalibration(long sampleSize) {
                coverageGroup.setVisibility(View.GONE);
                insufficientGroup.setVisibility(View.VISIBLE);
                int quantity = sampleSize > Integer.MAX_VALUE
                        ? Integer.MAX_VALUE
                        : (int) sampleSize;
                insufficientMessage.setText(getResources().getQuantityString(
                        R.plurals.stats_coverage_insufficient,
                        quantity,
                        sampleSize,
                        CalibrationTracker.MINIMUM_MEANINGFUL_SAMPLE_SIZE
                ));
                long remaining = CalibrationTracker.MINIMUM_MEANINGFUL_SAMPLE_SIZE - sampleSize;
                rangesRemaining.setText(getResources().getQuantityString(
                        R.plurals.stats_ranges_remaining,
                        (int) remaining,
                        remaining
                ));
            }
        }

        private final class SessionViewHolder extends RecyclerView.ViewHolder {
            private final TextView date;
            private final TextView bands;
            private final TextView score;

            private SessionViewHolder(View itemView) {
                super(itemView);
                date = itemView.findViewById(R.id.stats_session_date);
                bands = itemView.findViewById(R.id.stats_session_bands);
                score = itemView.findViewById(R.id.stats_session_score);
            }

            private void bind(RecentSessionStatistics session) {
                date.setText(dateFormat.format(new Date(session.getEndedAtEpochMillis())));
                if (session.getLevel() == Level.POINT_ESTIMATES) {
                    bindPointSession(session);
                } else {
                    bindIntervalSession(session);
                }
                if (session.getState() == StoredSession.State.ABANDONED) {
                    bands.append(getString(R.string.stats_abandoned_suffix));
                }
            }

            private void bindPointSession(RecentSessionStatistics session) {
                OptionalDouble meanPoints = session.getMeanPoints();
                score.setText(meanPoints.isPresent()
                        ? getString(
                                R.string.stats_point_score,
                                decimalFormat.format(meanPoints.getAsDouble())
                        )
                        : getString(R.string.stats_empty_session_score));
                String correct = getResources().getQuantityString(
                        R.plurals.result_correct_count,
                        session.getCorrectCount(),
                        session.getCorrectCount()
                );
                String close = getResources().getQuantityString(
                        R.plurals.result_close_count,
                        session.getCloseCount(),
                        session.getCloseCount()
                );
                String wrong = getResources().getQuantityString(
                        R.plurals.result_wrong_count,
                        session.getWrongCount(),
                        session.getWrongCount()
                );
                bands.setText(getString(R.string.stats_band_counts, correct, close, wrong));
            }

            private void bindIntervalSession(RecentSessionStatistics session) {
                OptionalDouble meanLoss = session.getMeanRawValue();
                score.setText(meanLoss.isPresent()
                        ? getString(
                                R.string.stats_interval_loss,
                                decimalFormat.format(meanLoss.getAsDouble())
                        )
                        : getString(R.string.stats_empty_session_score));
                int contained = (int) session.getCalibrationHitCount();
                int missed = session.getAnswerCount() - contained;
                String containedText = getResources().getQuantityString(
                        R.plurals.stats_contained_count,
                        contained,
                        contained
                );
                String missedText = getResources().getQuantityString(
                        R.plurals.stats_missed_count,
                        missed,
                        missed
                );
                bands.setText(getString(
                        R.string.stats_interval_counts,
                        containedText,
                        missedText
                ));
            }
        }
    }
}
