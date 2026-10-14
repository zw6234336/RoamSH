# RoamSH - AI 辅助构建全景提示词与研发沉淀指南

本项目（RoamSH）是一个完全借助先进大语言模型（AI Coding Agent）进行架构设计、界面开发、协议接入、疑难 Bug 排查及真机调试演进完成的现代 Android SSH 远程终端与开发工作站客户端。

本文件旨在完整沉淀和复现整个研发过程中的**核心提示词设计、系统要求架构、以及疑难问题修复记录**，以便其他开发者或团队能够使用 AI 模型复现类似的高品质移动端系统软件。

---

## 目录
1. [项目立项与核心提示词 (System & Genesis Prompts)](#一项目立项与核心提示词-system--genesis-prompts)
2. [我们在创建过程中的核心要求与架构规范](#二我们在创建过程中的核心要求与架构规范)
3. [开发与演进过程中修复的典型关键问题](#三开发与演进过程中修复的典型关键问题)
4. [给其他开发者使用 AI 构建项目的复现指南与最佳实践](#四给其他开发者使用-ai-构建项目的复现指南与最佳实践)

---

## 一、项目立项与核心提示词 (System & Genesis Prompts)

以下提示词可直接作为初始化一个高水准 Android 终端项目的系统引导词（Prompt Template）：

```markdown
你是一名拥有丰富资历的 Android 系统专家、网络协议专家与现代 UI 设计师。
请协助我打造一款名为 "RoamSH" 的现代化 Android SSH 终端工具，专为程序员在移动端进行远程运维、编码调试和服务器管理量身定制。

### 核心技术栈
1. 语言与运行时: Kotlin 2.0+, Jetpack Compose, Material 3, Kotlin Coroutines, StateFlow.
2. 架构模式: 单 Activity + MVVM 架构, Room 本地持久化, Navigation 状态流转.
3. SSH 协议引擎: 基于 JSch 结合 BouncyCastle 安全套件 (支持 RSA, Ed25519, ECDSA 及现代加密套件 chacha20-poly1305, aes256-gcm).
4. 终端解析引擎: 自研或轻量级 ANSI 控制台解析器，支持 ANSI 色彩代码、Dracula / Nord / Solarized 等 8+ 种主流主题，支持动态折行与自适应 PTY 窗口尺寸计算 (SIGWINCH).

### 产品核心特性
1. 主机资产管理:
   - 支持多服务器分类标签（电脑、工作、家庭、云服务器、WSL 等）、收藏星标与快速搜索。
   - 凭据解析：内置识别 1Password / Bitwarden 等密码管理器剪贴板格式与标准 SSH 命令字符串的一键快速填入。
2. 沉浸式终端控制台:
   - 专为手机触屏打造的双层开发者快捷辅助键栏（ESC、TAB、Ctrl-C、Ctrl-D、Ctrl/Alt 组合修饰键、方向键、管道符 |、波浪号 ~、斜杠 / 等）。
   - 中文拼音输入法与原生控制台命令平滑交互，提供实时命令自动补全与 Snippets 代码片段插入。
3. 零配置沙盒体验 (Local Dev Sandbox):
   - 无需真实服务器，内置高仿真 Linux 模拟 Shell，可立即体验 git、npm、python、docker、htop 等仿真操作与大模型智能对话交互。
4. 后台长连接守护 (Keep-Alive):
   - 前台服务 (Foreground Service) + CPU 唤醒锁 (Partial WakeLock) + WiFi 高性能锁 + 底层 SSH 心跳包保持。
   - 保证切屏查阅微信/浏览器或手机熄屏时，长时间耗时的编译/训练任务绝不断连。
5. 密钥管理与隧道转发:
   - 本地一键生成 2048 位 RSA 或 Ed25519 密钥对，支持导入导出。
   - SSH 端口转发（Local Port Forwarding），让开发者在手机浏览器直接预览远程电脑上的 Web 服务（如 127.0.0.1:3000）。
```

---

## 二、我们在创建过程中的核心要求与架构规范

在与 AI 模型协同研发的全流程中，我们强调并落实了以下几项极严苛的要求：

### 1. 移动端开发体验（Developer Ergonomics）绝不妥协
- **问题意识**：传统移动端 SSH 工具最大的痛点是手机软键盘缺少 PC 键位，导致输入 `Ctrl+C`、`Esc`、`Tab` 补全时极度痛苦。
- **设计要求**：
  - 必须常驻紧凑的悬浮扩展键盘栏（`ExtendedKeyboardBar`），并将输入框集成于键盘上方，支持纯触控长按、高频符号直达。
  - 必须支持双击、单指缩放调整终端字号（9sp ~ 28sp），支持随时切换「保持对齐（横向滚动）」或「智能折行（自动换行）」。

### 2. 视觉美学与设计系统（Aesthetics & Theming）
- **设计要求**：
  - 拒绝粗糙简陋的默认控件或低对比度配色；
  - 采用深度科技暗色系统（`Slate950` 背景搭配 `Slate900` / `Slate800` 卡片），使用优雅的高对比青色（`CyanAccent: #38BDF8`）与翡翠绿（`EmeraldSuccess: #10B981`）作为强调色。
  - 内置提供多套程序员最爱的终端配色：`Dracula`、`Solarized Dark/Light`、`Gruvbox Dark/Light`、`One Dark`、`Monokai`、`Nord`、`Retro Matrix`。

### 3. 会话高可用与切屏生存能力（Background Survival）
- **设计要求**：
  - 严禁因系统省电策略或前后台切换而悄然杀掉 SSH 进程。
  - 使用 Android 标准前台服务（`SshKeepAliveService`），注册系统常驻通知栏卡片，明确告知用户当前保持的主机名称与时长，并提供通知栏直接一键断开按钮。
  - 终端输出历史（`terminalListState`）与解析后的分段文本（`TerminalLine`）需由 `ViewModel` 托管，切屏或旋转屏幕时绝不重新拉取、绝不丢失已有屏幕输出。

### 4. 严守安全底线与开源规范
- **设计要求**：
  - 作为开源公开项目，代码库与提交历史中严禁出现任何真实公网 IP、个人私钥、真实密码或私有密钥库。
  - 密码只加密或在内存中保存，测试用例一律使用标准局域网保留 IP（如 `192.168.1.100` 或 `192.0.2.1`）与虚拟占位凭据。
  - `.gitignore` 必须将所有私钥后缀（`*.jks`、`*.keystore`、`*.key`、`*.pem`、`*.p12`）与环境文件严密拦截。

---

## 三、开发与演进过程中修复的典型关键问题

在 AI 编码与真机联调过程中，我们遇到了若干在 Jetpack Compose 和底层系统交互中极具代表性的疑难 Bug，均通过深度分析根因后完成了精准修复：

### 1. 终端返回后再次点击底部导航导致应用强退 (`ComposeRuntimeError`)
- **Bug 现象**：
  用户连接终端正常进入后，点击左上角「返回主机列表」，再点击底部导航栏的「终端控制台」时，App 瞬间强退闪退。
- **根因分析 (Root Cause)**：
  终端组件中监听了页面可见性：
  ```kotlin
  // 错误代码示例
  androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
      if (autoScroll && lines.isNotEmpty()) {
          viewModel.scrollToBottom() // 内部调用了 viewModelScope.launch { terminalListState.scrollToItem(...) }
      }
  }
  ```
  在从其他 Tab 切回终端时，Activity 一直处于 `RESUMED` 状态，`LifecycleEventEffect` 内部的 `DisposableEffect` 挂载期间会**同步**分发生命周期通知。而 `viewModelScope` 默认运行在 `Dispatchers.Main.immediate` 上，导致 `scrollToItem` 直接在 Compose 正在提交快照修改的底层挂载阶段（`applyChanges`）同步修改列表滚动状态，触发了 Compose 底层保护断言：
  `ComposeRuntimeError: pending composition has not been applied`。
- **修复方案**：
  - 将不可控的同步生命周期分发改为在安全协程中调度：
    ```kotlin
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
      lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
        if (autoScroll && lines.isNotEmpty()) {
          try {
            listState.scrollToItem((lines.size - 1).coerceAtLeast(0))
          } catch (_: Throwable) {}
        }
      }
    }
    ```
  - ViewModel 内部的 `scrollToBottom()` 显式指定 `Dispatchers.Main`（推入主线程下一事件队列而非立即内联插队），并将异常保护提升至 `Throwable`。

### 2. 多应用切换与剪贴板识别 (1Password / 微信 交互场景)
- **Bug 现象**：
  从 1Password 复制复杂凭据（包含服务器多行地址、带端口命令、用户名）或从微信接收临时密码时，手动分段复制粘贴极其繁琐，切出应用时会话容易被系统中断。
- **修复方案**：
  - 编写了强大的智能规则解析器 [CredentialParser.kt](file:///Users/zhangwei/work/github/RoamSH/app/src/main/java/com/example/util/CredentialParser.kt)，能够无缝正则解析三种剪贴板格式：
    1. 1Password / Bitwarden 多行键值项；
    2. 标准 SSH URL 格式（`ssh://user:pass@host:port`）；
    3. 终端原生命令（`ssh -p 2200 ubuntu@192.168.1.100`）。
  - 在弹窗顶部常驻「一键识别剪贴板」Banner，点击直接填满所有字段。

### 3. 项目公开前敏感信息泄露防范
- **排查与修复**：
  - **AI Studio 个人地址**：清理了 [README.md](file:///Users/zhangwei/work/github/RoamSH/README.md) 中残留的个人工作区链接 `https://ai.studio/apps/9b431d5e-f3c9-4018-aa12-6fc18e69e4b0`。
  - **`.gitignore` 强化**：增加了对所有类型证书（`*.jks`, `*.keystore`, `*.key`, `*.pem`, `*.p12`）、`google-services.json` 以及 `.idea/` 的通配忽略，防止未来私钥被意外上传。
  - **真机截屏脱敏**：编写自动化高斯模糊脚本，将真机截图上的实际内网机器名与 Tailscale / VPN IP 进行无痕打码，保证开源资产视觉真实性的同时彻底杜绝隐私泄露。

### 4. 项目品牌命名与工程配置一致性 (DevSSH -> RoamSH)
- **演进与对齐**：
  - 全量将应用桌面名称、`metadata.json`、Gradle 项目名、测试用例断言、日志 Tag、前台通知及 WakeLock 标识统一更新为 **RoamSH**，保证工程各层命名无撕裂感。

---

## 四、给其他开发者使用 AI 构建项目的复现指南与最佳实践

如果你正打算使用 Claude 3.7 / Gemini 2.5 / GPT-4o 等 AI 模型全流程开发一个原生移动应用，以下经验经过实践检验非常关键：

1. **坚持连接真机实时闭环验证**：
   - 使用 `adb install` / `installDebug` 实时推送到真机，配合 `adb logcat` 查看实际栈帧，远比单纯依赖单元测试或 Mock 更快发现 Compose 状态机冲突。
2. **警惕 Compose 中 DisposableEffect 与快照冲突**：
   - 任何涉及 UI 状态（如 `ScrollState`、`LazyListState`、焦点变换）的操作，必须在 `LaunchedEffect` 或异步协程作用域内完成，切忌在事件回调（如 `onClick`、`DisposableEffect` 闭包）中调用内联即时执行的 Main.immediate 协程。
3. **保持单元测试作为重构锚点**：
   - 每一次架构调整或大范围重命名后，第一时间运行 `./gradlew testDebugUnitTest`，以自动化用例防止回归。
