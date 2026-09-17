package de.christiankorn.giveortake.core;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;

/**
 * Validates the exact generated question bank that the Android application packages.
 */
public class BundledQuestionBankTest {

    /** Verifies at build time that the bundled asset is readable, valid, and non-empty. */
    @Test
    public void packagedQuestionBank_isValidAndNonEmpty() throws Exception {
        Path asset = Path.of("src", "main", "assets", "questions.json");

        try (Reader reader = new BufferedReader(
                Files.newBufferedReader(asset, StandardCharsets.UTF_8)
        )) {
            QuestionBank bank = QuestionBank.fromJson(reader);

            assertFalse(bank.getQuestions().isEmpty());
        }
    }
}
