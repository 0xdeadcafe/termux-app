package com.termux.shared.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class NotificationUtils {

    private static final String LOG_TAG = "NotificationUtils";

    /**
     * Get the {@link NotificationManager}.
     *
     * @param context The {@link Context} for operations.
     * @return Returns the {@link NotificationManager}.
     */
    @Nullable
    public static NotificationManager getNotificationManager(final Context context) {
        if (context == null) return null;
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Get {@link NotificationCompat.Builder}.
     *
     * @param context The {@link Context} for operations.
     * @param channelId The channel id for the notification.
     * @param priority The priority for the notification.
     * @param title The title for the notification.
     * @param notificationText The second line text of the notification.
     * @param notificationBigText The full text of the notification that may optionally be styled.
     * @param contentIntent The {@link PendingIntent} which should be sent when notification is clicked.
     * @param deleteIntent The {@link PendingIntent} which should be sent when notification is deleted.
     * @return Returns the {@link NotificationCompat.Builder}, or {@code null} if {@code context} is null.
     */
    @Nullable
    public static NotificationCompat.Builder getNotificationBuilder(
        final Context context, final String channelId, final int priority, final CharSequence title,
        final CharSequence notificationText, final CharSequence notificationBigText,
        final PendingIntent contentIntent, final PendingIntent deleteIntent) {
        if (context == null) return null;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);
        builder.setContentTitle(title);
        builder.setContentText(notificationText);
        if (notificationBigText != null) {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(notificationBigText));
        }
        builder.setContentIntent(contentIntent);
        builder.setDeleteIntent(deleteIntent);
        builder.setPriority(priority);
        return builder;
    }

    /**
     * Setup the notification channel.
     *
     * @param context The {@link Context} for operations.
     * @param channelId The id of the channel. Must be unique per package.
     * @param channelName The user visible name of the channel.
     * @param importance The importance of the channel. This controls how interruptive notifications
     *                   posted to this channel are.
     */
    public static void setupNotificationChannel(final Context context, final String channelId, final CharSequence channelName, final int importance) {
        NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);

        NotificationManager notificationManager = getNotificationManager(context);
        if (notificationManager != null)
            notificationManager.createNotificationChannel(channel);
    }

}
