package de.christiankorn.giveortake.data;

import android.content.res.AssetManager;

import de.christiankorn.giveortake.core.QuestionBank;
import de.christiankorn.giveortake.core.QuestionBankFormatException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Loads the application's bundled, UTF-8 question bank from Android assets.
 *
 * <p>A missing, unreadable, malformed, or empty bundled bank is an application defect. The loader
 * therefore fails fast with an unchecked exception instead of presenting a retry action that
 * cannot repair the installed APK.</p>
 */
public final class AssetQuestionBankLoader {
    /** Name under which the generated bank is packaged in the APK. */
    public static final String ASSET_NAME = "questions.json";

    private final AssetManager assetManager;
    private final String assetName;

    /**
     * Creates a loader for the supplied Android asset collection.
     *
     * @param assetManager manager belonging to the application or current Activity
     * @throws IllegalArgumentException if the manager is {@code null}
     */
    public AssetQuestionBankLoader(AssetManager assetManager) {
        if (assetManager == null) {
            throw new IllegalArgumentException("assetManager must not be null");
        }
        this.assetManager = assetManager;
        assetName = ASSET_NAME;
    }

    AssetQuestionBankLoader(AssetManager assetManager, String assetName) {
        if (assetManager == null) {
            throw new IllegalArgumentException("assetManager must not be null");
        }
        if (assetName == null || assetName.trim().isEmpty()) {
            throw new IllegalArgumentException("assetName must not be null or blank");
        }
        this.assetManager = assetManager;
        this.assetName = assetName;
    }

    /**
     * Opens, parses, validates, and closes the bundled question-bank stream.
     *
     * @return the complete immutable question bank
     * @throws IllegalStateException if the asset is missing, unreadable, malformed, or empty
     */
    public QuestionBank load() {
        try (Reader reader = new BufferedReader(
                new InputStreamReader(
                        assetManager.open(assetName),
                        StandardCharsets.UTF_8
                )
        )) {
            QuestionBank questionBank = QuestionBank.fromJson(reader);
            if (questionBank.getQuestions().isEmpty()) {
                throw new IllegalStateException("Bundled " + assetName + " contains no questions");
            }
            return questionBank;
        } catch (IOException | QuestionBankFormatException exception) {
            throw new IllegalStateException(
                    "Bundled " + assetName + " is missing, unreadable, or invalid",
                    exception
            );
        }
    }
}
