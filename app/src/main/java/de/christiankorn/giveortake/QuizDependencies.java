package de.christiankorn.giveortake;

import java.util.Random;

import de.christiankorn.giveortake.core.QuestionBank;

/**
 * Supplies session inputs at the Android boundary without coupling the core to the application.
 *
 * <p>The narrow injection seam is limited to nondeterministic inputs required by ADR 0025
 * ({@code docs/adr/0025-inject-session-inputs-for-deterministic-ui-tests.md}).</p>
 */
interface QuizDependencies {
    /** Returns the bank used both to resolve settings and to construct or restore a session. */
    QuestionBank loadQuestionBank();

    /** Returns a fresh random generator for each new session, never for a restored session. */
    Random newSessionRandom();
}
