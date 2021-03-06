package eu.faircode.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018-2019 by Marcel Bokhorst (M66B)
*/

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import com.android.billingclient.api.BillingClient;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.util.FolderClosedIOException;
import com.sun.mail.util.MailConnectException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;

import javax.mail.Address;
import javax.mail.AuthenticationFailedException;
import javax.mail.FolderClosedException;
import javax.mail.MessageRemovedException;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.net.ssl.HttpsURLConnection;

import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION;

public class Helper {
    private static Map<String, String> hostOrganization = new HashMap<>();

    static final int NOTIFICATION_SYNCHRONIZE = 1;
    static final int NOTIFICATION_SEND = 2;
    static final int NOTIFICATION_EXTERNAL = 3;

    static final int AUTH_TYPE_PASSWORD = 1;
    static final int AUTH_TYPE_GMAIL = 2;

    static final float LOW_LIGHT = 0.6f;

    static final String FAQ_URI = "https://github.com/M66B/open-source-email/blob/master/FAQ.md";

    static ThreadFactory backgroundThreadFactory = new ThreadFactory() {
        @Override
        public Thread newThread(@NonNull Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setPriority(THREAD_PRIORITY_BACKGROUND);
            return thread;
        }
    };

    static boolean hasPermission(Context context, String name) {
        return (ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED);
    }

    static void view(Context context, LifecycleOwner owner, Intent intent) {
        Uri uri = intent.getData();
        if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
            view(context, owner, intent.getData(), false);
        else
            context.startActivity(intent);
    }

    static void view(Context context, LifecycleOwner owner, Uri uri, boolean browse) {
        Log.i("View=" + uri);

        if (!hasCustomTabs(context, uri))
            browse = true;

        if (browse) {
            Intent view = new Intent(Intent.ACTION_VIEW, uri);
            context.startActivity(getChooser(context, view));
        } else {
            // https://developer.chrome.com/multidevice/android/customtabs
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setToolbarColor(resolveColor(context, R.attr.colorPrimary));

            CustomTabsIntent customTabsIntent = builder.build();
            try {
                customTabsIntent.launchUrl(context, uri);
            } catch (ActivityNotFoundException ex) {
                Log.w(ex);
                Toast.makeText(context, context.getString(R.string.title_no_viewer, uri.toString()), Toast.LENGTH_LONG).show();
            } catch (Throwable ex) {
                Log.e(ex);
                unexpectedError(context, owner, ex);
            }
        }
    }

    static boolean hasCustomTabs(Context context, Uri uri) {
        PackageManager pm = context.getPackageManager();
        Intent view = new Intent(Intent.ACTION_VIEW, uri);

        for (ResolveInfo info : pm.queryIntentActivities(view, 0)) {
            Intent intent = new Intent();
            intent.setAction(ACTION_CUSTOM_TABS_CONNECTION);
            intent.setPackage(info.activityInfo.packageName);
            if (pm.resolveService(intent, 0) != null)
                return true;
        }

        return false;
    }

    static Intent getChooser(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        if (pm.queryIntentActivities(intent, 0).size() == 1)
            return intent;
        else
            return Intent.createChooser(intent, context.getString(R.string.title_select_app));
    }

    static Intent getIntentSetupHelp() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://github.com/M66B/open-source-email/blob/master/SETUP.md#setup-help"));
        return intent;
    }

    static Intent getIntentFAQ() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(Helper.FAQ_URI));
        return intent;
    }

    static Intent getIntentPrivacy() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://github.com/M66B/open-source-email/blob/master/PRIVACY.md#fairemail"));
        return intent;
    }

    static Intent getIntentOpenKeychain() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://f-droid.org/en/packages/org.sufficientlysecure.keychain/"));
        return intent;
    }

    static int dp2pixels(Context context, int dp) {
        float scale = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * scale);
    }

    static float getTextSize(Context context, int zoom) {
        TypedArray ta = null;
        try {
            if (zoom == 0)
                ta = context.obtainStyledAttributes(
                        R.style.TextAppearance_AppCompat_Small, new int[]{android.R.attr.textSize});
            else if (zoom == 2)
                ta = context.obtainStyledAttributes(
                        R.style.TextAppearance_AppCompat_Large, new int[]{android.R.attr.textSize});
            else
                ta = context.obtainStyledAttributes(
                        R.style.TextAppearance_AppCompat_Medium, new int[]{android.R.attr.textSize});
            return ta.getDimension(0, 0);
        } finally {
            if (ta != null)
                ta.recycle();
        }
    }

    static int resolveColor(Context context, int attr) {
        int[] attrs = new int[]{attr};
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs);
        int color = a.getColor(0, 0xFF0000);
        a.recycle();
        return color;
    }

    static Bitmap decodeImage(File file, int scaleToPixels) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        int factor = 1;
        while (options.outWidth / factor > scaleToPixels)
            factor *= 2;

        if (factor > 1) {
            Log.i("Decode image factor=" + factor);
            options.inJustDecodeBounds = false;
            options.inSampleSize = factor;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        }

        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    static void setViewsEnabled(ViewGroup view, boolean enabled) {
        for (int i = 0; i < view.getChildCount(); i++) {
            View child = view.getChildAt(i);
            if (child instanceof Spinner ||
                    child instanceof EditText ||
                    child instanceof CheckBox ||
                    child instanceof ImageView /* =ImageButton */ ||
                    (child instanceof Button && "disable".equals(child.getTag())))
                child.setEnabled(enabled);
            if (child instanceof BottomNavigationView) {
                Menu menu = ((BottomNavigationView) child).getMenu();
                menu.setGroupEnabled(0, enabled);
            } else if (child instanceof ViewGroup)
                setViewsEnabled((ViewGroup) child, enabled);
        }
    }

    static String localizeFolderName(Context context, String name) {
        if (name != null && "INBOX".equals(name.toUpperCase()))
            return context.getString(R.string.title_folder_inbox);
        else if ("OUTBOX".equals(name))
            return context.getString(R.string.title_folder_outbox);
        else
            return name;
    }

    static String formatThrowable(Throwable ex) {
        return formatThrowable(ex, false, " ");
    }

    static String formatThrowable(Throwable ex, boolean sanitize) {
        return formatThrowable(ex, sanitize, " ");
    }

    static String formatThrowable(Throwable ex, boolean sanitize, String separator) {
        if (sanitize) {
            if (ex instanceof MessageRemovedException)
                return null;
            if (ex instanceof FolderClosedException)
                return null;
            if (ex instanceof FolderClosedIOException)
                return null;
            if (ex instanceof IllegalStateException)
                // sync when store disconnected
                return null;
            //if (ex instanceof SSLException || ex.getCause() instanceof SSLException)
            //    return null;
            if (ex instanceof MailConnectException && ex.getCause() instanceof UnknownHostException)
                return null;
        }

        StringBuilder sb = new StringBuilder();
        if (BuildConfig.DEBUG)
            sb.append(ex.toString());
        else
            sb.append(ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage());

        Throwable cause = ex.getCause();
        while (cause != null) {
            if (BuildConfig.DEBUG)
                sb.append(separator).append(cause.toString());
            else
                sb.append(separator).append(cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage());
            cause = cause.getCause();
        }

        return sb.toString();
    }

    static void unexpectedError(final Context context, final LifecycleOwner owner, final Throwable ex) {
        if (owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED))
            new DialogBuilderLifecycle(context, owner)
                    .setTitle(R.string.title_unexpected_error)
                    .setMessage(ex.toString())
                    .setPositiveButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.title_report, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            new SimpleTask<Long>() {
                                @Override
                                protected Long onExecute(Context context, Bundle args) throws Throwable {
                                    return getDebugInfo(context, R.string.title_crash_info_remark, ex, null).id;
                                }

                                @Override
                                protected void onExecuted(Bundle args, Long id) {
                                    context.startActivity(
                                            new Intent(context, ActivityCompose.class)
                                                    .putExtra("action", "edit")
                                                    .putExtra("id", id));
                                }

                                @Override
                                protected void onException(Bundle args, Throwable ex) {
                                    if (ex instanceof IllegalArgumentException)
                                        Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
                                    else
                                        Toast.makeText(context, ex.toString(), Toast.LENGTH_LONG).show();
                                }
                            }.execute(context, owner, new Bundle(), "error:unexpected");
                        }
                    })
                    .show();
        else
            ApplicationEx.writeCrashLog(context, ex);
    }

    static EntityMessage getDebugInfo(Context context, int title, Throwable ex, String log) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(context.getString(title)).append("\n\n\n\n");
        sb.append(getAppInfo(context));
        if (ex != null)
            sb.append(ex.toString()).append("\n").append(android.util.Log.getStackTraceString(ex));
        if (log != null)
            sb.append(log);
        String body = "<pre>" + sb.toString().replaceAll("\\r?\\n", "<br />") + "</pre>";

        EntityMessage draft;

        DB db = DB.getInstance(context);
        try {
            db.beginTransaction();

            EntityFolder drafts = db.folder().getPrimaryDrafts();
            if (drafts == null)
                throw new IllegalArgumentException(context.getString(R.string.title_no_primary_drafts));

            List<EntityIdentity> identities = db.identity().getIdentities(drafts.account);
            EntityIdentity primary = null;
            for (EntityIdentity identity : identities) {
                if (identity.primary) {
                    primary = identity;
                    break;
                } else if (primary == null)
                    primary = identity;
            }

            draft = new EntityMessage();
            draft.account = drafts.account;
            draft.folder = drafts.id;
            draft.identity = (primary == null ? null : primary.id);
            draft.msgid = EntityMessage.generateMessageId();
            draft.thread = draft.msgid;
            draft.to = new Address[]{myAddress()};
            draft.subject = context.getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME + " debug info";
            draft.received = new Date().getTime();
            draft.id = db.message().insertMessage(draft);
            writeText(draft.getFile(context), body);
            db.message().setMessageContent(draft.id, true, HtmlHelper.getPreview(body), null);

            attachSettings(context, draft.id, 1);
            attachNetworkInfo(context, draft.id, 2);
            attachLog(context, draft.id, 3);
            attachOperations(context, draft.id, 4);
            attachLogcat(context, draft.id, 5);

            EntityOperation.queue(context, db, draft, EntityOperation.ADD);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return draft;
    }

    private static StringBuilder getAppInfo(Context context) {
        StringBuilder sb = new StringBuilder();

        // Get version info
        String installer = context.getPackageManager().getInstallerPackageName(BuildConfig.APPLICATION_ID);
        sb.append(String.format("%s: %s/%s %s/%s%s%s\r\n",
                context.getString(R.string.app_name),
                BuildConfig.APPLICATION_ID,
                installer,
                BuildConfig.VERSION_NAME,
                hasValidFingerprint(context) ? "1" : "3",
                BuildConfig.PLAY_STORE_RELEASE ? "p" : "",
                isPro(context) ? "+" : ""));
        sb.append(String.format("Android: %s (SDK %d)\r\n", Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
        sb.append("\r\n");

        // Get device info
        sb.append(String.format("Brand: %s\r\n", Build.BRAND));
        sb.append(String.format("Manufacturer: %s\r\n", Build.MANUFACTURER));
        sb.append(String.format("Model: %s\r\n", Build.MODEL));
        sb.append(String.format("Product: %s\r\n", Build.PRODUCT));
        sb.append(String.format("Device: %s\r\n", Build.DEVICE));
        sb.append(String.format("Host: %s\r\n", Build.HOST));
        sb.append(String.format("Display: %s\r\n", Build.DISPLAY));
        sb.append(String.format("Id: %s\r\n", Build.ID));
        sb.append("\r\n");

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean ignoring = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            ignoring = pm.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID);
        sb.append(String.format("Battery optimizations: %b\r\n", !ignoring));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            int bucket = usm.getAppStandbyBucket();
            sb.append(String.format("Standby bucket: %d\r\n", bucket));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean saving = (cm.getRestrictBackgroundStatus() == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED);
            sb.append(String.format("Data saving: %b\r\n", saving));
        }

        sb.append("\r\n");

        return sb;
    }

    private static void attachSettings(Context context, long id, int sequence) throws IOException {
        DB db = DB.getInstance(context);

        EntityAttachment attachment = new EntityAttachment();
        attachment.message = id;
        attachment.sequence = sequence;
        attachment.name = "settings.txt";
        attachment.type = "text/plain";
        attachment.size = null;
        attachment.progress = 0;
        attachment.id = db.attachment().insertAttachment(attachment);

        File file = attachment.getFile(context);
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file))) {

            long size = 0;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            Map<String, ?> settings = prefs.getAll();
            for (String key : settings.keySet())
                size += write(os, key + "=" + settings.get(key) + "\r\n");

            db.attachment().setDownloaded(attachment.id, size);
        }
    }

    private static void attachNetworkInfo(Context context, long id, int sequence) throws IOException {
        DB db = DB.getInstance(context);

        EntityAttachment attachment = new EntityAttachment();
        attachment.message = id;
        attachment.sequence = sequence;
        attachment.name = "network.txt";
        attachment.type = "text/plain";
        attachment.size = null;
        attachment.progress = 0;
        attachment.id = db.attachment().insertAttachment(attachment);

        File file = attachment.getFile(context);
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file))) {

            long size = 0;
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo ani = cm.getActiveNetworkInfo();
            size += write(os, "active=" + ani + "\r\n\r\n");

            for (Network network : cm.getAllNetworks()) {
                NetworkInfo ni = cm.getNetworkInfo(network);
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                size += write(os, "network=" + ni + " capabilities=" + caps + "\r\n\r\n");
            }

            db.attachment().setDownloaded(attachment.id, size);
        }
    }

    private static void attachLog(Context context, long id, int sequence) throws IOException {
        DB db = DB.getInstance(context);

        EntityAttachment attachment = new EntityAttachment();
        attachment.message = id;
        attachment.sequence = sequence;
        attachment.name = "log.txt";
        attachment.type = "text/plain";
        attachment.size = null;
        attachment.progress = 0;
        attachment.id = db.attachment().insertAttachment(attachment);

        File file = attachment.getFile(context);
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file))) {

            long size = 0;
            long from = new Date().getTime() - 24 * 3600 * 1000L;
            DateFormat DF = SimpleDateFormat.getTimeInstance();

            for (EntityLog entry : db.log().getLogs(from))
                size += write(os, String.format("%s %s\r\n", DF.format(entry.time), entry.data));

            db.attachment().setDownloaded(attachment.id, size);
        }
    }

    private static void attachOperations(Context context, long id, int sequence) throws IOException {
        DB db = DB.getInstance(context);

        EntityAttachment attachment = new EntityAttachment();
        attachment.message = id;
        attachment.sequence = sequence;
        attachment.name = "operations.txt";
        attachment.type = "text/plain";
        attachment.size = null;
        attachment.progress = 0;
        attachment.id = db.attachment().insertAttachment(attachment);

        File file = attachment.getFile(context);
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file))) {

            long size = 0;
            DateFormat DF = SimpleDateFormat.getTimeInstance();

            for (EntityOperation op : db.operation().getOperations())
                size += write(os, String.format("%s %d %s %s %s\r\n",
                        DF.format(op.created),
                        op.message == null ? -1 : op.message,
                        op.name,
                        op.args,
                        op.error));

            db.attachment().setDownloaded(attachment.id, size);
        }
    }

    private static void attachLogcat(Context context, long id, int sequence) throws IOException {
        DB db = DB.getInstance(context);

        EntityAttachment attachment = new EntityAttachment();
        attachment.message = id;
        attachment.sequence = sequence;
        attachment.name = "logcat.txt";
        attachment.type = "text/plain";
        attachment.size = null;
        attachment.progress = 0;
        attachment.id = db.attachment().insertAttachment(attachment);

        Process proc = null;
        File file = attachment.getFile(context);
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file))) {

            String[] cmd = new String[]{"logcat",
                    "-d",
                    "-v", "threadtime",
                    //"-t", "1000",
                    Log.TAG + ":I"};
            proc = Runtime.getRuntime().exec(cmd);

            long size = 0;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null)
                    size += write(os, line + "\r\n");
            }


            db.attachment().setDownloaded(attachment.id, size);
        } finally {
            if (proc != null)
                proc.destroy();
        }
    }

    private static int write(OutputStream os, String text) throws IOException {
        byte[] bytes = text.getBytes();
        os.write(bytes);
        return bytes.length;
    }

    static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return new DecimalFormat("@@").format(bytes / Math.pow(unit, exp)) + " " + pre + "B";
    }

    static InternetAddress myAddress() throws UnsupportedEncodingException {
        return new InternetAddress("marcel+fairemail@faircode.eu", "FairCode");
    }

    static String canonicalAddress(String address) {
        String[] a = address.split("@");
        if (a.length > 0) {
            String[] extra = a[0].split("\\+");
            if (extra.length > 0)
                a[0] = extra[0];
        }
        return TextUtils.join("@", a).toLowerCase();
    }

    static void writeText(File file, String content) throws IOException {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
            out.write(content == null ? "" : content);
        }
    }

    static String readText(File file) throws IOException {
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                body.append(line);
                body.append('\n');
            }
            return body.toString();
        }
    }

    static void copy(File src, File dst) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(src))) {
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(dst))) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0)
                    out.write(buf, 0, len);
            }
        }
    }

    static String getExtension(String filename) {
        if (filename == null)
            return null;
        int index = filename.lastIndexOf(".");
        if (index < 0)
            return null;
        return filename.substring(index + 1);
    }

    static boolean suitableNetwork(Context context, boolean log) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean metered = prefs.getBoolean("metered", true);
        Boolean isMetered = isMetered(context, log);
        boolean suitable = (isMetered != null && (metered || !isMetered));
        if (log)
            EntityLog.log(context, "suitable=" + suitable + " metered=" + metered + " isMetered=" + isMetered);
        return suitable;
    }

    static Boolean isMetered(Context context, boolean log) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            NetworkInfo ani = cm.getActiveNetworkInfo();
            if (ani == null || !ani.isConnected())
                return null;
            return cm.isActiveNetworkMetered();
        }

        Network active = cm.getActiveNetwork();
        if (active == null) {
            if (log)
                EntityLog.log(context, "isMetered: no active network");
            return null;
        }

        NetworkInfo ani = cm.getNetworkInfo(active);
        if (log)
            EntityLog.log(context, "isMetered: active info=" + ani);

        if (ani == null || !ani.isConnected()) {
            if (log)
                EntityLog.log(context, "isMetered: active network not connected");
            return null;
        }

        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        if (caps == null) {
            if (log)
                EntityLog.log(context, "isMetered: active no caps");
            return null; // network unknown
        }

        if (log)
            EntityLog.log(context, "isMetered: active caps=" + caps);

        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
            boolean unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
            if (log)
                EntityLog.log(context, "isMetered: active not VPN unmetered=" + unmetered);
            return !unmetered;
        }

        // VPN: evaluate underlying networks

        boolean underlying = false;
        Network[] networks = cm.getAllNetworks();
        if (networks != null)
            for (Network network : networks) {
                NetworkInfo ni = cm.getNetworkInfo(network);
                if (log)
                    Log.i("isMetered: underlying info=" + ni);

                caps = cm.getNetworkCapabilities(network);
                if (caps == null) {
                    if (log)
                        EntityLog.log(context, "isMetered: no underlying caps");
                    continue; // network unknown
                }

                if (log)
                    Log.i("isMetered: underlying caps=" + caps);

                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                    underlying = true;

                    if (log)
                        Log.i("isMetered: underlying caps=" + caps);

                    if (ni != null && ni.isConnected()) {
                        if (log)
                            Log.i("isMetered: underlying is connected");

                        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                            if (log)
                                EntityLog.log(context, "isMetered: underlying is unmetered");
                            return false;
                        }
                    } else {
                        if (log)
                            Log.i("isMetered: underlying is disconnected");
                    }
                }
            }

        if (!underlying) {
            EntityLog.log(context, "isMetered: no underlying network");
            return null;
        }

        if (log)
            EntityLog.log(context, "isMetered: underlying assume metered");
        // Assume metered
        return true;
    }

    static void connect(Context context, IMAPStore istore, EntityAccount account) throws MessagingException {
        try {
            istore.connect(account.host, account.port, account.user, account.password);
        } catch (AuthenticationFailedException ex) {
            if (account.auth_type == AUTH_TYPE_GMAIL) {
                account.password = refreshToken(context, "com.google", account.user, account.password);
                DB.getInstance(context).account().setAccountPassword(account.id, account.password);
                istore.connect(account.host, account.port, account.user, account.password);
            } else
                throw ex;
        }

        // https://www.ietf.org/rfc/rfc2971.txt
        if (istore.hasCapability("ID"))
            try {
                Map<String, String> id = new LinkedHashMap<>();
                id.put("name", context.getString(R.string.app_name));
                id.put("version", BuildConfig.VERSION_NAME);
                Map<String, String> sid = istore.id(id);
                if (sid != null)
                    for (String key : sid.keySet())
                        Log.i("Server " + key + "=" + sid.get(key));
            } catch (MessagingException ex) {
                Log.w(ex);
            }
    }

    static String refreshToken(Context context, String type, String name, String current) {
        try {
            AccountManager am = AccountManager.get(context);
            Account[] accounts = am.getAccountsByType(type);
            for (Account account : accounts)
                if (name.equals(account.name)) {
                    Log.i("Refreshing token");
                    am.invalidateAuthToken(type, current);
                    String refreshed = am.blockingGetAuthToken(account, getAuthTokenType(type), true);
                    Log.i("Refreshed token");
                    return refreshed;
                }
        } catch (Throwable ex) {
            Log.w(ex);
        }
        return current;
    }

    static String getAuthTokenType(String type) {
        if ("com.google".equals(type))
            return "oauth2:https://mail.google.com/";
        return null;
    }

    static boolean isPlayStoreInstall(Context context) {
        return BuildConfig.PLAY_STORE_RELEASE;
    }

    static String sha256(String data) throws NoSuchAlgorithmException {
        return sha256(data.getBytes());
    }

    static String sha256(byte[] data) throws NoSuchAlgorithmException {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static String getBillingResponseText(@BillingClient.BillingResponse int responseCode) {
        switch (responseCode) {
            case BillingClient.BillingResponse.BILLING_UNAVAILABLE:
                // Billing API version is not supported for the type requested
                return "BILLING_UNAVAILABLE";

            case BillingClient.BillingResponse.DEVELOPER_ERROR:
                // Invalid arguments provided to the API.
                return "DEVELOPER_ERROR";

            case BillingClient.BillingResponse.ERROR:
                // Fatal error during the API action
                return "ERROR";

            case BillingClient.BillingResponse.FEATURE_NOT_SUPPORTED:
                // Requested feature is not supported by Play Store on the current device.
                return "FEATURE_NOT_SUPPORTED";

            case BillingClient.BillingResponse.ITEM_ALREADY_OWNED:
                // Failure to purchase since item is already owned
                return "ITEM_ALREADY_OWNED";

            case BillingClient.BillingResponse.ITEM_NOT_OWNED:
                // Failure to consume since item is not owned
                return "ITEM_NOT_OWNED";

            case BillingClient.BillingResponse.ITEM_UNAVAILABLE:
                // Requested product is not available for purchase
                return "ITEM_UNAVAILABLE";

            case BillingClient.BillingResponse.OK:
                // Success
                return "OK";

            case BillingClient.BillingResponse.SERVICE_DISCONNECTED:
                // Play Store service is not connected now - potentially transient state.
                return "SERVICE_DISCONNECTED";

            case BillingClient.BillingResponse.SERVICE_UNAVAILABLE:
                // Network connection is down
                return "SERVICE_UNAVAILABLE";

            case BillingClient.BillingResponse.USER_CANCELED:
                // User pressed back or canceled a dialog
                return "USER_CANCELED";

            default:
                return Integer.toString(responseCode);
        }
    }

    static boolean hasWebView(Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm.hasSystemFeature("android.software.webview"))
            try {
                new WebView(context);
                return true;
            } catch (Throwable ex) {
                return false;
            }
        else
            return false;
    }

    public static String getFingerprint(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            String pkg = context.getPackageName();
            PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
            byte[] cert = info.signatures[0].toByteArray();
            MessageDigest digest = MessageDigest.getInstance("SHA1");
            byte[] bytes = digest.digest(cert);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes)
                sb.append(Integer.toString(b & 0xff, 16).toUpperCase());
            return sb.toString();
        } catch (Throwable ex) {
            Log.e(ex);
            return null;
        }
    }

    public static boolean hasValidFingerprint(Context context) {
        String signed = getFingerprint(context);
        String expected = context.getString(R.string.fingerprint);
        return Objects.equals(signed, expected);
    }

    static boolean isPro(Context context) {
        //TODO: TMC Change (03/28/2019):  Remove Pro check - always return True
        return true;
//        if (false && BuildConfig.DEBUG)
//            return true;
//        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pro", false);
    }

    static long[] toLongArray(List<Long> list) {
        long[] result = new long[list.size()];
        for (int i = 0; i < list.size(); i++)
            result[i] = list.get(i);
        return result;
    }

    static List<Long> fromLongArray(long[] array) {
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < array.length; i++)
            result.add(array[i]);
        return result;
    }

    static boolean equal(String[] a1, String[] a2) {
        if (a1.length != a2.length)
            return false;

        for (int i = 0; i < a1.length; i++)
            if (!a1[i].equals(a2[i]))
                return false;

        return true;
    }

    static String sanitizeKeyword(String keyword) {
        // https://tools.ietf.org/html/rfc3501
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyword.length(); i++) {
            // flag-keyword    = atom
            // atom            = 1*ATOM-CHAR
            // ATOM-CHAR       = <any CHAR except atom-specials>
            char kar = keyword.charAt(i);
            // atom-specials   = "(" / ")" / "{" / SP / CTL / list-wildcards / quoted-specials / resp-specials
            if (kar == '(' || kar == ')' || kar == '{' || kar == ' ' || Character.isISOControl(kar))
                continue;
            // list-wildcards  = "%" / "*"
            if (kar == '%' || kar == '*')
                continue;
            // quoted-specials = DQUOTE / "\"
            if (kar == '"' || kar == '\\')
                continue;
            // resp-specials   = "]"
            if (kar == ']')
                continue;
            sb.append(kar);
        }
        return sb.toString();
    }

    static String sanitizeFilename(String name) {
        return (name == null ? null : name.replaceAll("[^a-zA-Z0-9\\.\\-]", "_"));
    }

    static String getOrganization(String host) throws IOException {
        synchronized (hostOrganization) {
            if (hostOrganization.containsKey(host))
                return hostOrganization.get(host);
        }
        InetAddress address = InetAddress.getByName(host);
        URL url = new URL("https://ipinfo.io/" + address.getHostAddress() + "/org");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setReadTimeout(15 * 1000);
        connection.connect();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String organization = reader.readLine();
            if ("undefined".equals(organization))
                organization = null;
            synchronized (hostOrganization) {
                hostOrganization.put(host, organization);
            }
            return organization;
        }
    }
}
