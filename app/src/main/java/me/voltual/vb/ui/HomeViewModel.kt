package me.voltual.vb.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import me.voltual.fridainjector.FridaAgent
import me.voltual.fridainjector.FridaInjector
import me.voltual.fridainjector.OnMessage
import me.voltual.vb.core.database.dao.ScriptDao
import me.voltual.vb.core.database.entity.ScriptEntry

class HomeViewModel(
    private val scriptDao: ScriptDao,
    private val context: Context
) : ViewModel(), OnMessage {

    // 状态：所有可用脚本
    val allScripts: StateFlow<List<ScriptEntry>> = scriptDao.getAllScripts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 状态：当前 UI 输入
    var selectedScript by mutableStateOf<ScriptEntry?>(null)
    var targetPackage by mutableStateOf("")
    var isInjecting by mutableStateOf(false)
    
    // 状态：日志输出
    val logs = mutableStateListOf<String>()

    override fun onMessage(data: String?) {
        data?.let {
            logs.add(0, "[Frida] $it") // 新日志放在最上面
        }
    }

    fun performInjection() {
        val script = selectedScript
        val pkg = targetPackage
        
        if (script == null || pkg.isBlank()) {
            logs.add(0, "[Error] 请先选择脚本并输入包名")
            return
        }

        isInjecting = true
        logs.add(0, "[System] 正在尝试注入: $pkg ...")

        try {
            // 1. 构建 Injector (假设 assets 目录下有对应的二进制文件)
            // 根据你的 tree.txt，文件名是 frida-inject-17.9.1-android-arm64
            val injector = FridaInjector.Builder(context)
                .withArm64Injector("frida-inject-17.9.1-android-arm64")
                .build()

            // 2. 构建 Agent (从数据库内容构建)
            val agent = FridaAgent.Builder(context)
                .withAgentFromString(script.content)
                .withOnMessage(this)
                .build()

            // 3. 执行注入 (spawn 模式默认为 true，因为你提到了输入包名)
            injector.inject(agent, pkg, spawn = true)
            
            logs.add(0, "[Success] 注入指令已发送")
        } catch (e: Exception) {
            logs.add(0, "[Error] 注入失败: ${e.message}")
            e.printStackTrace()
        } finally {
            isInjecting = false
        }
    }
    
    fun clearLogs() {
        logs.clear()
    }
}