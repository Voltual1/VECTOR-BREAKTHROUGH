package me.voltual.fridainjector

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class FridaInjector private constructor(builder: Builder) {
    private val context: Context = builder.context
    private val injector: File = builder.injector ?: throw RuntimeException("Injector not found")

    var loggingCallback: ((String) -> Unit)? = null

    companion object {
        private fun extractInjectorIfNeeded(context: Context, name: String): File {
            val injectorPath = File(context.filesDir, "injector")
            val injector = File(injectorPath, name)
            if (!injectorPath.exists()) injectorPath.mkdir()
            
            Utils.extractAsset(context, name, injector)
            Shell.cmd("chmod 777 ${injector.path}").exec()
            return injector
        }

        private val arch: String
            get() {
                for (androidArch in Build.SUPPORTED_ABIS) {
                    return when (androidArch) {
                        "arm64-v8a" -> "arm64"
                        "armeabi-v7a" -> "arm"
                        "x86_64" -> "x86_64"
                        "x86" -> "x86"
                        else -> continue
                    }
                }
                throw RuntimeException("Unable to determine arch")
            }
    }

    private fun getPid(packageName: String): String? {
        val result = Shell.cmd("pidof $packageName").exec()
        return result.out.firstOrNull()?.trim()
    }

    fun inject(fridaAgent: FridaAgent, packageName: String, spawn: Boolean = false) {
        val agentScript = StringBuilder(fridaAgent.wrappedAgent)
        // ... (省略 interfaces 处理部分，保持不变) ...

        // 1. 将脚本写入 /data/local/tmp (Frida 的黄金目录)
        val tempAgentPath = "/data/local/tmp/wrapped_agent.js"
        val localFile = File(context.filesDir, "temp_agent.js")
        Utils.writeToFile(localFile, agentScript.toString())
        
        log("[System] 准备环境...")
        Shell.cmd("cp ${localFile.path} $tempAgentPath").exec()
        Shell.cmd("chmod 777 $tempAgentPath").exec()
        Shell.cmd("chmod 777 ${injector.path}").exec()

        // 2. 关键：暂时放宽 SELinux 限制
        log("[System] 放宽 SELinux 策略...")
        Shell.cmd("setenforce 0").exec()

        if (spawn || getPid(packageName) == null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            log("[System] 正在重启应用: $packageName")
            Shell.cmd("am force-stop $packageName").exec()

            thread {
                val start = System.currentTimeMillis()
                var pid: String? = null
                while (pid == null) {
                    try {
                        Thread.sleep(500)
                        pid = getPid(packageName)
                        if (System.currentTimeMillis() - start > 10000) {
                            log("[Error] 等待进程启动超时")
                            return@thread
                        }
                    } catch (e: Exception) {}
                }

                log("[System] 发现 PID: $pid，等待 2.5 秒初始化...")
                Thread.sleep(2500) 
                
                executeInjectCommand(packageName, pid, tempAgentPath)
            }

            launchIntent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
        } else {
            val pid = getPid(packageName)
            if (pid != null) {
                executeInjectCommand(packageName, pid, tempAgentPath)
            }
        }
    }

    private fun executeInjectCommand(packageName: String, pid: String, agentPath: String) {
        log("[System] 尝试连接 PID: $pid ...")
        
        // 使用 PID (-p) 注入比包名 (-n) 更稳定
        val command = "${injector.path} -p $pid -s $agentPath --runtime=v8"
        
        val result = Shell.cmd(command).exec()
        
        if (result.isSuccess) {
            log("[Success] 注入完成!")
        } else {
            log("[Error] 注入失败，错误码: ${result.code}")
            if (result.code == 4) {
                log("[Tip] 错误码 4 通常是权限问题，请检查 Magisk 是否授予了完整 Root。")
            }
        }
        
        result.out.forEach { log("[frida-out] $it") }
        result.err.forEach { log("[frida-err] $it") }
        
        // 注入完成后可以恢复 SELinux (可选)
        // Shell.cmd("setenforce 1").exec()
    }
    
    private fun log(msg: String) {
        loggingCallback?.invoke(msg)
    }

    class Builder(val context: Context) {
        private var armBinaryPath: String? = null
        private var arm64BinaryPath: String? = null
        private var x86BinaryPath: String? = null
        private var x86_64BinaryPath: String? = null
        var injector: File? = null
            private set

        init {
            // 注意：这里不再检查 root，因为我们在 Application 中已经初始化了 Shell
            // 如果 Shell 不可用，后续操作会失败
        }

        fun withArmInjector(assetName: String) = apply { armBinaryPath = assetName }
        fun withArm64Injector(assetName: String) = apply { arm64BinaryPath = assetName }
        fun withX86Injector(assetName: String) = apply { x86BinaryPath = assetName }
        fun withX86_64Injector(assetName: String) = apply { x86_64BinaryPath = assetName }

        @Throws(IOException::class)
        fun build(): FridaInjector {
            val selectedName = when (arch) {
                "arm" -> armBinaryPath
                "arm64" -> arm64BinaryPath
                "x86" -> x86BinaryPath
                "x86_64" -> x86_64BinaryPath
                else -> null
            } ?: throw RuntimeException("injector binary not provided for arch: $arch")

            injector = extractInjectorIfNeeded(context, selectedName)
            return FridaInjector(this)
        }
    }
}