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
    private val injector: File = builder.injector ?: throw RuntimeException("did you forget to call init()?")

    // 日志回调，用于将 frida-inject 的输出传递给 UI
    var loggingCallback: ((String) -> Unit)? = null

    companion object {
        private fun extractInjectorIfNeeded(context: Context, name: String): File {
            val injectorPath = File(context.filesDir, "injector")
            val injector = File(injectorPath, name)

            if (!injectorPath.exists()) {
                injectorPath.mkdir()
            } else {
                val files = injectorPath.listFiles()
                if (!files.isNullOrEmpty()) {
                    if (files[0].name == name) {
                        return injector
                    }
                    files[0].delete()
                }
            }

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

    private fun isProcessRunning(packageName: String): Boolean {
        val result = Shell.cmd("pidof $packageName").exec()
        return result.out.isNotEmpty()
    }

    fun inject(fridaAgent: FridaAgent, packageName: String, spawn: Boolean = false) {
        var shouldSpawn = spawn
        if (!isProcessRunning(packageName)) {
            shouldSpawn = true
        }

        val agentScript = StringBuilder(fridaAgent.wrappedAgent)

        if (fridaAgent.interfaces.isNotEmpty()) {
            try {
                val ownAi = fridaAgent.getPackageManager().getApplicationInfo(fridaAgent.getPackageName(), 0)
                val ownApk = ownAi.publicSourceDir
                val targetAi = fridaAgent.getPackageManager().getApplicationInfo(packageName, 0)

                val targetApkFile = File(targetAi.publicSourceDir)
                val targetPath = targetApkFile.parent ?: ""

                val job = Shell.cmd()
                if (targetPath.startsWith("/system/")) {
                    job.add("mount -o remount,rw /system")
                }

                job.add("cp $ownApk $targetPath/xd.apk")
                job.add("chmod 644 $targetPath/xd.apk")

                if (targetPath.startsWith("/system/")) {
                    job.add("chown root:root $targetPath/xd.apk")
                    job.add("mount -o remount,ro /system")
                } else {
                    job.add("chown system:system $targetPath/xd.apk")
                }
                job.exec()

                agentScript.append("\n").append(FridaAgent.REGISTER_CLASS_LOADER_JS)

                for ((key, value) in fridaAgent.interfaces) {
                    agentScript.append("""
                        Java['$key'] = function() {
                            var defaultClassLoader = Java.classFactory.loader;
                            Java.classFactory.loader = Java.classFactory['xd_loader'];
                            var clazz = Java.use('${value.name}').${'$'}new();
                            var args = [];
                            for (var i=0; i<arguments.length; i++) {
                                args[i] = arguments[i];
                            }
                            clazz.call(Java.array('java.lang.Object', args));
                            Java.classFactory.loader = defaultClassLoader;
                        };
                    """.trimIndent()).append("\n")
                }
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                log("[Error] 无法获取目标应用信息: ${e.message}")
            }
        }

        val fridaAgentFile = File(fridaAgent.getFilesDir(), "wrapped_agent.js")
        Utils.writeToFile(fridaAgentFile, agentScript.toString())
        Shell.cmd("chmod 777 ${fridaAgentFile.path}").exec()

        if (shouldSpawn) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            log("[System] 正在停止目标应用...")
            Shell.cmd("am force-stop $packageName").exec()

            thread {
                val start = System.currentTimeMillis()
                while (!isProcessRunning(packageName)) {
                    try {
                        Thread.sleep(250)
                        if (System.currentTimeMillis() - start > TimeUnit.SECONDS.toMillis(10)) {
                            log("[Error] 等待进程启动超时 (10秒)")
                            return@thread
                        }
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }

                log("[System] 进程已发现，等待 Java 环境初始化 (2秒)...")
                Thread.sleep(2000) // 关键：等待 ART 虚拟机完全初始化

                executeInjectCommand(packageName, fridaAgentFile.path)
            }

            launchIntent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
                log("[System] 已启动目标 Activity")
            } ?: run {
                log("[Warning] 无法获取启动 Intent，目标可能是系统应用")
            }
        } else {
            executeInjectCommand(packageName, fridaAgentFile.path)
        }
    }

    private fun executeInjectCommand(packageName: String, agentPath: String) {
        log("[System] 执行注入指令...")
        
        // 注意：Frida 17.x 中 -e 是 --eval，需要后面跟代码。我们不需要，所以移除。
        // 使用 --runtime=v8 指定 V8 运行时
        val command = "${injector.path} -n $packageName -s $agentPath --runtime=v8"
        
        val result = Shell.cmd(command).exec()
        
        if (result.isSuccess) {
            log("[Success] frida-inject 命令执行成功")
        } else {
            log("[Error] frida-inject 返回错误码: ${result.code}")
        }
        
        // 输出所有 stdout 和 stderr
        result.out.forEach { line -> log("[frida-out] $line") }
        result.err.forEach { line -> log("[frida-err] $line") }
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