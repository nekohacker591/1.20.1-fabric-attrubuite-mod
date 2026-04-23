# Universal Attributes (Fabric 1.20.1)

A Fabric mod that creates a large config for globally applying permanent player attributes, status effects, and damage resistances/immunities.

## Features
- Discovers **all registered attributes** (including modded namespace IDs) and writes them into config.
- Discovers **all registered status effects** (including modded effects) and writes them into config.
- Permanent player attribute overrides that persist on join and respawn.
- Permanent status effects with configurable amplifier, hidden particles, and icon visibility.
- Damage resistance values (0.0 - 1.0) and optional immunity toggle per configured damage type.
- Max health changes are applied as a base attribute, then player health is set to max so hearts are full immediately.
- Mod Menu + Cloth Config integration for a searchable config UI.
- `/universalattributes search <query>` command as a fast in-game search utility.

## Windows setup
Run `setup.bat` once (or run `gradlew.bat` directly). The wrapper JAR will auto-download if missing.

## Config file
`config/universalattributes.json`

Important behavior:
- The mod only edits attributes/effects that are explicitly `enabled`.
- Unselected attributes are untouched.

## Commands
- `/universalattributes reload` — reload config and reapply rules to online players.
- `/universalattributes search <query>` — search attributes/effects/damage keys by ID or display name.
- `/universalattributes set attribute <id> <value>` — set and enable an attribute quickly.
- `/universalattributes set effect <id> <level>` — set and enable a permanent effect level.
- `/universalattributes set damage_resistance <id> <0.0-1.0>` — set resistance values.
- `/universalattributes set internal_number <id> <value>` / `internal_boolean <id> <true|false>` — edit internal experimental keys.
- `/universalattributes enable|disable <type> <id>` — toggles state (attribute/effect/damage_immunity/internal).
- `/universalattributes list <attributes|effects|damage|internals>` — prints known keys.
