# Question bank JSON format

[`question-bank.example.json`](question-bank.example.json) is the canonical example for version 1
of the bundled question-bank format. Generated question data must use the same field names, nesting,
and JSON value types.

## Top-level object

| Field | JSON type | Rules |
|---|---|---|
| `version` | number | Required integer. Version 1 is the only currently supported value. |
| `metadata` | object | Required. Describes the provenance of the bank as a whole. |
| `questions` | array | Required. Contains zero or more question objects. An empty bank is valid. |

## Metadata object

| Field | JSON type | Rules |
|---|---|---|
| `generationDate` | string | Required ISO 8601 calendar date in `YYYY-MM-DD` form. |
| `sourceDatasets` | array | Required. Contains non-blank strings naming the datasets used to generate the bank. The array may be empty when no dataset was used. |
| `licence` | string | Required and non-blank. Identifies the licence applying to the compiled bank. |

## Question object

Every listed field is required. JSON `null` is not accepted for any field.

| Field | JSON type | Rules |
|---|---|---|
| `id` | string | Non-blank and unique within the file. Stable across wording or value corrections. |
| `prompt` | string | Non-blank text shown to the player. |
| `trueValue` | number | Finite and greater than zero, matching the logarithmic scoring domain. A numeric string is not a number. |
| `unit` | string | Non-blank unit in which answers are entered. |
| `category` | string | Non-blank subject grouping. |
| `sourceUrl` | string | Non-blank absolute `http` or `https` URL supporting the authoritative value. |
| `sourceLabel` | string | Non-blank human-readable source name. |
| `difficulty` | number | Integer from 1 (easiest) through 5 (hardest). |

JSON numbers must be unquoted. In particular, `"trueValue": "346.0"` is invalid even though the
string contains digits. This keeps schema errors visible instead of silently coercing them.

Whether unrecognised fields are rejected or ignored is a loader policy and is deliberately not part
of the version 1 data shape. Generated data should never emit fields not documented above.
