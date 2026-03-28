# 🛡️ LightGuard V2

### Enterprise-Grade Packet Analysis & Anti-Exploit System

**LightGuard V2** is a high-performance, lightweight, and highly advanced security plugin designed to protect Minecraft servers (**Spigot/Paper 1.16.5**) from zero-day crashers, sophisticated exploit clients, lag machines, and network-level attacks.

Unlike traditional anti-exploits that rely on the main server thread, LightGuard operates directly on the **Netty Pipeline**. It intercepts, analyzes, and mitigates malicious traffic **before** it ever reaches the Bukkit API, providing **maximum server protection with near-zero TPS impact**.

---

## ⚡ 👑 Enterprise-Level Architecture

LightGuard is not just a packet listener; it's a multi-layered network firewall.

* 🧱 **Pre-Login Raw Filter:** Drops malicious bot connections and "Login Crash" attempts at the raw byte level (e.g., dropping packets >128KB before the server even registers the connection).
* 🔬 **NBT Streaming Scanner:** Analyzes Item NBT (Book Bans, Chest Bans, JSON bombs) *without* fully deserializing the packet. It scans the raw byte stream for recursive structures and depth limits, completely avoiding CPU starvation.
* ⚖️ **Hybrid Rate Limiter (Weight-Based):** Doesn't just count packets; it measures **Threat Score**. A `WindowClick` costs more than a `Flying` packet. It features micro-burst detection to stop "Ping-Collision" and "Packet Bundling" lag spikes.
* 🧠 **Protocol State Machine:** Tracks the player's actual game state. Blocks "Ghost Interactions" like inventory clicks when the GUI is closed, or moving while dead.
* 🛡️ **Sentinel (Safe-Mode) System:** A built-in watchdog. If a specific packet check throws continuous errors, Sentinel automatically disables that specific check to prevent TPS loss, while keeping "Critical" checks permanently active.
* ⏱️ **Microsecond Benchmarking:** Built-in performance monitor measuring the exact nanosecond cost of every single packet check, accessible in-game.

---

## 🧨 Packet-Level Exploit Protections

LightGuard blocks an extensive range of client-side exploits and crashers:

* **Custom Payload & Brand Security 🛰️**
  * Blocks oversized payloads, invalid channels (`MC|BEdit`, `REGISTER` flood), and spoofed/malicious client brand tags.
* **Complex NBT & JSON Bomb Security 📜**
  * Deep-scans NBT for oversized IntArrays, recursive lists, excessive attributes (+Attack Damage exploits), and illegal enchant levels.
  * Blocks `HoverEvent` and `Translate` JSON bombs designed to freeze the server parser.
* **Math & Vector Poisoning 🚫**
  * Blocks packets injecting `NaN` (Not a Number) or `Infinity` into interaction or movement vectors (Hitbox Poisoning).
* **Sign & Book Exploits ✍️**
  * Validates book page counts, byte limits, and character encodings. Blocks JSON injection in signs and sign-update range exploits.
* **GUI & Window Crashers 🪟**
  * Prevents Lectern page spam, negative slot exploits, and cross-window ghost clicks.
* **Movement & NoCom Exploits 👟**
  * Strictly validates chunk loading states, extreme Elytra velocities, and out-of-world bounds. Prevents NoCom (Interaction distance) exploits.

---

## ⚙️ Vanilla Bug & Dupe Patches (MechanicListener)

LightGuard doesn't just stop packet crashers; it patches notorious Vanilla Minecraft duplication glitches and lag machines:

* **Duplication Fixes 💰**
  * **Chested Horse/Llama (Mule) Dupe:** Patched.
  * **Bed + Crop Dupe:** Patched.
  * **Nether Portal Dimension Dupe:** Patched (Forces inventory close on teleport).
  * **Anvil, Furnace & Hopper UI Dupes:** Patched.
* **Lag Machine Mitigations 🌊**
  * **Noisy Redstone Limiter:** Randomly throttles excessive Trapdoor/Rail/Observer clocks to prevent block-update lag.
  * **Falling Block Limits:** Prevents Sand/Gravel entity crashers per chunk.
  * **Projectile Limits:** Hard-caps arrow velocity to prevent physics-engine calculation crashes.
* **Gameplay Exploits 🚫**
  * **Nether Roof Build Prevention:** Stops block placement above Y:127 in the Nether.
  * **Portal Breaking:** Prevents breaking portals using liquid flow or mushroom/tree growth.
  * **FastPlace / Schematic Printer:** Detects and throttles illegal block placement speeds.

---

## 💬 Chat & Command Security

* **Log4Shell & Syntax Blocking 📝**
  * Drops commands and Tab-Complete requests containing `${jndi:`, `${ldap:`, or invalid syntax like `//calc` (WorldEdit crashers) and `::`.
* **Anti-Zalgo & Unicode 🧿**
  * Filters out unrenderable ASCII characters and Zalgo (stacked) text that can crash client games.

---

## 📊 Metrics & Performance Commands

| Command | Description
| --- | --- | --- |
| `/lg reload` | Reloads configurations, illegal block caches, and checks.
| `/lg profile` | Toggles the live packet profiler (Shows Top PPS players).
| `/lg safemode` | Shows active internal errors and automatically disabled checks.
| `/lg benchmark` | Prints nanosecond performance stats for every check module.
| `/lg stats` | Shows total blocked packets, uptime, and top violators.
| `/lg tps` | Displays accurate internal server TPS.

**Permissions:** * `lg.admin`: Grants access to commands and total immunity to security checks.

---

## 🤖 Co-Developed with Artificial Intelligence

This project is a result of advanced **human + AI collaboration**.
* **Core Logic & Network Architecture:** Engineered with assistance from advanced LLMs to identify zero-day vulnerabilities in the Minecraft protocol.
* **Optimization:** AI-driven refactoring for `O(1)` time complexity in lookups, ThreadLocal caching for NBT analysis, and garbage-collection (GC) pressure reduction.

---

⚠️ **Disclaimer**

This plugin is highly optimized and specifically mapped for **Spigot/Paper 1.16.5**. It relies on direct NMS (Net.Minecraft.Server) bindings and Netty pipeline injection specific to the 1.16.5 protocol to achieve its speed.