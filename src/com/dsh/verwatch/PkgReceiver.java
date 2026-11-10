package com.dsh.verwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.util.List;

/** 某个应用被安装/更新时，顺手把本机版本同步掉。 */
public class PkgReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String pkg = null;
        Uri d = intent.getData();
        if (d != null) pkg = d.getSchemeSpecificPart();
        List<Store.Entry> list = Store.load(c);
        PackageManager pm = c.getPackageManager();
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            Store.Entry e = list.get(i);
            if (pkg != null && !pkg.equals(e.pkg)) continue;
            try {
                String v = pm.getPackageInfo(e.pkg, 0).versionName;
                if (v != null && v.length() > 0 && !v.equals(e.installed)) {
                    e.installed = v;
                    changed = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (changed) Store.save(c, list);
    }
}
