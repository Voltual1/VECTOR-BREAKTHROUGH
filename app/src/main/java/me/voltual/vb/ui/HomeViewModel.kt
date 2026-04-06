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
import com.termux.terminal.TerminalSession
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
    
    private var terminalSession: TerminalSession? = null

fun setTerminalSession(session: TerminalSession) {
    terminalSession = session
}

fun performInjectionInTerminal(session: TerminalSession) {
    val script = selectedScript
    val pkg = targetPackage
    if (script == null || pkg.isBlank()) return
    
    // 先确保 SELinux 是 Permissive 模式
    session.write("su -c 'setenforce 0'\n")   
        val injectorPath = "/data/user/0/${context.packageName}/files/injector/frida-inject-17.9.1-android-arm64"
        val agentPath = "/data/local/tmp/wrapped_agent.js"
        
        val command = "su -c \"$injectorPath -n $pkg -s $agentPath --runtime=qjs -e\"\n"
        session.write(command)
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

    fun clearLogs() {
        logs.clear()
    }
}