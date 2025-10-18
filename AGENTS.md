# AiHandMadeApp · CODEX RULES (v1)

## 0) Mission and Scope
- Target v1: PHOTO branch to publication quality; DISCRETE marked "in development" without processing.
- Single source of truth: https://github.com/Inneren12/AiHandMadeApp (branch `main`).
- All processing on-device, deterministic and reproducible; tiled processing with overlap ≥ 2·radius; 16F inside tiles.
- Do not change module architecture or package names without an explicit request.

## 1) Responsibilities for Codex
### Must do
- Work only with the specified repo/branch.
- Propose minimal patches (surgical changes, no cosmetic refactors).
- Write compilable Kotlin/NDK code following repo conventions (no dependency or plugin upgrades unless requested).
- Preserve determinism (fixed seeds, stable operation ordering).
- Add logs and diagnostics where tracing is required.
- Cover changed logic with unit tests when feasible.

### Must not do
- Do not change Gradle/Kotlin/AGP/Compose versions.
- Do not introduce large or network dependencies.
- No network calls; everything remains local.
- No silent fallback palettes; missing catalog/palette must fail explicitly.

## 2) Response Format (mandatory)
Every Codex reply **must** contain the following blocks:
1. `PLAN` — short list of changes (what/where/why).
2. `PATCH` — unified diff blocks:
   - `=== BEGIN PATCH`
   - `PATH: <relative/path>`
   - `HASH_MATCH: <previous file sha256 or SKIP_IF_UNAVAILABLE>`
   - Standard diff body
   - `=== END PATCH`
   - One patch block per file, minimal diff, no reformatting.
3. `COMMANDS` — terminal commands for local verification (e.g., `./gradlew :app:assembleDebug`).
4. `TESTS` — new/updated tests and their coverage.
5. `VERIFY` — manual checks (logs/metrics/UX).
6. `ROLLBACK` — git commands to revert changes.
7. `NOTES` — risks, limitations, follow-ups.

## 3) Change Boundaries (LOCK)
- Work within existing structure:
  - `app/`
  - `core/{io,color,scene,prescale,preset,palette,filters,data}`
- Only touch files required for the task. No mass renames or cosmetic edits.
- Align tasks with roadmap:
  - PR-A: core/io, core/color, filters/HaloRemoval, filters/Deblocking8x8
  - PR-B: core/scene/…
  - PR-C: core/preset/… + integration
  - PR-D: core/prescale/BuildSpec
  - PR-E: core/prescale/RunFull + Verify + UI
  - PR-F: core/palette/…
  - PR-G: core/palette/{Dithering,Topology,Metrics} + UI
  - PR-H: Export/legend, UX, docs

## 4) Code Style & Dependencies
- Kotlin/NDK style consistent with repo. No version catalog.
- Dependencies only via `implementation("group:artifact:version")` when needed.
- Provide KDoc for public APIs, meaningful names, null safety, immutable defaults.
- Single logging entry point; tags such as PRE, PALETTE, DITHER, TOPO, VERIFY.

## 5) Technical Invariants
- Tiles: overlap ≥ 2·radius with Gaussian blending at seams.
- Internal tile buffers: half-float (16F).
- Determinism: fixed seeds for noise/dithering, stable operation order.
- PreScale Verify thresholds (v1):
  - SSIM ≥ 0.985
  - Edge-keep ≥ 0.98
  - ΔBand ≤ +0.003
  - ΔE95 ≤ 3.0
- Post-quant thresholds (v1):
  - Edge-F1 ≥ 0.95; GBI ≤ 0.02 (Sky/Skin)
  - Topology Cleanliness: ThreadChanges/100 ≤ 8–12, SmallIslands/1000 ≤ 5, RunMedian ≥ 4
  - Palette separation: ΔE00_min ≥ 3.0, ΔE00_med ≥ 6.0
  - Skin ΔE95 ≤ 4–5; Catalog fit Avg ≤ 2.5, Max ≤ 5.0
- Catalogs/palettes must be read from `core/data/assets`; if missing, log fatal `CATALOG_MISSING` and throw.

## 6) Algorithm Change Principles
- PHOTO branch only; DISCRETE remains disabled.
- Any "improvement" that worsens thresholds in §5 must be flagged or rejected (e.g., via `ADD_SAFE_MODE`).
- New heuristics must be tile-stable, deterministic, have toggles, and contribute to Verify metrics.

## 7) Diagnostics, Logging & Failures
### 7.1 Parameter Log Contract (mandatory)
- Log **all** influencing parameters at the start of each pipeline stage and public API.
- Format: single-line JSON via `Logger.i(TAG, "params", mapOf(...))`.
- Keys: flat `stage.param` naming (e.g., `prescale.sigma`, `build.wst`, `quant.k_max`).
- Mandatory keys include (default values logged explicitly):
  - General: `seed`, `tile.size`, `tile.overlap`, `colorspace.in`, `colorspace.out`, `icc.space`, `icc.confidence`, `hdr.mode`, `device.profile`, `threads`, `neon.enabled`, `gpu.enabled`.
  - PresetGate: `preset.id`, `addons`, `k_sigma`, `edge_mult`, `flat_mult`, `verify.thresholds` (ssim, edge, band, de95).
  - BuildSpec: `fabric.ct`, `build.wst`, `build.hst`, `phase.lock`, `filter.kernel`, `fr.adjust`.
  - RunFull: `nrY.radius`, `nrC.radius`, `anti_sand.enabled`, `skin.unify`, `sky.unify`, `halo.remove`, `sharp.edge`, `post.dering`, `post.clahe`.
  - Palette/Quant: `quant.k_start`, `quant.k_max`, `roi.quota.edges/skin/sky/hitex/flat`, `spread.de00_min`, `kneedle.tau`, `photoScore.lambda1`, `photoScore.lambda2`, `catalog.id`.
  - Dithering: `dither.mode`, `dither.amp.sky`, `dither.amp.skin`, `dither.amp.hitex`, `diffusion.cap`, `edge_aware.mode`, `blue_noise.seed`.
  - Topology: `topology.minrun.skin`, `topology.minrun.sky`, `topology.minrun.edge`, `topology.island_kill`, `topology.potts_lambda`.
- No omissions: log defaults when applicable; omit only heavy data (use summaries like coverage, hashes).
- Event flow:
  - `Logger.i(TAG, "params", {...})` — before execution.
  - `Logger.i(TAG, "done", {"ms":…, "memMB":…})` — after completion.
  - `Logger.i(TAG, "verify", {...})` — for metrics/threshold reports.
- Fail fast: missing resources → `Logger.e(TAG, "fatal", {"code": "..."})` + explicit exception. No silent fallbacks.
- Recommended tags per stage: IO, PRE, PGATE, BSPEC, RUN, QUANT, DITHER, TOPO, VERIFY, EXPORT.

## 8) Testing & Acceptance
- Minimum unit tests: OKLab/ΔE00 accuracy, σ(r) determinism, phase-lock 2×2, preset gating, palette growth (Kneedle), catalog mapping, dithering masks, topology.
- Integration: full pipeline on "golden" image set without threshold regressions (§5).
- Commands: `./gradlew :app:assembleDebug`, `./gradlew test`.
- Add Robolectric/instrumented tests only if already used.

## 9) Roadmap Recap
- PR-A: core/io, core/color, filters/HaloRemoval, filters/Deblocking8x8.
- PR-B: core/scene/Analyze (masks, metrics) + UI detection.
- PR-C: core/preset (PresetGateFOTO + library) + integration.
- PR-D: prescale/BuildSpec (σ(r), phase-lock 2×2, FR-check).
- PR-E: prescale/RunFull (tiling, Verify) + UI report.
- PR-F: palette (Greedy+, ROI, 2-opt, spread, penalties, Kneedle, CatalogMapper).
- PR-G: palette (Dithering, Topology, Metrics) + UI report.
- PR-H: Export/legend, UX, docs, release build.

## 10) Forbidden Mistakes
- No silent fallbacks to mini-palettes.
- No duplicate logging systems.
- No unresolved references left behind (e.g., `Result`, `grid`, `parseHex`).
- No mass reformatting (keeps `HASH_MATCH` diff reviewable).

## 11) UX Constraints (v1)
- UI covers PHOTO pipeline: Import → Analyze → Preset → BuildSpec → RunFull → Verify.
- DISCRETE path remains disabled; "Process as PHOTO" override only.
- Hidden toggles (blends, v2 features) remain OFF.

## 12) Commits & PRs
- Branch naming: `feat/...`, `fix/...`, `perf/...`, `docs/...`, `chore/...`.
- Commit message: `type(scope): summary` + body describing what/why.
- PR description must follow format in §2 and include checklist for §5 thresholds.

## Pinned Cheat Sheet
- PHOTO only. No hidden fallback palettes. Fail fast.
- Responses: `PLAN → PATCH → COMMANDS → TESTS → VERIFY → ROLLBACK → NOTES`.
- Determinism everywhere: seeds, order, 16F, overlap ≥ 2·r.
- Thresholds in §5 are sacred; regressions require a flag or rollback.
- Dependencies: use direct coordinates, no `libs.*`.
- All parameters must be logged at stage entry (no omissions).

---

# CODEX WORK ORDER — PR-A (Foundation)
- **Repo**: https://github.com/Inneren12/AiHandMadeApp (`main`)
- **Scope**: PHOTO pipeline, deterministic, no network, no hidden fallbacks.

## 1) Objective
Build deterministic input stack: decoding + ICC/HDR → linear sRGB 16F, unified logger (JSON, 100% parameters), HaloRemoval and Deblocking8x8 filters, with public APIs and unit tests.

## 2) Required Files & APIs
Create/update:
- `core/logging/Logger.kt`
- `core/io/Decoder.kt`
- `core/color/ColorMgmt.kt`
- `core/color/HdrTonemap.kt`
- `core/filters/HaloRemoval.kt`
- `core/filters/Deblocking8x8.kt`
- `core/data/blue_noise_64x64.bin`

Data classes & enums:
```kotlin
data class LinearImageF16(val width: Int, val height: Int, val planes: Int, val data: ShortArray)
data class U8Mask(val width: Int, val height: Int, val data: ByteArray)
data class ColorMeta(val iccSpace: String, val iccConfidence: Float, val hdrMode: HdrMode)

enum class HdrMode { HDR_OFF, HLG, PQ, GAINMAP }
```

Public functions:
```kotlin
fun Decoder.decode(source: ImageSource, hdrMode: HdrMode = HdrMode.HDR_OFF): Pair<LinearImageF16, ColorMeta>
fun ColorMgmt.rgbToOKLab(img: LinearImageF16): FloatArray
fun ColorMgmt.okLabToRgb(lab: FloatArray, width: Int, height: Int): LinearImageF16
fun ColorMgmt.deltaE00(labA: FloatArray, labB: FloatArray): Double
fun HdrTonemap.apply(img: LinearImageF16, mode: HdrMode, gainMap: Any? = null): LinearImageF16
fun HaloRemoval.apply(img: LinearImageF16, edgeMask: U8Mask? = null, strength: Float = 0.8f): LinearImageF16
fun Deblocking8x8.apply(img: LinearImageF16, strength: Float = 0.35f): LinearImageF16
```
- Use IEEE754 half (`ShortArray`) storage; provide inline converters half↔float; no Float32 replacement.

## 3) Logging Contract
- Log all parameters at entry via JSON line (`Logger.i(tag, "params", mapOf(...))`).
- Log completion with `"done"` (duration ms, memory MB) and metrics with `"verify"`.
- Tags: IO, COLOR, HDR, FILTERS.
- Key coverage (defaults included):
  - General: `seed`, `tile.overlap`, `colorspace.in`, `colorspace.out="SRGB_LINEAR"`, `icc.space`, `icc.confidence`, `hdr.mode`, `threads`, `neon.enabled`, `gpu.enabled`.
  - HDR: `hdr.mode`, `gainmap.present`, `tonemap.curve`.
  - Halo: `halo.strength`, `edgeMask.present`.
  - Deblocking: `deblock.strength`, `jpeg.blockiness`.
- No omitted keys; large data summarized (coverage, hashes).

## 4) Fail-Fast Errors
- Unsupported ICC → `Logger.e("IO","fatal",{"code":"ICC_UNSUPPORTED"})` + exception.
- Unsupported HDR mode → `HDR_UNSUPPORTED`.
- No silent fallback to sRGB/palettes.

## 5) Mandatory Tests
Add unit tests under `core/src/test/...`:
- `ColorMgmtTest.kt`: round-trip ΔE95 ≤ 0.5; ΔE00 regression cases.
- `HdrTonemapTest.kt`: monotonicity & relative luminance preservation; `GAINMAP` path guarded (temporary `GAINMAP_OFF`).
- `HaloRemovalTest.kt`: synthetic halo reduced ≥ 30%.
- `Deblocking8x8Test.kt`: synthetic blockiness reduced ≥ 25%.
- `DecoderTest.kt`: EXIF rotation, ICC detection, logging keys.

## 6) Definition of Done
- `./gradlew :app:assembleDebug` and `./gradlew test` pass.
- Public APIs implemented; every input parameter logged per §3.
- No network deps; no hidden fallbacks.
- Public APIs documented with KDoc.

## 7) Constraints
- Touch only specified files. No tooling version changes. No version catalog.
- Minimal diff, preserve style, maintain determinism (fixed seeds, stable ordering).

## 8) Required Response Format
Codex must reply with `PLAN`, `PATCH`, `COMMANDS`, `TESTS`, `VERIFY`, `ROLLBACK`, `NOTES` blocks exactly.
