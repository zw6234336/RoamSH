package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.HostDao
import com.example.data.dao.PortTunnelDao
import com.example.data.dao.SnippetDao
import com.example.data.dao.SshKeyDao
import com.example.data.model.AuthType
import com.example.data.model.HostConfig
import com.example.data.model.PortTunnel
import com.example.data.model.Snippet
import com.example.data.model.SshKey
import com.example.data.model.TerminalThemeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
  entities = [HostConfig::class, SshKey::class, Snippet::class, PortTunnel::class],
  version = 1,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun hostDao(): HostDao
  abstract fun sshKeyDao(): SshKeyDao
  abstract fun snippetDao(): SnippetDao
  abstract fun portTunnelDao(): PortTunnelDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "dev_ssh_database"
        )
          .addCallback(DatabaseCallback())
          .build()
        INSTANCE = instance
        instance
      }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
      override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        INSTANCE?.let { database ->
          CoroutineScope(Dispatchers.IO).launch {
            populateInitialData(database)
          }
        }
      }

      override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        INSTANCE?.let { database ->
          CoroutineScope(Dispatchers.IO).launch {
            ensureDefaultSnippets(database)
          }
        }
      }
    }

    private suspend fun populateInitialData(db: AppDatabase) {
      val sampleHost = HostConfig(
        id = UUID.randomUUID().toString(),
        name = "我的开发电脑 (示例)",
        host = "192.168.1.100",
        port = 22,
        username = "dev",
        authType = AuthType.PASSWORD,
        password = "",
        groupTag = "电脑",
        initialCommand = "",
        theme = TerminalThemeType.DRACULA,
        fontSize = 13,
        isStarred = true
      )
      db.hostDao().insertHost(sampleHost)
      ensureDefaultSnippets(db)
    }

    suspend fun ensureDefaultSnippets(db: AppDatabase) {
      val existing = try {
        db.snippetDao().getAllSnippets().first()
      } catch (_: Exception) {
        emptyList()
      }
      val existingTitles = existing.map { it.title }.toSet()

      val allDefaults = listOf(
        // AI Agent / 自动化编程 CLI (跳过确认 / 执行危险命令)
        Snippet(
          title = "Claude Code: 自主执行模式 (跳过所有权限确认)",
          command = "claude --dangerously-skip-permissions\n",
          category = "AI Agent (免确认)",
          autoExecute = true
        ),
        Snippet(
          title = "Claude Code: 快速任务提示 (自主执行)",
          command = "claude --dangerously-skip-permissions -p \"\"\n",
          category = "AI Agent (免确认)",
          autoExecute = false
        ),
        Snippet(
          title = "Aider: 全自动结对编程 (跳过所有确认)",
          command = "aider --yes --yes-always\n",
          category = "AI Agent (免确认)",
          autoExecute = true
        ),
        Snippet(
          title = "Agy / Agent: 自动执行模式 (-y)",
          command = "agy -y\n",
          category = "AI Agent (免确认)",
          autoExecute = true
        ),
        Snippet(
          title = "Open Interpreter: 自动执行终端代码 (-y)",
          command = "interpreter -y\n",
          category = "AI Agent (免确认)",
          autoExecute = true
        ),
        Snippet(
          title = "Grok CLI: 自动确认执行 (-y)",
          command = "grok -y\n",
          category = "AI Agent (免确认)",
          autoExecute = true
        ),
        Snippet(
          title = "VS Code Server: 免确认启动 Web 服务",
          command = "code serve-web --without-connection-token --accept-server-license-terms\n",
          category = "AI Agent (免确认)",
          autoExecute = true
        ),
        Snippet(
          title = "VS Code Tunnel: 启动远程隧道 (免确认)",
          command = "code tunnel --accept-server-license-terms\n",
          category = "AI Agent (免确认)",
          autoExecute = true
        ),
        Snippet(
          title = "AI 提示快捷确认 [Y] + 回车",
          command = "y\n",
          category = "AI Agent (免确认)",
          autoExecute = true
        ),
        Snippet(
          title = "AI 提示快捷拒绝 [N] + 回车",
          command = "n\n",
          category = "AI Agent (免确认)",
          autoExecute = true
        ),

        // 大模型 AI 模型服务
        Snippet(title = "Ollama 运行 DeepSeek-R1", command = "ollama run deepseek-r1:8b\n", category = "大模型 AI"),
        Snippet(title = "Ollama 运行 Qwen2.5-Coder", command = "ollama run qwen2.5-coder:7b\n", category = "大模型 AI"),
        Snippet(title = "Ollama 查看载入模型与显存", command = "ollama ps\n", category = "大模型 AI"),
        Snippet(title = "vLLM 启动模型 API 服务", command = "python3 -m vllm.entrypoints.openai.api_server --model Qwen/Qwen2.5-Coder-7B-Instruct --port 8000\n", category = "大模型 AI"),
        Snippet(title = "实时监控 GPU 显存 (nvidia-smi)", command = "watch -n 1 nvidia-smi\n", category = "大模型 AI"),
        Snippet(title = "交互式 GPU 监控 (nvtop)", command = "nvtop\n", category = "大模型 AI"),

        // Git
        Snippet(title = "Git 状态与最近提交", command = "git status -sb && git log --oneline -n 5\n", category = "Git"),
        Snippet(title = "Git 拉取最新代码", command = "git pull --rebase\n", category = "Git"),
        Snippet(title = "Git 暂存变更", command = "git diff --stat\n", category = "Git"),
        Snippet(title = "Git 切换分支", command = "git checkout ", category = "Git"),

        // Tmux & Vim
        Snippet(title = "Tmux 恢复或创建新会话", command = "tmux attach -t dev || tmux new -s dev\n", category = "Tmux/Vim"),
        Snippet(title = "Tmux 会话列表", command = "tmux ls\n", category = "Tmux/Vim"),
        Snippet(title = "打开 Neovim / Vim", command = "nvim .\n", category = "Tmux/Vim"),
        Snippet(title = "Vim 强制保存退出", command = ":wq!\n", category = "Tmux/Vim"),

        // Docker
        Snippet(title = "Docker 正在运行容器", command = "docker ps --format \"table {{.Names}}\\t{{.Status}}\\t{{.Ports}}\"\n", category = "Docker"),
        Snippet(title = "Docker Compose 启动", command = "docker compose up -d\n", category = "Docker"),
        Snippet(title = "Docker 实时日志追踪", command = "docker compose logs -f --tail=100\n", category = "Docker"),

        // Dev & Run
        Snippet(title = "Node/Web 前端开发服务", command = "npm run dev\n", category = "开发"),
        Snippet(title = "Python 运行脚本", command = "python3 main.py\n", category = "开发"),
        Snippet(title = "Cargo 构建运行 (Rust)", command = "cargo run\n", category = "开发"),
        Snippet(title = "Go 运行", command = "go run .\n", category = "开发"),

        // System & Monitor
        Snippet(title = "系统资源监视 (htop)", command = "htop\n", category = "系统"),
        Snippet(title = "查看磁盘占用 (df -h)", command = "df -h\n", category = "系统"),
        Snippet(title = "查看内存占用 (free -m)", command = "free -m -h\n", category = "系统")
      )

      val toInsert = allDefaults.filter { it.title !in existingTitles }
      if (toInsert.isNotEmpty()) {
        db.snippetDao().insertSnippets(toInsert)
      }
    }
  }
}
