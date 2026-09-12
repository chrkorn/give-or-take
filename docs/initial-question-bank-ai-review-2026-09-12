## Verdict

This is a useful generated candidate set, but it is not ready to ship.

The good news: all 80 `trueValue` fields occur exactly in the revision-pinned Wikidata statements cited by their records. The generator appears internally correct.

The bad news: “Wikidata contained this number” is not the same as “this is a defensible ground truth.” I found at least six values that should be rejected, pervasive ambiguity in building heights, hidden date/method distinctions in populations, and too many difficulty-4/5 questions that are merely obscure-name roulette.

My examiner’s verdict would be: strong reproducible data-generation exercise, weak editorial validation.

### Legend

- `OK`: defensible as written, apart from the global unit-display caveat below.
- `Rewrite`: usable after clarifying the prompt.
- `Reject`: wrong, materially disputed, or irredeemably ambiguous in its current form.
- `K`: substantially a knowledge/recognition question rather than an estimation question.
- Difficulty `↑`: harder than rated; `↓`: easier than rated.

## Universal unit and wording findings

Every record has an unambiguous separate unit field:

- Mountains: `metres above sea level`
- Buildings: `metres`
- Populations: `people`

However, none of the prompts itself states the answer unit. Therefore:

- If the app permanently displays the unit beside the input field, the units pass.
- If prompts may ever appear without that field—history, accessibility announcement, test report, notification, or export—all 80 fail the “prompt states the unit” test.

For buildings, the unit is clear but the quantity is not. “How tall?” might mean architectural height, roof height, occupied height, or antenna/pinnacle height. All 25 building prompts need a measurement criterion.

The repeated building wording also sounds clipped in places. Use “the Empire State Building,” “the New York Times Building,” “the Woolworth Building,” and similar articles where natural.

## Hard factual and sourcing failures

These are not matters of taste:

- **Loreley: 109 m** — reject. The regional tourism authority describes the rock/plateau as **193.14 m above sea level** and about 125–132 m above the Rhine. The Wikidata number appears to describe some other coordinate or feature. [Loreley tourism authority](https://www.loreley-touristik.de/das-loreley-plateau)

- **Mount Fuji: 3,777.24 m** — reject or correct. Japan’s Geospatial Information Authority retains the summit elevation as **3,776 m**; another Japanese measurement is 3,776.224 m, suggesting that Wikidata’s 3,777.24 may be a transcription error. [Geospatial Information Authority of Japan](https://web2.gsi.go.jp/WNEW/PRESS-RELEASE/keikaku61003.html)

- **Monte Titano: 756 m** — reject. The same Wikidata revision contains an alternative 739 m statement. Other sources give 739 m or 749 m, but not 756 m. That is exactly the sort of unresolved source disagreement a single-answer quiz must avoid. [San Marino tourism](https://www.sanmarino.it/mete-san-marino/monte-titano/), [Treccani](https://www.treccani.it/enciclopedia/monte-titano/)

- **Mount Tabor: 530 m** — reject. Israel’s Nature and Parks Authority gives a maximum elevation of **562 m**; other sources commonly give 575 m. [Israel Nature and Parks Authority](https://www.parks.org.il/reserve-park/%D7%A9%D7%9E%D7%95%D7%A8%D7%AA-%D7%98%D7%91%D7%A2-%D7%95%D7%92%D7%9F-%D7%9C%D7%90%D7%95%D7%9E%D7%99-%D7%94%D7%A8-%D7%AA%D7%91%D7%95%D7%A8/)

- **Empire State Building: 453 m** — reject. The Wikidata statement labels 453 m as architectural height and cites the Skyscraper Center, but that source gives **381 m architectural** and **443.2 m to tip**. The building’s own site gives 443 m including spire and antenna. This is a source-transcription failure. [Skyscraper Center](https://www.skyscrapercenter.com/building/building/261), [Empire State Building](https://www.esbnyc.com/about/facts-figures)

- **30 St Mary Axe: 250 m** — reject. Its architectural height is **179.8 m**, not 250 m. [Skyscraper Center](https://www.skyscrapercenter.com/london/30-st-mary-axe/2369)

Two more records are not plainly “wrong,” but their definitions are unacceptable:

- **Mount Nebo: 808 m** — the Mount Nebo site distinguishes the actual high point at 802 m, the religious site/Pisgah at 710 m, and another peak at 790 m. The prompt does not identify which one. [Mount Nebo site](https://mountneboonline.com/en/about)

- **Albania 2023: 2,811,655** — plausibly an annual estimate, but Albania’s 2023 census counted **2,402,113 residents**. Asking simply for “the population in 2023” makes two answers differing by over 400,000 defensible. [INSTAT census report](https://www.instat.gov.al/media/13615/cens-i-popullsise-2023.pdf)

## Per-question audit

### Mountain elevations

| Question | Finding | Value/source | Difficulty and estimability |
|---|---|---|---|
| Mount Everest — 8,848.86 | Minor historic-measurement ambiguity, but preferred modern value is defensible. | Plausible; exact source match. | 1: appropriate. |
| Mount Vesuvius — 1,281 | Summit changes after eruptions; revision also contains 1,297. | Plausible, but say “current summit elevation.” | 1 is too easy; use 2. |
| Mont Blanc — 4,805.59 | Snow-and-ice summit changes. Prompt omits measurement date. | Exact match for 5 Oct 2023. | 1 plausible, but rewrite with date. |
| Matterhorn — 4,477.54 | Minor measurement/rounding variation. “The Matterhorn” is more natural English. | Plausible; exact match. | 2: appropriate. |
| Loreley — 109 | Materially wrong or wrong feature. Loreley is also not naturally treated as a conventional mountain summit. | Reject. | 4 masks a bad question; K. |
| Mount Fuji — 3,777.24 | Apparent transcription error. | Reject/correct to a properly sourced 3,776 m value. | 1: appropriate after correction. |
| Aconcagua — 6,962 | Published values differ by roughly a metre. | Plausible; exact match. | 1 is slightly easy; use 2. |
| Mount Elbrus — 5,642 | Clear conventional summit. | Plausible; exact match. | 2: appropriate. |
| K2 — 8,611 | Clear conventional summit. | Plausible; exact match. | 2: appropriate, arguably 1. |
| Mount Olympus — 2,917.727 | Plausible, but millimetre precision looks absurd in a consumer quiz. | Exact match to a named measurement study. Round to 2,918. | 2: appropriate. |
| Kanchenjunga — 8,586 | Clear conventional summit. | Plausible; exact match. | 2 may be low; use 3. |
| Nanga Parbat — 8,126 | Clear conventional summit. | Plausible; exact match. | 3: appropriate. |
| Monte Cassino — 520 | Could mean the hill, settlement, or abbey site. | Plausible but weakly sourced. | 5: obscurity rather than estimation; K. |
| Monte Titano — 756 | Competing elevations; selected value is poorly supported. | Reject. | 4; K. |
| Mount Tabor — 530 | Conflicts materially with authoritative figures. | Reject. | 3; K. |
| Mount of Olives — 826 | Ridge with several summits. Rewrite as “highest point of the Mount of Olives.” Add “the.” | Plausible as the highest point. | 3: reasonable. |
| Mount Herzl — 834 | Urban hill whose exact extent/summit is not obvious. | Plausible but weakly sourced. | 4; K. |
| Sugarloaf Mountain — 395 | Many features share this name. Add “in Rio de Janeiro.” | Plausible. | 4 is too hard for an iconic landmark; use 3. |
| Mount Arafat — 454 | Distinguish elevation above sea level from its much smaller local prominence. | Plausible. | 3: reasonable. |
| Mount Kailash — 6,638 | Published values vary somewhat. | Plausible; specify the adopted source. | 3: appropriate. |
| Mount Scopus — 826 | A ridge rather than one self-evident point. | Plausible, but rewrite as highest point. | 4; largely K. |
| Jebel Barkal — 287 | Users may confuse elevation with its roughly 100 m rise above the surroundings. | Plausible because the unit says above sea level. | 5; K. |
| Corcovado — 710 | Name is not unique. Add “in Rio de Janeiro.” | Plausible. | 5 is too hard; use 3. |
| Mount Nebo — 808 | Multiple named peaks/sites at 710, 790, and about 802 m. | Reject current formulation. | 5; K. |
| Mount Gerizim — 881 | Minor summit/source variation; little basis for someone unfamiliar with it. | Plausible. | 5; K. |

### Building heights

All rows below require “architectural height” or another explicit criterion. A consistent template would be:

> What is the architectural height of [building], in metres?

| Question | Additional finding | Value/source | Difficulty and estimability |
|---|---|---|---|
| Empire State Building — 453 | Add “the,” but the larger problem is the wrong value. | Reject; use 381 m architectural or explicitly 443.2 m to tip. | 1: appropriate after repair. |
| One World Trade Center — 541.3 | Source also has 546.2 m to pinnacle and 386.5 m occupied. | 541.3 architectural is plausible. | 2: appropriate. |
| Burj Khalifa — 828 | Source also has 829.8 m to tip and 584.5 m occupied. | 828 architectural is defensible. | 1: appropriate. |
| Shanghai Tower — 632 | Conventional architectural figure. | Plausible. | 2: appropriate. |
| Willis Tower — 442.1 | Antennas produce a much greater total height. | 442.1 architectural is defensible. | 1: appropriate. |
| Mirante do Vale — 170 | Text gives an unfamiliar name and no location/image. | Plausible, but criterion absent. | 2 is far too easy; use 4. K. |
| Jin Mao Tower — 420.5 | Revision also has an unqualified 421 m value. | 420.5 architectural is defensible. | 2 may be low; use 3. |
| Shanghai World Financial Center — 492 | Source also gives 494.3 m to tip and 474 m occupied. | 492 architectural is defensible. | 2 may be low; use 3. |
| Taipei 101 — 508 | Conventional architectural figure. | Plausible. | 1: appropriate. |
| Edifício Itália — 165 | Unfamiliar outside Brazil; add “in São Paulo.” | Plausible. | 3 is low; use 4. K. |
| 30 St Mary Axe — 250 | Materially wrong. | Reject; authoritative figure is 179.8 m. | Rating irrelevant until corrected. |
| New York Times Building — 318.8 | Add “the.” Source oddly tags the figure as both architectural height and pinnacle height. | Plausible but criterion must be explained. | 4: defensible. |
| Grande Arche — 110.9 | Prefer full name/location: “the Grande Arche de la Défense in Paris.” | Plausible. | 3: appropriate. |
| Woolworth Building — 241 | Add “the”; add New York City. | Plausible. | 4: reasonable, but K without an image. |
| Oriental Pearl Tower — 468 | It is a tower rather than an ordinary building; criterion still matters. | Plausible. | 3 may be high; use 2. |
| 7 World Trade Center — 225 | Minor rounding differences exist. | Plausible. | 4; largely K. |
| Montparnasse Tower — 209 | Add Paris for context. | Plausible. | 3: appropriate. |
| Édifice Price — 82 | Unfamiliar and locationless; add Quebec City. | Plausible. | 5; K. |
| Cadillac Place — 67.1 | Unfamiliar name offers almost no estimation basis. | Plausible. | 5; K. |
| Trump Tower — 202 | There are multiple Trump Towers. Add “in New York City.” | Plausible for the NYC building. | 4 is too hard for the intended landmark; use 2–3. |
| Mode Gakuen Cocoon Tower — 204 | Rating is inexplicable without an image. Add Tokyo. | Plausible. | 1 is indefensible; use 4–5. K. |
| Freedom Tower — 78 | Catastrophically ambiguous: often used for One World Trade Center, while this record means Miami’s Freedom Tower. | Value plausible only for the Miami building. | 5; K. |
| 90 West Street — 99 | Locationless, obscure, and little estimation basis. | Plausible. | 5; K. |
| Tour Légende — 70 | Obscure and locationless. | Plausible but weakly sourced. | 4; K. |
| Torres Blancas — 81 | Add Madrid; otherwise it supplies little basis. | Plausible. | 5; K. |

### National populations

The English is natural in all 30 population prompts, but the template is under-specified. A year does not identify whether a figure is:

- a census count,
- a 1 January population,
- an end-of-year population,
- a mid-year estimate, or
- a projection.

That distinction is hidden in Wikidata qualifiers. For example, the bank mixes 1 January, 30 June, 1 July, 30 September, 31 December, census-day, and year-only figures.

A defensible template would be:

> According to [statistical authority], what was the estimated population of [country] on [date]?

Or, for censuses:

> How many usual residents did the [year] census count in [country]?

| Question | Finding | Value/source | Difficulty and estimability |
|---|---|---|---|
| Canada 2021 — 36,991,981 | Census count; well defined once “2021 Census” is stated. | Strong official source and plausible. | 2: appropriate. |
| Norway 2026 — 5,627,400 | Figure is for 1 January, not generic “in 2026.” | Official and plausible. | 2: appropriate. |
| United States 2024 — 340,110,988 | 1 July estimate, not a census. | Strong official source and plausible. | 1: appropriate. |
| Luxembourg 2026 — 690,959 | 1 January figure. | Official and plausible. | 4: appropriate. |
| Finland 2025 — 5,652,881 | 31 December figure. | Official and plausible. | 2: appropriate. |
| Italy 2023 — 58,850,717 | Exact reference date is hidden by year-only wording. | Official and plausible. | 1: appropriate. |
| Switzerland 2025 — 9,104,063 | Specifically 30 September. | Official and plausible. | 3 may be slightly high; use 2–3. |
| Austria 2022 — 8,979,894 | Exact date/method needs to be surfaced. | Official and plausible. | 3 may be high; use 2. |
| Turkey 2023 — 85,372,377 | 31 December address-based count. | Official and plausible. | 1: appropriate. |
| Egypt 2023 — 114,535,772 | Annual estimate; matched statement lacks a directly inspectable reference URL. | Plausible, but improve sourcing. | 3 may be high; use 2–3. |
| Mexico 2024 — 132,274,416 | Government projection, not an observed count. | Plausible if explicitly called a projection. | 2: appropriate. |
| France 2025 — 68,605,616 | 1 January figure. | Official and plausible. | 1: appropriate. |
| Brazil 2026 — 214,211,951 | 1 July estimate. | Plausible; official underlying source. | 3 is high; use 2. |
| Russia 2025 — 146,119,928 | Territorial scope must be stated because competing definitions are politically and statistically material. | Plausible only under the source’s territorial definition. | 1 for rough estimation; prompt needs scope. |
| Germany 2024 — 83,577,140 | 31 December figure. | Official and plausible. | 1: appropriate. |
| Albania 2023 — 2,811,655 | Conflicts with the 2023 resident census count of 2,402,113. | Reject unless explicitly framed as a particular estimate and date. | 3: reasonable only after repair. |
| Andorra 2026 — 89,752 | 30 June estimate. | Official and plausible. | 4: appropriate. |
| Malta 2023 — 553,214 | World Bank annual estimate; date/method hidden. | Plausible but not a unique “true” 2023 value. | 4: appropriate. |
| Monaco 2025 — 38,857 | Year-only qualifier. | Plausible; official underlying source. | 4: appropriate. |
| Montenegro 2022 — 617,213 | Annual demographic figure, not a census-day count. | Plausible. | 4: reasonable. |
| Australia 2025 — 27,614,411 | 30 June estimated resident population. | Strong official source and plausible. | 2: appropriate. |
| India 2020 — 1,326,093,247 | July estimate; India did not conduct a 2020 census. | Plausible estimate, weak choice of underlying CIA citation. | 2 is high; use 1. |
| Tuvalu 2022 — 10,643 | Specific census-date count. | Plausible, but matched Wikidata claim lacks a direct source URL. | 5; K for many users. |
| Federated States of Micronesia 2023 — 75,817 | Initially surprising but confirmed by the national 2023 census. | Strong official support. [FSM Statistics](https://stats.gov.fm/topics/social/population-statistics/) | 5; K. |
| Saint Kitts and Nevis 2025 — 46,922 | Projection from a commercial secondary site. | Plausible, but source quality is too weak for ground truth. | 5; K. |
| Dominica 2023 — 74,656 | CIA estimate. | Plausible, but not uniquely authoritative. | 5; K. |
| Israel 2023 — 9,840,000 | Territorial/residency scope and date need stating. Underlying link is a news article. | Plausible, but source quality and scope need improvement. | 3 may be high; use 2–3. |
| Bhutan 2023 — 787,424 | World Bank estimate. | Plausible. | 4; borderline K. |
| Brunei 2023 — 458,949 | Matched claim lacks a directly inspectable reference URL. | Plausible but weakly documented. | 5; K. |
| Cape Verde 2020 — 555,988 | World Bank estimate; the 2021 census found 491,233 residents, illustrating the method problem. | Plausible only as a named estimate. [Cape Verde census authority](https://ine.cv/censo2020/?p=5489) | 5; K. |

## Distribution

### Categories

| Category | Count | Share |
|---|---:|---:|
| Mountain elevations | 25 | 31.25% |
| Building heights | 25 | 31.25% |
| National populations | 30 | 37.5% |

The balance is numerically tidy but conceptually narrow. Eighty questions cover only three quantities, and 50 are essentially “height of a named object.” There are no distances, durations, masses, areas, speeds, depths, temperatures, ages, capacities, or counts of familiar things.

### Orders of magnitude

| Range | Count | Source of values |
|---|---:|---|
| \(10^1\): 10–99 | 6 | Buildings only |
| \(10^2\): 100–999 | 32 | 19 buildings, 13 mountains |
| \(10^3\): 1,000–9,999 | 12 | Mountains only |
| \(10^4\): 10,000–99,999 | 6 | Populations only |
| \(10^5\): 100,000–999,999 | 6 | Populations only |
| \(10^6\): 1–9.9 million | 6 | Populations only |
| \(10^7\): 10–99 million | 6 | Populations only |
| \(10^8\): 100–999 million | 5 | Populations only |
| \(10^9\): 1 billion+ | 1 | India |

The raw spread is excellent: nine orders of magnitude, \(10^1\) through \(10^9\). That suits log-relative scoring.

But the spread is visibly engineered: exactly six population questions in each of \(10^4\)–\(10^7\), then five and one in the top bands. Magnitude is almost perfectly predicted by category. The bank tests “recognise what sort of number this category has” more than flexible estimation across heterogeneous quantities.

The difficulty distribution is even more artificial: exactly 16 questions at each level, and each category contributes exactly five or six to every level. That is quota filling, not validated difficulty.

## Knowledge questions

The clearest poor-estimation questions are:

- Loreley
- Monte Cassino
- Monte Titano
- Mount Tabor
- Mount Herzl
- Mount Scopus
- Jebel Barkal
- Mount Nebo
- Mount Gerizim
- Mirante do Vale
- Édifice Price
- Cadillac Place
- Mode Gakuen Cocoon Tower
- 90 West Street
- Tour Légende
- Torres Blancas
- Tuvalu, Saint Kitts and Nevis, Dominica, Brunei, and Cape Verde populations for users unfamiliar with those countries

Their difficulty comes primarily from not knowing what or where the named thing is. The user cannot reason from the prompt. They can only recall the fact or make a generic category-level guess.

Images and brief context could rescue several building questions. “Estimate the height of this pictured 50-storey tower” is estimation; “How tall is Tour Légende?” is trivia.

## The 10 records I would cut now

“Cut” here means remove the current record. Some subjects can return later as newly sourced rewrites.

1. **Loreley** — wrong feature/value.
2. **Monte Titano** — unresolved 739/749/756 m disagreement.
3. **Mount Tabor** — value conflicts with the authoritative park source.
4. **Mount Nebo** — several defensible peaks/sites; 808 m is not adequately defined.
5. **Empire State Building** — 453 m contradicts the source it purports to derive from.
6. **30 St Mary Axe** — 250 m is simply wrong.
7. **Freedom Tower** — identity ambiguity with One World Trade Center.
8. **Albania 2023** — estimate versus census creates two materially different defensible answers.
9. **Mode Gakuen Cocoon Tower** — pure recognition trivia and absurdly rated difficulty 1.
10. **Jebel Barkal** — almost no estimation basis from the text; also invites confusion between elevation and local height.

The next cuts would be Tour Légende, 90 West Street, Cadillac Place, Mount Herzl, Mount Gerizim, and the current Cape Verde record.

## Source-quality conclusion

Revision-pinning is excellent and worth keeping. It makes the dataset reproducible. But the citation policy needs strengthening:

- Exact value present in cited Wikidata revision: **80/80**
- Matched claim containing a direct underlying reference URL:
  - Mountains: **4/25**
  - Buildings: **15/25**
  - Populations: **25/30**

Wikidata should be treated as a discovery index, not the final authority. I would only admit a question when its selected statement has:

1. a primary or authoritative source,
2. an explicit measurement criterion or population method,
3. a date where the value can change,
4. no unresolved competing statement,
5. sensible precision, and
6. enough contextual information that estimation is possible.

With those rules, roughly 60 of these subjects are salvageable, but substantially fewer than 60 of the current records are ship-ready.