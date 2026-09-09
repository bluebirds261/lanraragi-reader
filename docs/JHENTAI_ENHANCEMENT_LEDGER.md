# JHenTai Enhancement Implementation Ledger

This ledger records implementation evidence for
`JHENTAI_ENHANCEMENT_ROADMAP.md`. The repository is the implementation
authority; roadmap bullets are not treated as completion evidence.

## 2026-09-08 Baseline

- Base commit: `994e9c4`.
- The working tree contains the in-progress roadmap integration and is
  intentionally not reset or split by destructive Git operations.
- LANraragi API authority: local `0.9.81` OpenAPI and matching server source.

| ID | Status | Evidence | Remaining acceptance |
| --- | --- | --- | --- |
| A01 | Delivered | `ArchiveIdentity`, capability policy, and JVM tests | None for current scope |
| A02 | Delivered | Room v1, unified four-source JSON bootstrap, atomic markers, rollback JSON mirrors, JVM and PHK110 Room tests | Upgrade from a production APK remains a release test |
| H01 | Partial | Full Room history plus recent projection and legacy mirror | Mixed remote/local long-run device validation |
| R02 | Partial | Four `PageSource` implementations and resolver tests | `ReaderScreen` still uses legacy source state and image models |
| C01 | Delivered | Thumbnail 200/202/Minion coordinator, bounded polling, single-flight, retry, stale-result protection | Real LANraragi missing-thumbnail timing test |
| C02 | Delivered | Typed cover identities, server-isolated revision keys, remote UI integration | Local archive/folder cover derivation belongs to L03 |
| L01 | Partial | Room local index/import and scanner integration with JSON rollback projection | Large SAF library and revoked-permission device matrix |
| M01 | Delivered | Canonical tag identity plus namespace registry and registry-backed UI/search adapters; 98 JVM tests and PHK110 device checks pass | None for current scope |
| M02 | Partial | Pure metadata patch merger and provenance fixtures | Persistence, conflict workbench, and write-back orchestration |

## Current Round: M01

- State owner: immutable `TagNamespaceRegistry`; `CanonicalTag` owns semantic
  identity independently of raw spelling, translation, source, and confidence.
- UI compatibility: `TagRules` delegates labels, colors, aliases, ordering, and
  hidden presentation policy to the registry.
- Hidden presentation namespaces: `source` and `timestamp`. They remain in raw
  metadata and are available through explicit `includeHidden` grouping.
- Rollback: revert registry consumer wiring while retaining the additive pure
  model. No Room schema, server API, or stored tag data changes are involved.
- Acceptance: 20 JVM suites / 98 tests, 2 PHK110 instrumentation tests, debug
  APK assembly, process startup, and crash-buffer smoke all passed.

## 2026-09-09 M-final development pass (pre-build)

- Planning gate: `gpt-5.6-sol/high` reviewed the dirty worktree against the
  roadmap and local LANraragi 0.9.81 OpenAPI/server source. The selected
  dependency-closed slice was Reader/Library wiring, local derived-cache
  consumption, orientation/input settings, diagnostics context, and evidence
  closure. No Gradle command has been run in this pass by user instruction.
- Terra write sets: Reader input and preferences (`ReaderInput.kt`, Reader and
  settings boundaries); local derived cache (`ArchiveFileReader`, local cache,
  Coil fetcher); adaptive Library call-site; bounded diagnostics producers and
  frame sampler. Shared integration and acceptance remain owned by the main
  agent.
- Newly implemented but still `Partial` until the integrated build and device
  matrix: U01 tablet master-detail call-site with stable selection; L03 local
  page/cover derived cache with revision keys, atomic writes, LRU and leases;
  I7 unified `ReaderAction` for volume/direction/PageUp/PageDown; independent
  portrait/landscape reader mode/direction/fit overrides with global fallback;
  J2 thumbnail hit-rate and bounded dropped-frame sampling; diagnostics export
  context (device/server hash/task/cache/schema summaries).
- Evidence additions: exact E-H archive-link pagination/host-confusion tests;
  metadata canonical-tag persistence/restore/raw-tag round-trip test; derived
  cache lease test; Reader frame sampler tests. They are intentionally not
  marked delivered before the single integrated verification run.
- Main-owned integration risks to inspect before build: Compose/import/braces
  in the large Library and Settings screens; Coil `ImageSource` API shape;
  Room schema export and migration registration; local index page-count
  parsing on SAF providers; orientation profile updates during rotation.
- Rollback boundaries: U01 can fall back to the existing phone single-pane
  master; derived artifacts are disposable and can fall back to source-copy /
  Coil reads; orientation keys fall back to legacy global keys; diagnostics
  context is optional; E-H/metadata additions are test-only.

### Final development closure before central acceptance

- Reader: production `ReaderRouteEffects`, `ReaderSurface`, `ReaderControls`
  and `ReaderSheets` boundaries are active; `ReaderSession/PageSource` owns
  source/revision/page state; Coil prefetch requests are bounded by the
  configured local/remote radius and disposed on window/session changes.
  Page, screen, spread and first-cover mapping share zero-based
  `ReaderPageMapping`; all physical keys pass through `ReaderAction`.
- Saved resources: server scope plus archive id produces one canonical catalog
  key. Offline caching reuses an existing saved archive, explicit downloads
  reuse the private offline artifact, Reader owns a lease, and the Download UI
  owns pin/unpin. Offline LRU only considers current offline-index keys.
- Native metadata: exact E-H/ExH gid+token and nH id candidates can explicitly
  fetch full metadata through a credential-capable, throttled gateway. The
  external client never receives the LANraragi bearer key. Remote and local SAF
  results both enter the same review/apply pipeline; local targets remain Room
  only and cannot issue a LANraragi metadata PUT.
- Settings: four typed preference projections coexist with the legacy aggregate
  for compatibility. Cache limits persist as overflow-safe bytes with legacy GB
  fallback, MB/GB input, disk-capacity validation and explicit zero=no automatic
  eviction semantics. Global/portrait/landscape mode, direction, fit and
  first-cover behavior have independent overrides and a clear-to-global action.
  Non-sensitive config import now previews changed fields before one atomic
  DataStore edit.
- Central acceptance is now authorized. All above items remain `Partial` until
  the JVM, APK and connected-device commands below pass.
