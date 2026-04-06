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
                setTextSize(16)
                
                // 启用文本选择（长按选择文本）
                // TerminalView 默认支持长按选择，不需要额外设置
                
                // 设置客户端
                setTerminalViewClient(object : TerminalViewClient {
                    override fun onScale(scale: Float): Float {
                        // 允许双指缩放，计算新字体大小
                        val newTextSize = (textSize * scale).coerceIn(8f, 48f)
                        setTextSize(newTextSize.toInt())
                        return scale
                    }
                    
                    override fun onSingleTapUp(e: MotionEvent) {
                        // 单点触控：取消文本选择模式（如果有的话）
                        if (isSelectingText) {
                            stopTextSelectionMode()
                        }
                    }
                    
                    override fun shouldBackButtonBeMappedToEscape() = false
                    override fun shouldEnforceCharBasedInput() = false
                    override fun shouldUseCtrlSpaceWorkaround() = false
                    override fun isTerminalViewSelected() = true
                    override fun copyModeChanged(copyMode: Boolean) = Unit
                    
                    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
                        // 处理复制粘贴快捷键（Ctrl+Shift+C 和 Ctrl+Shift+V）
                        if (e.isCtrlPressed && e.isShiftPressed) {
                            when (keyCode) {
                                KeyEvent.KEYCODE_C -> {
                                    val selectedText = getSelectedText()
                                    if (!selectedText.isNullOrEmpty()) {
                                        copyTextToClipboard(selectedText)
                                    }
                                    return true
                                }
                                KeyEvent.KEYCODE_V -> {
                                    pasteTextFromClipboard()
                                    return true
                                }
                            }
                        }
                        return false
                    }
                    
                    override fun onKeyUp(keyCode: Int, e: KeyEvent) = false
                    override fun onLongPress(event: MotionEvent): Boolean {
                        // 长按：启动文本选择模式
                        startTextSelectionMode(event)
                        return true
                    }
                    
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
                        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                            // 将文本复制到系统剪贴板
                            copyTextToClipboard(text)
                        }
                        override fun onPasteTextFromClipboard(session: TerminalSession?) {
                            pasteTextFromClipboard()
                        }
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

// 扩展函数：复制文本到剪贴板
private fun TerminalView.copyTextToClipboard(text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("Terminal Selection", text)
    clipboard.setPrimaryClip(clip)
}

// 扩展函数：从剪贴板粘贴文本
private fun TerminalView.pasteTextFromClipboard() {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = clipboard.primaryClip
    if (clip != null && clip.itemCount > 0) {
        val text = clip.getItemAt(0).coerceToText(context).toString()
        if (text.isNotEmpty()) {
            mTermSession?.write(text)
        }
    }
}