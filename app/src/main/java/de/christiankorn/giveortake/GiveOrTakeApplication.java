package de.christiankorn.giveortake;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.StrictMode;

import java.util.Random;

import de.christiankorn.giveortake.core.QuestionBank;
import de.christiankorn.giveortake.data.AssetQuestionBankLoader;
import de.christiankorn.giveortake.data.QuizHistoryStore;

/** Owns process-scoped services and debug diagnostics shared by the application's Activities. */
public final class GiveOrTakeApplication extends Application {
    private QuizHistoryStore quizHistoryStore;
    private QuizDependencies quizDependencies;

    /** Enables debug diagnostics and creates the process-scoped history writer. */
    @Override
    public void onCreate() {
        super.onCreate();
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            enableDebugStrictMode();
        }
        quizHistoryStore = new QuizHistoryStore(this);
        quizDependencies = new QuizDependencies() {
            /** Loads the production asset through the existing validated loader. */
            @Override
            public QuestionBank loadQuestionBank() {
                return new AssetQuestionBankLoader(getAssets()).load();
            }

            /** Keeps normal sessions randomly ordered. */
            @Override
            public Random newSessionRandom() {
                return new Random();
            }
        };
    }

    /**
     * Returns the serial quiz-history persistence service for this application process.
     *
     * @return process-scoped history store
     */
    public QuizHistoryStore getQuizHistoryStore() {
        return quizHistoryStore;
    }

    /** Returns the current session input provider; callers use the main thread. */
    QuizDependencies getQuizDependencies() {
        return quizDependencies;
    }

    /** Replaces the provider on the main thread before launch and returns it for later restoration. */
    QuizDependencies replaceQuizDependencies(QuizDependencies replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException("replacement must not be null");
        }
        QuizDependencies previous = quizDependencies;
        quizDependencies = replacement;
        return previous;
    }

    private static void enableDebugStrictMode() {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .penaltyLog()
                .build());
    }
}
