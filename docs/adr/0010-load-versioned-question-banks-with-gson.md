# ADR 0010 — Load versioned question banks with Gson

- **Status:** Accepted
- **Date:** 2026-09-10

## Context

Questions will ship as a generated JSON asset. Android includes `org.json`, but local unit tests
run against a mockable Android library whose methods throw by default. Supplying a separate
`org.json` implementation only to tests would mean that production and tests execute different
parsers. Jackson provides more facilities than this small, fixed format needs and brings several
artifacts. A hand-written JSON parser would add edge-case code unrelated to the app's learning
goals.

The parser also needs a policy for invalid entries and fields that are not part of the documented
format. These questions would have different answers for user-authored imports, where recovering
valid records and forward compatibility could be valuable. The current file is instead generated,
bundled, and released together with the reader.

## Decision

Use Gson's tree API and validate each value explicitly rather than reflectively deserialising
`Question`. Gson is one JVM-compatible artifact, and explicit inspection prevents values such as a
quoted number from being silently coerced. The parser uses strict JSON syntax.

`QuestionBank` accepts a `Reader` or `String`. The Android layer opens the asset and owns the stream;
the core layer parses and validates it. This keeps `AssetManager` and all other `android.*` types out
of the core package, allowing the production parser to run unchanged in local JUnit tests.

The version-1 format is transactional and strict. Any malformed structure, invalid entry, duplicate
ID, or unknown field rejects the whole file with an exception that identifies the JSON path. An
empty question array remains valid. Format evolution requires a new top-level version rather than
silently changing the meaning of version 1.

## Alternatives considered

**Android's `org.json`** avoids a production dependency but makes ordinary JVM tests depend on
Android stubs or a different test-only implementation. **Jackson** has strong data-binding and
streaming support but is larger and more complex than needed. **Skipping invalid entries** could
keep user-supplied data partly usable, but for a bundled asset it hides a generation defect and can
silently reduce the quiz. **Ignoring unknown fields** aids forward compatibility, but the asset and
reader ship together and already have explicit versioning. **Writing a parser locally** would remove
the dependency at the cost of maintaining and testing general JSON syntax ourselves.

## Consequences

Gson is an intentional third-party dependency and must be kept current. Because loading uses the
tree API and explicit builders, `Question` needs no Gson annotations and no reflective field access;
release shrinking cannot rename the JSON contract accidentally. Asset validation failures must be
handled by the Android layer as application-data defects, not presented as partially valid banks.
Future user-import functionality will need a separate recovery policy instead of weakening this
bundled-asset loader.
