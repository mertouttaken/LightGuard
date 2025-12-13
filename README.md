# 🛡️ LightGuard
### Packet-Based Anti-Exploit & Crash Protection

**LightGuardAdvanced** is a high-performance, lightweight security plugin designed to protect Minecraft servers (**Spigot/Paper 1.16.5**) from crashers, exploit clients, lag machines, and malicious packets.

Built directly on the **Netty Pipeline**, LightGuard intercepts and analyzes packets **before** they reach the main server thread, providing **maximum protection with minimal performance impact**.

---

## ⚡ Key Features & Protections

LightGuard blocks a wide range of attacks and exploits. Below is a breakdown of its active protection modules.

---

## 🧨 Anti-Crash & Exploit Protection

- **Custom Payload Fixes**
  - Blocks `REGISTER`, `MC|BEdit`, and oversized payload attacks
- **NBT Security**
  - Prevents **Book Ban** and **Chest Ban**
  - Analyzes NBT depth, recursive lists, and excessive data size
- **Invalid Data Protection**
  - Blocks packets containing `NaN` or `Infinity` values
- **Window / GUI Crash Fixes**
  - Negative slot exploits
  - Furnace & merchant swap crashes
  - Lectern spam
- **Sign & Book Exploits**
  - Blocks JSON injection
  - Prevents oversized text abuse

---

## 🌊 Flood & Lag Prevention

- **Packet Flooding**
  - Intelligent rate-limiting for all packets
- **Sound Spam Protection**
  - Prevents sound-based lag attacks (e.g. door/trapdoor spam)
- **Map Spam**
  - Limits map creation to prevent memory overflows
- **Projectile & Entity Limits**
  - Arrow velocity limits
  - Armor stand interaction spam protection

---

## 🚫 Gameplay & Interaction Protection

- **Printer / FastPlace**
  - Detects schematic printers (Schematica-like behavior)
  - Blocks impossible placement speeds
- **Reach & Angle Checks**
  - Prevents interaction through walls or extreme angles
- **Ghost Inventory Protection**
  - Stops AutoSteal / ChestStealer exploits
- **Movement Checks**
  - Elytra speed abuse
  - Extreme velocity packets
  - Teleport-style movement hacks

---

## 💬 Chat & Command Security

- **Command Whitelist / Blacklist**
  - Blocks dangerous syntaxes like `//calc` or `::`
  - Hides sensitive commands (`/plugins`, `/ver`) from tab completion
- **Anti-Zalgo**
  - Removes glitchy Zalgo text
  - Filters illegal Unicode characters that can corrupt chat

---

## 🚀 Performance Architecture

LightGuard is engineered for **enterprise-level performance** and high player counts.

- **Netty Injection**
  - Operates at the network layer for ultra-fast packet handling
- **Zero-Allocation Checks**
  - Cached configs & reusable objects
  - Reduced GC pressure and RAM usage
- **Async Logging**
  - Uses `LinkedBlockingQueue`
  - Disk I/O handled on a separate thread
- **Adaptive Throttling**
  - Automatically relaxes checks if TPS drops
  - Prioritizes gameplay stability

---

## 🤖 Co-Developed with Artificial Intelligence

This project is a result of **human + AI collaboration**.

- **Core Logic & Architecture**
  - Designed with assistance from advanced LLMs
- **Optimization**
  - AI-driven refactoring
  - Config caching
  - Thread-safe visibility
- **Purpose**
  - Demonstrates AI-assisted, production-ready software development

---

## 🛠️ Commands & Permissions

| Command | Permission | Description |
|-------|-----------|-------------|
| `/lg reload` | `lg.admin` | Reloads the configuration |
| `/lg profile` | `lg.admin` | Toggles live packet profiler (PPS view) |
| `/lg watchdog` | `lg.admin` | Toggles Netty thread watchdog |
| `/lg tps` | `lg.admin` | Displays accurate server TPS |

**Bypass Permission:**  
`lg.bypass` — Grants immunity to all checks

---

⚠️ Disclaimer

This plugin is designed specifically for Spigot/Paper 1.16.5. (Only Works 1.16.5)

While it may work on other versions, it relies on NMS (Net.Minecraft.Server) packets specific to the 1.16.5 protocol.
