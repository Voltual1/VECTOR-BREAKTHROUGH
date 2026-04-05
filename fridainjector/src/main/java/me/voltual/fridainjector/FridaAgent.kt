package me.voltual.fridainjector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import java.io.File
import java.io.IOException

class FridaAgent private constructor(builder: Builder) {
    val wrappedAgent: String = builder.wrappedAgent ?: throw RuntimeException("no agent specified")
    val interfaces = LinkedHashMap<String, Class<out FridaInterface>>()
    private val context: Context = builder.context

    companion object {
        const val WRAPPER_JS = """
            // 重写 console.log，通过 Android 日志输出
            console.log = function() {
                var args = arguments;
                Java.perform(function() {
                    try {
                        var Log = Java.use('android.util.Log');
                        for (var i=0; i<args.length; i++) {
                            Log.e('FridaAndroidInject', String(args[i]));
                        }
                    } catch (e) {
                        // 如果 Log 类不可用，回退到原生的 console.log
                        console.log('Log error:', e);
                    }
                });
            };

            // 重写 Java.send，通过广播发送数据回宿主应用
            Java['send'] = function(data) {
                Java.perform(function () {
                    try {
                        var Intent = Java.use('android.content.Intent');
                        var ActivityThread = Java.use('android.app.ActivityThread');
                        var Context = Java.use('android.content.Context');
                        
                        var app = ActivityThread.currentApplication();
                        if (app == null) {
                            console.log('Application not ready yet, skipping send');
                            return;
                        }
                        
                        var ctx = Java.cast(app.getApplicationContext(), Context);
                        var intent = Intent.${'$'}new('com.frida.injector.SEND');
                        intent.putExtra('data', JSON.stringify(data));
                        ctx.sendBroadcast(intent);
                        console.log('Broadcast sent:', data);
                    } catch (e) {
                        console.log('Send failed: ' + e);
                    }
                });
            }
            
            console.log('Frida wrapper loaded. Script ready.');
        """

        const val REGISTER_CLASS_LOADER_JS = """
            Java.perform(function() {
                try {
                    var app = Java.use('android.app.ActivityThread').currentApplication();
                    var context = app.getApplicationContext();
                    var pm = context.getPackageManager();
                    var ai = pm.getApplicationInfo(context.getPackageName(), 0);
                    var apkPath = ai.publicSourceDir.value;
                    apkPath = apkPath.substring(0, apkPath.lastIndexOf('/')) + '/xd.apk';
                    var cl = Java.use('dalvik.system.DexClassLoader').${'$'}new(
                            apkPath, context.getCacheDir().getAbsolutePath(), null,
                            context.getClass().getClassLoader());
                    Java.classFactory['xd_loader'] = cl;
                    console.log('Custom class loader registered');
                } catch (e) {
                    console.log('Class loader registration failed: ' + e);
                }
            });
        """
    }

    fun getPackageManager(): PackageManager = context.packageManager
    fun getPackageName(): String = context.packageName
    fun getFilesDir(): File = context.filesDir

    fun registerInterface(cmd: String, fridaInterface: Class<out FridaInterface>) {
        interfaces[cmd] = fridaInterface
    }

    class Builder(val context: Context) {
        var wrappedAgent: String? = null
            private set
        private var onMessage: OnMessage? = null

        @Throws(IOException::class)
        fun withAgentFromAssets(agentPath: String): Builder {
            val agent = Utils.readFromFile(context.assets.open(agentPath))
            return withAgentFromString(agent)
        }

        fun withAgentFromString(agent: String): Builder {
            wrappedAgent = WRAPPER_JS + "\n" + agent
            return this
        }

        fun withOnMessage(onMessage: OnMessage): Builder {
            this.onMessage = onMessage
            return this
        }

        fun build(): FridaAgent {
            val agent = FridaAgent(this)
            onMessage?.let {
                val filter = IntentFilter("com.frida.injector.SEND")
                // 注意：在 Android 14+ 中可能需要指定 RECEIVER_EXPORTED
                // 但我们的广播是发送给自己应用的，所以不需要 exported
                context.registerReceiver(DataBroadcast(it), filter)
            }
            return agent
        }
    }

    private class DataBroadcast(private val onMessage: OnMessage) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val data = intent.getStringExtra("data")
            onMessage.onMessage(data)
        }
    }
}