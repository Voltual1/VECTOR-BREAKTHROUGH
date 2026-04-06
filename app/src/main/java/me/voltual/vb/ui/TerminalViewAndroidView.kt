package me.voltual.vb.ui

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

@Composable
fun TerminalViewAndroidView(
    modifier: Modifier = Modifier,
    onSessionCreated: (TerminalSession) -> Unit = {}
) {
    AndroidView(
        factory = { ctx ->
            TerminalView(ctx, null).apply {
                // 必须设置文本大小，否则 mRenderer 为 null 导致崩溃
                setTextSize(128)

                setTerminalViewClient(object : TerminalViewClient {
                    override fun onScale(scale: Float) = 1f
                    override fun onSingleTapUp(e: MotionEvent) = Unit
                    override fun shouldBackButtonBeMappedToEscape() = false
                    override fun shouldEnforceCharBasedInput() = false
                    override fun shouldUseCtrlSpaceWorkaround() = false
                    override fun isTerminalViewSelected() = true
                    override fun copyModeChanged(copyMode: Boolean) = Unit
                    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession) = false
                    override fun onKeyUp(keyCode: Int, e: KeyEvent) = false
                    override fun onLongPress(event: MotionEvent) = false
                    override fun readControlKey() = false
                    override fun readAltKey() = false
                    override fun readShiftKey() = false
                    override fun readFnKey() = false
                    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession) = false
                    override fun onEmulatorSet() = Unit
                    override fun logError(tag: String, message: String) = Unit
                    override fun logWarn(tag: String, message: String) = Unit
                    override fun logInfo(tag: String, message: String) = Unit
                    override fun logDebug(tag: String, message: String) = Unit
                    override fun logVerbose(tag: String, message: String) = Unit
                    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Unit
                    override fun logStackTrace(tag: String, e: Exception) = Unit
                })

                val session = TerminalSession(
                    "/system/bin/sh",
                    "/data/local/tmp",
                    arrayOf("sh"),
                    arrayOf("TERM=xterm-256color"),
                    1000,
                    object : TerminalSessionClient {
                        override fun onTextChanged(session: TerminalSession) = Unit
                        override fun onTitleChanged(session: TerminalSession) = Unit
                        override fun onSessionFinished(session: TerminalSession) = Unit
                        override fun onCopyTextToClipboard(session: TerminalSession, text: String) = Unit
                        override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit
                        override fun onBell(session: TerminalSession) = Unit
                        override fun onColorsChanged(session: TerminalSession) = Unit
                        override fun onTerminalCursorStateChange(state: Boolean) = Unit
                        override fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit
                        override fun getTerminalCursorStyle(): Int? = null
                        override fun logError(tag: String, message: String) = Unit
                        override fun logWarn(tag: String, message: String) = Unit
                        override fun logInfo(tag: String, message: String) = Unit
                        override fun logDebug(tag: String, message: String) = Unit
                        override fun logVerbose(tag: String, message: String) = Unit
                        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Unit
                        override fun logStackTrace(tag: String, e: Exception) = Unit
                    }
                )
                attachSession(session)
                onSessionCreated(session)
            }
        },
        modifier = modifier
    )
}