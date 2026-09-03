package de.christiankorn.giveortake.core;

/**
 * Describes a numerical estimation question and the authoritative value against
 * which guesses are scored.
 *
 * <p>A question is immutable. Its identifier defines its identity, while the
 * remaining fields describe content that may be corrected without creating a
 * different question.</p>
 */
public final class Question {
    private final String id;
    private final String prompt;
    private final double trueValue;
    private final String unit;
    private final String category;
    private final String sourceUrl;
    private final String sourceLabel;
    private final int difficulty;

    private Question(Builder builder) {
        id = requireNonBlank(builder.id, "id");
        prompt = requireNonBlank(builder.prompt, "prompt");
        unit = requireNonBlank(builder.unit, "unit");

        if (!Double.isFinite(builder.trueValue)) {
            throw new IllegalArgumentException("trueValue must be finite");
        }
        if (builder.trueValue <= 0.0) {
            throw new IllegalArgumentException("trueValue must be greater than zero");
        }
        if (builder.difficulty < 1 || builder.difficulty > 5) {
            throw new IllegalArgumentException("difficulty must be between 1 and 5");
        }

        trueValue = builder.trueValue;
        category = builder.category;
        sourceUrl = builder.sourceUrl;
        sourceLabel = builder.sourceLabel;
        difficulty = builder.difficulty;
    }

    /**
     * Creates an empty builder whose values are checked when {@link Builder#build()} is called.
     *
     * @return a builder for a question
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the stable identifier used to distinguish this question from all others.
     *
     * @return the question identifier
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the text shown to a player.
     *
     * @return the question prompt
     */
    public String getPrompt() {
        return prompt;
    }

    /**
     * Returns the strictly positive authoritative value used for scoring guesses.
     *
     * @return the true numerical value
     */
    public double getTrueValue() {
        return trueValue;
    }

    /**
     * Returns the unit in which the true value and guesses are expressed.
     *
     * @return the non-blank unit label
     */
    public String getUnit() {
        return unit;
    }

    /**
     * Returns the optional subject grouping for the question.
     *
     * @return the category, or {@code null} when none is assigned
     */
    public String getCategory() {
        return category;
    }

    /**
     * Returns the optional address of the source supporting the true value.
     *
     * @return the source URL, or {@code null} when none is recorded
     */
    public String getSourceUrl() {
        return sourceUrl;
    }

    /**
     * Returns the optional human-readable name of the source.
     *
     * @return the source label, or {@code null} when none is recorded
     */
    public String getSourceLabel() {
        return sourceLabel;
    }

    /**
     * Returns the authored difficulty level, where 1 is easiest and 5 is hardest.
     *
     * @return a difficulty from 1 through 5
     */
    public int getDifficulty() {
        return difficulty;
    }

    /**
     * Compares questions by their stable identifiers.
     *
     * @param other the object to compare with this question
     * @return {@code true} when the other object is a question with the same identifier
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Question)) {
            return false;
        }
        Question question = (Question) other;
        return id.equals(question.id);
    }

    /**
     * Returns a hash code derived from the stable question identifier.
     *
     * @return the identifier's hash code
     */
    @Override
    public int hashCode() {
        return id.hashCode();
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }

    /**
     * Collects the values needed to construct an immutable {@link Question} without relying on
     * an error-prone sequence of similarly typed constructor arguments.
     */
    public static final class Builder {
        private String id;
        private String prompt;
        private double trueValue;
        private String unit;
        private String category;
        private String sourceUrl;
        private String sourceLabel;
        private int difficulty;

        private Builder() {
        }

        /**
         * Sets the stable identifier of the question.
         *
         * @param id the non-blank identifier
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the text shown to a player.
         *
         * @param prompt the non-blank question text
         * @return this builder
         */
        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        /**
         * Sets the authoritative value used to score guesses.
         *
         * @param trueValue a finite value greater than zero
         * @return this builder
         */
        public Builder trueValue(double trueValue) {
            this.trueValue = trueValue;
            return this;
        }

        /**
         * Sets the unit in which the value and guesses are expressed.
         *
         * @param unit the non-blank unit label
         * @return this builder
         */
        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        /**
         * Sets the optional subject grouping for the question.
         *
         * @param category the category, which may be {@code null}
         * @return this builder
         */
        public Builder category(String category) {
            this.category = category;
            return this;
        }

        /**
         * Sets the optional address of the source supporting the true value.
         *
         * @param sourceUrl the source URL, which may be {@code null}
         * @return this builder
         */
        public Builder sourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
            return this;
        }

        /**
         * Sets the optional human-readable name of the source.
         *
         * @param sourceLabel the source label, which may be {@code null}
         * @return this builder
         */
        public Builder sourceLabel(String sourceLabel) {
            this.sourceLabel = sourceLabel;
            return this;
        }

        /**
         * Sets the authored difficulty level.
         *
         * @param difficulty a level from 1 through 5
         * @return this builder
         */
        public Builder difficulty(int difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        /**
         * Validates the supplied values and creates an immutable question.
         *
         * @return the constructed question
         * @throws IllegalArgumentException if a required value is missing or invalid
         */
        public Question build() {
            return new Question(this);
        }
    }
}
