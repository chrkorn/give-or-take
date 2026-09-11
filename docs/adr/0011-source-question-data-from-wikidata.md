# ADR 0011 — Source the initial question bank from Wikidata

- **Status:** Accepted
- **Date:** 2026-09-11

## Context

The application requires numerical questions whose scoring values can be inspected rather than
treated as unsupported trivia. Because the question bank will be generated once and bundled in the
APK, its source must permit redistribution and must remain identifiable after the upstream data
changes. A small student project also benefits from one extraction method, one licensing regime,
and a deliberately limited content scope.

The candidate sources do not meet these needs equally. World Bank Open Data is strong for dated
population statistics, but covers few physical quantities and has dataset-specific licensing
exceptions. Our World in Data often retains an underlying provider's licence. Natural Earth's
public-domain GIS data are generalised for cartography, while CC BY 4.0-licensed GeoNames focuses
on geographical names. The current REST Countries terms restrict retention and redistribution,
making that service incompatible with a permanent asset.

Wikidata covers all proposed subjects through one structured model and one SPARQL endpoint. Its
main structured data are released under the Creative Commons CC0 1.0 public-domain dedication,
which permits copying, modification, and redistribution without a legal attribution condition.
This resolves licensing complexity, but not epistemic quality: community-edited statements can be
unreferenced, outdated, qualified for different dates, or contradicted by other statements.

## Decision

The initial question bank will use Wikidata as its sole data source. A development-time generator
will query the Wikidata Query Service; the released application will neither contact Wikidata nor
depend on network access. The generated JSON asset will identify Wikidata and CC0 1.0 in its
metadata and record its generation date.

The first bank will contain three categories:

- mountain elevations, expressed in metres above sea level;
- completed-building heights, expressed in metres; and
- national populations for an explicitly stated calendar year, expressed as persons.

This selection represents natural, human-made, and demographic quantities while remaining small
enough for manual review. Population prompts must include the statement's point-in-time year; a
question about a country's current population would become misleading. The report will acknowledge
that later censuses or methodological revisions can alter historical estimates. Corrections will
require a new bank or application release, making an installed bank reproducible rather than
continuously current.

Automatic extraction is a candidate-selection mechanism, not an assertion of truth. Every included
statement must have the expected unit, an appropriate rank, a supporting reference, and all
qualifiers necessary to interpret it. Population statements must have a point-in-time qualifier.
A curator must inspect each candidate for incompatible values and ambiguous definitions before
release. The question's `sourceUrl` will link to the Wikidata item at the revision used for the
snapshot where practicable, while `sourceLabel` will identify Wikidata. This preserves an
inspectable record even if the live item later changes.

The initial bank will exclude river lengths, disputed country areas, and objects with materially
conflicting measurements. Selecting one such value can change a player's correctness band and
repeat schedule; calling it *the* truth would disguise an editorial choice as measurement
certainty. Source-qualified prompts or accepted-value intervals would require a separate domain
and scoring decision.

Although CC0 imposes no attribution requirement, the application will provide a Data Sources entry
stating "Question data from Wikidata, retrieved [date], CC0 1.0" and linking to Wikidata and the
CC0 deed. Answer feedback will expose each question's source link. The README will repeat the
dataset name, retrieval date, licence, licence URL, extraction and manual-curation changes, and a
statement that neither Wikidata nor the Wikimedia Foundation endorses the application. Attribution
is retained for academic traceability and provenance, not presented as a CC0 obligation.

## Consequences

One source and one licence keep generation and compliance understandable. Manual review limits the
number of questions and prevents fully automatic updates, but this cost is proportionate to a
student project whose scoring depends on defensible values. The three-category boundary is an
intentional first-release scope rather than a claim that Wikidata cannot support further subjects.

## References

- Wikidata. *Licensing*. https://www.wikidata.org/wiki/Wikidata:Licensing
- Wikidata. *Data access*. https://www.wikidata.org/wiki/Wikidata:Data_access
- Creative Commons. *CC0 1.0 Universal*. https://creativecommons.org/publicdomain/zero/1.0/
- World Bank. *Data Access and Licensing*. https://datacatalog.worldbank.org/public-licenses
- Our World in Data. *FAQs and User Guidelines*. https://ourworldindata.org/faqs
- Natural Earth. *Terms of Use*. https://www.naturalearthdata.com/about/terms-of-use/
- REST Countries. *Terms of Service*. https://restcountries.com/legal/terms-of-service
- GeoNames. *Data Extract and Web Services*. https://www.geonames.org/export/
