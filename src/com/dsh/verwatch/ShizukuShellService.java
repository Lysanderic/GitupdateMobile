package com.dsh.verwatch;

import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 在 Shizuku 的用户服务进程里跑（uid 2000 / shell）。
 * 只做一件事：执行一条 shell 命令并回传输出。
 */
public class ShizukuShellService extends Binder {

    public static final String DESCRIPTOR = "com.dsh.verwatch.SHELL";

    public ShizukuShellService() {
    }

    public ShizukuShellService(android.content.Context context) {
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == 1) {
            data.enforceInterface(DESCRIPTOR);
            String cmd = data.readString();
            String out;
            try {
                out = exec(cmd);
            } catch (Throwable t) {
                out = "EXCEPTION " + t;
            }
            if (reply != null) {
                reply.writeNoException();
                reply.writeString(out);
            }
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }

    private String exec(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", cmd});
        String out = read(p.getInputStream());
        String err = read(p.getErrorStream());
        int code = p.waitFor();
        StringBuilder sb = new StringBuilder();
        sb.append("exit=").append(code).append("\n");
        if (out.length() > 0) sb.append(out);
        if (err.length() > 0) sb.append(err);
        return sb.toString();
    }

    private String read(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
        } catch (Exception ignored) {
        }
        return sb.toString();
    }
}
