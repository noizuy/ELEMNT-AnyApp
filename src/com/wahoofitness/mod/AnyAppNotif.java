package com.wahoofitness.mod;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Injected into BCNotificationListener.onNotificationPosted right after the stock
 * classification. Notifications from apps Wahoo doesn't know (appType 255) are
 * re-labelled as a generic MESSAGE (type 0 / app 0) so they flow through the
 * stock "message" path: dedup + forward to the ELEMNT as "from: body".
 *
 * Optional filter file: Android/data/com.wahoofitness.boltcompanion/files/notif_filter.txt
 *   -com.spotify.music   never forward this app
 *   +com.example.app     if any "+" line exists, ONLY "+" apps (and Wahoo's built-in ones) are forwarded
 *   # comment
 */
public final class AnyAppNotif {
    private static final String TAG = "AnyAppNotif";

    private static final int TYPE_MESSAGE = 0, TYPE_CALL = 1, TYPE_EMAIL = 3, TYPE_SYS = 6;
    private static final int APP_MESSAGE = 0, APP_UNKNOWN = 255;

    private static long sFilterMtime = -1;
    private static final Set<String> sAllow = new HashSet<>();
    private static final Set<String> sBlock = new HashSet<>();

    private AnyAppNotif() {}

    /** @return (type << 8) | app — the (possibly rewritten) classification. */
    public static int remap(Context ctx, StatusBarNotification sbn, int type, int app) {
        int unchanged = ((type & 0xff) << 8) | (app & 0xff);
        try {
            if (app != APP_UNKNOWN || !shouldForward(ctx, sbn, type)) {
                return unchanged;
            }
            Notification n = sbn.getNotification();
            Bundle extras = n.extras;
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (text == null || text.length() == 0) {
                text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            }
            if (text == null || text.length() == 0) {
                text = n.tickerText;
            }
            String label = appLabel(ctx, sbn.getPackageName(), extras);
            String from;
            if (title == null || title.length() == 0) {
                from = label;
            } else if (title.toString().contains(label)) {
                from = title.toString();
            } else {
                from = label + ": " + title;
            }
            if (text == null || text.length() == 0) {
                // Title-only notification: show it as the body.
                if (title == null || title.length() == 0) return unchanged;
                text = title;
                from = label;
            }
            // sbn is our own parcelled copy, safe to rewrite.
            extras.putCharSequence(Notification.EXTRA_TITLE, from);
            extras.putCharSequence(Notification.EXTRA_TEXT, text);
            return (TYPE_MESSAGE << 8) | APP_MESSAGE;
        } catch (Throwable t) {
            Log.w(TAG, "remap failed", t);
            return unchanged;
        }
    }

    private static boolean shouldForward(Context ctx, StatusBarNotification sbn, int type) {
        // Calls and e-mail already have dedicated handling; SYS is noise.
        if (type == TYPE_CALL || type == TYPE_EMAIL || type == TYPE_SYS) return false;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(ctx.getPackageName())
                || pkg.equals("android") || pkg.equals("com.android.systemui")) return false;

        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return false;
        int f = n.flags;
        if ((f & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE
                | Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_LOCAL_ONLY)) != 0) return false;
        if (!sbn.isClearable()) return false;

        String cat = n.category;
        if (cat != null) {
            switch (cat) {
                case Notification.CATEGORY_TRANSPORT:
                case Notification.CATEGORY_PROGRESS:
                case Notification.CATEGORY_SERVICE:
                case Notification.CATEGORY_STATUS:
                case Notification.CATEGORY_NAVIGATION:
                case Notification.CATEGORY_RECOMMENDATION:
                case Notification.CATEGORY_CALL:
                case Notification.CATEGORY_MISSED_CALL:
                case "stopwatch":
                case "location_sharing":
                    return false;
                default:
            }
        }

        // Respect channel importance: silent / minimised notifications stay on the phone.
        if (ctx instanceof NotificationListenerService) {
            try {
                NotificationListenerService.RankingMap map =
                        ((NotificationListenerService) ctx).getCurrentRanking();
                NotificationListenerService.Ranking r = new NotificationListenerService.Ranking();
                if (map != null && map.getRanking(sbn.getKey(), r)
                        && r.getImportance() < NotificationManager.IMPORTANCE_DEFAULT) {
                    return false;
                }
            } catch (Throwable ignored) {
            }
        }

        loadFilter(ctx);
        synchronized (AnyAppNotif.class) {
            if (sBlock.contains(pkg)) return false;
            if (!sAllow.isEmpty() && !sAllow.contains(pkg)) return false;
        }
        return true;
    }

    private static void loadFilter(Context ctx) {
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir == null) return;
            File file = new File(dir, "notif_filter.txt");
            long mtime = file.exists() ? file.lastModified() : 0;
            synchronized (AnyAppNotif.class) {
                if (mtime == sFilterMtime) return;
                sFilterMtime = mtime;
                sAllow.clear();
                sBlock.clear();
                if (mtime == 0) {
                    writeTemplate(file);
                    return;
                }
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.length() < 2) continue;
                        char c = line.charAt(0);
                        String p = line.substring(1).trim();
                        if (c == '+') sAllow.add(p);
                        else if (c == '-') sBlock.add(p);
                    }
                }
                Log.i(TAG, "filter loaded allow=" + sAllow + " block=" + sBlock);
            }
        } catch (Throwable t) {
            Log.w(TAG, "loadFilter failed", t);
        }
    }

    private static void writeTemplate(File file) {
        try (java.io.FileWriter w = new java.io.FileWriter(file)) {
            w.write("# ELEMNT any-app notification filter (edits are picked up automatically)\n"
                    + "# -package.name  -> never forward this app\n"
                    + "# +package.name  -> if any + line exists, ONLY + apps (plus Wahoo's built-in ones) are forwarded\n"
                    + "# Example:\n"
                    + "# -com.spotify.music\n");
            sFilterMtime = file.lastModified();
        } catch (Throwable ignored) {
        }
    }

    private static String appLabel(Context ctx, String pkg, Bundle extras) {
        try {
            PackageManager pm = ctx.getPackageManager();
            // Notification.Builder stores the poster's ApplicationInfo here; avoids
            // needing package visibility (QUERY_ALL_PACKAGES) on Android 11+.
            ApplicationInfo ai = extras.getParcelable("android.appInfo");
            if (ai == null) ai = pm.getApplicationInfo(pkg, 0);
            CharSequence l = pm.getApplicationLabel(ai);
            if (l != null && l.length() > 0) return l.toString();
        } catch (Throwable ignored) {
        }
        return pkg;
    }
}
