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
    log("[System] 正在使用 Frida 17.9.1 + QuickJS 优化逻辑...")

    // 1. 确保 SELinux 是 Permissive
    Shell.cmd("setenforce 0").exec()

    // 2. 根据你提供的源码和文档：
    // - 使用 -p [pid] 注入特定进程
    // - 使用 -s [script] 加载脚本
    // - 使用 -R qjs 强制使用 QuickJS (文档说这是默认，但我们显式指定更稳)
    // - 使用 -e (eternalize) 注入后脱离
    // 注意：移除 --runtime=v8，那是导致 17.x 在 Android 10 上报错误 4 的元凶
    
    val command = "cat /dev/null | ${injector.path} -p $pid -s $agentPath -R qjs -e"
    
    log("[System] 执行 17.x 指令: $command")

    val result = Shell.cmd(command).exec()
    
    if (result.isSuccess) {
        log("[Success] 17.9.1 注入指令已发送 (QuickJS 模式)")
    } else {
        log("[Error] 17.9.1 注入失败，错误码: ${result.code}")
        
        // 如果 QuickJS 也报错误 4，尝试完全不带运行时参数
        if (result.code == 4) {
            log("[System] 尝试不带运行时参数的最后方案...")
            val lastResort = "cat /dev/null | ${injector.path} -p $pid -s $agentPath -e"
            val lastResult = Shell.cmd(lastResort).exec()
            if (lastResult.isSuccess) {
                log("[Success] 自动运行时模式注入成功")
                return
            }
            log("[Error] 最终尝试也失败，错误码: ${lastResult.code}")
        }
    }
    
    result.out.forEach { log("[frida-out] $it") }
    result.err.forEach { log("[frida-err] $it") }
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