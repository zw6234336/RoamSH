package com.example.ui.terminal

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

data class CompletionResult(
  val newTextFieldValue: TextFieldValue,
  val candidates: List<CandidateItem> = emptyList(),
  val isSingleExactMatch: Boolean = false,
  val hasNoLocalMatch: Boolean = false,
  val completedToken: String? = null,
  val feedbackMessage: String? = null
)

data class CandidateItem(
  val text: String,
  val displayText: String = text,
  val type: CandidateType = CandidateType.COMMAND,
  val isDirectory: Boolean = false
)

enum class CandidateType {
  COMMAND,
  SUBCOMMAND,
  FILE,
  DIRECTORY,
  PATH,
  OPTION
}

object CommandCompletionEngine {

  val ALL_COMMANDS = listOf(
    // System & Service Management
    "systemctl", "journalctl", "service", "cron", "crontab", "reboot", "shutdown",
    "dmesg", "sysctl", "hostnamectl", "timedatectl", "logrotate", "ufw", "iptables",
    // Files & Directories
    "ls", "cd", "pwd", "cat", "less", "more", "head", "tail", "cp", "mv", "rm",
    "mkdir", "rmdir", "touch", "find", "locate", "grep", "diff", "chmod", "chown",
    "chgrp", "ln", "tree", "tar", "gzip", "gunzip", "zip", "unzip", "stat", "file",
    "wc", "sort", "uniq", "sed", "awk", "cut", "tee", "which", "whereis", "nano", "vim", "vi",
    // Networking
    "curl", "wget", "ssh", "scp", "rsync", "ping", "traceroute", "netstat", "ss",
    "ip", "ifconfig", "dig", "nslookup", "host", "nc", "nmap", "tcpdump", "telnet",
    // Process & System Status
    "ps", "top", "htop", "kill", "killall", "pkill", "pgrep", "free", "df", "du",
    "uname", "uptime", "lsof", "fuser", "lscpu", "lsblk", "lspci", "lsusb", "nvidia-smi",
    // Package Managers
    "apt", "apt-get", "dpkg", "yum", "dnf", "rpm", "pacman", "snap", "flatpak", "brew", "apk",
    // Development, Runtimes & Containers
    "git", "docker", "docker-compose", "podman", "kubectl", "helm", "node", "npm",
    "npx", "pnpm", "yarn", "bun", "python", "python3", "pip", "pip3", "cargo",
    "rustc", "go", "make", "cmake", "gcc", "g++", "java", "mvn", "gradle",
    // Modern AI & Developer Tools
    "ollama", "claude", "agy", "aider", "interpreter", "tmux", "screen", "jq", "yq",
    "zoxide", "fzf", "rg", "fd", "bat", "eza", "neofetch", "fastfetch",
    // Shell Builtins
    "alias", "export", "source", "echo", "printf", "env", "history", "clear",
    "exit", "sudo", "su", "whoami", "id", "man", "help"
  ).sorted().distinct()

  val SUBCOMMANDS: Map<String, List<String>> = mapOf(
    "git" to listOf(
      "status", "log", "diff", "add", "commit", "push", "pull", "fetch",
      "checkout", "branch", "merge", "rebase", "stash", "clone", "reset",
      "remote", "tag", "cherry-pick", "switch", "restore", "show", "init"
    ),
    "docker" to listOf(
      "ps", "images", "run", "stop", "start", "restart", "exec", "logs",
      "compose", "build", "rm", "rmi", "pull", "push", "inspect", "network",
      "volume", "stats", "system"
    ),
    "docker-compose" to listOf(
      "up", "down", "ps", "logs", "build", "restart", "stop", "exec", "pull"
    ),
    "systemctl" to listOf(
      "status", "restart", "start", "stop", "reload", "enable", "disable",
      "daemon-reload", "is-active", "is-enabled", "mask", "unmask", "list-units"
    ),
    "journalctl" to listOf(
      "-u", "-f", "-xe", "-n", "--since", "--until", "-r", "-b"
    ),
    "kubectl" to listOf(
      "get", "describe", "logs", "apply", "delete", "exec", "port-forward",
      "create", "edit", "rollout", "config"
    ),
    "npm" to listOf(
      "run dev", "install", "test", "build", "start", "add", "remove",
      "update", "audit", "init", "publish"
    ),
    "pnpm" to listOf(
      "run dev", "install", "test", "build", "start", "add", "remove",
      "update", "audit", "init"
    ),
    "yarn" to listOf(
      "dev", "add", "remove", "install", "build", "start", "test", "run"
    ),
    "cargo" to listOf(
      "run", "build", "test", "check", "add", "update", "clippy", "fmt", "clean", "new", "init"
    ),
    "apt" to listOf(
      "install", "update", "upgrade", "remove", "purge", "search", "show", "autoremove", "clean"
    ),
    "apt-get" to listOf(
      "install", "update", "upgrade", "remove", "purge", "search", "show", "autoremove", "clean"
    ),
    "ollama" to listOf(
      "run deepseek-r1:8b", "run qwen2.5-coder:7b", "list", "ps", "pull", "rm", "serve", "show"
    ),
    "tail" to listOf("-f", "-n 50", "-n 100", "-n 20"),
    "head" to listOf("-n 20", "-n 50"),
    "ls" to listOf("-la", "-lh", "-l", "-a", "-t", "-r", "-R", "-h"),
    "ps" to listOf("aux", "-ef", "-e", "-u"),
    "chmod" to listOf("+x", "755", "644", "777", "-R"),
    "tar" to listOf("-zxvf", "-zcvf", "-xvf", "-cvf", "-jxvf")
  )



  /**
   * Extract potential filenames, paths, and identifiers from recent terminal screen buffer.
   */
  fun extractWordsFromTerminal(lines: List<String>): List<String> {
    val result = mutableSetOf<String>()
    val wordRegex = Regex("""[a-zA-Z0-9_\-\.\/]+""")
    lines.takeLast(100).forEach { line ->
      wordRegex.findAll(line).forEach { match ->
        val word = match.value.trim()
        if (word.length >= 2 && !word.all { it.isDigit() }) {
          result.add(word)
          if (!word.contains('.') && !word.endsWith('/')) {
            result.add("$word/")
          }
        }
      }
    }
    return result.toList()
  }

  /**
   * Performs in-place completion directly inside TextFieldValue with support for cycleIndex.
   */
  fun complete(
    currentValue: TextFieldValue,
    recentTerminalLines: List<String> = emptyList(),
    cycleIndex: Int = 0
  ): CompletionResult {
    val text = currentValue.text
    val cursor = currentValue.selection.end.coerceIn(0, text.length)

    if (text.isBlank()) {
      // For empty input, return top common Linux commands as quick candidate suggestions
      val quickStart = listOf(
        CandidateItem("ls -la", "ls -la", CandidateType.COMMAND),
        CandidateItem("cd ", "cd", CandidateType.COMMAND),
        CandidateItem("git status", "git status", CandidateType.COMMAND),
        CandidateItem("docker ps", "docker ps", CandidateType.COMMAND),
        CandidateItem("systemctl status ", "systemctl status", CandidateType.COMMAND),
        CandidateItem("htop", "htop", CandidateType.COMMAND),
        CandidateItem("cat ", "cat", CandidateType.COMMAND),
        CandidateItem("tail -f ", "tail -f", CandidateType.COMMAND),
        CandidateItem("df -h", "df -h", CandidateType.COMMAND),
        CandidateItem("free -m", "free -m", CandidateType.COMMAND)
      )
      val chosen = quickStart[cycleIndex % quickStart.size]
      val newTfv = TextFieldValue(chosen.text, TextRange(chosen.text.length))
      return CompletionResult(
        newTextFieldValue = newTfv,
        candidates = quickStart,
        isSingleExactMatch = false,
        completedToken = chosen.text,
        feedbackMessage = "快捷指令: ${chosen.displayText}"
      )
    }

    val prefixBeforeCursor = text.substring(0, cursor)
    val textAfterCursor = text.substring(cursor)

    val lastSpaceIndex = prefixBeforeCursor.lastIndexOf(' ')
    val tokenStartIndex = lastSpaceIndex + 1
    val currentToken = prefixBeforeCursor.substring(tokenStartIndex)

    val terminalWords = extractWordsFromTerminal(recentTerminalLines)

    // Case 1: First token -> Command completion (or path if starts with / or ~ or ./)
    if (tokenStartIndex == 0) {
      if (currentToken.startsWith("/") || currentToken.startsWith("~") || currentToken.startsWith("./") || currentToken.startsWith("../")) {
        return completePath(currentValue, tokenStartIndex, currentToken, textAfterCursor, terminalWords, cycleIndex)
      }

      val matches = ALL_COMMANDS
        .filter { it.startsWith(currentToken, ignoreCase = true) }
        .map { CandidateItem(it, it, CandidateType.COMMAND) }

      return buildResult(currentValue, tokenStartIndex, currentToken, textAfterCursor, matches, cycleIndex)
    }

    // Case 2: Argument completion (Subcommand or File/Path)
    val commandLineBeforeToken = prefixBeforeCursor.substring(0, lastSpaceIndex).trim()
    val commandTokens = commandLineBeforeToken.split(" ").filter { it.isNotBlank() }
    val primaryCommand = commandTokens.firstOrNull()?.lowercase() ?: ""

    // Check if user is typing a path (starts with / or ~ or ./)
    if (currentToken.startsWith("/") || currentToken.startsWith("~") || currentToken.startsWith("./") || currentToken.startsWith("../")) {
      return completePath(currentValue, tokenStartIndex, currentToken, textAfterCursor, terminalWords, cycleIndex)
    }

    // Special handling for cd: prioritize directories from terminal screen
    if (primaryCommand == "cd") {
      val dirCandidates = terminalWords.filter { it.endsWith('/') || !it.contains('.') }.distinct()
      val dirMatches = dirCandidates
        .filter { it.startsWith(currentToken, ignoreCase = true) }
        .map { CandidateItem(it, it, CandidateType.DIRECTORY, isDirectory = true) }

      if (dirMatches.isNotEmpty()) {
        return buildResult(currentValue, tokenStartIndex, currentToken, textAfterCursor, dirMatches, cycleIndex)
      }
    }

    // Check subcommand matches if this tool has registered subcommands
    val subList = SUBCOMMANDS[primaryCommand]
    if (subList != null && commandTokens.size == 1) {
      val matches = subList
        .filter { it.startsWith(currentToken, ignoreCase = true) }
        .map { CandidateItem(it, it, CandidateType.SUBCOMMAND) }

      if (matches.isNotEmpty()) {
        return buildResult(currentValue, tokenStartIndex, currentToken, textAfterCursor, matches, cycleIndex)
      }
    }

    // File / Directory completion from terminal buffer words
    val pathMatches = terminalWords
      .filter { it.startsWith(currentToken, ignoreCase = true) }
      .map {
        val isDir = it.endsWith("/")
        CandidateItem(it, it, if (isDir) CandidateType.DIRECTORY else CandidateType.FILE, isDir)
      }

    if (pathMatches.isNotEmpty()) {
      return buildResult(currentValue, tokenStartIndex, currentToken, textAfterCursor, pathMatches, cycleIndex)
    }

    // General fallback: match any subcommands or terminal words
    val generalCandidates = (terminalWords + (SUBCOMMANDS[primaryCommand] ?: emptyList())).distinct()
    val generalMatches = generalCandidates
      .filter { it.startsWith(currentToken, ignoreCase = true) }
      .map { CandidateItem(it, it, CandidateType.OPTION) }

    return buildResult(currentValue, tokenStartIndex, currentToken, textAfterCursor, generalMatches, cycleIndex)
  }

  private fun completePath(
    currentValue: TextFieldValue,
    tokenStartIndex: Int,
    currentToken: String,
    textAfterCursor: String,
    terminalWords: List<String>,
    cycleIndex: Int
  ): CompletionResult {
    val allPathPool = terminalWords.filter { it.contains('/') }.distinct()
    val matches = allPathPool
      .filter { it.startsWith(currentToken, ignoreCase = true) }
      .map {
        val isDir = it.endsWith("/")
        CandidateItem(it, it, if (isDir) CandidateType.DIRECTORY else CandidateType.PATH, isDir)
      }

    return buildResult(currentValue, tokenStartIndex, currentToken, textAfterCursor, matches, cycleIndex)
  }

  fun buildResult(
    currentValue: TextFieldValue,
    tokenStartIndex: Int,
    currentToken: String,
    textAfterCursor: String,
    matches: List<CandidateItem>,
    cycleIndex: Int
  ): CompletionResult {
    if (matches.isEmpty()) {
      return CompletionResult(currentValue, emptyList(), isSingleExactMatch = false, hasNoLocalMatch = true, feedbackMessage = "无本地候选")
    }

    if (matches.size == 1) {
      val single = matches.first()
      val suffix = if (single.isDirectory || single.text.endsWith('/')) "" else " "
      val replacement = single.text + suffix
      val newText = currentValue.text.substring(0, tokenStartIndex) + replacement + textAfterCursor
      val newCursor = tokenStartIndex + replacement.length
      val newTfv = TextFieldValue(newText, TextRange(newCursor))
      return CompletionResult(newTfv, emptyList(), isSingleExactMatch = true, completedToken = single.text, feedbackMessage = "已补全: ${single.text}")
    }

    // Multiple matches -> check if LCP extends what user typed on the 0th cycle
    val matchStrings = matches.map { it.text }
    val lcp = longestCommonPrefix(matchStrings)

    if (cycleIndex == 0 && lcp.length > currentToken.length) {
      val suffix = if (lcp.endsWith('/')) "" else ""
      val replacement = lcp + suffix
      val newText = currentValue.text.substring(0, tokenStartIndex) + replacement + textAfterCursor
      val newCursor = tokenStartIndex + replacement.length
      val newTfv = TextFieldValue(newText, TextRange(newCursor))
      return CompletionResult(
        newTextFieldValue = newTfv,
        candidates = matches,
        isSingleExactMatch = false,
        completedToken = lcp,
        feedbackMessage = "已补全前缀: $lcp (共 ${matches.size} 个候选，再点切换)"
      )
    }

    // Directly cycle through candidate items directly into the input box!
    val selectedIndex = cycleIndex % matches.size
    val selected = matches[selectedIndex]

    val suffix = if (selected.isDirectory || selected.text.endsWith('/')) "" else " "
    val replacement = selected.text + suffix
    val newText = currentValue.text.substring(0, tokenStartIndex) + replacement + textAfterCursor
    val newCursor = tokenStartIndex + replacement.length
    val newTfv = TextFieldValue(newText, TextRange(newCursor))

    val feedback = "补全 (${selectedIndex + 1}/${matches.size}): ${selected.displayText} (再点切换)"
    return CompletionResult(
      newTextFieldValue = newTfv,
      candidates = matches,
      isSingleExactMatch = false,
      completedToken = selected.text,
      feedbackMessage = feedback
    )
  }

  /**
   * Applies a specific selected candidate chip into the TextFieldValue.
   */
  fun applyCandidate(
    currentValue: TextFieldValue,
    candidate: CandidateItem
  ): TextFieldValue {
    val text = currentValue.text
    val cursor = currentValue.selection.end.coerceIn(0, text.length)
    val prefixBeforeCursor = text.substring(0, cursor)
    val lastSpaceIndex = prefixBeforeCursor.lastIndexOf(' ')
    val tokenStartIndex = lastSpaceIndex + 1
    val textAfterCursor = text.substring(cursor)

    val suffix = if (candidate.isDirectory || candidate.text.endsWith('/')) "" else " "
    val replacement = candidate.text + suffix
    val newText = text.substring(0, tokenStartIndex) + replacement + textAfterCursor
    val newCursor = tokenStartIndex + replacement.length
    return TextFieldValue(newText, TextRange(newCursor))
  }

  private fun longestCommonPrefix(strings: List<String>): String {
    if (strings.isEmpty()) return ""
    var prefix = strings[0]
    for (i in 1 until strings.size) {
      while (!strings[i].startsWith(prefix, ignoreCase = true)) {
        prefix = prefix.substring(0, prefix.length - 1)
        if (prefix.isEmpty()) return ""
      }
    }
    return prefix
  }
}
