---
status: accepted
---

# A language's extracts are built once, then each pair is written independently

The pipeline runs one streaming pass per pair over the same source. With a matrix
that is ninety passes over files totalling about 13 GB uncompressed, inside one
job whose failure loses every artefact.

**Two stages.** Stage one runs once per **Reading language**: it downloads that
language's edition, streams it once, and writes the language's form index
(ADR-0045) beside one content file per **Translation language** — the words,
senses and translations the pack will hold. Stage two runs once per pair: it reads
one content file and the shared index and writes the **Dictionary pack**, its
NOTICE and its manifest. A third stage writes each language's **Difficulty table**
from the shared index and its frequency list, and a final stage assembles the
**Language manifest** and publishes.

**Why not one job per pair from the source.** Each pair would re-download and
re-parse its edition: ninety downloads (about 40 GB per run), the same 2.9 GiB
English file fetched nine times in parallel from kaikki.org, and ten times the
parsing for the same result.

**Why not one job per language throughout.** It is cheaper still, but a failure in
one pair loses its eight siblings, and a pair added later rebuilds them. Stage two
exists to make one pair independently retryable and independently publishable.

**The intermediate is a workflow artefact, not a published asset.** The stages of
one run hand files to each other through the run's own artefacts, which never
reach a release and cost nothing on a public repository. Publishing the
intermediate becomes worthwhile only when stage two must run alone, later; that is
a two-way door and nothing here forecloses it.

**Wall clock stops tracking the matrix.** The stages are job matrices, so a run
takes as long as its slowest job — around ten minutes for ten languages — rather
than the sum of its pairs.

Rejected: caching the edition between runs, since GitHub evicts a cache entry
after seven days and the schedule is monthly; one process holding every language
in memory; committing the content files, which would put CC BY-SA data in the
source repository (ADR-0011).

**Consequences**: the CLI gains an extraction mode and a per-pair mode, both
deterministic; the workflow gains four job layers and a matrix; the run's cost
grows with the matrix while its duration does not; the intermediate's retention is
the workflow artefact's own.
