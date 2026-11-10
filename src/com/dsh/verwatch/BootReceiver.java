package com.dsh.verwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机 / 改时间 / 覆盖安装后重排每天 10:00。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Scheduler.schedule(ctx.getApplicationContext());
    }
}
