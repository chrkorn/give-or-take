package de.christiankorn.giveortake.data;

import android.content.res.AssetManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import de.christiankorn.giveortake.core.QuestionBankFormatException;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Verifies fail-fast behaviour at the Android bundled-asset boundary.
 */
@RunWith(AndroidJUnit4.class)
public class AssetQuestionBankLoaderTest {

    /** Verifies that a missing bundled asset retains its I/O cause for diagnosis. */
    @Test
    public void load_whenAssetIsMissing_throwsProgrammingErrorWithCause() {
        AssetManager assets = InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getAssets();
        AssetQuestionBankLoader loader = new AssetQuestionBankLoader(
                assets,
                "missing-questions.json"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                loader::load
        );

        assertEquals(
                "Bundled missing-questions.json is missing, unreadable, or invalid",
                exception.getMessage()
        );
        assertTrue(exception.getCause() instanceof IOException);
    }

    /** Verifies that malformed bundled JSON retains its schema exception for diagnosis. */
    @Test
    public void load_whenAssetIsMalformed_throwsProgrammingErrorWithCause() {
        AssetManager assets = InstrumentationRegistry.getInstrumentation()
                .getContext()
                .getAssets();
        AssetQuestionBankLoader loader = new AssetQuestionBankLoader(
                assets,
                "malformed-questions.json"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                loader::load
        );

        assertEquals(
                "Bundled malformed-questions.json is missing, unreadable, or invalid",
                exception.getMessage()
        );
        assertTrue(exception.getCause() instanceof QuestionBankFormatException);
    }
}
