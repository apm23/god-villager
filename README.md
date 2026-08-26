# God Villager

Optimized continuation of `god-villagers-0.1.0-alpha.73+mc26.2` for Minecraft Java 26.2 / Fabric.

## alpha.74 goals

- Preserve alpha.73 villager trades, custom books, spawn eggs, Stormcall and God Skeleton Horse behavior.
- Remove the God Horse command-per-tick selector loop.
- Replace Stormcall's global END_SERVER_TICK player scan with an AnvilMenu hook.
- Keep fluid physics scoped strictly to Skeleton Horses tagged `godvillagers_god_horse`.
- Remove old stub/class build debris from release jars.

The alpha.73 jar is stored as a base64 baseline so unreconstructed legacy classes/assets can be carried forward while hot-path runtime code is replaced cleanly.
