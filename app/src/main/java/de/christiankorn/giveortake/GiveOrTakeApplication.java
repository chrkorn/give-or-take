package de.christiankorn.giveortake;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.StrictMode;

import de.christiankorn.giveortake.data.QuizHistoryStore;

/** Owns process-scoped services and debug diagnostics shared by the application's Activities. */
public final class GiveOrTakeApplication extends Application {
    private QuizHistoryStore quizHistoryStore;

    /** Enables debug diagnostics and creates the process-scoped history writer. */
    @Override
    public void onCreate() {
        super.onCreate();
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            enableDebugStrictMode();
        }
        quizHistoryStore = new QuizHistoryStore(this);
    }

    /**
     * Returns the serial quiz-history persistence service for this application process.
     *
     * @return process-scoped history store
     */
    public QuizHistoryStore getQuizHistoryStore() {
        return quizHistoryStore;
    }

    private static void enableDebugStrictMode() {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .penaltyLog()
                .build());
    }
}
