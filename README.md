# LightGuard V3

### Packet-level anti-crash and exploit protection for Paper 1.21.4

LightGuard inspects inbound traffic on the **Netty pipeline**, before packets reach the Bukkit API,
so malformed and abusive packets are dropped without costing main-thread time.

> **Platform:** Paper **1.21.4** only. LightGuard binds directly to Mojang-mapped NMS and to the
> unversioned `org.bukkit.craftbukkit` package introduced in Paper 1.20.5. It will not load on
> Spigot, and will not load on other Minecraft versions.

---

## Building

Requires **JDK 21**.

```sh
./gradlew build
```

The jar lands in `build/libs/`. The build uses
[paperweight-userdev](https://github.com/PaperMC/paperweight) 2.x, which ships **Mojang-mapped**
output — hence `paperweight-mappings-namespace: mojang` in `plugin.yml`. There is no reobfuscation
step; Paper 1.20.5+ runs Mojang-mapped natively.

---

## Architecture

* **Pre-login raw filter** — caps the bytes a connection may send before it finishes logging in,
  dropping login-crash payloads at the raw `ByteBuf` level.
* **Hybrid rate limiter** — weight-based rather than a flat packet count: a `ContainerClick` costs
  more than a movement packet, and micro-bursts are surcharged to catch packet bundling.
* **Protocol state machine** — tracks whether a GUI is actually open, so "ghost" interactions
  (inventory clicks with no window, hotbar spam) are rejected.
* **Sentinel safe-mode** — if a check throws repeatedly for a player, it is disabled for that player
  only. Checks listed under `settings.sentinel.critical-checks` are never auto-disabled.
* **Violation radar** — kicks a player who accumulates `burst-threshold` blocked packets inside
  `window-ms`.
* **Per-check benchmarking** — nanosecond timings per check, via `/lg benchmark`.

---

## Protections

**Items and components** — validates stack counts, attribute-modifier counts and magnitudes,
enchantment levels (including an enchanted book's *stored* enchantments), lore size, custom-name
length, bundle contents and profile properties. Screens custom data for JSON-bomb structures.
Applies to both creative-slot packets and ordinary container clicks.

**Payloads** — screens the custom-payload channel: rejects control characters, over-long channel
names, and any channel on the configured blocklist, including forged BungeeCord messages.

**Movement** — rejects non-finite and out-of-world coordinates, movement into unloaded chunks,
whole-block coordinate jumps, and offsets beyond the configured walking/elytra limits.

**Signs, books, anvils** — validates line and page limits against vanilla ceilings, blocks control
characters and JSON injection in signs, and rejects Log4Shell patterns in anvil renames.

**Commands and tab-completion** — screens both signed and unsigned command packets against a
blacklist and for `${jndi:` / `${ldap:` / `${rmi:` / `${dns:` payloads; blocks expression-evaluator
tab crashers (`//calc` and friends).

**Windows, entities, vehicles** — slot and button bounds, clone-click gating, entity interaction
distance and rate, and vehicle movement bounds.

**Vanilla mechanic patches** (`MechanicListener`) — chested-horse and bed/crop dupes, anvil, furnace
and hopper dupes, nether-roof building, portal breaking via liquid or tree growth, falling-block and
projectile-velocity caps, and piston block-entity crashes.

---

## Commands

All require `lg.admin`.

| Command | Description |
| --- | --- |
| `/lg reload` | Reload config, caches and checks |
| `/lg stats` | Blocked-packet totals, uptime, top violators (`/lg stats reset` clears) |
| `/lg profile` | Toggle the live per-player PPS profiler |
| `/lg safemode` | Sentinel state and auto-disabled checks |
| `/lg benchmark` | Per-check timings, printed to console (`/lg benchmark reset` clears) |
| `/lg tps` | Internal server TPS |

---

## Configuration

Every `checks.<name>.enabled` flag in `config.yml` is honoured — a disabled check is never
registered, so it costs nothing per packet. Changes apply on `/lg reload`.

Two settings are worth reading before enabling:

* `mechanics.trapdoor-rail-redstone` randomly drops ~20% of redstone pulses on trapdoors, rails,
  comparators and observers. It is effective against clock-based lag machines and will also break
  legitimate contraptions.
* `checks.tab.block-contains` is matched as a plain substring against the tab-completion buffer.
  Broad entries such as `nbt`, `tag` or `execute` match ordinary vanilla commands and will cause
  real players to be kicked.

---

## License

See [LICENSE](LICENSE).
