package de.christiankorn.giveortake.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loads and retains an immutable collection of numerical estimation questions.
 *
 * <p>The loading boundary accepts a {@link Reader} or a JSON {@link String}, never Android's
 * {@code AssetManager}. Opening a bundled asset and managing its stream belong to the Android
 * layer; parsing and domain validation belong here. This boundary keeps the core package free of
 * {@code android.*} imports and lets ordinary JVM unit tests exercise exactly the parser used by
 * the application without an emulator or Android framework mocks.</p>
 *
 * <p>Version 1 uses strict, transactional validation. A malformed document, an invalid question,
 * a duplicate identifier, or an unknown field rejects the entire bank. Bundled data is controlled
 * by the application build, so silently omitting a defective entry would conceal a data-generation
 * error and could ship an unexpectedly incomplete quiz.</p>
 */
public final class QuestionBank {
    /** The only question-bank format version understood by this loader. */
    public static final int SUPPORTED_VERSION = 1;

    private static final int BUFFER_SIZE = 4096;
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Set<String> ROOT_FIELDS = fields("version", "metadata", "questions");
    private static final Set<String> METADATA_FIELDS = fields(
            "generationDate",
            "sourceDatasets",
            "licence"
    );
    private static final Set<String> QUESTION_FIELDS = fields(
            "id",
            "prompt",
            "trueValue",
            "unit",
            "category",
            "sourceUrl",
            "sourceLabel",
            "difficulty"
    );

    private final List<Question> questions;

    private QuestionBank(List<Question> questions) {
        this.questions = Collections.unmodifiableList(new ArrayList<>(questions));
    }

    /**
     * Loads a complete question bank from JSON text.
     *
     * @param json complete JSON document using the version-1 question-bank format
     * @return an immutable bank containing the validated questions
     * @throws IllegalArgumentException if {@code json} is {@code null}
     * @throws QuestionBankFormatException if the document is malformed or violates the schema
     */
    public static QuestionBank fromJson(String json) throws QuestionBankFormatException {
        if (json == null) {
            throw new IllegalArgumentException("json must not be null");
        }
        return parse(json);
    }

    /**
     * Reads and loads a complete question bank without closing the caller-owned reader.
     *
     * <p>The Android layer can open an asset as a character stream and pass it here. Keeping stream
     * ownership with the caller makes lifecycle and error handling explicit while preserving the
     * framework-independent parsing boundary.</p>
     *
     * @param reader source of a complete version-1 question-bank document
     * @return an immutable bank containing the validated questions
     * @throws IllegalArgumentException if {@code reader} is {@code null}
     * @throws IOException if reading the supplied character stream fails
     * @throws QuestionBankFormatException if the document is malformed or violates the schema
     */
    public static QuestionBank fromJson(Reader reader)
            throws IOException, QuestionBankFormatException {
        if (reader == null) {
            throw new IllegalArgumentException("reader must not be null");
        }

        StringBuilder json = new StringBuilder();
        char[] buffer = new char[BUFFER_SIZE];
        int characterCount;
        while ((characterCount = reader.read(buffer)) != -1) {
            json.append(buffer, 0, characterCount);
        }
        return parse(json.toString());
    }

    /**
     * Returns the questions in source-file order.
     *
     * <p>The returned list is unmodifiable so validated bank contents cannot later acquire nulls,
     * duplicates, or entries that bypassed the loader.</p>
     *
     * @return an unmodifiable list of validated questions
     */
    public List<Question> getQuestions() {
        return questions;
    }

    private static QuestionBank parse(String json) throws QuestionBankFormatException {
        JsonElement document = parseDocument(json);
        JsonObject root = requireObject(document, "$", "top-level value");
        rejectUnknownFields(root, ROOT_FIELDS, "$");

        int version = requireInteger(root, "version", "$.version");
        if (version != SUPPORTED_VERSION) {
            throw invalid("$.version", "unsupported version " + version);
        }

        validateMetadata(requireObject(root, "metadata", "$.metadata"));
        JsonArray questionArray = requireArray(root, "questions", "$.questions");
        List<Question> questions = new ArrayList<>(questionArray.size());
        Set<String> identifiers = new HashSet<>();

        for (int index = 0; index < questionArray.size(); index++) {
            String path = "$.questions[" + index + "]";
            JsonObject questionObject = requireObject(
                    questionArray.get(index),
                    path,
                    "question"
            );
            rejectUnknownFields(questionObject, QUESTION_FIELDS, path);
            Question question = parseQuestion(questionObject, path);
            if (!identifiers.add(question.getId())) {
                throw invalid(path + ".id", "duplicate identifier '" + question.getId() + "'");
            }
            questions.add(question);
        }

        return new QuestionBank(questions);
    }

    private static JsonElement parseDocument(String json) throws QuestionBankFormatException {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setStrictness(Strictness.STRICT);
        try {
            JsonElement document = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw invalid("$", "unexpected content after the top-level value");
            }
            return document;
        } catch (JsonParseException | IOException exception) {
            throw new QuestionBankFormatException("Invalid question bank at $: malformed JSON", exception);
        }
    }

    private static void validateMetadata(JsonObject metadata) throws QuestionBankFormatException {
        String path = "$.metadata";
        rejectUnknownFields(metadata, METADATA_FIELDS, path);

        String generationDate = requireNonBlankString(
                metadata,
                "generationDate",
                path + ".generationDate"
        );
        if (!ISO_DATE.matcher(generationDate).matches()) {
            throw invalid(path + ".generationDate", "expected a date in YYYY-MM-DD form");
        }
        try {
            LocalDate.parse(generationDate);
        } catch (DateTimeException exception) {
            throw invalid(path + ".generationDate", "expected a valid calendar date");
        }

        JsonArray datasets = requireArray(metadata, "sourceDatasets", path + ".sourceDatasets");
        for (int index = 0; index < datasets.size(); index++) {
            requireNonBlankString(
                    datasets.get(index),
                    path + ".sourceDatasets[" + index + "]"
            );
        }
        requireNonBlankString(metadata, "licence", path + ".licence");
    }

    private static Question parseQuestion(JsonObject object, String path)
            throws QuestionBankFormatException {
        String id = requireNonBlankString(object, "id", path + ".id");
        String prompt = requireNonBlankString(object, "prompt", path + ".prompt");
        double trueValue = requireFiniteNumber(object, "trueValue", path + ".trueValue");
        if (trueValue <= 0.0) {
            throw invalid(path + ".trueValue", "must be greater than zero");
        }
        String unit = requireNonBlankString(object, "unit", path + ".unit");
        String category = requireNonBlankString(object, "category", path + ".category");
        String sourceUrl = requireNonBlankString(object, "sourceUrl", path + ".sourceUrl");
        requireHttpUrl(sourceUrl, path + ".sourceUrl");
        String sourceLabel = requireNonBlankString(
                object,
                "sourceLabel",
                path + ".sourceLabel"
        );
        int difficulty = requireInteger(object, "difficulty", path + ".difficulty");
        if (difficulty < 1 || difficulty > 5) {
            throw invalid(path + ".difficulty", "must be between 1 and 5");
        }

        return Question.builder()
                .id(id)
                .prompt(prompt)
                .trueValue(trueValue)
                .unit(unit)
                .category(category)
                .sourceUrl(sourceUrl)
                .sourceLabel(sourceLabel)
                .difficulty(difficulty)
                .build();
    }

    private static void requireHttpUrl(String value, String path)
            throws QuestionBankFormatException {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null) {
                throw invalid(path, "must be an absolute HTTP or HTTPS URL");
            }
        } catch (URISyntaxException exception) {
            throw invalid(path, "must be an absolute HTTP or HTTPS URL");
        }
    }

    private static int requireInteger(JsonObject object, String fieldName, String path)
            throws QuestionBankFormatException {
        JsonPrimitive primitive = requirePrimitive(object, fieldName, path);
        if (!primitive.isNumber()) {
            throw invalid(path, "expected a number");
        }
        double value = primitive.getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw invalid(path, "expected an integer");
        }
        return (int) value;
    }

    private static double requireFiniteNumber(JsonObject object, String fieldName, String path)
            throws QuestionBankFormatException {
        JsonPrimitive primitive = requirePrimitive(object, fieldName, path);
        if (!primitive.isNumber()) {
            throw invalid(path, "expected a number");
        }
        double value = primitive.getAsDouble();
        if (!Double.isFinite(value)) {
            throw invalid(path, "must be finite");
        }
        return value;
    }

    private static String requireNonBlankString(
            JsonObject object,
            String fieldName,
            String path
    ) throws QuestionBankFormatException {
        if (!object.has(fieldName)) {
            throw invalid(path, "missing required field");
        }
        return requireNonBlankString(object.get(fieldName), path);
    }

    private static String requireNonBlankString(JsonElement element, String path)
            throws QuestionBankFormatException {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw invalid(path, "expected a string");
        }
        String value = element.getAsString();
        if (value.trim().isEmpty()) {
            throw invalid(path, "must not be blank");
        }
        return value;
    }

    private static JsonPrimitive requirePrimitive(
            JsonObject object,
            String fieldName,
            String path
    ) throws QuestionBankFormatException {
        if (!object.has(fieldName)) {
            throw invalid(path, "missing required field");
        }
        JsonElement element = object.get(fieldName);
        if (!element.isJsonPrimitive()) {
            throw invalid(path, "expected a primitive value");
        }
        return element.getAsJsonPrimitive();
    }

    private static JsonObject requireObject(JsonObject parent, String fieldName, String path)
            throws QuestionBankFormatException {
        if (!parent.has(fieldName)) {
            throw invalid(path, "missing required field");
        }
        return requireObject(parent.get(fieldName), path, "value");
    }

    private static JsonObject requireObject(JsonElement element, String path, String description)
            throws QuestionBankFormatException {
        if (!element.isJsonObject()) {
            throw invalid(path, description + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject parent, String fieldName, String path)
            throws QuestionBankFormatException {
        if (!parent.has(fieldName)) {
            throw invalid(path, "missing required field");
        }
        JsonElement element = parent.get(fieldName);
        if (!element.isJsonArray()) {
            throw invalid(path, "expected an array");
        }
        return element.getAsJsonArray();
    }

    private static void rejectUnknownFields(
            JsonObject object,
            Set<String> knownFields,
            String path
    ) throws QuestionBankFormatException {
        for (String fieldName : object.keySet()) {
            if (!knownFields.contains(fieldName)) {
                throw invalid(path + "." + fieldName, "unknown field");
            }
        }
    }

    private static Set<String> fields(String... names) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(names)));
    }

    private static QuestionBankFormatException invalid(String path, String reason) {
        return new QuestionBankFormatException("Invalid question bank at " + path + ": " + reason);
    }
}
