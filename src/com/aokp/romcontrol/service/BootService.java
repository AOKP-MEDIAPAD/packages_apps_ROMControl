package com.aokp.romcontrol.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import com.aokp.romcontrol.R;

import com.aokp.romcontrol.fragments.performance.CPUSettings;
import com.aokp.romcontrol.fragments.performance.Voltage;
import com.aokp.romcontrol.fragments.performance.VoltageControlSettings;
import com.aokp.romcontrol.util.CMDProcessor;
import com.aokp.romcontrol.util.Helpers;

public class BootService extends Service {

    public static boolean servicesStarted = false;
    public static SharedPreferences preferences;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
        }
        new BootWorker(this).execute();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    class BootWorker extends AsyncTask<Void, Void, Void> {

        Context c;

        public BootWorker(Context c) {
            this.c = c;
        }

        @Override
        protected Void doInBackground(Void... args) {

            Context c = getApplicationContext();
            preferences = PreferenceManager.getDefaultSharedPreferences(c);
            final CMDProcessor cmd = new CMDProcessor();
            if (HeadphoneService.getUserHeadphoneAudioMode(c) != -1
                    || HeadphoneService.getUserBTAudioMode(c) != -1) {
                c.startService(new Intent(c, HeadphoneService.class));
            }

            if (FlipService.getUserFlipAudioMode(c) != -1
                    || FlipService.getUserCallSilent(c) != 0)
                c.startService(new Intent(c, FlipService.class));

            if (Settings.System
                    .getBoolean(getContentResolver(), Settings.System.USE_WEATHER, false)) {
                sendLastWeatherBroadcast();
                getApplicationContext().startService(new Intent(c, WeatherRefreshService.class));
            }

            if (preferences.getBoolean("cpu_boot", false)) {
                final String max = preferences.getString(
                        "max_cpu", null);
                final String min = preferences.getString(
                        "min_cpu", null);
                final String gov = preferences.getString(
                        "gov", null);
                final String io = preferences.getString("io", null);
                if (max != null && min != null && gov != null) {
                    cmd.su.runWaitFor("busybox echo " + max +
                            " > " + CPUSettings.MAX_FREQ);
                    cmd.su.runWaitFor("busybox echo " + min +
                            " > " + CPUSettings.MIN_FREQ);
                    cmd.su.runWaitFor("busybox echo " + gov +
                            " > " + CPUSettings.GOVERNOR);
                    cmd.su.runWaitFor("busybox echo " + io +
                            " > " + CPUSettings.IO_SCHEDULER);
                    if (new File("/sys/devices/system/cpu/cpu1").exists()) {
                        cmd.su.runWaitFor("busybox echo " + max +
                                " > " + CPUSettings.MAX_FREQ
                                .replace("cpu0", "cpu1"));
                        cmd.su.runWaitFor("busybox echo " + min +
                                " > " + CPUSettings.MIN_FREQ
                                .replace("cpu0", "cpu1"));
                        cmd.su.runWaitFor("busybox echo " + gov +
                                " > " + CPUSettings.GOVERNOR
                                .replace("cpu0", "cpu1"));
                    }
                    if (new File("/sys/devices/system/cpu/cpu2").exists()) {
                        cmd.su.runWaitFor("busybox echo " + max +
                                " > " + CPUSettings.MAX_FREQ
                                .replace("cpu0", "cpu2"));
                        cmd.su.runWaitFor("busybox echo " + min +
                                " > " + CPUSettings.MIN_FREQ
                                .replace("cpu0", "cpu2"));
                        cmd.su.runWaitFor("busybox echo " + gov +
                                " > " + CPUSettings.GOVERNOR
                                .replace("cpu0", "cpu2"));
                    }
                    if (new File("/sys/devices/system/cpu/cpu3").exists()) {
                        cmd.su.runWaitFor("busybox echo " + max +
                                " > " + CPUSettings.MAX_FREQ
                                .replace("cpu0", "cpu3"));
                        cmd.su.runWaitFor("busybox echo " + min +
                                " > " + CPUSettings.MIN_FREQ
                                .replace("cpu0", "cpu3"));
                        cmd.su.runWaitFor("busybox echo " + gov +
                                " > " + CPUSettings.GOVERNOR
                                .replace("cpu0", "cpu3"));
                    }
                }
            }

            if (preferences.getBoolean(VoltageControlSettings
                    .KEY_APPLY_BOOT, false)) {
                final List<Voltage> volts = VoltageControlSettings
                        .getVolts(preferences);

                 for (final Voltage volt : volts) {
                    	   cmd.su.runWaitFor("busybox echo '"	
	                    + volt.getFreq() + " " + volt.getSavedMV()
                            + "' > " + VoltageControlSettings.MV_TABLE0);
                 }
            }

            if (preferences.getBoolean("fast_charge_boot", false)) {
                try {
                    File fastcharge = new File("/sys/kernel/fastcharge",
                            "force_fast_charge");
                    FileWriter fwriter = new FileWriter(fastcharge);
                    BufferedWriter bwriter = new BufferedWriter(fwriter);
                    bwriter.write("1");
                    bwriter.close();
                    Intent i = new Intent();
                    i.setAction("com.aokp.romcontrol.FCHARGE_CHANGED");
                    getApplicationContext().sendBroadcast(i);
                } catch (IOException e) {
                }

                // add notification to warn user they can only charge
                CharSequence contentTitle = getApplicationContext()
                        .getText(R.string.fast_charge_notification_title);
                CharSequence contentText = getApplicationContext()
                        .getText(R.string.fast_charge_notification_message);

                Notification n = new Notification.Builder(getApplicationContext())
                        .setAutoCancel(true)
                        .setContentTitle(contentTitle)
                        .setContentText(contentText)
                        .setSmallIcon(R.drawable.ic_rom_control_general)
                        .setWhen(System.currentTimeMillis())
                        .getNotification();

                NotificationManager nm = (NotificationManager) getApplicationContext()
                        .getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(1337, n);
            } else {
                try {
                    File fastcharge = new File("/sys/kernel/fastcharge",
                            "force_fast_charge");
                    FileWriter fwriter = new FileWriter(fastcharge);
                    BufferedWriter bwriter = new BufferedWriter(fwriter);
                    bwriter.write("0");
                    bwriter.close();
                    Intent i = new Intent();
                    i.setAction("com.aokp.romcontrol.FCHARGE_CHANGED");
                    getApplicationContext().sendBroadcast(i);
                } catch (IOException e) {
                }
            }

            if (preferences.getBoolean("free_memory_boot", false)) {
                final String values = preferences.getString(
                        "free_memory", null);
                if (!values.equals(null)) {
                    cmd.su.runWaitFor("busybox echo " + values +
                            " > /sys/module/lowmemorykiller/parameters/minfree");
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            servicesStarted = true;
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
