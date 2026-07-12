# Changelog

## 1.3.1

Small polish update for the Explorer’s Update.

### Changes

* Fixed Granter’s Choose Skill list sorting.
* Grantable skills are now grouped correctly as:

    * Unique
    * Extra
    * Basic
    * Resistances
* Improved skill category detection so Tensura skills like Magic Sense are placed in the correct category.
* Improved named/endowed recognition consistency across progression checks.
* Minor release polish and internal cleanup.

## 1.3.0 - Explorer’s Update

Version 1.3.0 expands the singleplayer progression path with exploration, shrine discovery, and a full ritual route for evolving Sage into Great Sage.

### Added

* Added naturally generating Great Crystal Shrines.
* Added temperate, hot, cold, and cave shrine variants.
* Added cave shrine deepslate blending.
* Added the Great Crystal Altar.
* Added shrine-based Sage to Great Sage progression.
* Added a 15-second Great Sage ritual.
* Added ritual stasis, sleeping/collapsed pose, Darkness, soul particles, sounds, and Sage narration.
* Added the Soul Resonator.
* Added Soul Resonator animated needle behavior.
* Added Soul Resonator recipe.
* Added Soul Resonator Sage/Great Sage requirement.
* Added Soul Resonator advancement: Resonance of the Soul.
* Added `/moostensura guide sage`.
* Added improved player-facing progression guidance.

### Changed

* `/upgradesage` is now admin-only.
* Great Sage progression is now intended to happen through shrines instead of commands.
* Great Sage ritual completion now uses shared Java logic instead of internally running a command.
* Improved named/endowed recognition consistency.
* Updated guide/help command output.
* Improved release polish and config defaults.

### Fixed

* Fixed Soul Resonator needle direction.
* Fixed Soul Resonator working after Sage evolves into Great Sage.
* Fixed Great Sage ritual progression feeling too command-like.
* Fixed several Great Crystal Shrine worldgen and structure placement issues during development.

## 1.2.0

Major Granter progression update.

### Added

* Added Unique Skill: Granter.
* Added skill granting to subordinates.
* Added skill selection GUI.
* Added take-back functionality.
* Added subordinate skill listing.
* Added mastery-based progression support.
* Added paths toward Ultimate Skill evolutions.

### Added Ultimate Skill Paths

* Benevolent Empowerment
* Absolute Governance

## 1.1.0

Singleplayer naming/endowment update.

### Added

* Added `/getnamed`.
* Added singleplayer self-endowment progression.
* Added `/checknamed` debug command.
* Added basic named/endowed progression checks.
