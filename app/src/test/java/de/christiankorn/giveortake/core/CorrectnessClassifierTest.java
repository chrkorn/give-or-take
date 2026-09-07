package de.christiankorn.giveortake.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Tests correctness-band classification and its inclusive boundaries from ADR 0006.
 */
public class CorrectnessClassifierTest {
    private final CorrectnessClassifier classifier = new CorrectnessClassifier();

    /** Verifies an error comfortably inside the correct band. */
    @Test
    public void classify_errorWellInsideCorrectBand_returnsCorrect() {
        assertEquals(Correctness.CORRECT, classifier.classify(0.1));
    }

    /** Verifies an error comfortably inside the close band. */
    @Test
    public void classify_errorWellInsideCloseBand_returnsClose() {
        assertEquals(Correctness.CLOSE, classifier.classify(0.3));
    }

    /** Verifies an error comfortably beyond the close threshold. */
    @Test
    public void classify_errorWellInsideWrongBand_returnsWrong() {
        assertEquals(Correctness.WRONG, classifier.classify(1.0));
    }

    /** Verifies that the correct-band upper boundary is inclusive. */
    @Test
    public void classify_errorExactlyAtCorrectThreshold_includesBoundaryInCorrectBand() {
        assertEquals(
                Correctness.CORRECT,
                classifier.classify(CorrectnessClassifier.DEFAULT_CORRECT_THRESHOLD)
        );
    }

    /** Verifies that the close-band upper boundary is inclusive. */
    @Test
    public void classify_errorExactlyAtCloseThreshold_includesBoundaryInCloseBand() {
        assertEquals(
                Correctness.CLOSE,
                classifier.classify(CorrectnessClassifier.DEFAULT_CLOSE_THRESHOLD)
        );
    }

    /** Verifies that an exact estimate belongs to the best band. */
    @Test
    public void classify_errorOfExactlyZero_returnsCorrect() {
        assertEquals(Correctness.CORRECT, classifier.classify(0.0));
    }

    /** Verifies that even the largest finite error remains classifiable. */
    @Test
    public void classify_veryLargeFiniteError_returnsWrong() {
        assertEquals(Correctness.WRONG, classifier.classify(Double.MAX_VALUE));
    }

    /** Verifies that injected thresholds replace the defaults without changing band semantics. */
    @Test
    public void classify_withInjectedThresholds_usesConfiguredInclusiveBoundaries() {
        CorrectnessClassifier configuredClassifier = new CorrectnessClassifier(1.0, 2.0);

        assertEquals(Correctness.CORRECT, configuredClassifier.classify(1.0));
        assertEquals(Correctness.CLOSE, configuredClassifier.classify(1.5));
        assertEquals(Correctness.CLOSE, configuredClassifier.classify(2.0));
        assertEquals(Correctness.WRONG, configuredClassifier.classify(2.1));
    }

    /** Verifies that malformed errors cannot silently enter a correctness band. */
    @Test
    public void classify_nonFiniteOrNegativeError_throwsUsefulExceptions() {
        assertEquals(
                "rawError must be finite and non-negative",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> classifier.classify(-0.1)
                ).getMessage()
        );
        assertEquals(
                "rawError must be finite and non-negative",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> classifier.classify(Double.NaN)
                ).getMessage()
        );
        assertEquals(
                "rawError must be finite and non-negative",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> classifier.classify(Double.POSITIVE_INFINITY)
                ).getMessage()
        );
    }
}
