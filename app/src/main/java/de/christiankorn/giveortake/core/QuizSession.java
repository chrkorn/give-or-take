package de.christiankorn.giveortake.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Coordinates scoring, correctness classification, and question scheduling for one point quiz.
 *
 * <p>This class is the domain boundary used by the Android screen. Keeping the complete submission
 * sequence here prevents an Activity from duplicating arithmetic or partially updating the
 * training schedule.</p>
 */
public final class QuizSession {
    private final TrainingStrategy trainingStrategy;
    private final ScoringPolicy scoringPolicy;
    private final CorrectnessClassifier correctnessClassifier;
    private final int initialQuestionCount;
    private final List<Score> scores;
    private Question currentQuestion;

    /**
     * Starts a point-estimate session and selects its first question.
     *
     * @param questionPool questions available for selection
     * @param sessionLength maximum number of distinct initial questions
     * @param random caller-owned source of shuffle randomness
     * @param scoringPolicy policy used to score each point estimate
     * @param correctnessClassifier classifier used to drive remedial repeats
     * @throws IllegalArgumentException if an argument is invalid
     */
    public QuizSession(
            List<Question> questionPool,
            int sessionLength,
            Random random,
            ScoringPolicy scoringPolicy,
            CorrectnessClassifier correctnessClassifier
    ) {
        if (scoringPolicy == null) {
            throw new IllegalArgumentException("scoringPolicy must not be null");
        }
        if (correctnessClassifier == null) {
            throw new IllegalArgumentException("correctnessClassifier must not be null");
        }

        trainingStrategy = new TrainingStrategy(questionPool, sessionLength, random);
        this.scoringPolicy = scoringPolicy;
        this.correctnessClassifier = correctnessClassifier;
        initialQuestionCount = Math.min(sessionLength, questionPool.size());
        scores = new ArrayList<>();
        currentQuestion = trainingStrategy.nextQuestion();
    }

    private QuizSession(
            TrainingStrategy trainingStrategy,
            ScoringPolicy scoringPolicy,
            CorrectnessClassifier correctnessClassifier,
            int initialQuestionCount,
            List<Score> scores,
            Question currentQuestion
    ) {
        this.trainingStrategy = trainingStrategy;
        this.scoringPolicy = scoringPolicy;
        this.correctnessClassifier = correctnessClassifier;
        this.initialQuestionCount = initialQuestionCount;
        this.scores = new ArrayList<>(scores);
        this.currentQuestion = currentQuestion;
    }

    /**
     * Restores a session against a freshly loaded question pool.
     *
     * @param questionPool current bundled questions used to resolve snapshot identifiers
     * @param snapshot previously captured domain state
     * @param scoringPolicy policy used for subsequent submissions
     * @param correctnessClassifier classifier used for subsequent remedial scheduling
     * @return a session at the exact captured position
     * @throws IllegalArgumentException if an argument is null, question identifiers cannot be
     *                                  resolved uniquely, or the snapshot is inconsistent
     */
    public static QuizSession restore(
            List<Question> questionPool,
            QuizSessionSnapshot snapshot,
            ScoringPolicy scoringPolicy,
            CorrectnessClassifier correctnessClassifier
    ) {
        if (questionPool == null) {
            throw new IllegalArgumentException("questionPool must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        if (scoringPolicy == null) {
            throw new IllegalArgumentException("scoringPolicy must not be null");
        }
        if (correctnessClassifier == null) {
            throw new IllegalArgumentException("correctnessClassifier must not be null");
        }

        Map<String, Question> questionsById = indexQuestions(questionPool);
        List<Question> pendingQuestions = resolveQuestions(
                snapshot.getPendingQuestionIds(),
                questionsById
        );
        Question currentQuestion = resolveQuestion(
                snapshot.getCurrentQuestionId(),
                questionsById
        );
        TrainingStrategy strategy = new TrainingStrategy(pendingQuestions, currentQuestion);
        return new QuizSession(
                strategy,
                scoringPolicy,
                correctnessClassifier,
                snapshot.getInitialQuestionCount(),
                snapshot.getScores(),
                currentQuestion
        );
    }

    /**
     * Returns the question currently awaiting a guess.
     *
     * @return the current question
     * @throws IllegalStateException if the session is complete
     */
    public Question getCurrentQuestion() {
        if (currentQuestion == null) {
            throw new IllegalStateException("the quiz session is complete");
        }
        return currentQuestion;
    }

    /**
     * Returns the one-based ordinal of the question currently displayed.
     *
     * @return the number of recorded answers plus one
     * @throws IllegalStateException if the session is complete
     */
    public int getCurrentQuestionNumber() {
        if (currentQuestion == null) {
            throw new IllegalStateException("the quiz session is complete");
        }
        return scores.size() + 1;
    }

    /**
     * Returns the number of distinct questions selected before remedial repeats.
     *
     * @return the positive initial question count
     */
    public int getInitialQuestionCount() {
        return initialQuestionCount;
    }

    /**
     * Returns how many guesses have been recorded, including remedial repeats.
     *
     * @return the non-negative number of submitted answers
     */
    public int getAnsweredQuestionCount() {
        return scores.size();
    }

    /**
     * Returns how many questions are currently scheduled, including the displayed question.
     *
     * <p>The value may stay unchanged after a wrong answer because that answer schedules a later
     * repeat. Computing it in core keeps the Activity independent of queue details.</p>
     *
     * @return zero after completion, otherwise the positive scheduled-question count
     */
    public int getRemainingQuestionCount() {
        return trainingStrategy.getRemainingQuestionCount();
    }

    /**
     * Scores a guess and atomically advances the training schedule.
     *
     * @param guess point estimate to submit for the current question
     * @return the score, correctness, and selected next question
     * @throws IllegalArgumentException if the guess is null or unsupported by the scoring policy
     * @throws IllegalStateException if the session is already complete
     */
    public QuizSubmission submit(Guess guess) {
        if (guess == null) {
            throw new IllegalArgumentException("guess must not be null");
        }
        Question answeredQuestion = getCurrentQuestion();
        Score score = scoringPolicy.score(answeredQuestion, guess);
        Correctness correctness = correctnessClassifier.classify(score.getRawError());

        trainingStrategy.recordAnswer(correctness);
        scores.add(score);
        currentQuestion = trainingStrategy.isComplete() ? null : trainingStrategy.nextQuestion();

        return new QuizSubmission(answeredQuestion, score, correctness, currentQuestion);
    }

    /**
     * Reports whether no scheduled question remains.
     *
     * @return {@code true} after the final submission
     */
    public boolean isComplete() {
        return currentQuestion == null;
    }

    /**
     * Produces the aggregate result after the session has completed.
     *
     * @return the completed point-estimate result
     * @throws IllegalStateException if a question still awaits an answer
     */
    public SessionResult getResult() {
        if (!isComplete()) {
            throw new IllegalStateException("the quiz session is not complete");
        }
        return new SessionResult(Level.POINT_ESTIMATES, scores);
    }

    /**
     * Captures the minimal state required to recreate this session.
     *
     * @return an immutable framework-independent snapshot
     */
    public QuizSessionSnapshot snapshot() {
        List<String> pendingQuestionIds = new ArrayList<>();
        for (Question question : trainingStrategy.getPendingQuestionsSnapshot()) {
            pendingQuestionIds.add(question.getId());
        }
        Question strategyQuestion = trainingStrategy.getCurrentQuestionSnapshot();
        String currentQuestionId = strategyQuestion == null ? null : strategyQuestion.getId();
        return new QuizSessionSnapshot(
                initialQuestionCount,
                pendingQuestionIds,
                currentQuestionId,
                scores
        );
    }

    private static Map<String, Question> indexQuestions(List<Question> questionPool) {
        Map<String, Question> questionsById = new HashMap<>();
        for (Question question : questionPool) {
            if (question == null) {
                throw new IllegalArgumentException("questionPool must not contain null questions");
            }
            if (questionsById.put(question.getId(), question) != null) {
                throw new IllegalArgumentException(
                        "questionPool must not contain duplicate question identifiers"
                );
            }
        }
        return questionsById;
    }

    private static List<Question> resolveQuestions(
            List<String> questionIds,
            Map<String, Question> questionsById
    ) {
        List<Question> questions = new ArrayList<>(questionIds.size());
        for (String questionId : questionIds) {
            questions.add(requireQuestion(questionId, questionsById));
        }
        return questions;
    }

    private static Question resolveQuestion(
            String questionId,
            Map<String, Question> questionsById
    ) {
        return questionId == null ? null : requireQuestion(questionId, questionsById);
    }

    private static Question requireQuestion(
            String questionId,
            Map<String, Question> questionsById
    ) {
        Question question = questionsById.get(questionId);
        if (question == null) {
            throw new IllegalArgumentException(
                    "snapshot question identifier is absent from the question pool: " + questionId
            );
        }
        return question;
    }
}
