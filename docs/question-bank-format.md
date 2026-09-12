# Question bank JSON format

[`question-bank.example.json`](question-bank.example.json) is the canonical example for version 3
of the bundled question-bank format. Generated question data must use the same field names, nesting,
and JSON value types.

## Top-level object

| Field | JSON type | Rules |
|---|---|---|
| `version` | number | Required integer. Version 3 is the only currently supported value. |
| `metadata` | object | Required. Describes the provenance of the bank as a whole. |
| `timeVaryingCategories` | array | Required. Contains unique, non-blank category names whose questions require an `asOf` date. |
| `questions` | array | Required. Contains zero or more question objects. An empty bank is valid. |

## Metadata object

| Field | JSON type | Rules |
|---|---|---|
| `generationDate` | string | Required ISO 8601 calendar date in `YYYY-MM-DD` form. |
| `generationTimestamp` | string | Required ISO 8601 UTC timestamp ending in `Z`. Its UTC date must equal `generationDate`. |
| `sourceDatasets` | array | Required. Contains non-blank strings naming the datasets used to generate the bank. The array may be empty when no dataset was used. |
| `licence` | string | Required and non-blank. Identifies the licence applying to the compiled bank. |
| `scriptVersion` | string | Required and non-blank. Identifies the generator version used to create the bank. |

## Question object

Every listed field except `asOf` is required. JSON `null` is not accepted for any field.

| Field | JSON type | Rules |
|---|---|---|
| `id` | string | Non-blank and unique within the file. Stable across wording or value corrections. |
| `prompt` | string | Non-blank, fully composed text shown to the player. It states the unit, measurement basis, and reference date when applicable. |
| `trueValue` | number | Finite and greater than zero, matching the logarithmic scoring domain. A numeric string is not a number. |
| `unit` | string | Non-blank unit in which answers are entered. |
| `measurementBasis` | string | Required and non-blank. Defines what was measured, independently of its unit. |
| `asOf` | string | Required ISO 8601 date in `YYYY-MM-DD` form when `category` occurs in `timeVaryingCategories`; forbidden otherwise. |
| `category` | string | Non-blank subject grouping. |
| `sourceUrl` | string | Non-blank absolute `http` or `https` URL supporting the authoritative value. |
| `sourceLabel` | string | Non-blank human-readable source name. |

JSON numbers must be unquoted. In particular, `"trueValue": "346.0"` is invalid even though the
string contains digits. This keeps schema errors visible instead of silently coercing them.

Whether unrecognised fields are rejected or ignored is a loader policy and is deliberately not part
of the version 3 data shape. Generated data should never emit fields not documented above.

## Prompt representation

The JSON stores the complete reviewed prompt rather than a runtime template identifier. Structured
`unit`, `measurementBasis`, and `asOf` fields remain authoritative context and support validation,
display, and future analysis. The generator composes both representations together and rejects a
manual prompt override that drops the required measurement words or date. This keeps the exact text
visible in data-review diffs without moving English grammar and category exceptions into the app.

## Version compatibility

The bundled-asset loader accepts only version 3. Version 3 removes the version-2 `difficulty` field
because its sitelink-derived value did not measure estimation difficulty. This is a schema-version
change even though the bank has no external consumers: the strict version-2 reader required the
field, while the strict version-3 reader rejects it as unknown. The version bump therefore exposes
an accidentally mismatched reader and asset directly.

Earlier versions are not migrated at runtime because the application and its generated bank are
released together. In particular, version 1 has no reliable value from which a required
`measurementBasis` can be inferred. Supporting old formats would add parser paths that are useful
for user-owned data but unnecessary for this controlled asset.
