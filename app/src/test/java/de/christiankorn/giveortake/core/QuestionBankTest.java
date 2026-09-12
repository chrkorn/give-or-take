package de.christiankorn.giveortake.core;

import org.junit.Test;

import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests strict JSON loading and validation by {@link QuestionBank} on the local JVM.
 */
public class QuestionBankTest {
    @Test
    public void fromJson_withSeveralValidQuestions_loadsEveryFieldInSourceOrder()
            throws Exception {
        String questions = validQuestion("river-thames", 346.0)
                + ","
                + validQuestion("mount-everest", 8848.86);

        QuestionBank bank = QuestionBank.fromJson(new StringReader(validBank(questions)));

        List<Question> loaded = bank.getQuestions();
        assertEquals(2, loaded.size());
        assertEquals("river-thames", loaded.get(0).getId());
        assertEquals("How large is river-thames?", loaded.get(0).getPrompt());
        assertEquals(346.0, loaded.get(0).getTrueValue(), 0.0);
        assertEquals("units", loaded.get(0).getUnit());
        assertEquals("example measurement basis", loaded.get(0).getMeasurementBasis());
        assertFalse(loaded.get(0).isTimeVarying());
        assertNull(loaded.get(0).getAsOf());
        assertEquals("Example category", loaded.get(0).getCategory());
        assertEquals("https://example.org/river-thames", loaded.get(0).getSourceUrl());
        assertEquals("Example source", loaded.get(0).getSourceLabel());
        assertEquals("mount-everest", loaded.get(1).getId());
    }

    @Test
    public void fromJson_withDatedTimeVaryingQuestion_loadsAsOf() throws Exception {
        QuestionBank bank = QuestionBank.fromJson(
                validBank(validPopulationQuestion("germany-population", "2024-12-31"))
        );

        Question question = bank.getQuestions().get(0);
        assertEquals("resident population", question.getMeasurementBasis());
        assertTrue(question.isTimeVarying());
        assertEquals(LocalDate.of(2024, 12, 31), question.getAsOf());
    }

    @Test
    public void fromJson_withEmptyQuestionArray_returnsEmptyBank() throws Exception {
        QuestionBank bank = QuestionBank.fromJson(validBank(""));

        assertTrue(bank.getQuestions().isEmpty());
    }

    @Test
    public void fromJson_withMalformedJson_rejectsWholeFile() {
        QuestionBankFormatException exception = assertThrows(
                QuestionBankFormatException.class,
                () -> QuestionBank.fromJson("{\"version\": 1, \"metadata\":")
        );

        assertEquals("Invalid question bank at $: malformed JSON", exception.getMessage());
    }

    @Test
    public void fromJson_withMissingRequiredField_rejectsWholeFile() {
        String questionWithoutPrompt = "{"
                + "\"id\":\"missing-prompt\","
                + "\"trueValue\":10.0,"
                + "\"unit\":\"units\","
                + "\"measurementBasis\":\"example measurement basis\","
                + "\"category\":\"Example category\","
                + "\"sourceUrl\":\"https://example.org/missing-prompt\","
                + "\"sourceLabel\":\"Example source\""
                + "}";

        assertInvalid(
                validBank(questionWithoutPrompt),
                "Invalid question bank at $.questions[0].prompt: missing required field"
        );
    }

    @Test
    public void fromJson_withMissingMeasurementBasis_rejectsWholeFile() {
        String missingBasis = validQuestion("missing-basis", 10.0)
                .replace("\"measurementBasis\":\"example measurement basis\",", "");

        assertInvalid(
                validBank(missingBasis),
                "Invalid question bank at $.questions[0].measurementBasis: "
                        + "missing required field"
        );
    }

    @Test
    public void fromJson_withMissingAsOfWhenTimeVarying_rejectsWholeFile() {
        String missingAsOf = validPopulationQuestion("missing-date", "2024-12-31")
                .replace("\"asOf\":\"2024-12-31\",", "");

        assertInvalid(
                validBank(missingAsOf),
                "Invalid question bank at $.questions[0].asOf: missing required field"
        );
    }

    @Test
    public void fromJson_withInvalidAsOfDate_rejectsWholeFile() {
        assertInvalid(
                validBank(validPopulationQuestion("invalid-date", "2024-02-30")),
                "Invalid question bank at $.questions[0].asOf: "
                        + "expected a valid calendar date"
        );
    }

    @Test
    public void fromJson_withAsOfWhenTimeVaryingIsFalse_rejectsWholeFile() {
        String unexpectedAsOf = validQuestion("unexpected-date", 10.0)
                .replace(
                        "\"measurementBasis\":\"example measurement basis\",",
                        "\"measurementBasis\":\"example measurement basis\","
                                + "\"asOf\":\"2024-12-31\","
                );

        assertInvalid(
                validBank(unexpectedAsOf),
                "Invalid question bank at $.questions[0].asOf: "
                        + "must be absent when timeVarying is false"
        );
    }

    @Test
    public void fromJson_withMissingTimeVaryingFlag_rejectsWholeFile() {
        String missingFlag = validQuestion("missing-flag", 10.0)
                .replace("\"timeVarying\":false,", "");

        assertInvalid(
                validBank(missingFlag),
                "Invalid question bank at $.questions[0].timeVarying: missing required field"
        );
    }

    @Test
    public void fromJson_withNonBooleanTimeVaryingFlag_rejectsWholeFile() {
        String wrongType = validQuestion("wrong-flag-type", 10.0)
                .replace("\"timeVarying\":false", "\"timeVarying\":\"false\"");

        assertInvalid(
                validBank(wrongType),
                "Invalid question bank at $.questions[0].timeVarying: expected a boolean"
        );
    }

    @Test
    public void fromJson_withPreviousSchemaVersion_rejectsWholeFile() {
        String versionThreeQuestion = validQuestion("version-three", 10.0)
                .replace(
                        "\"timeVarying\":false,",
                        ""
                );
        String versionThreeBank = validBank(versionThreeQuestion)
                .replace("\"version\":4", "\"version\":3")
                .replace(",\"questions\"", ",\"timeVaryingCategories\":[],\"questions\"");

        assertInvalid(
                versionThreeBank,
                "Invalid question bank at $.version: unsupported version 3"
        );
    }

    @Test
    public void fromJson_withFutureSchemaVersion_rejectsWholeFile() {
        assertInvalid(
                validBank("").replace("\"version\":4", "\"version\":5"),
                "Invalid question bank at $.version: unsupported version 5"
        );
    }

    @Test
    public void fromJson_withTrueValueAsString_rejectsWholeFile() {
        String wrongType = validQuestion("wrong-type", 10.0)
                .replace("\"trueValue\":10.0", "\"trueValue\":\"10.0\"");

        assertInvalid(
                validBank(wrongType),
                "Invalid question bank at $.questions[0].trueValue: expected a number"
        );
    }

    @Test
    public void fromJson_withDuplicateIdentifiers_rejectsWholeFile() {
        String questions = validQuestion("duplicate", 10.0)
                + ","
                + validQuestion("duplicate", 20.0);

        assertInvalid(
                validBank(questions),
                "Invalid question bank at $.questions[1].id: duplicate identifier 'duplicate'"
        );
    }

    @Test
    public void fromJson_withUnknownQuestionField_rejectsWholeFile() {
        String unknownField = validQuestion("extra", 10.0)
                .replace(
                        "\"sourceLabel\":\"Example source\"",
                        "\"sourceLabel\":\"Example source\",\"hint\":\"unexpected\""
                );

        assertInvalid(
                validBank(unknownField),
                "Invalid question bank at $.questions[0].hint: unknown field"
        );
    }

    @Test
    public void fromJson_withRemovedDifficultyField_rejectsWholeFile() {
        String obsoleteDifficulty = validQuestion("obsolete-difficulty", 10.0)
                .replace(
                        "\"sourceLabel\":\"Example source\"",
                        "\"sourceLabel\":\"Example source\",\"difficulty\":2"
                );

        assertInvalid(
                validBank(obsoleteDifficulty),
                "Invalid question bank at $.questions[0].difficulty: unknown field"
        );
    }

    @Test
    public void fromJson_withInvalidGenerationTimestamp_rejectsWholeFile() {
        String invalidTimestamp = validBank("")
                .replace("2026-09-10T12:30:00Z", "10 September 2026");

        assertInvalid(
                invalidTimestamp,
                "Invalid question bank at $.metadata.generationTimestamp: "
                        + "expected an ISO 8601 UTC timestamp"
        );
    }

    @Test
    public void fromJson_whenGenerationDateAndTimestampDisagree_rejectsWholeFile() {
        String mismatchedDate = validBank("")
                .replace("2026-09-10T12:30:00Z", "2026-09-11T00:00:00Z");

        assertInvalid(
                mismatchedDate,
                "Invalid question bank at $.metadata.generationDate: "
                        + "must match the UTC date in generationTimestamp"
        );
    }

    @Test
    public void fromJson_withBlankScriptVersion_rejectsWholeFile() {
        String blankScriptVersion = validBank("")
                .replace("\"scriptVersion\":\"1.0.0\"", "\"scriptVersion\":\" \"");

        assertInvalid(
                blankScriptVersion,
                "Invalid question bank at $.metadata.scriptVersion: must not be blank"
        );
    }

    @Test(timeout = 5000L)
    public void fromJson_withOneThousandQuestions_completesAndLoadsAllQuestions()
            throws Exception {
        StringBuilder questions = new StringBuilder();
        for (int index = 0; index < 1000; index++) {
            if (index > 0) {
                questions.append(',');
            }
            questions.append(validQuestion("question-" + index, index + 1.0));
        }

        QuestionBank bank = QuestionBank.fromJson(validBank(questions.toString()));

        assertEquals(1000, bank.getQuestions().size());
        assertEquals("question-999", bank.getQuestions().get(999).getId());
    }

    private static void assertInvalid(String json, String expectedMessage) {
        QuestionBankFormatException exception = assertThrows(
                QuestionBankFormatException.class,
                () -> QuestionBank.fromJson(json)
        );
        assertEquals(expectedMessage, exception.getMessage());
    }

    private static String validBank(String questions) {
        return "{"
                + "\"version\":4,"
                + "\"metadata\":{"
                + "\"generationDate\":\"2026-09-10\","
                + "\"generationTimestamp\":\"2026-09-10T12:30:00Z\","
                + "\"sourceDatasets\":[\"Example dataset\"],"
                + "\"licence\":\"Example licence\","
                + "\"scriptVersion\":\"1.0.0\""
                + "},"
                + "\"questions\":[" + questions + "]"
                + "}";
    }

    private static String validQuestion(String id, double trueValue) {
        return "{"
                + "\"id\":\"" + id + "\","
                + "\"prompt\":\"How large is " + id + "?\","
                + "\"trueValue\":" + trueValue + ","
                + "\"unit\":\"units\","
                + "\"measurementBasis\":\"example measurement basis\","
                + "\"timeVarying\":false,"
                + "\"category\":\"Example category\","
                + "\"sourceUrl\":\"https://example.org/" + id + "\","
                + "\"sourceLabel\":\"Example source\""
                + "}";
    }

    private static String validPopulationQuestion(String id, String asOf) {
        return "{"
                + "\"id\":\"" + id + "\","
                + "\"prompt\":\"According to Example source, what was the resident "
                + "population of Exampleland on 31 December 2024? Give your answer as a "
                + "number of people.\","
                + "\"trueValue\":1234567,"
                + "\"unit\":\"people\","
                + "\"measurementBasis\":\"resident population\","
                + "\"timeVarying\":true,"
                + "\"asOf\":\"" + asOf + "\","
                + "\"category\":\"National populations\","
                + "\"sourceUrl\":\"https://example.org/" + id + "\","
                + "\"sourceLabel\":\"Example source\""
                + "}";
    }
}
