# SOMA Key/Index mutation architecture handoff

Status: `CANDIDATE_A_FROZEN / CANDIDATE_B_PRODUCT_OWNER_APPROVED / S0_BASELINE_READY / NOT IMPLEMENTED / NOT QUALIFIED`

Initial date: 2026-08-10

Last update: 2026-08-11

Baseline: `develop@3049bd4`

S0 executable checkpoint: `develop@a0f1cd9`

## 1. Current decision boundary

The Product Owner froze the previously approved delayed-maintenance proposal on 2026-08-11. It is
preserved as
[`CANDIDATE-A-DELAYED-STRUCTURAL-MAINTENANCE.md`](CANDIDATE-A-DELAYED-STRUCTURAL-MAINTENANCE.md)
and no longer authorizes implementation.

The only active proposal is now [`DESIGN.md`](DESIGN.md), candidate B: a 32-bit structural domain,
64-bit cumulative domain, Schema-defined application numeric domain, immediate Key/Index membership,
a custom typed primitive Hash directory, singleton-inline/ordered `int[]` locator Buckets, raw
locators and dense packed remove. The Product Owner approved this candidate and its sequential
implementation route on 2026-08-11.

No candidate-B implementation or qualification has started. S0 has isolated the separately owned
scheduling refactor, removed the unfinished accelerator experiment and restored the reproducible
linked-posting checkpoint. That checkpoint remains predecessor evidence for S1/S2; it is not
candidate-B implementation evidence.

No benchmark or solver process remains active.

## 2. Last verified implementation checkpoint

Before the unfinished adaptive-accelerator experiment began, the following implementation was
complete and verified in the worktree:

- point add retained its existing incremental Key/Index append path;
- indexed point update incrementally unlinked and inserted only affected postings;
- point remove incrementally maintained the Key and every secondary Index while preserving dense
  tail-to-hole compaction;
- non-unique posting predecessor search was confined to the affected bucket;
- physical postings remained in canonical locator order;
- hash deletion used tombstones, and add/update performed shard-local cleanup or growth rehash;
- ordinary point update/remove no longer fell back to whole-sidecar rebuild;
- plain-Chunk indexed point update used the Design-authorized prevalidated in-place final commit,
  avoiding a complete Chunk copy.

Evidence at that checkpoint:

- clean Java 8 runtime compile: `PASS`;
- complete `soma-runtime` tests: `63 tests, 0 failures/errors`;
- randomized 600-step add/update/remove state-machine comparison: `PASS`;
- forced same-shard growth and tombstone-reuse fixture: `PASS`;
- Key, two secondary Indexes, retained accounting and reference/optimized canonical locators were
  checked after mixed mutation;
- fault injection proved ordinary indexed point update and point remove do not invoke the old
  full rebuild hooks and publish zero change before incremental commit.
- repository `./scripts/check.sh`: `PASS`, including processor, stable examples, benchmark compile,
  local package, SBOM/provenance and independent source consumer.

## 3. Measured effect

The standard 100,000-operation FJSP journey completed for the first time after incremental
sidecar maintenance:

| Runtime state | Result | Wall time |
|---|---|---:|
| predecessor implementation, three terminated attempts | no completed result | 148.53 s, 154.55 s, 419.26 s |
| incremental Key/Index maintenance | `PASS` | 55.73 s |
| plus plain-Chunk in-place indexed update | `PASS` | 44.01 s |
| second profile run | `PASS` | 45.11 s |

These values are dirty-worktree development evidence, not a formal benchmark claim.

The second bounded async-profiler CPU run contained 4,819 samples. Inclusive attribution showed:

- `GeneratedTable.update`: 4,314 samples;
- `IdentityHashIndex.prepareUpdate`: 3,630 samples;
- `GeneratedTableLayout.hashField`: 2,010 samples;
- affected-bucket predecessor lookup: 730 samples;
- encoded/overlay candidate update remained visible;
- plain Chunk copying fell to zero samples after the in-place path.

The hotspot was overwhelmingly `OperationStateTable.update`, especially large low-cardinality
`status` / `assignedMachineId` posting buckets. Profiles are temporary machine evidence at:

- `/tmp/soma-incremental-mutation-cpu.jfr` and matching `.collapsed`;
- `/tmp/soma-incremental-mutation-cpu-v2.jfr` and matching `.collapsed`.

## 4. Retired experiment after the verified checkpoint

Profiling satisfied the previously recorded condition for considering a bucket-local accelerator.
An adaptive locator-bitmap experiment was started, but the Product Owner paused the topic before
it was completed or tested. S0 removed this experiment from the active worktree.

The retired experiment had contained:

- new `soma-runtime/.../LocatorBitmap.java`;
- `PagedLongLinks.locatorCapacity()`;
- partial `IdentityHashIndex` wiring for an accelerator threshold, accelerator accounting,
  PreparedAdd maintenance and per-Shard accelerator references;
- Shard retained-byte estimation was provisionally expanded for accelerator references.

It never completed `prepareUpdate`, destination insertion, remove maintenance, exact retained
accounting, failure proof, tests or performance validation. It is historical handoff evidence only
and must not be restored into candidate B.

## 5. Frozen candidate A boundary

Candidate A previously proposed:

- tagged locator flags and sparse locator state;
- stale memberships with clean/dirty Bucket paths;
- a structural-debt threshold and synchronous cleanup;
- one canonical posting container with delayed invalidation.

Those decisions are historical candidate material only. Candidate B explicitly removes all four.
Do not cherry-pick individual delayed-state mechanisms into candidate B without a new Product Owner
decision.

## 6. Active candidate B summary

- Table size/capacity/locator and other Table-local addressable structures use checked `int` with
  `Integer.MAX_VALUE` as the maximum Table size and `-1` as the missing locator;
- Stream/Relation/Group cardinality, stateVersion, memory bytes and cumulative arithmetic remain
  `long`; application Field types remain Schema-defined;
- structural capability overflow uses `RESOURCE_LIMIT_EXCEEDED`; valid cumulative arithmetic
  overflow uses `ARITHMETIC_OVERFLOW`;
- Key is an immediately maintained typed unique Hash map to one raw `int` locator;
- each secondary Index is an immediately maintained typed directory to one ordered locator Bucket;
- singleton locator is inline; multi Bucket uses one ascending `int[]`;
- linear scan and `System.arraycopy` are the first internal baseline, not a long-term Design contract;
- point add/update/remove update only affected directories/Buckets and use prevalidated
  non-throwing final commit;
- packed remove relocates tail `T -> R` across payload, Key and every Index in one generation;
- Selection mutation builds one complete candidate sidecar and root-swaps once;
- there is no delayed logical membership, locator flag, dirty query, debt threshold or cleanup hook;
- fastutil, alternate containers, future acceleration SPIs and all other new production dependencies
  remain unadmitted.

## 7. Scheduling boundary

The scheduling reference refactor is outside this governance topic. Its source and handoff were
preserved in a recoverable local Git stash, and the active checkout was restored to the repository
baseline. Current implementation and qualification must not depend on completing or changing the
scheduling example; that refactor resumes only as a later, separately governed topic.

## 8. Next-governance protocol

1. Use candidate B as the only implementation Design input;
2. keep the scheduling refactor outside the checkout and do not restore the discarded bitmap
   experiment;
3. retain `a0f1cd9` as the reproducible linked-posting predecessor baseline;
4. execute S1 as one bounded slice: migrate the complete structural domain to checked `int` while
   retaining the linked-posting algorithm, then close compile/test/signature/full qualification;
5. only after S1 closes, execute S2 as the next bounded slice: replace linked postings with the
   singleton-inline/ascending-`int[]` Index and close its independent evidence;
6. complete Selection/read-path integration, correctness, atomicity, accounting, clean-family
   non-regression and focused mutation-profile qualification;
7. promote the approved numeric/Index deltas to the formal Owners only after executable evidence
   closes, then record Conformance and retire this Temporary;
8. leave scheduling frozen until this mutation governance topic passes and the Product Owner opens
   its separate follow-up topic.
