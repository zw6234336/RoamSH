# RoamSH 📱⚡️

> **专为移动端开发者打造的现代化 Android SSH 远程终端与开发工作站**  
> 采用 Kotlin + Jetpack Compose + Material 3 开发，原生高性能、全功能特制键盘、超长后台守护、1Password 凭据一键解析与零配置沙盒体验。

---

## 📸 应用真机截图 (Screenshots)

> *以下截图均采自 Google Pixel 9 真机，已对敏感网络 IP 与主机名进行高斯模糊脱敏。*

| 主机列表与资产管理 | 全功能终端控制台 (双层键盘与 ANSI 高亮) |
| :---: | :---: |
| <img src="screenshots/01_hosts.png" width="360" alt="主机管理列表" /> | <img src="screenshots/02_terminal.png" width="360" alt="全功能终端与开发者键盘" /> |

| 1Password 凭据快速解析 | SSH 密钥管理 (免密登录) | 端口转发隧道 (本地/远程映射) |
| :---: | :---: | :---: |
| <img src="screenshots/05_add_host.png" width="280" alt="1Password 凭据解析" /> | <img src="screenshots/03_keys.png" width="280" alt="SSH 密钥管理" /> | <img src="screenshots/04_tunnels.png" width="280" alt="端口转发隧道" /> |

---

## ✨ 核心特性

- **⌨️ 开发者特制双层辅助键盘**：
  - 针对触屏编码的极致人体工程学优化：`ESC`、`TAB`、`Ctrl-C`、`Ctrl-D`、`Ctrl`、`Alt` 修饰键、方向键、常用代码符号（`|`、`~`、`/`、`\`）一键直达。
  - 原生中文拼音输入与终端控制台平滑融合，支持动态智能代码片段与指令自动补全。
- **🛡️ 超强后台守护 (Keep-Alive)**：
  - 前台服务 (Foreground Service) + CPU 浅度唤醒锁 (Partial WakeLock) + WiFi 高性能锁 + 底层 SSH 心跳包保持。
  - 熄屏或切屏回微信、查阅文档时，远程长耗时编译、训练或服务绝不断连。
- **📋 1Password / Bitwarden 凭据一键智能解析**：
  - 智能识别剪贴板中的多行格式凭据、标准 SSH URL（`ssh://user:pass@host:port`）以及原生连接命令（`ssh -p 2200 user@host`），一键自动填充。
- **🎨 8+ 程序员主题与自适应终端**：
  - 内置 Dracula, Nord, Solarized, Gruvbox, One Dark, Monokai, Retro Matrix 等经典终端配色方案。
  - 实时根据屏幕分辨率自适应计算 PTY 尺寸并分发 `SIGWINCH` 信号，支持随时切换「保持对齐」与「智能折行」。
- **🧪 零配置沙盒体验 (Local Dev Sandbox)**：
  - 无需先配置真实主机，内置高仿真本地沙盒环境，即刻体验开发指令交互。
- **🔑 密钥生成与端口转发**：
  - 手机端一键生成 Ed25519 / RSA 2048 位密钥对，支持导入私钥与公钥一键复制。
  - 支持 Local Port Forwarding，手机端直接预览远程运行的 Vite (3000) 或 API (8080) 服务。

---

## 🤖 纯 AI 协同构建与提示词沉淀

本项目完全借助现代大语言模型（AI Coding Agent）进行架构规划、Compose 界面设计、协议接入及多轮真机调试演进。

我们将整个开发过程中**所有的系统级提示词要求、架构约束规范、以及所修复的关键问题（如 Jetpack Compose 页面回退导航的 `ComposeRuntimeError` 根因排查）**完整沉淀在了独立文件中：

👉 **[查看完整 AI 构建提示词与架构指南 (AI_BUILD_PROMPTS.md)](./AI_BUILD_PROMPTS.md)**

---

## 🛠️ 本地编译与运行指南

### 前置环境
- [Android Studio Ladybug 或更高版本](https://developer.android.com/studio)
- JDK 17+
- Android SDK 34 ~ 36 (推荐 Android 14+)

### 快速开始
1. 克隆代码仓库：
   ```bash
   git clone https://github.com/zw6234336/RoamSH.git
   cd RoamSH
   ```
2. 运行单元测试验证代码正确性：
   ```bash
   ./gradlew testDebugUnitTest
   ```
3. 连接手机或启动模拟器，通过 USB 部署安装：
   ```bash
   ./gradlew installDebug
   ```

---

## 📄 开源许可证

本项目基于 [Apache License 2.0](LICENSE) 协议开源。
