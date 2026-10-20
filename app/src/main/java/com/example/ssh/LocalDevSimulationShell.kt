package com.example.ssh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ui.terminal.CandidateItem
import com.example.ui.terminal.CandidateType

class LocalDevSimulationShell(
  private val scope: CoroutineScope,
  private val hostname: String = "computer",
  private val username: String = "dev"
) {
  private val _output = MutableSharedFlow<String>(extraBufferCapacity = 64)
  val output = _output.asSharedFlow()

  private val commandHistory = mutableListOf<String>()
  private var historyIndex = -1
  private val currentInput = StringBuilder()

  private var currentDir = "~/projects/dev-workspace"
  private var isRunning = false
  private var liveJob: Job? = null

  fun start() {
    isRunning = true
    scope.launch(Dispatchers.Default) {
      val banner = buildString {
        append("\u001b[1;36m========================================================\u001b[0m\r\n")
        append("\u001b[1;32m   RoamSH 本地模拟环境 (Local Dev Sandbox)\u001b[0m\r\n")
        append("\u001b[0;37m   Linux workstation 6.6.14-arch1-1 #1 SMP PREEMPT_DYNAMIC\u001b[0m\r\n")
        append("\u001b[0;34m   提示: 在主机列表中配置你电脑的真实 IP/域名即可真实连接！\u001b[0m\r\n")
        append("\u001b[1;33m   输入 'help' 查看常用模拟开发指令 (git, npm, htop, ls, 等)\u001b[0m\r\n")
        append("\u001b[1;36m========================================================\u001b[0m\r\n\r\n")
      }
      _output.emit(banner)
      printPrompt()
    }
  }

  fun sendInput(input: String) {
    if (!isRunning) return

    scope.launch(Dispatchers.Default) {
      var i = 0
      while (i < input.length) {
        val c = input[i]
        when (c) {
          '\r', '\n' -> {
            _output.emit("\r\n")
            val cmd = currentInput.toString().trim()
            currentInput.clear()
            historyIndex = -1
            if (cmd.isNotEmpty()) {
              commandHistory.add(cmd)
              executeCommand(cmd)
            } else {
              printPrompt()
            }
          }
          '\u007f', '\b' -> { // Backspace
            if (currentInput.isNotEmpty()) {
              currentInput.deleteCharAt(currentInput.length - 1)
              _output.emit("\b \b")
            }
          }
          '\t' -> { // Tab autocomplete
            handleTab()
          }
          '\u0003' -> { // Ctrl+C
            liveJob?.cancel()
            _output.emit("^C\r\n")
            currentInput.clear()
            printPrompt()
          }
          '\u000c' -> { // Ctrl+L
            _output.emit("\u001b[2J\u001b[H")
            printPrompt()
            _output.emit(currentInput.toString())
          }
          '\u001b' -> { // Escape sequences (e.g. arrow keys)
            if (i + 2 < input.length && input[i + 1] == '[') {
              val arrow = input[i + 2]
              i += 2
              when (arrow) {
                'A' -> handleHistoryNavigate(-1) // UP
                'B' -> handleHistoryNavigate(1)  // DOWN
              }
            }
          }
          else -> {
            currentInput.append(c)
            _output.emit(c.toString())
          }
        }
        i++
      }
    }
  }

  private suspend fun printPrompt() {
    val prompt = "\u001b[1;32m$username@$hostname\u001b[0m:\u001b[1;34m$currentDir\u001b[0m$ "
    _output.emit(prompt)
  }

  private suspend fun handleTab() {
    val current = currentInput.toString()

    val allCommands = listOf(
      "git", "npm", "cargo", "docker", "python3", "python", "htop", "top", "tmux",
      "cat", "ls", "clear", "help", "pwd", "whoami", "uname", "ollama", "nvidia-smi",
      "cd", "node", "curl", "vim", "systemctl"
    )

    val workspaceFiles = listOf(
      "workspace-github/", "workspace/", "work/", "projects/", "repos/",
      "README.md", "package.json", "Cargo.toml", "Dockerfile", "main.py",
      "tsconfig.json", "src/", "tests/", ".git/"
    )

    val gitSubcommands = listOf("status", "log", "pull", "push", "checkout", "commit", "branch", "diff", "add")
    val dockerSubcommands = listOf("ps", "images", "run", "stop", "exec", "logs", "compose")
    val npmSubcommands = listOf("run dev", "install", "test", "build", "start")
    val ollamaSubcommands = listOf("run deepseek-r1:8b", "run qwen2.5-coder:7b", "ps", "list")
    val systemctlSubcommands = listOf("status", "restart", "start", "stop", "reload")

    if (current.isEmpty()) {
      _output.emit("\r\n\u001b[0;37m" + allCommands.take(12).joinToString("  ") + "\u001b[0m\r\n")
      printPrompt()
      return
    }

    val tokens = current.split(" ")
    if (tokens.size == 1) {
      val prefix = tokens[0]
      val matches = allCommands.filter { it.startsWith(prefix) }
      if (matches.size == 1) {
        val completed = matches[0]
        val remaining = completed.substring(prefix.length) + " "
        currentInput.append(remaining)
        _output.emit(remaining)
      } else if (matches.size > 1) {
        val lcp = longestCommonPrefix(matches)
        if (lcp.length > prefix.length) {
          val remaining = lcp.substring(prefix.length)
          currentInput.append(remaining)
          _output.emit(remaining)
        } else {
          _output.emit("\r\n\u001b[0;37m" + matches.joinToString("  ") + "\u001b[0m\r\n")
          printPrompt()
          _output.emit(current)
        }
      }
    } else {
      val cmd = tokens[0]
      val argPrefix = tokens.last()

      val candidates: List<String> = when (cmd) {
        "git" -> if (tokens.size == 2) gitSubcommands else workspaceFiles
        "docker" -> if (tokens.size == 2) dockerSubcommands else emptyList()
        "npm" -> if (tokens.size == 2) npmSubcommands else emptyList()
        "ollama" -> if (tokens.size == 2) ollamaSubcommands else emptyList()
        "systemctl" -> if (tokens.size == 2) systemctlSubcommands else emptyList()
        "cat", "ls", "cd", "vim", "nano" -> workspaceFiles
        else -> workspaceFiles
      }

      val matches = candidates.filter { it.startsWith(argPrefix) }
      if (matches.size == 1) {
        val completed = matches[0]
        val suffix = if (completed.endsWith("/")) "" else " "
        val remaining = completed.substring(argPrefix.length) + suffix
        currentInput.append(remaining)
        _output.emit(remaining)
      } else if (matches.size > 1) {
        val lcp = longestCommonPrefix(matches)
        if (lcp.length > argPrefix.length) {
          val remaining = lcp.substring(argPrefix.length)
          currentInput.append(remaining)
          _output.emit(remaining)
        } else {
          _output.emit("\r\n\u001b[0;37m" + matches.joinToString("  ") + "\u001b[0m\r\n")
          printPrompt()
          _output.emit(current)
        }
      }
    }
  }

  fun getCompletions(commandLine: String): List<CandidateItem> {
    val trimmed = commandLine.trimStart()
    val lastSpace = trimmed.lastIndexOf(' ')
    val token = if (lastSpace >= 0) trimmed.substring(lastSpace + 1) else trimmed
    val primaryCommand = if (lastSpace >= 0) {
      trimmed.substring(0, lastSpace).trim().split(" ").firstOrNull()?.lowercase() ?: ""
    } else {
      ""
    }

    val simulatedDirs = listOf("workspace-github/", "projects/", "src/", "build/", "dist/", "scripts/")
    val simulatedFiles = listOf("README.md", "package.json", "Cargo.toml", "Dockerfile", "main.py", "tsconfig.json")
    val gitSubcommands = listOf("status", "log", "diff", "add", "commit", "push", "pull", "checkout", "branch")
    val dockerSubcommands = listOf("ps", "images", "run", "stop", "start", "exec", "logs", "compose")
    val agySubcommands = listOf(
      "--dangerously-skip-permissions", "-c", "--continue", "--mode auto", "--mode plan",
      "--effort high", "--effort medium", "agent", "agents", "models", "mcp", "plugin", "plugins", "update", "changelog", "--sandbox", "-p"
    )
    val allCommands = listOf("agy", "git", "docker", "npm", "cargo", "python3", "htop", "cat", "ls", "cd", "vim", "systemctl", "curl")

    val matches = when {
      primaryCommand.isEmpty() -> {
        allCommands.filter { it.startsWith(token, ignoreCase = true) }
          .map { CandidateItem(it, it, CandidateType.COMMAND) }
      }
      primaryCommand in listOf("cd", "rmdir") -> {
        simulatedDirs.filter { it.startsWith(token, ignoreCase = true) }
          .map { CandidateItem(it, it, CandidateType.DIRECTORY, isDirectory = true) }
      }
      primaryCommand == "agy" -> {
        agySubcommands.filter { it.startsWith(token, ignoreCase = true) }
          .map {
            val isOpt = it.startsWith("-")
            CandidateItem(it, it, if (isOpt) CandidateType.OPTION else CandidateType.SUBCOMMAND)
          }
      }
      primaryCommand == "git" -> {
        gitSubcommands.filter { it.startsWith(token, ignoreCase = true) }
          .map { CandidateItem(it, it, CandidateType.SUBCOMMAND) }
      }
      primaryCommand == "docker" -> {
        dockerSubcommands.filter { it.startsWith(token, ignoreCase = true) }
          .map { CandidateItem(it, it, CandidateType.SUBCOMMAND) }
      }
      else -> {
        (simulatedDirs + simulatedFiles).filter { it.startsWith(token, ignoreCase = true) }
          .map {
            val isDir = it.endsWith("/")
            CandidateItem(it, it, if (isDir) CandidateType.DIRECTORY else CandidateType.FILE, isDirectory = isDir)
          }
      }
    }
    return matches
  }

  private fun longestCommonPrefix(strings: List<String>): String {
    if (strings.isEmpty()) return ""
    var prefix = strings[0]
    for (i in 1 until strings.size) {
      while (!strings[i].startsWith(prefix)) {
        prefix = prefix.substring(0, prefix.length - 1)
        if (prefix.isEmpty()) return ""
      }
    }
    return prefix
  }

  private suspend fun handleHistoryNavigate(direction: Int) {
    if (commandHistory.isEmpty()) return
    val newIndex = (historyIndex + direction).coerceIn(-1, commandHistory.size - 1)
    if (newIndex != historyIndex) {
      // Clear current input on screen
      val erase = "\b \b".repeat(currentInput.length)
      _output.emit(erase)
      currentInput.clear()

      historyIndex = newIndex
      if (historyIndex >= 0) {
        val historicalCmd = commandHistory[historyIndex]
        currentInput.append(historicalCmd)
        _output.emit(historicalCmd)
      }
    }
  }

  private suspend fun executeCommand(cmd: String) {
    val parts = cmd.split(" ").filter { it.isNotBlank() }
    val program = parts.firstOrNull()?.lowercase() ?: ""
    val args = parts.drop(1)

    when (program) {
      "help" -> {
        val text = buildString {
          append("\u001b[1;33mRoamSH 模拟环境支持指令:\u001b[0m\r\n")
          append("  \u001b[32mls\u001b[0m              查看当前目录文件\r\n")
          append("  \u001b[32mgit status\u001b[0m      查看 Git 工作区状态\r\n")
          append("  \u001b[32mgit log\u001b[0m         查看最近提交历史\r\n")
          append("  \u001b[32mnpm run dev\u001b[0m     启动开发服务器演示\r\n")
          append("  \u001b[32mcargo run\u001b[0m       编译并运行 Rust 项目\r\n")
          append("  \u001b[32mpython3\u001b[0m         运行 Python 示例\r\n")
          append("  \u001b[32mdocker ps\u001b[0m       查看虚拟容器\r\n")
          append("  \u001b[32mhtop\u001b[0m            实时监控 CPU/内存 (按 Ctrl+C 退出)\r\n")
          append("  \u001b[32mtmux\u001b[0m            终端多路复用会话演示\r\n")
          append("  \u001b[32mcat [file]\u001b[0m      查看文件内容 (如 cat README.md)\r\n")
          append("  \u001b[32mclear\u001b[0m           清屏 (或使用 Ctrl+L 快捷键)\r\n")
          append("  \u001b[32muname -a\u001b[0m        查看内核信息\r\n")
        }
        _output.emit(text)
        printPrompt()
      }
      "cd" -> {
        val target = args.firstOrNull() ?: "~"
        when {
          target == "~" || target.isEmpty() -> currentDir = "~"
          target == ".." -> {
            currentDir = if (currentDir.contains("/")) currentDir.substringBeforeLast("/") else "~"
            if (currentDir.isEmpty()) currentDir = "/"
          }
          target.startsWith("/") || target.startsWith("~/") -> currentDir = target
          else -> {
            val clean = target.removeSuffix("/")
            currentDir = if (currentDir.endsWith("/")) "$currentDir$clean" else "$currentDir/$clean"
          }
        }
        printPrompt()
      }
      "ls" -> {
        val files = buildString {
          append("\u001b[1;34msrc/\u001b[0m          \u001b[1;34mtests/\u001b[0m        \u001b[1;34m.git/\u001b[0m\r\n")
          append("\u001b[1;32mpackage.json\u001b[0m  \u001b[1;32mCargo.toml\u001b[0m    \u001b[0;37mREADME.md\u001b[0m\r\n")
          append("\u001b[0;37mDockerfile\u001b[0m    \u001b[0;37mmain.py\u001b[0m       \u001b[0;37mtsconfig.json\u001b[0m\r\n")
        }
        _output.emit(files)
        printPrompt()
      }
      "git" -> {
        when (args.firstOrNull()) {
          "status" -> {
            val s = buildString {
              append("On branch \u001b[32mmain\u001b[0m\r\n")
              append("Your branch is up to date with 'origin/main'.\r\n\r\n")
              append("Changes not staged for commit:\r\n")
              append("  (use \"git add <file>...\" to update what will be committed)\r\n")
              append("	\u001b[31mmodified:   src/server.ts\u001b[0m\r\n")
              append("	\u001b[31mmodified:   src/routes/api.ts\u001b[0m\r\n\r\n")
              append("Untracked files:\r\n")
              append("	\u001b[31msrc/auth/jwt.ts\u001b[0m\r\n\r\n")
              append("no changes added to commit (use \"git add\" to track)\r\n")
            }
            _output.emit(s)
          }
          "log" -> {
            val log = buildString {
              append("\u001b[33m8f3a9d2\u001b[0m \u001b[1;32m(HEAD -> main, origin/main)\u001b[0m feat: add remote SSH stream support\r\n")
              append("\u001b[33m4b1e7c0\u001b[0m fix: handle pty resize event gracefully\r\n")
              append("\u001b[33m2d90f5a\u001b[0m refactor: optimize ANSI color parsing loop\r\n")
              append("\u001b[33mc63821b\u001b[0m initial commit\r\n")
            }
            _output.emit(log)
          }
          "pull" -> {
            _output.emit("Updating 8f3a9d2..e041ab9\r\nFast-forward\r\n src/core.ts | 12 \u001b[32m++++++\u001b[31m------\u001b[0m\r\n 1 file changed, 6 insertions(+), 6 deletions(-)\r\n")
          }
          else -> _output.emit("git: try 'git status', 'git log' or 'git pull'\r\n")
        }
        printPrompt()
      }
      "npm" -> {
        if (args.joinToString(" ").contains("run dev")) {
          _output.emit("\u001b[1;36m> my-project@1.0.0 dev\u001b[0m\r\n\u001b[1;36m> vite --host\u001b[0m\r\n\r\n")
          _output.emit("  \u001b[32m➜\u001b[0m  \u001b[1mLocal\u001b[0m:   \u001b[36mhttp://localhost:3000/\u001b[0m\r\n")
          _output.emit("  \u001b[32m➜\u001b[0m  \u001b[1mNetwork\u001b[0m: \u001b[36mhttp://192.168.1.100:3000/\u001b[0m\r\n")
          _output.emit("  \u001b[32m➜\u001b[0m  press \u001b[1mh + enter\u001b[0m to show help\r\n\r\n")
        } else {
          _output.emit("npm version 10.8.2\r\n")
        }
        printPrompt()
      }
      "htop", "top" -> {
        liveJob = scope.launch(Dispatchers.Default) {
          _output.emit("\u001b[2J\u001b[H")
          val header = buildString {
            append("\u001b[1;32m1  [|||||||||||||||||||||||||         48.2%]   Tasks: 78, 126 thr; 1 running\u001b[0m\r\n")
            append("\u001b[1;32m2  [||||||||||||||                    28.5%]   Load average: 1.15 0.98 0.76\u001b[0m\r\n")
            append("\u001b[1;34mMem[||||||||||||||||||||||||    8.42G/31.2G]   Uptime: 4 days, 16:32:10\u001b[0m\r\n")
            append("\u001b[1;37mSwp[|                             256M/8.00G]\u001b[0m\r\n\r\n")
            append("\u001b[7m  PID USER      PRI  NI  VIRT   RES   SHR S CPU% MEM%   TIME+  Command         \u001b[0m\r\n")
            append(" 1492 dev        20   0 14.2G  1.2G 98320 S 14.5  3.8  2:42.12 node server.js  \r\n")
            append(" 1024 dev        20   0  2.1G  420M 65430 S  8.2  1.3  1:15.30 nvim src/app.rs \r\n")
            append("  820 root       20   0 18.5G  3.1G  1.2G S  4.0  9.9  6:18.45 dockerd         \r\n")
            append(" 2401 dev        20   0 48.2M 8420K 4120K R  1.2  0.1  0:00.32 htop            \r\n")
            append("\r\n\u001b[1;33m[按快捷键 Ctrl+C 停止监控并返回终端]\u001b[0m\r\n")
          }
          _output.emit(header)
        }
      }
      "cargo" -> {
        _output.emit("   \u001b[1;32mCompiling\u001b[0m my_rust_app v0.1.0\r\n   \u001b[1;32mFinished\u001b[0m dev [unoptimized + debuginfo] target(s) in 1.42s\r\n     \u001b[1;32mRunning\u001b[0m `target/debug/my_rust_app`\r\nHello from Rust on remote machine!\r\n")
        printPrompt()
      }
      "python3", "python" -> {
        _output.emit("Python 3.12.3 (main, Feb 4 2026, 12:00:00) [GCC 14.1.0] on linux\r\n>>> print('RoamSH Connected successfully!')\r\nRoamSH Connected successfully!\r\n")
        printPrompt()
      }
      "docker" -> {
        val d = buildString {
          append("CONTAINER ID   IMAGE          COMMAND                  CREATED         STATUS         PORTS\r\n")
          append("a481c9e83120   postgres:16    \"docker-entrypoint.s…\"   2 days ago      Up 2 days      0.0.0.0:5432->5432/tcp\r\n")
          append("f190e29d71c4   redis:7-alpine \"docker-entrypoint.s…\"   2 days ago      Up 2 days      0.0.0.0:6379->6379/tcp\r\n")
        }
        _output.emit(d)
        printPrompt()
      }
      "cat" -> {
        val target = args.firstOrNull() ?: ""
        if (target.contains("readme", ignoreCase = true)) {
          _output.emit("# Dev Workstation\r\n\r\nRemote development workspace configured for high productivity via SSH.\r\nStack: Node.js, Rust, Docker, Neovim.\r\n")
        } else {
          _output.emit("cat: $target: No such file or directory\r\n")
        }
        printPrompt()
      }
      "clear" -> {
        _output.emit("\u001b[2J\u001b[H")
        printPrompt()
      }
      "pwd" -> {
        _output.emit("/home/dev/projects/dev-workspace\r\n")
        printPrompt()
      }
      "whoami" -> {
        _output.emit("$username\r\n")
        printPrompt()
      }
      "ollama" -> {
        when (args.firstOrNull()) {
          "run" -> {
            val model = args.getOrNull(1) ?: "deepseek-r1:8b"
            _output.emit("pulling manifest \r\npulling 901e... 100% ▕████████████████▏ 4.9 GB\r\nverifying sha256 digest \r\nwriting manifest \r\nsuccess \r\n\u001b[1;36m>>> 正在与 $model 交互模型通信 (按 Ctrl+D 退出):\u001b[0m\r\n\u001b[1;32m<think>\r\n用户正在通过 RoamSH 远程终端连接电脑，体验大模型编程交互。\r\n</think>\r\n你好！我是 $model 模型，已成功在你的远程电脑上加载，随时为你编写代码和解答技术问题！\r\n")
          }
          "ps" -> {
            val s = buildString {
              append("NAME                 ID              SIZE      PROCESSOR    UNTIL\r\n")
              append("deepseek-r1:8b       4f57a6270634    5.2 GB    100% GPU     4 minutes from now\r\n")
              append("qwen2.5-coder:7b     8b9e64e9a112    4.7 GB    100% GPU     12 minutes from now\r\n")
            }
            _output.emit(s)
          }
          "list" -> {
            val s = buildString {
              append("NAME                    ID              SIZE      MODIFIED\r\n")
              append("deepseek-r1:8b          4f57a6270634    4.9 GB    2 hours ago\r\n")
              append("qwen2.5-coder:7b        8b9e64e9a112    4.7 GB    1 day ago\r\n")
              append("llama3.2:3b             a80c4f17b550    2.0 GB    3 days ago\r\n")
            }
            _output.emit(s)
          }
          else -> _output.emit("Ollama CLI: try 'ollama run deepseek-r1:8b', 'ollama ps' or 'ollama list'\r\n")
        }
        printPrompt()
      }
      "nvidia-smi" -> {
        val s = buildString {
          append("\u001b[1;32m+-----------------------------------------------------------------------------------------+\u001b[0m\r\n")
          append("| NVIDIA-SMI 550.54.14              Driver Version: 550.54.14      CUDA Version: 12.4     |\r\n")
          append("|-----------------------------------------+------------------------+----------------------+\r\n")
          append("| GPU  Name                 Persistence-M | Bus-Id          Disp.A | Volatile Uncorr. ECC |\r\n")
          append("| Fan  Temp   Perf          Pwr:Usage/Cap |           Memory-Usage | GPU-Util  Compute M. |\r\n")
          append("|=========================================+========================+======================|\r\n")
          append("|   0  NVIDIA GeForce RTX 4090        Off |   00000000:01:00.0  On |                  N/A |\r\n")
          append("| 38%   46C    P2             85W / 450W |    \u001b[1;33m9420MiB / 24564MiB\u001b[0m |     \u001b[1;32m34%\u001b[0m      Default |\r\n")
          append("+-----------------------------------------+------------------------+----------------------+\r\n")
        }
        _output.emit(s)
        printPrompt()
      }
      "nvtop" -> {
        liveJob = scope.launch(Dispatchers.Default) {
          _output.emit("\u001b[2J\u001b[H")
          val s = buildString {
            append("\u001b[1;36m[NVTOP - NVIDIA GPU Process Monitor]\u001b[0m\r\n")
            append("GPU0 [RTX 4090]:  GPU [||||||||||||           34%]  MEM [|||||||||||||||| 9.42G/24G]\r\n\r\n")
            append("  PID USER     GPU%   MEM%   TYPE  COMMAND\r\n")
            append(" 3120 dev      32.0   21.5   C+G   ollama runner (deepseek-r1:8b)\r\n")
            append(" 2814 dev       2.0    4.2   C     python3 -m vllm.entrypoints.openai\r\n\r\n")
            append("\u001b[1;33m[按 Ctrl+C 退出 GPU 监控]\u001b[0m\r\n")
          }
          _output.emit(s)
        }
      }
      "aider" -> {
        _output.emit("\u001b[1;32mAider v0.72.0 (AI Pair Programming in your terminal)\u001b[0m\r\nModel: deepseek/deepseek-chat with diff edit format\r\nGit repo: .git with 48 files\r\n\u001b[1;36mUse /help to see commands, /add to add files, or enter your coding task.\u001b[0m\r\n> \r\n")
        printPrompt()
      }
      "claude" -> {
        if (args.any { it.contains("dangerously-skip-permissions") }) {
          val isClaudeCode = args.any { it.equals("code", ignoreCase = true) }
          val toolName = if (isClaudeCode) "Claude Code" else "Claude"
          val s = buildString {
            append("\u001b[1;33m⚠️  [$toolName 危险免确认模式已激活: --dangerously-skip-permissions]\u001b[0m\r\n")
            append("\u001b[1;32m   已跳过所有命令执行与文件修改的交互确认，Agent 自主全权执行！\u001b[0m\r\n")
            append("Claude Code v1.0.18 (Research Preview)\r\n")
            append("Loaded workspace: /home/dev/projects/dev-workspace\r\n")
            append("claude> \r\n")
          }
          _output.emit(s)
        } else {
          _output.emit("\u001b[1;35mClaude Code CLI\u001b[0m\r\nWelcome! Type what you want to build (提示: 配合 --dangerously-skip-permissions 可免去手机端繁琐确认):\r\nclaude> \r\n")
        }
        printPrompt()
      }
      "code" -> {
        if (args.any { it.contains("serve-web") }) {
          val s = buildString {
            append("\u001b[1;36m* Visual Studio Code Server\u001b[0m\r\n")
            append("\u001b[1;32m* Server bound to 127.0.0.1:8000 (免确认条款已通过: --accept-server-license-terms)\u001b[0m\r\n")
            append("* Web UI available at \u001b[4;34mhttp://localhost:8000\u001b[0m\r\n")
            append("可在本应用「端口转发」页面将 8000 映射至手机，即可在手机浏览器全屏使用 VS Code！\r\n")
          }
          _output.emit(s)
        } else if (args.any { it.contains("tunnel") }) {
          _output.emit("\u001b[1;32m* Open this link in your browser to authenticate: https://github.com/login/device\u001b[0m\r\n* Tunnel active: my-computer-tunnel (免确认条款已通过)\r\n")
        } else {
          _output.emit("Opening VS Code in current directory (/home/dev/projects/dev-workspace)...\r\n")
        }
        printPrompt()
      }
      "agy", "agent" -> {
        val isSkip = args.any { it == "-y" || it.contains("yes") || it.contains("auto-approve") }
        val s = buildString {
          append("\u001b[1;32m[Agy / Autonomous Agent CLI]\u001b[0m\r\n")
          if (isSkip) {
            append("\u001b[1;33m⚡ 危险命令自动跳过确认: 已启用 (-y / --yes-always)\u001b[0m\r\n")
            append("\u001b[1;36m   系统已授予全自动执行权限，正在自主规划并执行代码任务...\u001b[0m\r\n")
          } else {
            append("Agent running. (提示: 添加 -y 或 --yes-always 可免去每次操作确认)\r\n")
          }
          append("agy> \r\n")
        }
        _output.emit(s)
        printPrompt()
      }
      "interpreter" -> {
        _output.emit("\u001b[1;36m▌ Open Interpreter (Auto-run: -y)\u001b[0m\r\nInterpreter will automatically execute code without asking permission.\r\nType your task:\r\n> \r\n")
        printPrompt()
      }
      "grok" -> {
        val isSkip = args.any { it == "-y" || it.contains("dangerously") || it.contains("yes") }
        val s = buildString {
          append("\u001b[1;37mGrok CLI v2.1\u001b[0m\r\n")
          if (isSkip) {
            append("\u001b[1;33m⚡ 免确认模式已激活: -y / --dangerously-skip-confirmation\u001b[0m\r\n")
            append("\u001b[1;32m   跳过所有运行权限拦截，自动批准所有危险系统命令！\u001b[0m\r\n")
          }
          append("grok> \r\n")
        }
        _output.emit(s)
        printPrompt()
      }
      "echo" -> {
        val message = args.joinToString(" ").removeSurrounding("\"").removeSurrounding("'")
        _output.emit("$message\r\n")
        printPrompt()
      }
      "y", "yes" -> {
        _output.emit("\u001b[1;32m[已确认执行]\u001b[0m 操作已继续。\r\n")
        printPrompt()
      }
      "n", "no" -> {
        _output.emit("\u001b[1;31m[已取消操作]\u001b[0m\r\n")
        printPrompt()
      }
      "a", "all" -> {
        _output.emit("\u001b[1;32m[已全部同意]\u001b[0m 批量操作已继续。\r\n")
        printPrompt()
      }
      "uptime" -> {
        val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _output.emit(" $now up 4 days, 16:32,  1 user,  load average: 1.15, 0.98, 0.76\r\n")
        printPrompt()
      }
      "uname" -> {
        _output.emit("Linux $hostname 6.6.14-arch1-1 #1 SMP PREEMPT_DYNAMIC x86_64 GNU/Linux\r\n")
        printPrompt()
      }
      else -> {
        _output.emit("bash: $program: command not found (输入 'help' 查看模拟指令)\r\n")
        printPrompt()
      }
    }
  }

  fun close() {
    isRunning = false
    liveJob?.cancel()
  }
}
