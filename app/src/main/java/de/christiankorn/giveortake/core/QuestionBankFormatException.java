package de.christiankorn.giveortake.core;

/**
 * Indicates that a question-bank document does not conform to its declared JSON format.
 *
 * <p>The exception is checked because invalid bundled data is distinct from programmer misuse of
 * the loader. Its message identifies the JSON path at which validation failed so a generated asset
 * can be corrected rather than silently reduced.</p>
 */
public final class QuestionBankFormatException extends Exception {
    private static final long serialVersionUID = 1L;

    QuestionBankFormatException(String message) {
        super(message);
    }

    QuestionBankFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
