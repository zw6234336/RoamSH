<div align="center">

# RoamSH 📱⚡️

[English](./README.md) | **简体中文**

> **专为移动端开发者打造的现代化 Android SSH 远程终端与开发工作站**  
> 采用 Kotlin + Jetpack Compose + Material 3 开发，原生高性能、多会话标签页管理、主机快速健康监控、Tmux 专属控制条、全功能特制键盘、超长后台守护、系统凭据一键解析与零配置沙盒体验。

</div>

---

## 📸 应用真机截图 (Screenshots)

> *以下截图均采自 Google Pixel 9 真机，已对敏感网络 IP 与主机名进行高斯模糊脱敏。*

| 主机监控看板与资产管理 | 主机实时健康探针详情 |
| :---: | :---: |
| <img src="screenshots/01_hosts.png" width="340" alt="主机监控看板与资产管理" /> | <img src="screenshots/02_health_detail.png" width="340" alt="主机实时健康探针详情" /> |

| 多会话标签页终端 (双层键盘与 ANSI 高亮) | Tmux / Screen 专属双层控制条 (多窗口与触控滚屏) |
| :---: | :---: |
| <img src="screenshots/03_terminal.png" width="340" alt="多会话标签页终端控制台" /> | <img src="screenshots/04_tmux.png" width="340" alt="Tmux / Screen 专属控制条" /> |

| 现代化主机添加与系统凭据自动填充 | SSH 密钥管理 (免密登录) | 端口转发隧道 (本地/远程映射) |
| :---: | :---: | :---: |
| <img src="screenshots/05_add_host.png" width="280" alt="现代化主机添加与凭据填充" /> | <img src="screenshots/06_keys.png" width="280" alt="SSH 密钥管理" /> | <img src="screenshots/07_tunnels.png" width="280" alt="端口转发隧道" /> |

---

## ✨ 核心特性

- **📑 多会话多标签页管理 (Multi-Session Tabs)**：
  - 顶部常驻轻量化标签栏（`TerminalTabBar`），支持同时连接多台远程 SSH 服务器与本地模拟沙箱会话。
  - 支持会话动态指示器、标签长按重命名、安全关闭确认、一键 `+` 快速新建会话或调起沙箱。
  - 各会话独立后台守护，互不干扰，秒级平滑切换。
- **📊 主机快速健康监控看板 (Live Host Health)**：
  - 卡片式健康监控，直观展示实时网络延迟徽标（如 `46ms 极佳`）、连通状态与 CPU/内存/磁盘概览。
  - 支持后台 10 秒周期自动刷新，无需手动刷新即可掌握服务器实时动态。
  - 深度「健康看板」弹窗：详细展示 CPU 占用进度条、内存/磁盘容量占比，以及占用最高的关键进程列表（如 `dockerd`、`node`、`redis-server`、`sshd`），并提供一键连接终端与刷新功能。
- **🎛️ Tmux / Screen 专属双层控制条 (Tmux Mode)**：
  - 专为手机触屏复用终端设计的双层控制栏：
    - **第 1 层（可视化窗口标签页）**：参照外部标签栏的操作习惯，直观展示 Tmux 内部窗口（`0: 窗口 1`、`1: 窗口 2`），支持点击直接切换、`+` 一键新建窗口、`x` 一键关闭窗口。
    - **第 2 层（触控与窗格工具）**：一键开启 `🖱️ 触控滚屏`（无需进入复制模式即可用手指滑动回滚查看历史输出），支持一键水平/垂直分屏（`%`、`"`）、窗格焦点切换、窗格缩放（`z`）与窗格指令同步。
- **⌨️ 开发者特制双层辅助键盘**：
  - 针对触屏编码的人体工程学优化：`ESC`、`TAB`、`Ctrl-C`、`Ctrl-D`、`Ctrl`、`Alt` 修饰键、方向键、常用代码符号（`|`、`~`、`/`、`\`）一键直达。
  - 原生拼音输入与终端控制台平滑融合，支持常用命令补全（如 `agy` 命令补全提示）与代码片段一键插入。
- **🛡️ 超强后台守护 (Keep-Alive)**：
  - 前台服务 (Foreground Service) + CPU 浅度唤醒锁 (Partial WakeLock) + WiFi 高性能锁 + 底层 SSH 心跳包保持。
  - 熄屏或切屏回微信、查阅文档时，远程长耗时编译、训练或服务绝不断连。
- **🔐 系统凭据管理与智能剪贴板解析**：
  - 原生无缝接入 Android 系统凭据服务（支持 1Password、Bitwarden、KeePassDX、Google 密码管理工具等）。
  - 智能识别剪贴板中的多行格式凭据、标准 SSH URL（`ssh://user:pass@host:port`）以及原生连接命令（`ssh -p 2200 user@host`），一键自动填充。
- **🎨 8+ 程序员主题与自适应终端**：
  - 内置 Dracula, Nord, Solarized, Gruvbox, One Dark, Monokai, Retro Matrix 等经典终端配色方案。
  - 实时根据屏幕分辨率自适应计算 PTY 尺寸并分发 `SIGWINCH` 信号，支持随时切换「保持对齐」与「智能折行」。
- **🧪 零配置沙盒体验 (Local Dev Sandbox)**：
  - 无需先配置真实主机，内置高仿真本地沙盒环境，即刻体验开发指令交互（`git`、`npm`、`python`、`docker`、`htop`、`tmux`）。
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
