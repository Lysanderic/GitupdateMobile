package com.dsh.verwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 每天 10:00 触发；检查完再排下一次。 */
public class CheckReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context ctx, Intent intent) {
        final PendingResult pr = goAsync();
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Core.runCheck(app, true);
                } catch (Throwable t) {
                    Notifier.show(app, "版本监视：检查失败", String.valueOf(t.getMessage()));
                } finally {
                    Scheduler.schedule(app);
                    pr.finish();
                }
            }
        }).start();
    }
}
