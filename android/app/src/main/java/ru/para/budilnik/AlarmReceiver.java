package ru.para.budilnik;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Alarms.ACTION_FIRE.equals(intent.getAction())) return;
        String seriesId = intent.getStringExtra(Alarms.EXTRA_SERIES);
        long time = intent.getLongExtra(Alarms.EXTRA_TIME, System.currentTimeMillis());

        String label = "Будильник";
        for (Store.Series s : new Store(c).series()) {
            if (s.id.equals(seriesId)) { label = s.label; break; }
        }

        Intent svc = new Intent(c, AlarmService.class)
                .setAction(AlarmService.ACTION_RING)
                .putExtra(Alarms.EXTRA_SERIES, seriesId)
                .putExtra(Alarms.EXTRA_TIME, time)
                .putExtra(AlarmService.EXTRA_LABEL, label);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc); else c.startService(svc);
        } catch (RuntimeException e) {
            // Service start refused: at least show the alarm notification without sound loop.
            AlarmService.showFallback(c, seriesId, time, label);
        }
        if (seriesId != null && seriesId.startsWith("test-")) new Store(c).removeSeries(seriesId);
        Alarms.reschedule(c, time);
    }
}
