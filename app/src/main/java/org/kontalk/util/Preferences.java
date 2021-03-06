/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.BaseColumns;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import android.text.TextUtils;
import android.view.Display;
import android.view.WindowManager;

import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.authenticator.MyAccount;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.kontalk.service.ServerListUpdater;
import org.kontalk.service.msgcenter.MessageCenterService;


/**
 * Access to application preferences.
 * @author Daniele Ricci
 */
public final class Preferences {

    private static SharedPreferences sPreferences;
    private static Drawable sCustomBackground;
    private static String sBalloonTheme;
    private static String sBalloonGroupsTheme;

    @SuppressLint("ApplySharedPref")
    public static void init(@NonNull Context context) {
        if (sPreferences == null) {
            sPreferences = PreferenceManager.getDefaultSharedPreferences(context);

            // set the new default theme if this is the first upgrade
            String newTheme = context.getString(R.string.pref_default_balloons);
            if (!getBooleanOnce("has_new_theme." + newTheme))
                sPreferences.edit().putString("pref_balloons", newTheme)
                    .commit();
        }
    }

    public static SharedPreferences getInstance() {
        return sPreferences;
    }

    public static void setCachedCustomBackground(Drawable customBackground) {
        sCustomBackground = customBackground;
    }

    public static void setCachedBalloonTheme(String balloonTheme) {
        sBalloonTheme = balloonTheme;
    }

    public static void setCachedBalloonGroupsTheme(String balloonTheme) {
        sBalloonGroupsTheme = balloonTheme;
    }

    public static void updateServerListLastUpdate(Preference pref, ServerList list) {
        Context context = pref.getContext();
        String timestamp = MessageUtils.formatTimeStampString(context, list.getDate().getTime(), true);
        pref.setSummary(context.getString(R.string.server_list_last_update, timestamp));
    }

    public static String getString(String key, String defaultValue) {
        return sPreferences.getString(key, defaultValue);
    }

    private static int getInt(String key, int defaultValue) {
        return sPreferences.getInt(key, defaultValue);
    }

    private static int getIntMinValue(String key, int minValue, int defaultValue) {
        String val = getString(key, null);
        int nval;
        try {
            nval = Integer.parseInt(val);
        }
        catch (Exception e) {
            nval = defaultValue;
        }
        return (nval < minValue) ? minValue : nval;
    }

    private static long getLong(String key, long defaultValue) {
        return sPreferences.getLong(key, defaultValue);
    }

    /** Retrieves a long and if >= 0 it sets it to -1. */
    @SuppressLint("ApplySharedPref")
    private static long getLongOnce(String key) {
        long value = sPreferences.getLong(key, -1);
        if (value >= 0)
            sPreferences.edit().putLong(key, -1).commit();
        return value;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return sPreferences.getBoolean(key, defaultValue);
    }

    private static String getShowcaseKey(String parent, String key) {
        return "showcase_" + parent + "_" + key;
    }

    public static boolean getShowcaseShowed(String parent, String key) {
        return Preferences.getBoolean(getShowcaseKey(parent, key), false);
    }

    public static boolean setShowcaseShowed(String parent, String key, boolean value) {
        return sPreferences.edit()
            .putBoolean(getShowcaseKey(parent, key), value)
            .commit();
    }

    /** Retrieve a boolean and if false set it to true. */
    @SuppressLint("ApplySharedPref")
    private static boolean getBooleanOnce(String key) {
        boolean value = sPreferences.getBoolean(key, false);
        if (!value)
            sPreferences.edit().putBoolean(key, true).commit();
        return value;
    }

    public static boolean setRingtone(String uri) {
        return sPreferences.edit()
            .putString("pref_ringtone", uri)
            .commit();
    }

    public static String getServerURI() {
        return getString("pref_network_uri", null);
    }

    public static boolean setServerURI(String serverURI) {
        return sPreferences.edit()
            .putString("pref_network_uri", serverURI)
            .commit();
    }

    /** Returns a random server from the cached list or the user-defined server. */
    @Deprecated
    public static EndpointServer getEndpointServer(Context context) {
        return getEndpointServer();
    }

    /** Returns a random server from the cached list or the user-defined server. */
    public static EndpointServer getEndpointServer() {
        String customUri = getServerURI();
        if (!TextUtils.isEmpty(customUri)) {
            try {
                return new EndpointServer(customUri);
            }
            catch (Exception e) {
                // custom is not valid - take one from list
            }
        }

        // return server stored in the default account
        MyAccount account = Kontalk.get().getDefaultAccount();
        return account != null ? account.getServer() : null;
    }

    /** Returns a server provider reflecting the current settings. */
    public static EndpointServer.EndpointServerProvider getEndpointServerProvider(Context context) {
        final String customUri = getServerURI();
        if (!TextUtils.isEmpty(customUri)) {
            return new EndpointServer.SingleServerProvider(customUri);
        }
        else {
            ServerList list = ServerListUpdater.getCurrentList(context);
            return new ServerList.ServerListProvider(list);
        }
    }

    public static boolean getForegroundServiceEnabled(Context context) {
        return getBoolean("pref_foreground_service", context
            .getResources().getBoolean(R.bool.pref_default_foreground_service));
    }

    public static boolean getEncryptionEnabled(Context context) {
        return getBoolean("pref_encrypt", context
            .getResources().getBoolean(R.bool.pref_default_encrypt));
    }

    public static boolean getSyncSIMContacts(Context context) {
        return getBoolean("pref_sync_sim_contacts", context
            .getResources().getBoolean(R.bool.pref_default_sync_sim_contacts));
    }

    public static boolean getSyncInvisibleContacts(Context context) {
        return getBoolean("pref_sync_invisible_contacts", context
            .getResources().getBoolean(R.bool.pref_default_sync_invisible_contacts));
    }

    public static boolean getAutoAcceptSubscriptions(Context context) {
        return getBoolean("pref_auto_accept_subscriptions", context
            .getResources().getBoolean(R.bool.pref_default_auto_accept_subscriptions));
    }

    public static boolean getPushNotificationsEnabled(Context context) {
        return getBoolean("pref_push_notifications", context
            .getResources().getBoolean(R.bool.pref_default_push_notifications));
    }

    public static boolean getNotificationsEnabled(Context context) {
        return getBoolean("pref_enable_notifications", context
            .getResources().getBoolean(R.bool.pref_default_enable_notifications));
    }

    public static String getNotificationVibrate(Context context) {
        return getString("pref_vibrate", context
            .getString(R.string.pref_default_vibrate));
    }

    public static String getNotificationRingtone(Context context) {
        return getString("pref_ringtone", context
            .getString(R.string.pref_default_ringtone));
    }

    public static boolean getNotificationLED(Context context) {
        return getBoolean("pref_enable_notification_led",
            context.getResources().getBoolean(R.bool.pref_default_enable_notification_led));
    }

    public static int getNotificationLEDColor(Context context) {
        return getInt("pref_notification_led_color",
            context.getResources().getInteger(R.integer.pref_default_notification_led_color));
    }

    public static boolean setNotificationLEDColor(int color) {
        return sPreferences.edit()
            .putInt("pref_notification_led_color", color)
            .commit();
    }

    public static boolean getOutgoingSoundEnabled(Context context) {
        return getBoolean("pref_enable_outgoing_sound", context
            .getResources().getBoolean(R.bool.pref_default_enable_outgoing_sound));
    }

    public static int getImageCompression(Context context) {
        return Integer.parseInt(getString("pref_image_resize", String
            .valueOf(context.getResources().getInteger(R.integer.pref_default_image_resize))));
    }

    /** Returns true if, as per settings, we can autodownload a file of the given size. */
    public static boolean canAutodownloadMedia(Context context, long size) {
        int threshold = Integer.parseInt(getString("pref_media_autodownload_threshold", String
            .valueOf(context.getResources().getInteger(R.integer.pref_default_media_autodownload_threshold))));
        String autodownload = getString("pref_media_autodownload",
            context.getResources().getString(R.string.pref_default_media_autodownload));
        return (size / 1024) < threshold || "always".equals(autodownload) ||
            ("wifi".equals(autodownload) && SystemUtils.isOnWifi(context));
    }

    public static String getMapsProvider(Context context) {
        return getString("pref_maps_service", context.getResources()
            .getString(R.string.pref_default_maps_service));
    }

    public static boolean getContactsListVisited() {
        return getBooleanOnce("pref_contacts_visited");
    }

    public static long getLastSyncTimestamp() {
        return getLong("pref_last_sync", -1);
    }

    public static boolean setLastSyncTimestamp(long timestamp) {
        return sPreferences.edit()
            .putLong("pref_last_sync", timestamp)
            .commit();
    }

    public static boolean setLastPushNotification(long timestamp) {
        return sPreferences.edit()
            .putLong("pref_last_push_notification", timestamp)
            .commit();
    }

    public static long getLastPushNotification() {
        return getLong("pref_last_push_notification", -1);
    }

    /** TODO cache value */
    public static String getFontSize(Context context) {
        return getString("pref_font_size", context
            .getString(R.string.pref_default_font_size));
    }

    public static String getBalloonTheme(Context context) {
        if (sBalloonTheme == null)
            sBalloonTheme = getString("pref_balloons", context
                .getString(R.string.pref_default_balloons));
        return sBalloonTheme;
    }

    public static String getBalloonGroupsTheme(Context context) {
        if (sBalloonGroupsTheme == null)
            sBalloonGroupsTheme = getString("pref_balloons_groups", context
                .getString(R.string.pref_default_balloons_groups));
        return sBalloonGroupsTheme;
    }

    /** Still unused. */
    public static boolean getEmojiConverter(Context context) {
        return getBoolean("pref_emoji_converter",
                context.getResources().getBoolean(R.bool.pref_default_emoji_converter));
    }

    public static String getStatusMessage() {
        return getString("pref_status_message", null);
    }

    public static void setStatusMessage(String message) {
        sPreferences.edit()
            .putString("pref_status_message", message)
            .apply();
    }

    /** Loads and stores a cached version of the given conversation background. */
    @SuppressWarnings("deprecation")
    public static File cacheConversationBackground(Context context, Uri uri) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            int width;
            int height;
            Point size = new Point();
            display.getSize(size);
            width = size.x;
            height = size.y;

            BitmapFactory.Options options;
            try {
                in = context.getContentResolver().openInputStream(uri);
                options = MediaStorage.preloadBitmap(in, width, height);
            }
            catch (Exception e) {
                throw new IOException(e);
            }
            finally {
                SystemUtils.close(in);
            }

            Bitmap bitmap;
            try {
                // open again
                in = context.getContentResolver().openInputStream(uri);
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            catch (Exception e) {
                throw new IOException(e);
            }
            finally {
                SystemUtils.close(in);
            }

            Bitmap tn = ThumbnailUtils.extractThumbnail(bitmap, width, height);
            bitmap.recycle();

            // check for rotation data
            tn = MediaStorage.bitmapOrientation(context, uri, tn);

            File outFile = new File(context.getFilesDir(), "background.png");
            out = new FileOutputStream(outFile);
            tn.compress(Bitmap.CompressFormat.PNG, 90, out);
            tn.recycle();

            return outFile;
        }
        finally {
            SystemUtils.close(out);
        }
    }

    public static Drawable getConversationBackground(Context context) {
        InputStream in = null;
        try {
            if (getBoolean("pref_custom_background", false)) {
                if (sCustomBackground == null) {
                    String _customBg = getString("pref_background_uri", null);
                    in = context.getContentResolver().openInputStream(Uri.parse(_customBg));
                    if (in != null) {
                        Bitmap bmap = BitmapFactory.decodeStream(in, null, null);
                        sCustomBackground = new BitmapDrawable(context.getResources(), bmap);
                    }
                }
                return sCustomBackground;
            }
        }
        catch (Exception e) {
            // ignored
        }
        finally {
            SystemUtils.close(in);
        }
        return null;
    }

    /**
     * Switches offline mode on or off.
     * @return offline mode status before the switch
     */
    public static boolean switchOfflineMode(Context context) {
        boolean old = sPreferences.getBoolean("offline_mode", false);
        // set flag again!
        boolean offline = !old;
        sPreferences.edit()
            .putBoolean("offline_mode", offline)
            .apply();

        if (offline) {
            // stop the message center and never start it again
            MessageCenterService.stop(context);
            Kontalk.setBackendEnabled(context, false);
        }
        else {
            Kontalk.setBackendEnabled(context, true);
            MessageCenterService.start(context);
        }

        return old;
    }

    /** Enable/disable offline mode. */
    public static void setOfflineMode(Context context, boolean enabled) {
        sPreferences.edit()
            .putBoolean("offline_mode", enabled)
            .apply();

        if (enabled) {
            // stop the message center and never start it again
            MessageCenterService.stop(context);
        }
        else {
            MessageCenterService.start(context);
        }
    }

    public static boolean getOfflineMode() {
        return getBoolean("offline_mode", false);
    }

    public static boolean getOfflineModeUsed() {
        return getBoolean("offline_mode_used", false);
    }

    public static void setOfflineModeUsed() {
        sPreferences.edit()
            .putBoolean("offline_mode_used", true)
            .apply();
    }

    public static boolean getSendTyping(Context context) {
        return getBoolean("pref_send_typing", context.getResources()
            .getBoolean(R.bool.pref_default_send_typing));
    }

    public static String getDialPrefix() {
        String pref = getString("pref_remove_prefix", null);
        return (pref != null && !TextUtils.isEmpty(pref.trim())) ? pref: null;
    }

    public static String getPushSenderId() {
        return getString("pref_push_sender", null);
    }

    public static boolean setPushSenderId(String senderId) {
        return sPreferences.edit()
            .putString("pref_push_sender", senderId)
            .commit();
    }

    public static boolean getAcceptAnyCertificate(Context context) {
        return getBoolean("pref_accept_any_certificate", context.getResources()
            .getBoolean(R.bool.pref_default_accept_any_certificate));
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public static boolean setAcceptAnyCertificate(boolean acceptAnyCertificate) {
        return sPreferences.edit()
            .putBoolean("pref_accept_any_certificate", acceptAnyCertificate)
            .commit();
    }

    public static int getIdleTimeMillis(Context context, int minValue) {
        return getIntMinValue("pref_idle_time", minValue, context
            .getResources().getInteger(R.integer.pref_default_idle_time));
    }

    public static int getWakeupTimeMillis(Context context, int minValue) {
        return getIntMinValue("pref_wakeup_time", minValue, context
            .getResources().getInteger(R.integer.pref_default_wakeup_time));
    }

    public static long getLastConnection() {
        return getLong("pref_last_connection", -1);
    }

    // TODO why isn't this used?
    public static void setLastConnection() {
        sPreferences.edit()
            .putLong("pref_last_connection", System.currentTimeMillis())
            .apply();
    }

    public static String getEnterKeyMode(Context context) {
        try {
            return getString("pref_text_enter", context
                .getString(R.string.pref_default_text_enter));
        }
        catch (ClassCastException e) {
            // legacy mode
            return getBoolean("pref_text_enter", false) ?
                "newline" : "default";
        }
    }

    public static boolean getShowBlockedUsers(Context context) {
        return getBoolean("pref_show_blocked_users", context
            .getResources().getBoolean(R.bool.pref_default_show_blocked_users));
    }

    public static String getRosterVersion() {
        return getString("roster_version", "");
    }

    public static boolean setRosterVersion(String version) {
        return sPreferences.edit()
            .putString("roster_version", version)
            .commit();
    }

    public static boolean isSkipDozeMode() {
        return getBoolean("skip_doze_mode", false);
    }

    public static boolean setSkipDozeMode(boolean value) {
        return sPreferences.edit()
            .putBoolean("skip_doze_mode", value)
            .commit();
    }

    public static boolean isSkipHuaweiProtectedApps() {
        return getBoolean("huawei_skip_protected_apps", false);
    }

    public static boolean setSkipHuaweiProtectedApps(boolean value) {
        return sPreferences.edit()
            .putBoolean("huawei_skip_protected_apps", value)
            .commit();
    }

    public static boolean isReportingEnabled(Context context) {
        return getBoolean("pref_reporting", context
            .getResources().getBoolean(R.bool.pref_default_reporting));
    }

    public static boolean isDebugLogEnabled(Context context) {
        return getBoolean("pref_debug_log", context
            .getResources().getBoolean(R.bool.pref_default_debug_log));
    }

    public static boolean isServerMessagesEnabled(Context context) {
        return getBoolean("pref_server_messages", context
            .getResources().getBoolean(R.bool.pref_default_server_messages));
    }

    public static long getPingAlarmInterval(Context context, long defaultValue) {
        String networkType = SystemUtils.getCurrentNetworkName(context);
        return (networkType != null) ?
            getLong("ping_alarm_interval_" + networkType, defaultValue) :
            defaultValue;
    }

    public static boolean setPingAlarmInterval(Context context, long intervalMillis) {
        String networkType = SystemUtils.getCurrentNetworkName(context);
        return networkType != null && sPreferences.edit()
            .putLong("ping_alarm_interval_" + networkType, intervalMillis)
            .commit();
    }

    public static long getPingAlarmBackoff(Context context, long defaultValue) {
        String networkType = SystemUtils.getCurrentNetworkName(context);
        return (networkType != null) ?
            getLong("ping_alarm_backoff_" + networkType, defaultValue) :
            defaultValue;
    }

    public static boolean setPingAlarmBackoff(Context context, long intervalMillis) {
        String networkType = SystemUtils.getCurrentNetworkName(context);
        return networkType != null && sPreferences.edit()
            .putLong("ping_alarm_backoff_" + networkType, intervalMillis)
            .commit();
    }

    public static boolean isPermissionAsked(String permission) {
        return sPreferences.getBoolean("permission_asked_" + permission, false);
    }

    public static void setPermissionAsked(String permission) {
        sPreferences.edit()
            .putBoolean("permission_asked_" + permission, true)
            .apply();
    }

    /** Recent statuses database helper. */
    private static final class RecentStatusDbHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "status.db";
        private static final int DATABASE_VERSION = 1;

        private static final String TABLE_STATUS = "status";
        private static final String SCHEMA_STATUS = "CREATE TABLE " + TABLE_STATUS + " (" +
            "_id INTEGER PRIMARY KEY," +
            "status TEXT UNIQUE," +
            "timestamp INTEGER" +
            ")";

        RecentStatusDbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SCHEMA_STATUS);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // no upgrade for version 1
        }

        public Cursor query() {
            SQLiteDatabase db = getReadableDatabase();
            return db.query(TABLE_STATUS, new String[] { BaseColumns._ID, "status" },
                null, null, null, null, "timestamp DESC");
        }

        public void insert(String status) {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues v = new ContentValues(2);
            v.put("status", status);
            v.put("timestamp", System.currentTimeMillis());
            db.replace(TABLE_STATUS, null, v);

            // delete old entries
            db.delete(TABLE_STATUS, "_id NOT IN (SELECT _id FROM " +
                TABLE_STATUS + " ORDER BY timestamp DESC LIMIT 10)", null);
        }
    }

    private static RecentStatusDbHelper recentStatusDb;

    private static void _recentStatusDbHelper(Context context) {
        if (recentStatusDb == null)
            recentStatusDb = new RecentStatusDbHelper(context.getApplicationContext());
    }

    /** Retrieves the list of recently used status messages. */
    public static Cursor getRecentStatusMessages(Context context) {
        _recentStatusDbHelper(context);
        return recentStatusDb.query();
    }

    public static void addRecentStatusMessage(Context context, String status) {
        _recentStatusDbHelper(context);
        recentStatusDb.insert(status);
        recentStatusDb.close();
    }

}
