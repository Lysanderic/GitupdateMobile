package com.dsh.verwatch;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;

import java.io.File;

import rikka.shizuku.Shizuku;

/** Shizuku 连接 / 授权 / 用用户服务执行命令。 */
public class ShizukuHelper {

    public interface Callback {
        void onResult(boolean ok, String message);
    }

    private static boolean inited = false;
    private static Runnable onState;

    public static void setOnStateChanged(Runnable r) {
        onState = r;
    }

    private static void notifyState() {
        try {
            if (onState != null) onState.run();
        } catch (Throwable ignored) {
        }
    }

    public static void init(Context c) {
        if (inited) return;
        inited = true;
        try {
            Shizuku.addBinderReceivedListenerSticky(new Shizuku.OnBinderReceivedListener() {
                @Override
                public void onBinderReceived() {
                    notifyState();
                }
            });
            Shizuku.addBinderDeadListener(new Shizuku.OnBinderDeadListener() {
                @Override
                public void onBinderDead() {
                    notifyState();
                }
            });
            Shizuku.addRequestPermissionResultListener(new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    notifyState();
                }
            });
        } catch (Throwable ignored) {
        }
    }

    public static boolean alive() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean granted() {
        try {
            return alive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void request() {
        try {
            Shizuku.requestPermission(0);
        } catch (Throwable ignored) {
        }
    }

    public static String stateText() {
        try {
            if (!alive()) return "Shizuku 未连接";
            if (!granted()) return "Shizuku 待授权";
            return "Shizuku 已连接";
        } catch (Throwable t) {
            return "Shizuku 不可用";
        }
    }

    public static boolean usable() {
        return alive() && granted();
    }

    /** 用用户服务（shell 身份）执行一条命令，回调在主线程。 */
    public static void exec(final Context ctx, final String command, final Callback cb) {
        final Handler main = new Handler(Looper.getMainLooper());
        if (!usable()) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    cb.onResult(false, "Shizuku 未连接或未授权");
                }
            });
            return;
        }
        final boolean[] done = {false};
        final ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                if (done[0]) return;
                done[0] = true;
                String out;
                try {
                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    data.writeInterfaceToken(ShizukuShellService.DESCRIPTOR);
                    data.writeString(command);
                    binder.transact(1, data, reply, 0);
                    reply.readException();
                    out = reply.readString();
                    data.recycle();
                    reply.recycle();
                } catch (Throwable t) {
                    out = "EXCEPTION " + t;
                }
                try {
                    Shizuku.unbindUserService(userServiceArgs(ctx), this, true);
                } catch (Throwable ignored) {
                }
                final String result = out;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        boolean ok = result != null && result.startsWith("exit=0");
                        cb.onResult(ok, result);
                    }
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onResult(false, "Shizuku 服务断开");
                    }
                });
            }
        };
        final Runnable timeout = new Runnable() {
            @Override
            public void run() {
                if (done[0]) return;
                done[0] = true;
                try {
                    Shizuku.unbindUserService(userServiceArgs(ctx), conn, true);
                } catch (Throwable ignored) {
                }
                cb.onResult(false, "连接 Shizuku 服务超时（20 秒）");
            }
        };
        main.postDelayed(timeout, 20000);
        try {
            Shizuku.bindUserService(userServiceArgs(ctx), conn);
        } catch (Throwable t) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    cb.onResult(false, "绑定 Shizuku 服务失败：" + t);
                }
            });
        }
    }

    public static Shizuku.UserServiceArgs userServiceArgs(Context ctx) {
        return new Shizuku.UserServiceArgs(
                new ComponentName(ctx.getPackageName(), ShizukuShellService.class.getName()))
                .daemon(false)
                .processNameSuffix("shell")
                .debuggable(false)
                .version(1);
    }

    /** pm install 一个 APK 文件（文件需 shell 可读）。 */
    public static void installApk(final Context ctx, File apk, final Callback cb) {
        final String src = shellQuote(apk.getAbsolutePath());
        final String tmp = "/data/local/tmp/verwatch_" + System.currentTimeMillis() + ".apk";
        // system_server 读不到 /sdcard（SELinux fuse），先由 shell 复制到 /data/local/tmp 再装
        String cmd = "cp " + src + " " + tmp
                + " && /system/bin/pm install -r -t " + tmp
                + "; rc=$?; rm -f " + tmp + "; exit $rc";
        exec(ctx, cmd, cb);
    }

    public static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
