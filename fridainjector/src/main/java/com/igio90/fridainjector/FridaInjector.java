package com.igio90.fridainjector;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FridaInjector {
    private final Context mContext;
    private final File mInjector;

    // 静态块初始化 libsu 的配置（可选）
    static {
        Shell.setDefaultBuilder(Shell.Builder.create()
            .setFlags(Shell.FLAG_REDIRECT_STDERR)
            .setTimeout(10));
    }

    private FridaInjector(FridaInjector.Builder builder) {
        mContext = builder.mContext;
        mInjector = builder.getInjector();
    }

    private boolean isProcessRunning(String packageName) {
        // 使用 pidof 检查进程是否存在，这是最快的方式
        List<String> out = Shell.cmd("pidof " + packageName).exec().getOut();
        return !out.isEmpty();
    }

    public void inject(FridaAgent fridaAgent, String packageName, boolean spawn) {
        if (mInjector == null) {
            throw new RuntimeException("did you forget to call init()?");
        }

        if (!isProcessRunning(packageName)) {
            spawn = true;
        }

        StringBuilder agent = new StringBuilder(fridaAgent.getWrappedAgent());

        if (!fridaAgent.getInterfaces().isEmpty()) {
            try {
                ApplicationInfo ownAi = fridaAgent.getPackageManager().getApplicationInfo(
                        fridaAgent.getPackageName(), 0);
                String ownApk = ownAi.publicSourceDir;
                ApplicationInfo targetAi = fridaAgent.getPackageManager().getApplicationInfo(packageName, 0);
                
                File targetApkFile = new File(targetAi.publicSourceDir);
                String targetPath = targetApkFile.getParent();

                // libsu 处理挂载和拷贝更加优雅
                Shell.Job job = Shell.cmd();
                if (targetPath.startsWith("/system/")) {
                    job.add("mount -o remount,rw /system");
                }
                
                job.add("cp " + ownApk + " " + targetPath + "/xd.apk");
                job.add("chmod 644 " + targetPath + "/xd.apk");
                
                if (targetPath.startsWith("/system/")) {
                    job.add("chown root:root " + targetPath + "/xd.apk");
                    job.add("mount -o remount,ro /system");
                } else {
                    job.add("chown system:system " + targetPath + "/xd.apk");
                }
                job.exec();

                agent.append(FridaAgent.sRegisterClassLoaderAgent);

                for (LinkedHashMap.Entry<String, Class<? extends FridaInterface>> entry :
                        fridaAgent.getInterfaces().entrySet()) {
                    agent.append("Java['")
                            .append(entry.getKey())
                            .append("'] = function() {")
                            .append("var defaultClassLoader = Java.classFactory.loader;")
                            .append("Java.classFactory.loader = Java.classFactory['xd_loader'];")
                            .append("var clazz = Java.use('")
                            .append(entry.getValue().getName())
                            .append("').$new();")
                            .append("var args = [];")
                            .append("for (var i=0;i<arguments.length;i++) {")
                            .append("args[i] = arguments[i]")
                            .append("}")
                            .append("clazz.call(Java.array('java.lang.Object', args));")
                            .append("Java.classFactory.loader = defaultClassLoader;")
                            .append("};")
                            .append("\n");
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }

        File fridaAgentFile = new File(fridaAgent.getFilesDir(), "wrapped_agent.js");
        Utils.writeToFile(fridaAgentFile, agent.toString());
        Shell.cmd("chmod 777 " + fridaAgentFile.getPath()).exec();

        if (spawn) {
            Intent launchIntent = mContext.getPackageManager().getLaunchIntentForPackage(packageName);
            // 杀进程
            Shell.cmd("am force-stop " + packageName).exec();
            
            new Thread(() -> {
                long start = System.currentTimeMillis();
                while (!isProcessRunning(packageName)) {
                    try {
                        Thread.sleep(250);
                        if (System.currentTimeMillis() - start > TimeUnit.SECONDS.toMillis(5)) {
                            throw new RuntimeException("wait timeout for process spawn");
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                executeInjectCommand(packageName, fridaAgentFile.getPath());
            }).start();

            if (launchIntent != null) {
                mContext.startActivity(launchIntent);
            }
        } else {
            executeInjectCommand(packageName, fridaAgentFile.getPath());
        }
    }

    private void executeInjectCommand(String packageName, String agentPath) {
        Shell.cmd(mInjector.getPath() + " -n " + packageName +
                " -s " + agentPath + " --runtime=v8 -e").exec();
    }

    public static class Builder {
        private final Context mContext;
        private String mArmBinaryPath;
        private String mArm64BinaryPath;
        private String mX86BinaryPath;
        private String mX86_64BinaryPath;
        private File mInjector;

        public Builder(Context context) {
            // libsu 会在第一次调用 Shell.getShell() 时弹出授权申请
            if (!Shell.getShell().isRoot()) {
                throw new RuntimeException("failed to obtain root permissions or device not rooted");
            }
            mContext = context;
        }

        public Builder withArmInjector(String armInjectorBinaryAssetName) {
            mArmBinaryPath = armInjectorBinaryAssetName;
            return this;
        }

        public Builder withArm64Injector(String arm64InjectorBinaryAssetName) {
            mArm64BinaryPath = arm64InjectorBinaryAssetName;
            return this;
        }

        public Builder withX86Injector(String x86InjectorBinaryAssetName) {
            mX86BinaryPath = x86InjectorBinaryAssetName;
            return this;
        }

        public Builder withX86_64Injector(String x86_64InjectorBinaryAssetName) {
            mX86_64BinaryPath = x86_64InjectorBinaryAssetName;
            return this;
        }

        public FridaInjector build() throws IOException {
            if (mArmBinaryPath == null && mArm64BinaryPath == null &&
                    mX86BinaryPath == null && mX86_64BinaryPath == null) {
                throw new RuntimeException("injector asset file name not provided");
            }

            String arch = getArch();
            String injectorName = null;
            switch (arch) {
                case "arm": injectorName = mArmBinaryPath; break;
                case "arm64": injectorName = mArm64BinaryPath; break;
                case "x86": injectorName = mX86BinaryPath; break;
                case "x86_64": injectorName = mX86_64BinaryPath; break;
            }

            if (injectorName == null) {
                throw new RuntimeException("injector binary not provided for arch: " + arch);
            }

            mInjector = extractInjectorIfNeeded(mContext, injectorName);
            return new FridaInjector(this);
        }

        private File getInjector() {
            return mInjector;
        }
    }

    private static File extractInjectorIfNeeded(Context context, String name) throws IOException {
        File injectorPath = new File(context.getFilesDir(), "injector");
        File injector = new File(injectorPath, name);

        if (!injectorPath.exists()) {
            injectorPath.mkdir();
        } else {
            File[] files = injectorPath.listFiles();
            if (files != null && files.length > 0) {
                if (files[0].getName().equals(name)) {
                    return injector;
                }
                files[0].delete();
            }
        }

        Utils.extractAsset(context, name, injector);
        Shell.cmd("chmod 777 " + injector.getPath()).exec();
        return injector;
    }

    private static String getArch() {
        for (String androidArch : Build.SUPPORTED_ABIS) {
            switch (androidArch) {
                case "arm64-v8a": return "arm64";
                case "armeabi-v7a": return "arm";
                case "x86_64": return "x86_64";
                case "x86": return "x86";
            }
        }
        throw new RuntimeException("Unable to determine arch");
    }
}