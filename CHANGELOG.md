# Changelog

## 1.4.0-rc1 — Soul Recognition Update

### Added

- Added the complete Soul Recognition system with Lawful/Neutral/Chaotic and
  Good/Neutral/Evil axes.
- Added nine recognition paths, combined outcomes, and stricter Pure outcomes.
- Added curated path-specific title pools and frozen display identities.
- Added the unified Soul Recognition progress screen with Overview, Paths,
  Benefits, and guidance.
- Added datapack-driven entity classification, civilians, civilian-hostile
  mobs, bosses, advancement evidence, structures, and supporting data.
- Added civilian-defense, aggression, kill attribution, exploration, mastery,
  subordinate, authority, alignment, TH, and TDL recognition evidence.
- Added a permanent alignment-neutral strength reward based on frozen Identity
  Strength: 10–20%, plus 2.5% for Pure recognition.
- Added an effort-scaled 0–1,000,000 EP extension to native HIGH endowment
  capacity, split between maximum magicules and aura.
- Added Character Reset lifecycle integration and persistent incarnation state.
- Added bounded native-endowment retry state and legacy save migration support.
- Added confirmed permission-level-4 migration/retry fixtures for controlled
  legacy, future-version, reload, exactly-once, and Character Reset testing.
- Added deterministic attribution validation and a confirmed Character Reset
  fixture for life-bound recognition and runtime combat-credit isolation.
- Added release validation for player-facing translations, Gradle portability,
  repository wrapper setup, metadata, resources, and the final RC1 surface.

### Changed

- Lowered the default Sage to Great Sage altar requirement from 100,000 EP to
  60,000 EP while leaving `/getnamed` unchanged at 200,000 EP. Existing
  default configurations migrate automatically; custom EP values are kept.
- The Great Crystal Altar now resolves Sage to Great Sage before beginning a
  new Soul Recognition ritual. An already-committed interrupted recognition
  reveal still resumes first so its frozen result cannot be stranded.
- Recognition commits through one authoritative transaction before the visual
  reveal, preventing rerolls after interruptions.
- Native Tensura naming no longer blocks Soul Recognition. A natively named but
  addon-unrecognized player can still complete the altar ritual.
- Existing native names are anchored without sending a second HIGH naming
  request.
- Tensura's native 900% endowment calculation and 1,000,000 EP cap remain
  unchanged; the effort reward is implemented through two stable additive
  energy-capacity modifiers.
- Current magicules and aura receive only newly added capacity. Repeated
  synchronization does not refill spent energy.
- `/getnamed` is retained only at administrator permission level 2, remains
  native-only, and cannot fabricate Soul Recognition or its rewards.
- The public command surface is consolidated around `/moostensura`,
  `/moostensura guide`, `/moostensura paths`, and `/moostensura help`.
- Character Reset now clears all life-bound addon progression and starts a new
  incarnation, while ordinary death, travel, and relog preserve the current
  life.

### Fixed

- Fixed the live altar path bypassing the authoritative reward-freezing
  service.
- Fixed native-named players being able to view recognition progress but being
  routed past Soul Recognition at the altar.
- Fixed the possibility of a duplicate native HIGH naming request during a
  recognition reveal.
- Fixed a completed reveal leaving the recognition title hidden in chat,
  player nametags, and the tab list until a later lifecycle refresh.
- Fixed fresh Soul Recognition sending only the account name to Tensura's
  native menu instead of the complete frozen recognition display name.
- Fixed lifecycle reconciliation publishing the frozen Tensura name while an
  interrupted Soul Recognition reveal was still pending.
- Fixed native-name repair leaving Minecraft's player custom name at the old
  `/getnamed` value after the complete recognition name was published.
- Fixed administrator unname leaving stale frozen-result metadata and the old
  native-endowment marker behind.
- Fixed administrator unname pointing to the removed `/checkrecognition`
  command instead of `/moostensura debug recognition`.
- Fixed recognition Overview paging wording for multi-row viewports.
- Hardened Character Reset confirmation so an early scroll release does not
  clear addon data.
- Hardened committed-result persistence, display-name synchronization, reward
  reconciliation, and native-endowment recovery across lifecycle events.
- Fixed unknown future recognition schema, result, rules, and reward versions
  being diagnosable but still writable through ordinary progression hooks.
- Fixed the artificial raw-v1 migration fixture being upgraded by live
  synchronization before its first save/reload cycle.
- Fixed environmental deaths losing recent direct, projectile, tame, or
  Tensura-subordinate player attribution.
- Added bounded recent-combat credit, explicit duplicate-death suppression,
  and lifecycle cleanup for subordinate participation caches.
- Consolidated the canonical guide, Great Sage guide, Soul Recognition screen,
  and recognition-benefit notice translations into the addon's real asset
  namespace instead of relying on a separate guidance namespace.
- Removed developer-machine Java paths from tracked Gradle configuration and
  restored the Gradle wrapper JAR to clean-checkout and CI packaging.
- Updated stale NeoForge mod metadata that still described retired survival
  commands instead of Soul Recognition and altar-based progression.
- Clarified that the altar's level-50 eligibility message belongs to Soul
  Recognition, while Sage to Great Sage keeps its separate level-45 gate.

### Release Notes

- Network protocol remains `11`.
- Runtime/mod metadata is `1.4.0-rc1`, the first release candidate for the
  Soul Recognition Update.

## 1.3.1

- Fixed Granter's Choose Skill list sorting.
- Grouped grantable skills as Unique, Extra, Basic, and Resistances.
- Improved skill category detection and named/endowed consistency.

## 1.3.0 — Explorer's Update

- Added naturally generating temperate, hot, cold, and cave Great Crystal
  Shrines.
- Added the Great Crystal Altar and shrine-based Sage to Great Sage ritual.
- Added the Soul Resonator, animated needle behavior, recipe, requirement, and
  advancement.
- Added ritual stasis, collapsed pose, Darkness, soul particles, sounds, and
  Sage narration.
- Moved normal Great Sage progression from commands into exploration and
  ritual gameplay.

## 1.2.0

- Added Granter, mastered-skill granting, selection GUI, take-back, subordinate
  skill inspection, and Ultimate Skill evolution paths.
- Added Benevolent Empowerment and Absolute Governance.

## 1.1.0

- Added the original singleplayer self-endowment compatibility command.
