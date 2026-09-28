package de.christiankorn.giveortake;

import java.util.Random;

import de.christiankorn.giveortake.core.QuestionBank;

/** Supplies session inputs at the Android boundary without coupling the core to the application. */
interface QuizDependencies {
    /** Returns the bank used both to resolve settings and to construct or restore a session. */
    QuestionBank loadQuestionBank();

    /** Returns a fresh random generator for each new session, never for a restored session. */
    Random newSessionRandom();
}
