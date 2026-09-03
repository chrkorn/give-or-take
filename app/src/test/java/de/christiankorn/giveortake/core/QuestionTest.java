package de.christiankorn.giveortake.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

/**
 * Tests construction, validation, and identity semantics of {@link Question}.
 */
public class QuestionTest {
    @Test
    public void build_withValidValues_exposesAllFields() {
        Question question = validQuestionBuilder().build();

        assertEquals("river-thames-length", question.getId());
        assertEquals("How long is the River Thames?", question.getPrompt());
        assertEquals(346.0, question.getTrueValue(), 0.0);
        assertEquals("km", question.getUnit());
        assertEquals("Geography", question.getCategory());
        assertEquals("https://example.com/river-thames", question.getSourceUrl());
        assertEquals("Example source", question.getSourceLabel());
        assertEquals(2, question.getDifficulty());
    }

    @Test
    public void build_withNullId_throwsUsefulException() {
        assertInvalid(validQuestionBuilder().id(null), "id must not be null or blank");
    }

    @Test
    public void build_withBlankId_throwsUsefulException() {
        assertInvalid(validQuestionBuilder().id(" \t"), "id must not be null or blank");
    }

    @Test
    public void build_withNullPrompt_throwsUsefulException() {
        assertInvalid(validQuestionBuilder().prompt(null), "prompt must not be null or blank");
    }

    @Test
    public void build_withBlankPrompt_throwsUsefulException() {
        assertInvalid(validQuestionBuilder().prompt(" \n"), "prompt must not be null or blank");
    }

    @Test
    public void build_withNullUnit_throwsUsefulException() {
        assertInvalid(validQuestionBuilder().unit(null), "unit must not be null or blank");
    }

    @Test
    public void build_withBlankUnit_throwsUsefulException() {
        assertInvalid(validQuestionBuilder().unit("   "), "unit must not be null or blank");
    }

    @Test
    public void build_withNonFiniteTrueValue_throwsUsefulException() {
        double[] invalidValues = {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalidValue : invalidValues) {
            assertInvalid(
                    validQuestionBuilder().trueValue(invalidValue),
                    "trueValue must be finite"
            );
        }
    }

    @Test
    public void build_withZeroTrueValue_throwsUsefulException() {
        assertInvalid(
                validQuestionBuilder().trueValue(0.0),
                "trueValue must be greater than zero"
        );
    }

    @Test
    public void build_withNegativeTrueValue_throwsUsefulException() {
        assertInvalid(
                validQuestionBuilder().trueValue(-1.0),
                "trueValue must be greater than zero"
        );
    }

    @Test
    public void build_withDifficultyBelowRange_throwsUsefulException() {
        assertInvalid(
                validQuestionBuilder().difficulty(0),
                "difficulty must be between 1 and 5"
        );
    }

    @Test
    public void build_withDifficultyAboveRange_throwsUsefulException() {
        assertInvalid(
                validQuestionBuilder().difficulty(6),
                "difficulty must be between 1 and 5"
        );
    }

    @Test
    public void equals_withSameId_ignoresDescriptiveFields() {
        Question original = validQuestionBuilder().build();
        Question corrected = Question.builder()
                .id("river-thames-length")
                .prompt("What is the length of the River Thames?")
                .trueValue(346.1)
                .unit("kilometres")
                .category("Rivers")
                .sourceUrl("https://example.org/thames")
                .sourceLabel("Different source")
                .difficulty(3)
                .build();

        assertEquals(original, corrected);
        assertEquals(original.hashCode(), corrected.hashCode());
    }

    @Test
    public void equals_withDifferentId_treatsQuestionsAsDifferent() {
        Question first = validQuestionBuilder().build();
        Question second = validQuestionBuilder().id("river-nile-length").build();

        assertNotEquals(first, second);
    }

    private static Question.Builder validQuestionBuilder() {
        return Question.builder()
                .id("river-thames-length")
                .prompt("How long is the River Thames?")
                .trueValue(346.0)
                .unit("km")
                .category("Geography")
                .sourceUrl("https://example.com/river-thames")
                .sourceLabel("Example source")
                .difficulty(2);
    }

    private static void assertInvalid(Question.Builder builder, String expectedMessage) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                builder::build
        );
        assertEquals(expectedMessage, exception.getMessage());
    }
}
