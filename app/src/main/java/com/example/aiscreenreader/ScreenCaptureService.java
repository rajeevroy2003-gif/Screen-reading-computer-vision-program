package com.example.aiscreenreader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID =
        "screen_capture_channel";

    private static final int NOTIFICATION_ID = 101;

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationManager manager =
            getSystemService(NotificationManager.class);

        NotificationChannel channel =
            new NotificationChannel(
                CHANNEL_ID,
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            );

        manager.createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(
        Intent intent,
        int flags,
        int startId
    ) {

        Notification notification =
            new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AI Screen Reader")
                .setContentText("Screen capture is active")
                .setSmallIcon(
                    android.R.drawable.ic_menu_view
                )
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            );
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
