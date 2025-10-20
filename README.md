# AiHandMadeApp (PHOTO v1)

**Stack**: Gradle 8.13, JDK 17, Android SDK 34.  
**Modules**: `app/` (UI), `core/` (pipeline).

## Build
```bash
gradle :core:test --console=plain
gradle :app:assembleDebug --console=plain
```
> Wrapper не хранится — используйте системный Gradle.

## PHOTO Pipeline (v1)
Import → Analyze → PresetGateFOTO → BuildSpec → RunFull → (Palette) → Dither → Topology → Legend → Export(PDF).

## Logs
JSON-события по стадиям: `SCENE/`, `PGATE/`, `BSPEC/`, `RUN/`, `VERIFY/`, `PALETTE/`, `DITHER/`, `TOPO/`, `EXPORT/`.

## Known limitations
- DISCRETE-ветка “in progress”.
- ΔE2000 упрощён (евклид в OKLab) — годится для v1 тестов, уточним в v2.
