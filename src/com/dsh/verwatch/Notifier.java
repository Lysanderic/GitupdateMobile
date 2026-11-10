package com.dsh.verwatch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** 通知。 */
public class Notifier {

    public static final String CHANNEL = "verwatch";
    private static final int ID = 1001;

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "版本更新", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("每天 10:00 检查已选应用的 GitHub 最新版本");
        nm.createNotificationChannel(ch);
    }

    public static boolean canNotify(Context c) {
        if (Build.VERSION.SDK_INT < 33) return true;
        return c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }

    public static void show(Context c, String title, String text) {
        if (!canNotify(c)) return;
        ensureChannel(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(c, 0, i, flags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(c, CHANNEL);
        else b = new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.stat_notify_sync);
        b.setContentTitle(title);
        b.setContentText(text);
        b.setStyle(new Notification.BigTextStyle().bigText(text));
        b.setAutoCancel(true);
        b.setShowWhen(true);
        b.setContentIntent(pi);
        try {
            nm.notify(ID, b.build());
        } catch (Exception ignored) {
        }
    }
}
