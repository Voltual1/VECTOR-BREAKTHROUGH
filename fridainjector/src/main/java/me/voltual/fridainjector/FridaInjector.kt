package me.voltual.fridainjector

import android.content.Context
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

    companion object {
        init {
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(10)
            )
        }

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
        val out = Shell.cmd("pidof $packageName").exec().out
        return out.isNotEmpty()
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
            }
        }

        val fridaAgentFile = File(fridaAgent.getFilesDir(), "wrapped_agent.js")
        Utils.writeToFile(fridaAgentFile, agentScript.toString())
        Shell.cmd("chmod 777 ${fridaAgentFile.path}").exec()

        if (shouldSpawn) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            Shell.cmd("am force-stop $packageName").exec()

            thread {
                val start = System.currentTimeMillis()
                while (!isProcessRunning(packageName)) {
                    try {
                        Thread.sleep(250)
                        if (System.currentTimeMillis() - start > TimeUnit.SECONDS.toMillis(5)) {
                            throw RuntimeException("wait timeout for process spawn")
                        }
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
                executeInjectCommand(packageName, fridaAgentFile.path)
            }

            launchIntent?.let {
                context.startActivity(it)
            }
        } else {
            executeInjectCommand(packageName, fridaAgentFile.path)
        }
    }

    private fun executeInjectCommand(packageName: String, agentPath: String) {
        Shell.cmd("${injector.path} -n $packageName -s $agentPath --runtime=v8 -e").exec()
    }

    class Builder(val context: Context) {
        private var armBinaryPath: String? = null
        private var arm64BinaryPath: String? = null
        private var x86BinaryPath: String? = null
        private var x86_64BinaryPath: String? = null
        var injector: File? = null
            private set

        init {
            if (!Shell.getShell().isRoot) {
                throw RuntimeException("failed to obtain root permissions or device not rooted")
            }
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