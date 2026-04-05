package me.voltual.vb.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.voltual.fridainjector.FridaAgent
import me.voltual.fridainjector.FridaInjector
import me.voltual.fridainjector.OnMessage
import me.voltual.vb.core.database.dao.ScriptDao
import me.voltual.vb.core.database.entity.ScriptEntry

enum class RootStatus {
    UNKNOWN,
    GRANTING,
    GRANTED,
    DENIED
}

class HomeViewModel(
    private val scriptDao: ScriptDao,
    private val context: Context
) : ViewModel(), OnMessage {

    // 使用 mutableStateOf 并显式导入 getValue/setValue
    var rootStatus by mutableStateOf(RootStatus.UNKNOWN)
        private set

    val allScripts: StateFlow<List<ScriptEntry>> = scriptDao.getAllScripts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    var selectedScript by mutableStateOf<ScriptEntry?>(null)
    var targetPackage by mutableStateOf("")

    var isInjecting by mutableStateOf(false)
        private set

    val logs = mutableStateListOf<String>()

    init {
        checkRootPermission()
    }

    fun checkRootPermission() {
        viewModelScope.launch {
            rootStatus = RootStatus.GRANTING
            val isRoot = withContext(Dispatchers.IO) {
                try {
                    Shell.getShell().isRoot
                } catch (e: Exception) {
                    false
                }
            }
            rootStatus = if (isRoot) RootStatus.GRANTED else RootStatus.DENIED

            if (isRoot) {
                logs.add(0, "[System] Root 权限已获取")
            } else {
                logs.add(0, "[Error] 未获得 Root 权限，注入功能将无法使用")
            }
        }
    }

    override fun onMessage(data: String?) {
        data?.let {
            logs.add(0, "[Frida] $it")
        }
    }

    fun performInjection() {
    if (rootStatus != RootStatus.GRANTED) {
        logs.add(0, "[Error] 请先授予 Root 权限")
        checkRootPermission()
        return
    }

    val script = selectedScript
    val pkg = targetPackage

    if (script == null || pkg.isBlank()) {
        logs.add(0, "[Error] 请先选择脚本并输入包名")
        return
    }

    isInjecting = true
    logs.add(0, "[System] 正在尝试注入: $pkg ...")

    viewModelScope.launch(Dispatchers.IO) {
        try {
            val injector = FridaInjector.Builder(context)
                .withArm64Injector("frida-inject-17.9.1-android-arm64")
                .build()

            // 关键：设置日志回调，将 frida-inject 的输出重定向到 UI
            injector.loggingCallback = { msg ->
                withContext(Dispatchers.Main) {
                    logs.add(0, msg)
                }
            }

            val agent = FridaAgent.Builder(context)
                .withAgentFromString(script.content)
                .withOnMessage(this@HomeViewModel)
                .build()

            injector.inject(agent, pkg, spawn = true)

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                logs.add(0, "[Error] 注入失败: ${e.message}")
                e.printStackTrace()
            }
        } finally {
            withContext(Dispatchers.Main) {
                isInjecting = false
            }
        }
    }
}

    fun clearLogs() {
        logs.clear()
    }
}