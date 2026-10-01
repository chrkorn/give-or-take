package de.christiankorn.giveortake;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import de.christiankorn.giveortake.core.CalibrationTracker;
import de.christiankorn.giveortake.core.IntervalGuess;
import de.christiankorn.giveortake.core.Level;
import de.christiankorn.giveortake.core.Question;
import de.christiankorn.giveortake.data.AnswerDraft;
import de.christiankorn.giveortake.data.CalibrationStatistics;
import de.christiankorn.giveortake.data.CategoryPerformance;
import de.christiankorn.giveortake.data.QuizDatabaseHelper;
import de.christiankorn.giveortake.data.QuizHistoryDao;
import de.christiankorn.giveortake.data.QuizStatisticsDao;
import de.christiankorn.giveortake.data.StatisticsOverview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the calibration-measuring instrument against synthetic answers of known true coverage.
 *
 * <p><b>Synthetic data, not usage data.</b> Nothing here measures any person's calibration. The
 * answers come from a simulated estimator whose interval coverage is fixed by construction, and
 * they are pushed through the shipped code: {@link QuizHistoryDao} writes them to an isolated
 * Robolectric SQLite database, and {@link QuizStatisticsDao} reads them back through the real
 * {@code core} policies ({@code CalibrationTracker}, {@code IntervalScore}). The verdict comes from
 * the real {@link StatsPresentation#assessCalibration(double)}.</p>
 *
 * <p><b>Generator.</b> True values are log-uniform over 10^0 to 10^6. The estimator's best guess
 * is {@code truth x 10^(sigma Z)} with {@code Z ~ N(0, 1)} and {@code sigma = 0.3} (a typical miss
 * by a factor of two). The stated interval is {@code guess x 10^(+/- w)}, so its true coverage is
 * {@code 2 Phi(w / sigma) - 1}. Solving for {@code w} sets the coverage exactly.</p>
 *
 * <p><b>What the generator assumes.</b> The estimator is deliberately simple, and no person would
 * behave like it: its errors are unbiased and symmetric in log space, where people tend to miss in
 * one direction; they are normally distributed, where real misses have heavier tails; they have
 * the same scale for every question, where real difficulty varies; they are independent between
 * answers, where a person's answers are linked by what they know and how tired they are; and its
 * interval width never changes, which is exactly what calibration training is supposed to change.
 * None of this weakens what the tests establish, because the pipeline's hit counts, widths and
 * losses are compared with an independent recount of the same answers, however those answers were
 * produced. The assumptions do determine the particular numbers that come out, such as the loss of
 * each profile and the sample size at which the verdict settles, so those numbers describe this
 * estimator and not a user.</p>
 *
 * <p><b>Tolerances and outputs.</b> Every tolerance derives from the sample size, and the seeds are
 * fixed, so a run is reproducible and a failure means the pipeline is wrong rather than that the
 * sample was unlucky. Besides asserting, each test writes its measurements as CSV to
 * {@code app/build/calibration-evidence/} (a Gradle unit test runs with the module directory as
 * its working directory). The files exist for inspection and plotting; no assertion reads them.</p>
 *
 * <p><b>Running the test.</b> It is part of the ordinary JVM suite and needs no device or
 * emulator, so {@code ./gradlew test} includes it. On its own:
 * {@code ./gradlew test --tests '*CalibrationEvidenceTest'}. A run takes a few seconds. The class
 * exercises production code only and adds none.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class CalibrationEvidenceTest {

    private static final double SIGMA = 0.3;
    private static final int ANSWERS_PER_PROFILE = 1_000;
    private static final int ANSWERS_PER_SESSION = 10;
    /** Two-sided 99.9 % normal quantile: a seeded run fails only if the pipeline is wrong. */
    private static final double Z_999 = 3.2905;
    private static final double Z_95 = 1.959964;
    private static final double MISS_PENALTY = 2.0 / (1.0 - CalibrationTracker.NOMINAL_COVERAGE);
    private static final double EXACT = 1e-9;
    /**
     * Floor for comparisons with the analytic expectation. The erfc approximation used for Phi has
     * a fractional error below 1.2e-7; scaled by the miss penalty and the widths used here, that
     * stays well under 1e-4. Needed because the width-gaming profile almost never misses, so its
     * sampling error is effectively zero.
     */
    private static final double ANALYTIC_FLOOR = 1e-4;
    private static final String CATEGORY = "Synthetic";

    private static final Profile OVERCONFIDENT =
            Profile.forCoverage("overconfident", 0.60, 20_260_930L);
    private static final Profile CALIBRATED =
            Profile.forCoverage("calibrated", 0.90, 20_260_931L);
    private static final Profile UNDERCONFIDENT =
            Profile.forCoverage("underconfident", 0.98, 20_260_932L);
    /** The calibrated estimator with every interval widened by a further factor of ten each way. */
    private static final Profile WIDTH_GAMING = new Profile(
            "width-gaming", CALIBRATED.halfWidth + 1.0, 20_260_933L);

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(QuizDatabaseHelper.DATABASE_NAME);
    }

    @After
    public void tearDown() {
        context.deleteDatabase(QuizDatabaseHelper.DATABASE_NAME);
    }

    /** Overconfident, calibrated and underconfident profiles are recovered with the right verdict. */
    @Test
    public void recoversKnownCoverageAndVerdict() throws IOException {
        List<String> rows = new ArrayList<>();
        rows.add("profile,true_coverage,n,measured_coverage,tolerance_999,dao_hits,oracle_hits,"
                + "half_width_log10,measured_mean_log_width,verdict");
        Profile[] profiles = {OVERCONFIDENT, CALIBRATED, UNDERCONFIDENT};
        StatsPresentation.CalibrationVerdict[] expected = {
                StatsPresentation.CalibrationVerdict.OVERCONFIDENT,
                StatsPresentation.CalibrationVerdict.WELL_CALIBRATED,
                StatsPresentation.CalibrationVerdict.UNDERCONFIDENT
        };
        for (int i = 0; i < profiles.length; i++) {
            Profile profile = profiles[i];
            PipelineResult result = runThroughPipeline(profile);
            CalibrationStatistics calibration = result.overview.getCalibration();

            // The pipeline counts exactly what an independent recount of the same answers finds.
            assertEquals(ANSWERS_PER_PROFILE, calibration.getSampleSize());
            assertEquals(result.oracleHits, calibration.getHitCount());
            // Every interval has the same width by construction, so the mean is exact.
            assertEquals(2.0 * profile.halfWidth,
                    calibration.getMeanLogScaleWidth().getAsDouble(), EXACT);

            double measured = calibration.getEmpiricalCoverage().getAsDouble();
            double tolerance = Z_999 * Math.sqrt(
                    profile.coverage * (1.0 - profile.coverage) / ANSWERS_PER_PROFILE);
            assertTrue(profile.name + ": coverage " + measured + " not within " + tolerance
                            + " of " + profile.coverage,
                    Math.abs(measured - profile.coverage) <= tolerance);

            assertTrue(calibration.hasMeaningfulSampleSize());
            StatsPresentation.CalibrationVerdict verdict = StatsPresentation.assessCalibration(
                    calibration.getNominalCoverageGap().getAsDouble());
            assertEquals(profile.name, expected[i], verdict);

            rows.add(String.format(Locale.ROOT, "%s,%.2f,%d,%.4f,%.4f,%d,%d,%.6f,%.6f,%s",
                    profile.name, profile.coverage, ANSWERS_PER_PROFILE, measured, tolerance,
                    calibration.getHitCount(), result.oracleHits, profile.halfWidth,
                    calibration.getMeanLogScaleWidth().getAsDouble(), verdict));
        }
        writeCsv("recovery.csv", rows);
    }

    /**
     * Coverage alone is gameable; the interval score is not. Widening every interval pushes coverage
     * towards 1.0 while the mean interval loss gets worse, and the calibrated profile has the
     * lowest loss of all four.
     */
    @Test
    public void widthGamingRaisesCoverageButWorsensTheIntervalScore() throws IOException {
        List<String> rows = new ArrayList<>();
        rows.add("profile,true_coverage,measured_coverage,measured_mean_loss,oracle_mean_loss,"
                + "expected_mean_loss,loss_se,interval_factor_each_way");
        Profile[] profiles = {OVERCONFIDENT, CALIBRATED, UNDERCONFIDENT, WIDTH_GAMING};
        double[] loss = new double[profiles.length];
        double[] lossSe = new double[profiles.length];
        double[] coverage = new double[profiles.length];
        for (int i = 0; i < profiles.length; i++) {
            Profile profile = profiles[i];
            PipelineResult result = runThroughPipeline(profile);
            CategoryPerformance category = result.overview.getCategoryPerformance().get(0);
            assertEquals(CATEGORY, category.getCategory());
            loss[i] = category.getMeanIntervalLoss().getAsDouble();
            coverage[i] = result.overview.getCalibration().getEmpiricalCoverage().getAsDouble();
            lossSe[i] = result.oracleLossSd / Math.sqrt(ANSWERS_PER_PROFILE);

            // The pipeline's loss equals the ADR 0007 formula applied independently.
            assertEquals(profile.name, result.oracleMeanLoss, loss[i], EXACT);
            // And it matches the analytic expectation within the sampling error.
            double expected = expectedLoss(profile.halfWidth);
            assertTrue(profile.name + ": loss " + loss[i] + " vs expected " + expected,
                    Math.abs(loss[i] - expected) <= Z_999 * lossSe[i] + ANALYTIC_FLOOR);

            rows.add(String.format(Locale.ROOT, "%s,%.6f,%.4f,%.6f,%.6f,%.6f,%.6f,%.4f",
                    profile.name, profile.coverage, coverage[i], loss[i], result.oracleMeanLoss,
                    expected, lossSe[i], Math.pow(10.0, profile.halfWidth)));
        }
        writeCsv("width-gaming.csv", rows);

        int calibrated = 1;
        int gaming = 3;
        assertTrue("gaming coverage", coverage[gaming] >= 0.99);
        assertTrue("gaming beats calibrated on coverage", coverage[gaming] > coverage[calibrated]);
        double gap = loss[gaming] - loss[calibrated];
        double gapSe = Math.hypot(lossSe[gaming], lossSe[calibrated]);
        assertTrue("gaming must lose on the interval score", gap > Z_999 * gapSe);
        for (int i = 0; i < profiles.length; i++) {
            if (i == calibrated) {
                continue;
            }
            double difference = loss[i] - loss[calibrated];
            assertTrue(profiles[i].name + " should score worse than calibrated",
                    difference > Z_999 * Math.hypot(lossSe[i], lossSe[calibrated]));
        }
    }

    /**
     * Measures how many interval answers the verdict needs before it is right 95 % of the time,
     * comparing a Monte-Carlo run through the real tracker and verdict rule with the exact binomial
     * probability of the same rule.
     */
    @Test
    public void verdictStabilityAgainstSampleSize() throws IOException {
        int[] sizes = {10, 20, 30, 50, 75, 100, 150, 200, 300, 500};
        int replicates = 4_000;
        Profile[] profiles = {OVERCONFIDENT, CALIBRATED, UNDERCONFIDENT};
        StatsPresentation.CalibrationVerdict[] expected = {
                StatsPresentation.CalibrationVerdict.OVERCONFIDENT,
                StatsPresentation.CalibrationVerdict.WELL_CALIBRATED,
                StatsPresentation.CalibrationVerdict.UNDERCONFIDENT
        };
        List<String> rows = new ArrayList<>();
        rows.add("profile,n,replicates,simulated_p_correct,exact_p_correct,mc_tolerance_999");
        List<String> summary = new ArrayList<>();
        summary.add("profile,first_n_with_exact_p_correct_at_least_0.95,app_threshold");
        for (int p = 0; p < profiles.length; p++) {
            Profile profile = profiles[p];
            Random random = new Random(profile.seed + 1_000L);
            Integer stableAt = null;
            for (int n : sizes) {
                int correct = 0;
                for (int r = 0; r < replicates; r++) {
                    CalibrationTracker tracker = new CalibrationTracker();
                    for (int k = 0; k < n; k++) {
                        tracker.recordOutcome(random.nextDouble() < profile.coverage,
                                2.0 * profile.halfWidth);
                    }
                    if (StatsPresentation.assessCalibration(
                            tracker.getNominalMinusEmpiricalCoverageGap().getAsDouble())
                            == expected[p]) {
                        correct++;
                    }
                }
                double simulated = (double) correct / replicates;
                double exact = exactProbabilityOfVerdict(n, profile.coverage, expected[p]);
                double tolerance = Z_999 * Math.sqrt(exact * (1.0 - exact) / replicates)
                        + 1.0 / replicates;
                assertTrue(profile.name + " n=" + n + ": simulated " + simulated
                        + " vs exact " + exact, Math.abs(simulated - exact) <= tolerance);
                if (stableAt == null && exact >= 0.95) {
                    stableAt = n;
                }
                rows.add(String.format(Locale.ROOT, "%s,%d,%d,%.4f,%.4f,%.4f",
                        profile.name, n, replicates, simulated, exact, tolerance));
            }
            summary.add(String.format(Locale.ROOT, "%s,%s,%d", profile.name,
                    stableAt == null ? ">500" : Integer.toString(stableAt),
                    CalibrationTracker.MINIMUM_MEANINGFUL_SAMPLE_SIZE));
        }
        writeCsv("verdict-stability.csv", rows);
        writeCsv("verdict-stability-summary.csv", summary);
    }

    /** Computes, rather than quotes, how precisely n interval answers measure coverage near 0.90. */
    @Test
    public void measurementPrecisionTable() throws IOException {
        int[] sizes = {10, 30, 50, 100, 150, 250, 500, 1000};
        double p = CalibrationTracker.NOMINAL_COVERAGE;
        List<String> rows = new ArrayList<>();
        rows.add("n,ci95_half_width_pp");
        for (int n : sizes) {
            double halfWidth = Z_95 * Math.sqrt(p * (1.0 - p) / n);
            rows.add(String.format(Locale.ROOT, "%d,%.1f", n, 100.0 * halfWidth));
        }
        // The precision that ruled out measuring one person: ten range answers per session, and
        // the smallest change that two halves of a 150-answer run could tell apart.
        assertEquals(18.6, 100.0 * Z_95 * Math.sqrt(p * (1.0 - p) / 10), 0.05);
        assertEquals(4.8, 100.0 * Z_95 * Math.sqrt(p * (1.0 - p) / 150), 0.05);
        double detectable = Z_95 * Math.sqrt(2.0 * p * (1.0 - p) / 75);
        assertEquals(9.6, 100.0 * detectable, 0.05);
        rows.add(String.format(Locale.ROOT, "two-halves-of-150 min detectable,%.1f",
                100.0 * detectable));
        writeCsv("precision.csv", rows);
    }

    // ---------------------------------------------------------------------------------------------

    private PipelineResult runThroughPipeline(Profile profile) {
        context.deleteDatabase(QuizDatabaseHelper.DATABASE_NAME);
        Random random = new Random(profile.seed);
        long oracleHits = 0;
        // Welford's running mean and sum of squared deviations: stable even when every loss is
        // identical, as for the width-gaming profile, where a sum-of-squares formula rounds to a
        // tiny negative variance and its square root to NaN.
        long lossCount = 0;
        double lossMean = 0.0;
        double lossM2 = 0.0;
        long clock = 1_000_000L;
        try (QuizHistoryDao history = new QuizHistoryDao(context)) {
            int sessions = ANSWERS_PER_PROFILE / ANSWERS_PER_SESSION;
            for (int s = 0; s < sessions; s++) {
                long sessionId = history.startSession(
                        Level.CONFIDENCE_INTERVALS, ANSWERS_PER_SESSION, clock);
                List<AnswerDraft> answers = new ArrayList<>();
                for (int q = 1; q <= ANSWERS_PER_SESSION; q++) {
                    double truth = Math.pow(10.0, 6.0 * random.nextDouble());
                    double guess = truth * Math.pow(10.0, SIGMA * random.nextGaussian());
                    IntervalGuess interval = new IntervalGuess(
                            guess * Math.pow(10.0, -profile.halfWidth),
                            guess * Math.pow(10.0, profile.halfWidth));
                    if (interval.containsTruth(truth)) {
                        oracleHits++;
                    }
                    double loss = oracleIntervalLoss(truth, interval);
                    lossCount++;
                    double delta = loss - lossMean;
                    lossMean += delta / lossCount;
                    lossM2 += delta * (loss - lossMean);
                    clock += 1_000L;
                    answers.add(new AnswerDraft(q, question(profile, s, q, truth), interval, clock));
                }
                history.recordAnswers(sessionId, answers.subList(0, ANSWERS_PER_SESSION - 1));
                clock += 1_000L;
                history.recordFinalAnswerAndCompleteSession(
                        sessionId, answers.get(ANSWERS_PER_SESSION - 1), clock);
            }
        }
        StatisticsOverview overview;
        try (QuizStatisticsDao statistics = new QuizStatisticsDao(context)) {
            overview = statistics.getOverview();
        }
        double variance = lossM2 / (lossCount - 1);
        return new PipelineResult(overview, oracleHits, lossMean, Math.sqrt(variance));
    }

    /** ADR 0007's interval score in log10 space, written out independently of IntervalScore. */
    private static double oracleIntervalLoss(double truth, IntervalGuess interval) {
        double lower = Math.log10(interval.getLowerBound());
        double upper = Math.log10(interval.getUpperBound());
        double t = Math.log10(truth);
        double loss = upper - lower;
        if (t < lower) {
            loss += MISS_PENALTY * (lower - t);
        } else if (t > upper) {
            loss += MISS_PENALTY * (t - upper);
        }
        return loss;
    }

    /** Expected interval loss for half-width w when log errors are N(0, SIGMA^2). */
    private static double expectedLoss(double w) {
        double z = w / SIGMA;
        double expectedOvershoot = 2.0 * (SIGMA * phi(z) - w * (1.0 - bigPhi(z)));
        return 2.0 * w + MISS_PENALTY * expectedOvershoot;
    }

    private static double exactProbabilityOfVerdict(
            int n, double coverage, StatsPresentation.CalibrationVerdict verdict) {
        double total = 0.0;
        for (int k = 0; k <= n; k++) {
            double gap = CalibrationTracker.NOMINAL_COVERAGE - (double) k / n;
            if (StatsPresentation.assessCalibration(gap) == verdict) {
                total += binomialPmf(n, k, coverage);
            }
        }
        // Summing the pmf can overshoot 1 by rounding (1.0000000000000195 was observed), which
        // would make p(1 - p) negative and the Monte-Carlo tolerance NaN.
        return Math.min(1.0, Math.max(0.0, total));
    }

    private static double binomialPmf(int n, int k, double p) {
        double logCoefficient = 0.0;
        for (int i = 1; i <= k; i++) {
            logCoefficient += Math.log(n - k + i) - Math.log(i);
        }
        double logP = k == 0 ? 0.0 : k * Math.log(p);
        double logQ = k == n ? 0.0 : (n - k) * Math.log1p(-p);
        return Math.exp(logCoefficient + logP + logQ);
    }

    private static double phi(double z) {
        return Math.exp(-0.5 * z * z) / Math.sqrt(2.0 * Math.PI);
    }

    /** Standard normal CDF via the complementary error function (Numerical Recipes erfcc). */
    private static double bigPhi(double z) {
        return 1.0 - 0.5 * erfc(z / Math.sqrt(2.0));
    }

    private static double erfc(double x) {
        double t = 1.0 / (1.0 + 0.5 * Math.abs(x));
        double y = t * Math.exp(-x * x - 1.26551223 + t * (1.00002368 + t * (0.37409196
                + t * (0.09678418 + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398
                + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0 ? y : 2.0 - y;
    }

    private static Question question(Profile profile, int session, int index, double truth) {
        String id = profile.name + "-s" + session + "-q" + index;
        return Question.builder()
                .id(id)
                .prompt("Synthetic question " + id)
                .trueValue(truth)
                .unit("units")
                .measurementBasis("synthetic")
                .timeVarying(false)
                .category(CATEGORY)
                .build();
    }

    private static void writeCsv(String name, List<String> rows) throws IOException {
        File directory = new File("build/calibration-evidence");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create " + directory.getAbsolutePath());
        }
        try (PrintWriter out = new PrintWriter(new File(directory, name),
                StandardCharsets.UTF_8.name())) {
            out.println("# Synthetic data from CalibrationEvidenceTest. Not usage data.");
            for (String row : rows) {
                out.println(row);
            }
        }
    }

    private static final class Profile {
        final String name;
        final double halfWidth;
        final double coverage;
        final long seed;

        Profile(String name, double halfWidth, long seed) {
            this.name = name;
            this.halfWidth = halfWidth;
            this.coverage = 2.0 * bigPhi(halfWidth / SIGMA) - 1.0;
            this.seed = seed;
        }

        /** Solves 2 Phi(w / sigma) - 1 = coverage for the half-width w by bisection. */
        static Profile forCoverage(String name, double coverage, long seed) {
            double low = 0.0;
            double high = 10.0 * SIGMA;
            for (int i = 0; i < 200; i++) {
                double mid = 0.5 * (low + high);
                if (2.0 * bigPhi(mid / SIGMA) - 1.0 < coverage) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            return new Profile(name, 0.5 * (low + high), seed);
        }
    }

    private static final class PipelineResult {
        final StatisticsOverview overview;
        final long oracleHits;
        final double oracleMeanLoss;
        final double oracleLossSd;

        PipelineResult(StatisticsOverview overview, long oracleHits, double oracleMeanLoss,
                       double oracleLossSd) {
            this.overview = overview;
            this.oracleHits = oracleHits;
            this.oracleMeanLoss = oracleMeanLoss;
            this.oracleLossSd = oracleLossSd;
        }
    }
}
