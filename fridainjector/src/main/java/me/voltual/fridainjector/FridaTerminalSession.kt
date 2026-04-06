package me.voltual.fridainjector

import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

class FridaTerminalSession(
    context: Context,
    private val command: String,
    private val args: Array<String>,
    private val onOutput: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onExit: (Int) -> Unit
) : TerminalSessionClient {

    private var session: TerminalSession? = null
    private val outputBuffer = StringBuilder()
    private val errorBuffer = StringBuilder()

    fun start(columns: Int = 80, rows: Int = 24) {
        // 构建完整的命令数组: ["sh", "-c", "full command"]
        val fullCommand = arrayOf("sh", "-c", "$command ${args.joinToString(" ")}")
        
        session = TerminalSession(
            shellPath = "/system/bin/sh",
            cwd = context.filesDir.absolutePath,
            args = fullCommand,
            env = arrayOf("TERM=xterm-256color"),
            transcriptRows = 100,
            client = this
        )
        
        // 更新终端大小
        session?.updateSize(columns, rows, 10, 20)
    }

    fun write(data: String) {
        session?.write(data)
    }

    fun writeLine(data: String) {
        session?.write("$data\n")
    }

    fun finish() {
        session?.finishIfRunning()
    }

    // TerminalSessionClient 回调实现

    override fun onTextChanged(session: TerminalSession) {
        // 这里会收到所有输出，但我们需要区分 stdout 和 stderr
        // 由于 PTY 合并了 stdout 和 stderr，我们需要通过其他方式区分
        // 简单起见，我们全部当作 stdout 处理
    }

    override fun onTitleChanged(session: TerminalSession) {}

    override fun onSessionFinished(session: TerminalSession) {
        val exitCode = session.exitStatus
        onExit(exitCode)
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        // 可以选择将输出转发给 onOutput
        onOutput(text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {}

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {}

    override fun onTerminalCursorStateChange(state: Boolean) {}

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String, message: String) { onError("[Error] $message") }
    override fun logWarn(tag: String, message: String) { onError("[Warn] $message") }
    override fun logInfo(tag: String, message: String) { onOutput("[Info] $message") }
    override fun logDebug(tag: String, message: String) { onOutput("[Debug] $message") }
    override fun logVerbose(tag: String, message: String) { onOutput("[Verbose] $message") }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        onError("[$tag] $message: ${e.message}")
    }
    override fun logStackTrace(tag: String, e: Exception) {
        onError("[$tag] ${e.message}")
    }
}