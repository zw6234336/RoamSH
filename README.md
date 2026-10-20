<div align="center">

# RoamSH 📱⚡️

**English** | [简体中文](./README_zh.md)

> **A Modern Native Android SSH Remote Terminal & Dev Workstation for Mobile Coders**  
> Built with Kotlin, Jetpack Compose, and Material 3. Engineered for developer ergonomics, multi-session tabs, live host health monitoring, dedicated Tmux control, full-featured touch keyboard, rock-solid background keep-alive, system credential autofill, and a zero-config simulation sandbox.

</div>

---

## 📸 Real Device Screenshots

> *All screenshots captured directly from a physical Google Pixel 9. Private network hostnames and IP addresses have been desensitized using Gaussian blur.*

| Host Management & Live Latency Dashboard | Real-Time Health Probe Detail |
| :---: | :---: |
| <img src="screenshots/01_hosts.png" width="340" alt="Host Management & Live Health Dashboard" /> | <img src="screenshots/02_health_detail.png" width="340" alt="Host Health Probe Detail" /> |

| Multi-Session Tabs Terminal Console | Dedicated Tmux / Screen Control Bar |
| :---: | :---: |
| <img src="screenshots/03_terminal.png" width="340" alt="Multi-Session Tabs Terminal Console" /> | <img src="screenshots/04_tmux.png" width="340" alt="Dedicated Tmux Control Bar" /> |

| Modern Host Setup & Credential Autofill | SSH Key Management | Port Forwarding Tunnels |
| :---: | :---: | :---: |
| <img src="screenshots/05_add_host.png" width="280" alt="Modern Host Setup & Credential Autofill" /> | <img src="screenshots/06_keys.png" width="280" alt="SSH Key Management" /> | <img src="screenshots/07_tunnels.png" width="280" alt="Port Forwarding Tunnels" /> |

---

## ✨ Features

- **📑 Multi-Session Tab Management (Tabs)**:
  - Simultaneous multi-host SSH connections and local sandbox tabs in a sleek, horizontal tab bar (`TerminalTabBar`).
  - Active session indicator, custom tab renaming, close confirmations, and swift switching between active tasks.
  - Independent background keepalive and execution persistence for each open session.
- **📊 Host Health Monitoring & Auto-Refresh (Live Dashboard)**:
  - Real-time host latency ping & status badge (`46ms 极佳`) displayed directly on the host card.
  - Automatic 10-second background polling cycle keeping server metrics up-to-date.
  - Comprehensive Host Health Probe Detail dialog with CPU utilization, RAM usage, storage space, and top resource-consuming processes (`dockerd`, `node`, `redis-server`, `sshd`).
- **🎛️ Dedicated Tmux / Screen Control Bar (Tmux Mode)**:
  - Dual-tier visual control bar optimized for mobile terminal multiplexing:
    - **Tier 1 (Visual Window Tabs)**: Replicates native tab ergonomics directly inside tmux (`0: window 1`, `1: window 2`, `+` new window, `x` kill window, and quick window jumping).
    - **Tier 2 (Touch Tools & Panes)**: One-tap mouse mode toggle (`🖱️ 触控滚屏`) enabling direct finger flick scrolling inside tmux without entering copy mode, split pane horizontally/vertically (`%`, `"`), pane navigation, pane zoom (`z`), and synchronize panes.
- **⌨️ Dual-Layer Developer Quick Keyboard Bar**:
  - Purpose-built for touchscreen coding: `ESC`, `TAB`, `Ctrl-C`, `Ctrl-D`, `Ctrl`, `Alt` modifier toggles, directional arrows, and high-frequency code symbols (`|`, `~`, `/`, `\`) accessible in one tap.
  - Native virtual keyboard (IME) integration with fluid Chinese/English input, shell auto-completion (including `agy` completions), and command snippet insertion.
- **🛡️ Ultra-Reliable Background Keep-Alive**:
  - System Foreground Service + Partial CPU WakeLock + WiFi High-Performance Lock + SSH heartbeat packets (`ServerAliveInterval`).
  - Keeps long-running compilation, tests, or AI training alive even when the screen is locked or while switching between apps (e.g. WeChat, Slack, browsers).
- **🔐 System Credential Autofill & Smart Parser**:
  - Native integration with Android System Credential Manager (compatible with 1Password, Bitwarden, KeePassDX, and Google Password Manager).
  - Smart clipboard parser supporting multi-line vault exports, URI formats (`ssh://user:pass@host:port`), and raw CLI connection strings (`ssh -p 2200 user@host`).
- **🎨 8+ Developer Themes & Dynamic Terminal Viewport**:
  - Built-in classic palettes: Dracula, Nord, Solarized Dark/Light, Gruvbox Dark/Light, One Dark, Monokai, and Retro Matrix.
  - Adaptive PTY dimension calculation with real-time `SIGWINCH` dispatching to remote hosts. Easily toggle between horizontal alignment and soft line wrapping.
- **🧪 Zero-Config Local Sandbox (Simulation Shell)**:
  - Experiment with CLI commands (`git`, `npm`, `python`, `docker`, `htop`, `tmux`) in a built-in sandbox without needing an active server.
- **🔑 On-Device Key Generation & Port Forwarding**:
  - Generate Ed25519 or 2048-bit RSA key pairs on your device with copyable public keys and step-by-step instructions.
  - Local Port Forwarding enables direct previews of remote web services (e.g., Vite on port 3000 or APIs on 8080) directly in your mobile browser.

---

## 🤖 Built with AI & Prompt Engineering Guide

RoamSH was architected, coded, and debugged entirely with advanced AI Coding Agents (LLMs).

All system-level prompts, architectural constraints, ergonomic requirements, and bug-fixing postmortems (such as diagnosing and resolving Compose navigation `ComposeRuntimeError`) have been compiled into a dedicated guide:

👉 **[Read the AI Build Prompts & Architecture Guide (AI_BUILD_PROMPTS.md)](./AI_BUILD_PROMPTS.md)**

---

## 🛠️ Building & Running Locally

### Prerequisites
- [Android Studio Ladybug or newer](https://developer.android.com/studio)
- JDK 17+
- Android SDK 34 ~ 36 (Android 14+ recommended)

### Quick Start
1. Clone the repository:
   ```bash
   git clone https://github.com/zw6234336/RoamSH.git
   cd RoamSH
   ```
2. Run unit tests to verify correctness:
   ```bash
   ./gradlew testDebugUnitTest
   ```
3. Connect your Android device or start an emulator, then install via USB:
   ```bash
   ./gradlew installDebug
   ```

---

## 📄 License

This project is licensed under the [Apache License 2.0](LICENSE).
