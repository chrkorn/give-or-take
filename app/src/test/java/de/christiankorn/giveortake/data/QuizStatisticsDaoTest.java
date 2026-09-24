package de.christiankorn.giveortake.data;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import de.christiankorn.giveortake.core.IntervalGuess;
import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.PointGuess;
import de.christiankorn.giveortake.core.Question;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Exercises statistics queries and core aggregation together on the local JVM. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class QuizStatisticsDaoTest {
    private static final double PRECISE = 1.0e-12;

    private Context context;
    private QuizHistoryDao historyDao;
    private QuizStatisticsDao statisticsDao;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(QuizDatabaseHelper.DATABASE_NAME);
        historyDao = new QuizHistoryDao(context);
        statisticsDao = new QuizStatisticsDao(context);
    }

    @After
    public void tearDown() {
        statisticsDao.close();
        historyDao.close();
        context.deleteDatabase(QuizDatabaseHelper.DATABASE_NAME);
    }

    /** Verifies empty history is represented by empty values rather than zeroed metrics. */
    @Test
    public void overview_withoutHistory_returnsExplicitEmptyResults() {
        StatisticsOverview overview = statisticsDao.getOverview();

        assertEquals(0, overview.getEndedSessionCount());
        assertEquals(0, overview.getAnswerCount());
        assertFalse(overview.getMeanPointLogRelativeError().isPresent());
        assertFalse(overview.getCalibration().getEmpiricalCoverage().isPresent());
        assertFalse(overview.getCalibration().getNominalCoverageGap().isPresent());
        assertFalse(overview.getCalibration().getMeanLogScaleWidth().isPresent());
        assertTrue(overview.getCoverageTrend().isEmpty());
        assertTrue(overview.getMeanErrorTrend().isEmpty());
        assertTrue(overview.getCategoryPerformance().isEmpty());
        assertTrue(overview.getPersonalBests().isEmpty());
        assertTrue(statisticsDao.getRecentSessions(10, 0).isEmpty());
    }

    /** Verifies terminal sessions are ordered and paged in SQLite, including stable tie-breaking. */
    @Test
    public void recentSessions_returnsNewestFirstPagesAndExcludesInProgress() {
        long oldest = completePointSession(1_000L, 1_100L, "old", "A", 100.0, 100.0);
        long tiedEarlierId = completePointSession(
                2_000L, 2_100L, "tie-1", "A", 100.0, 100.0
        );
        long tiedLaterId = completePointSession(
                2_050L, 2_100L, "tie-2", "A", 100.0, 100.0
        );
        historyDao.startSession(Level.POINT_ESTIMATES, 1, 3_000L);

        List<RecentSessionStatistics> firstPage = statisticsDao.getRecentSessions(2, 0);
        List<RecentSessionStatistics> secondPage = statisticsDao.getRecentSessions(2, 2);

        assertEquals(2, firstPage.size());
        assertEquals(tiedLaterId, firstPage.get(0).getSessionId());
        assertEquals(tiedEarlierId, firstPage.get(1).getSessionId());
        assertEquals(1, secondPage.size());
        assertEquals(oldest, secondPage.get(0).getSessionId());
        assertThrows(
                UnsupportedOperationException.class,
                () -> firstPage.add(firstPage.get(0))
        );
    }

    /** Verifies pagination rejects values SQLite should never receive. */
    @Test
    public void recentSessions_withInvalidPagination_rejectsArguments() {
        assertThrows(IllegalArgumentException.class, () -> statisticsDao.getRecentSessions(0, 0));
        assertThrows(IllegalArgumentException.class, () -> statisticsDao.getRecentSessions(1, -1));
    }

    /** Verifies core policies produce coverage, error, category, and record aggregates. */
    @Test
    public void overview_withMixedHistory_calculatesAllRequestedStatistics() {
        long firstPointSession = startPointSession(1_000L, 2);
        historyDao.recordAnswer(
                firstPointSession,
                pointDraft(1, "a-exact", "A", 100.0, 100.0, 1_010L)
        );
        historyDao.recordFinalAnswerAndCompleteSession(
                firstPointSession,
                pointDraft(2, "a-factor-ten", "A", 100.0, 1_000.0, 1_020L),
                1_030L
        );
        long secondPointSession = completePointSession(
                2_000L, 2_020L, "b-factor-two", "B", 100.0, 200.0
        );

        long intervalSession = historyDao.startSession(
                Level.CONFIDENCE_INTERVALS,
                10,
                3_000L
        );
        List<AnswerDraft> firstNine = new ArrayList<>();
        for (int index = 1; index <= 9; index++) {
            firstNine.add(intervalDraft(
                    index,
                    "interval-hit-" + index,
                    "A",
                    100.0,
                    50.0,
                    200.0,
                    3_000L + index
            ));
        }
        historyDao.recordAnswers(intervalSession, firstNine);
        historyDao.recordFinalAnswerAndCompleteSession(
                intervalSession,
                intervalDraft(10, "interval-miss", "A", 100.0, 1.0, 10.0, 3_010L),
                3_020L
        );

        long abandoned = historyDao.startSession(Level.POINT_ESTIMATES, 1, 4_000L);
        historyDao.recordAnswer(
                abandoned,
                pointDraft(1, "abandoned", "B", 100.0, 100.0, 4_010L)
        );
        historyDao.abandonSession(abandoned, 4_020L);

        StatisticsOverview overview = statisticsDao.getOverview();

        assertEquals(4, overview.getEndedSessionCount());
        assertEquals(14, overview.getAnswerCount());
        assertEquals(
                (1.0 + Math.log10(2.0)) / 4.0,
                overview.getMeanPointLogRelativeError().getAsDouble(),
                PRECISE
        );
        assertEquals(10L, overview.getCalibration().getSampleSize());
        assertEquals(9L, overview.getCalibration().getHitCount());
        assertEquals(
                0.90,
                overview.getCalibration().getEmpiricalCoverage().getAsDouble(),
                PRECISE
        );
        assertEquals(0.0, overview.getCalibration().getNominalCoverageGap().getAsDouble(), PRECISE);

        assertEquals(1, overview.getCoverageTrend().size());
        CoverageTrendPoint coverage = overview.getCoverageTrend().get(0);
        assertEquals(10, coverage.getSampleSize());
        assertEquals(9, coverage.getHitCount());
        assertEquals(0.90, coverage.getCoverage(), PRECISE);
        assertEquals(intervalSession, coverage.getEndingSessionId());

        assertEquals(3, overview.getMeanErrorTrend().size());
        assertEquals(firstPointSession, overview.getMeanErrorTrend().get(0).getSessionId());
        assertEquals(0.5, overview.getMeanErrorTrend().get(0).getMeanLogRelativeError(), PRECISE);
        assertEquals(secondPointSession, overview.getMeanErrorTrend().get(1).getSessionId());
        assertEquals(Math.log10(2.0),
                overview.getMeanErrorTrend().get(1).getMeanLogRelativeError(), PRECISE);

        assertEquals(2, overview.getCategoryPerformance().size());
        CategoryPerformance categoryA = overview.getCategoryPerformance().get(0);
        assertEquals("A", categoryA.getCategory());
        assertEquals(2, categoryA.getPointSampleSize());
        assertEquals(0.5, categoryA.getMeanLogRelativeError().getAsDouble(), PRECISE);
        assertEquals(10, categoryA.getIntervalSampleSize());
        assertEquals(9, categoryA.getIntervalHitCount());
        assertEquals(0.90, categoryA.getEmpiricalCoverage().getAsDouble(), PRECISE);

        CategoryPerformance categoryB = overview.getCategoryPerformance().get(1);
        assertEquals("B", categoryB.getCategory());
        assertEquals(2, categoryB.getPointSampleSize());
        assertFalse(categoryB.getEmpiricalCoverage().isPresent());

        assertEquals(2, overview.getPersonalBests().size());
        PersonalBestStatistics pointBest = overview.getPersonalBests().get(0);
        assertEquals(Level.POINT_ESTIMATES, pointBest.getLevel());
        assertEquals(firstPointSession, pointBest.getSessionId());
        assertEquals(55.0, pointBest.getValue(), PRECISE);
        PersonalBestStatistics intervalBest = overview.getPersonalBests().get(1);
        assertEquals(Level.CONFIDENCE_INTERVALS, intervalBest.getLevel());
        assertEquals(intervalSession, intervalBest.getSessionId());
    }

    /** Verifies rolling windows advance one interval answer at a time after the first ten. */
    @Test
    public void coverageTrend_withElevenIntervals_returnsTwoOverlappingWindows() {
        long sessionId = historyDao.startSession(Level.CONFIDENCE_INTERVALS, 11, 10_000L);
        List<AnswerDraft> firstTen = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            firstTen.add(intervalDraft(
                    index,
                    "hit-" + index,
                    "A",
                    100.0,
                    50.0,
                    200.0,
                    10_000L + index
            ));
        }
        historyDao.recordAnswers(sessionId, firstTen);
        historyDao.recordFinalAnswerAndCompleteSession(
                sessionId,
                intervalDraft(11, "miss", "A", 100.0, 1.0, 10.0, 10_011L),
                10_020L
        );

        List<CoverageTrendPoint> trend = statisticsDao.getOverview().getCoverageTrend();

        assertEquals(2, trend.size());
        assertEquals(10, trend.get(0).getHitCount());
        assertEquals(9, trend.get(1).getHitCount());
    }

    /** Verifies history without a recoverable category is kept in an explicit fallback group. */
    @Test
    public void categoryPerformance_withoutSnapshot_usesLegacyCategory() {
        long sessionId = historyDao.startSession(Level.POINT_ESTIMATES, 1, 20_000L);
        Question uncategorised = Question.builder()
                .id("legacy")
                .prompt("Estimate legacy")
                .trueValue(100.0)
                .unit("units")
                .measurementBasis("test measurement")
                .timeVarying(false)
                .build();
        historyDao.recordFinalAnswerAndCompleteSession(
                sessionId,
                new AnswerDraft(1, uncategorised, new PointGuess(100.0), 20_010L),
                20_020L
        );

        List<CategoryPerformance> categories = statisticsDao.getOverview()
                .getCategoryPerformance();

        assertEquals(1, categories.size());
        assertEquals(QuizStatisticsDao.LEGACY_CATEGORY, categories.get(0).getCategory());
        assertEquals(1, categories.get(0).getPointSampleSize());
    }

    private long startPointSession(long startedAt, int questionCount) {
        return historyDao.startSession(Level.POINT_ESTIMATES, questionCount, startedAt);
    }

    private long completePointSession(
            long startedAt,
            long endedAt,
            String questionId,
            String category,
            double truth,
            double guess
    ) {
        long sessionId = startPointSession(startedAt, 1);
        historyDao.recordFinalAnswerAndCompleteSession(
                sessionId,
                pointDraft(1, questionId, category, truth, guess, endedAt),
                endedAt
        );
        return sessionId;
    }

    private static AnswerDraft pointDraft(
            int sequence,
            String id,
            String category,
            double truth,
            double guess,
            long answeredAt
    ) {
        return new AnswerDraft(
                sequence,
                question(id, category, truth),
                new PointGuess(guess),
                answeredAt
        );
    }

    private static AnswerDraft intervalDraft(
            int sequence,
            String id,
            String category,
            double truth,
            double lower,
            double upper,
            long answeredAt
    ) {
        return new AnswerDraft(
                sequence,
                question(id, category, truth),
                new IntervalGuess(lower, upper),
                answeredAt
        );
    }

    private static Question question(String id, String category, double truth) {
        return Question.builder()
                .id(id)
                .prompt("Estimate " + id)
                .trueValue(truth)
                .unit("units")
                .measurementBasis("test measurement")
                .timeVarying(false)
                .category(category)
                .build();
    }
}
