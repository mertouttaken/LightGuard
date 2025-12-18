# 🛡️ LightGuard

### Packet-Based Anti-Exploit & Crash Protection

**LightGuard** is a high-performance, lightweight security plugin designed to protect Minecraft servers (**Spigot/Paper 1.16.5**) from crashers, exploit clients, lag machines, and malicious packets.

Built directly on the **Netty Pipeline**, LightGuard intercepts and analyzes packets **before** they reach the main server thread, providing **maximum protection with minimal performance impact**.

---

## ⚡ Key Features & Protections

LightGuard blocks a wide range of attacks and exploits. Below is a breakdown of its active protection modules.

---

## 🧨 Anti-Crash & Exploit Protection

* **Custom Payload Fixes** 🛰️
* Blocks `REGISTER`, `MC|BEdit`, and oversized payload attacks.


* **NBT Security** 📜
* Prevents **Book Ban** and **Chest Ban**.
* Analyzes NBT depth, recursive lists, and excessive data size.


* **Invalid Data Protection** 🚫
* Blocks packets containing `NaN` or `Infinity` values.


* **Window / GUI Crash Fixes** 🪟
* Negative slot exploits, furnace & merchant swap crashes, and lectern spam.


* **Sign & Book Exploits** ✍️
* Blocks JSON injection and prevents oversized text abuse.


* **Netty Crash Protection** 🛠️
* Specialized handler to catch and consume protocol-breaking errors (like NBT Tag 69) that would otherwise crash the entire Netty pipeline.



---

## 🌊 Flood & Lag Prevention

* **Packet Flooding** 🌊
* Intelligent rate-limiting for all packets with adaptive multipliers based on server TPS.


* **Sound Spam Protection** 🔔
* Prevents sound-based lag attacks such as door, trapdoor, or bell spam.


* **Map Spam** 🗺️
* Limits map creation and oversized map NBT to prevent memory overflows.


* **Projectile & Entity Limits** 🏹
* Arrow velocity limits and armor stand interaction spam protection.



---

## 🚫 Gameplay & Interaction Protection

* **Printer / FastPlace** 🏗️
* Detects schematic printers and blocks impossible placement speeds.


* **Ghost Inventory Protection** 👻
* Stops AutoSteal / ChestStealer exploits by validating inventory state.


* **Movement Checks** 👟
* Elytra speed abuse, extreme velocity packets, and teleport-style movement hacks.


* **Redstone Lag Protection** ⚙️
* Intelligent limiting of noisy redstone components like trapdoors and observers to prevent lag machines.



---

## 💬 Chat & Command Security

* **Command Whitelist / Blacklist** 📝
* Blocks dangerous syntaxes like `//calc` or `::` and hides sensitive commands from tab completion.


* **Anti-Zalgo** 🧿
* Removes glitchy Zalgo text and filters illegal Unicode characters that can corrupt chat.



---

## 🚀 Performance Architecture

LightGuard is engineered for **enterprise-level performance** and high player counts.

* **Netty Injection** 💉
* Operates at the network layer for ultra-fast packet handling.


* **Zero-Allocation Checks** ♻️
* Uses cached configs and optimized loops to reduce GC pressure and RAM usage.


* **Async Logging** 📁
* Uses `LinkedBlockingQueue` for processing logs on a separate thread to avoid disk I/O lag.


* **Adaptive Throttling** 📉
* Automatically tightens packet limits if TPS drops below thresholds to prioritize stability.



---

## 🤖 Co-Developed with Artificial Intelligence

This project is a result of **human + AI collaboration**.

* **Core Logic & Architecture**: Designed with assistance from advanced LLMs.
* **Optimization**: AI-driven refactoring, config caching, and thread-safe visibility improvements.

---

## 🛠️ Commands & Permissions

| Command | Permission | Description |
| --- | --- | --- |
| `/lg reload` | `lg.admin` | Reloads the configuration and clears caches. |
| `/lg profile` | `lg.admin` | Toggles live packet profiler (PPS view). |
| `/lg watchdog` | `lg.admin` | Toggles Netty thread block monitor. |
| `/lg benchmark` | `lg.admin` | Prints performance statistics for each check to console. |
| `/lg tps` | `lg.admin` | Displays accurate server TPS. |

**Permissions:** - `lg.bypass`: Grants total immunity to all security checks.

---

⚠️ **Disclaimer**

This plugin is designed specifically for **Spigot/Paper 1.16.5**. While it may work on other versions, it relies on NMS (Net.Minecraft.Server) packets specific to the 1.16.5 protocol.