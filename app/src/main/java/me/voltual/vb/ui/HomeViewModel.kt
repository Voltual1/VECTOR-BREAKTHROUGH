package me.voltual.vb.ui

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
    UNKNOWN,      // 尚未检查
    GRANTING,     // 正在请求 / 初始化中
    GRANTED,      // 已授权
    DENIED        // 被拒绝
}

class HomeViewModel(
    private val scriptDao: ScriptDao,
    private val context: Context
) : ViewModel(), OnMessage {

    // ========== Root 权限状态 ==========
    var rootStatus by mutableStateOf(RootStatus.UNKNOWN)
        private set

    // ========== 脚本相关 ==========
    val allScripts: StateFlow<List<ScriptEntry>> = scriptDao.getAllScripts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    var selectedScript by mutableStateOf<ScriptEntry?>(null)
    var targetPackage by mutableStateOf("")

    // ========== 注入状态 ==========
    var isInjecting by mutableStateOf(false)
        private set

    // ========== 日志 ==========
    val logs = mutableStateListOf<String>()

    init {
        checkRootPermission()
    }

    /**
     * 检查 Root 权限（libsu 会自动弹出授权窗口）
     */
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

    /**
     * 实现 OnMessage 接口，接收来自 Frida 脚本的 send() 消息
     */
    override fun onMessage(data: String?) {
        data?.let {
            logs.add(0, "[Frida] $it")
        }
    }

    /**
     * 执行注入
     */
    fun performInjection() {
        // 权限检查
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
                // 构建 Injector（根据你的设备架构选择对应的 binary）
                val injector = FridaInjector.Builder(context)
                    .withArm64Injector("frida-inject-17.9.1-android-arm64")
                    .build()

                // 构建 Agent
                val agent = FridaAgent.Builder(context)
                    .withAgentFromString(script.content)
                    .withOnMessage(this@HomeViewModel)
                    .build()

                // 执行注入（spawn = true 表示如果进程未运行则自动启动）
                injector.inject(agent, pkg, spawn = true)

                withContext(Dispatchers.Main) {
                    logs.add(0, "[Success] 注入指令已发送")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logs.add(0, "[Error] 注入失败: ${e.message}")
                }
                e.printStackTrace()
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