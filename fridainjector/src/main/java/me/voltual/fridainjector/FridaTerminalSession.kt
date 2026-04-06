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

    fun start(columns: Int = 80, rows: Int = 24) {
        val fullCommand = arrayOf("sh", "-c", "$command ${args.joinToString(" ")}")
        val cwd = context.filesDir.absolutePath
        val env = arrayOf("TERM=xterm-256color")
        
        // 注意：TerminalSession 是 Java 类，不能使用命名参数，必须按顺序传参
        session = TerminalSession(
            "/system/bin/sh",   // shellPath
            cwd,                // cwd
            fullCommand,        // args
            env,                // env
            100,                // transcriptRows
            this                // client
        )
        
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

    // ---------- TerminalSessionClient 回调 ----------
    override fun onTextChanged(session: TerminalSession) {
        // 输出在 onCopyTextToClipboard 中处理
    }

    override fun onTitleChanged(session: TerminalSession) {}

    override fun onSessionFinished(session: TerminalSession) {
        onExit(session.exitStatus)
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
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