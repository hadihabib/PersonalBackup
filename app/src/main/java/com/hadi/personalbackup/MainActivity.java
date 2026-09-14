package com.hadi.personalbackup;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 2001;
    private static final int EXPORT_REQUEST = 2002;
    private TextView statusText;
    private TextView countText;
    private ExecutorService executor;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        buildUi();
        refreshStatus();
        if (hasCorePermissions()) startBackupService();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(30), dp(24), dp(30));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setLayoutDirection(LinearLayout.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("النسخ الاحتياطي الشخصي");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("\nيحفظ SMS وسجل المكالمات على هذا الجهاز فقط.");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, matchWrap());

        statusText = new TextView(this);
        statusText.setTextSize(18);
        statusText.setPadding(0, dp(28), 0, dp(10));
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText, matchWrap());

        countText = new TextView(this);
        countText.setTextSize(17);
        countText.setGravity(Gravity.CENTER);
        countText.setPadding(0, 0, 0, dp(24));
        root.addView(countText, matchWrap());

        Button permissions = makeButton("1) إعطاء الصلاحيات");
        permissions.setOnClickListener(v -> requestNeededPermissions());
        root.addView(permissions, buttonLayout());

        Button start = makeButton("2) تشغيل النسخ التلقائي");
        start.setOnClickListener(v -> {
            if (!hasCorePermissions()) { requestNeededPermissions(); return; }
            startBackupService();
            refreshStatus();
            Toast.makeText(this, "تم تشغيل النسخ التلقائي", Toast.LENGTH_SHORT).show();
        });
        root.addView(start, buttonLayout());

        Button importExisting = makeButton("استيراد الموجود حاليًا");
        importExisting.setOnClickListener(v -> {
            if (!hasCorePermissions()) { requestNeededPermissions(); return; }
            Toast.makeText(this, "بدأ الاستيراد...", Toast.LENGTH_SHORT).show();
            executor.execute(() -> {
                BackupDb db = new BackupDb(getApplicationContext());
                db.importAllSms();
                db.importAllCalls();
                db.close();
                runOnUiThread(() -> { refreshStatus(); Toast.makeText(this, "تم الاستيراد", Toast.LENGTH_SHORT).show(); });
            });
        });
        root.addView(importExisting, buttonLayout());

        Button export = makeButton("تصدير إلى TXT");
        export.setOnClickListener(v -> createExportFile());
        root.addView(export, buttonLayout());

        Button settings = makeButton("فتح إعدادات التطبيق");
        settings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        root.addView(settings, buttonLayout());

        TextView note = new TextView(this);
        note.setText("\nاترك إشعار «النسخ الاحتياطي نشط» موجودًا كي يراقب أندرويد الرسائل الصادرة وسجل المكالمات بسرعة. الرسائل الواردة تُلتقط أيضًا مباشرة عند وصولها.");
        note.setTextSize(14);
        note.setTextColor(Color.GRAY);
        note.setGravity(Gravity.RIGHT);
        root.addView(note, matchWrap());

        scroll.addView(root);
        setContentView(scroll);
    }

    private Button makeButton(String text) { Button b = new Button(this); b.setText(text); b.setTextSize(17); b.setAllCaps(false); return b; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams buttonLayout() { LinearLayout.LayoutParams p = matchWrap(); p.topMargin = dp(10); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private boolean hasCorePermissions() {
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNeededPermissions() {
        List<String> needed = new ArrayList<>();
        addIfMissing(needed, Manifest.permission.RECEIVE_SMS);
        addIfMissing(needed, Manifest.permission.READ_SMS);
        addIfMissing(needed, Manifest.permission.READ_CALL_LOG);
        if (Build.VERSION.SDK_INT >= 33) addIfMissing(needed, Manifest.permission.POST_NOTIFICATIONS);
        if (needed.isEmpty()) {
            startBackupService(); refreshStatus(); Toast.makeText(this, "الصلاحيات موجودة", Toast.LENGTH_SHORT).show();
        } else requestPermissions(needed.toArray(new String[0]), PERMISSION_REQUEST);
    }

    private void addIfMissing(List<String> list, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) list.add(permission);
    }

    private void startBackupService() {
        try {
            Intent intent = new Intent(this, BackupService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        } catch (Exception e) {
            Toast.makeText(this, "تعذر تشغيل خدمة النسخ: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshStatus() {
        boolean ok = hasCorePermissions();
        statusText.setText(ok ? "● النسخ جاهز" : "● يحتاج صلاحيات");
        statusText.setTextColor(ok ? Color.rgb(20,120,55) : Color.rgb(180,70,40));
        if (executor == null || executor.isShutdown()) return;
        executor.execute(() -> {
            BackupDb db = new BackupDb(getApplicationContext());
            int sms = db.smsCount(); int calls = db.callCount(); db.close();
            runOnUiThread(() -> countText.setText("الرسائل المحفوظة: " + sms + "\nالمكالمات المحفوظة: " + calls));
        });
    }

    private void createExportFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "personal_backup.txt");
        startActivityForResult(intent, EXPORT_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        executor.execute(() -> {
            try {
                BackupDb db = new BackupDb(getApplicationContext());
                String text = db.exportText(); db.close();
                OutputStream out = getContentResolver().openOutputStream(uri);
                if (out == null) throw new Exception("تعذر فتح الملف");
                out.write(text.getBytes(StandardCharsets.UTF_8)); out.flush(); out.close();
                runOnUiThread(() -> Toast.makeText(this, "تم حفظ ملف TXT", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "فشل التصدير: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            if (hasCorePermissions()) { startBackupService(); Toast.makeText(this, "تم تفعيل النسخ التلقائي", Toast.LENGTH_SHORT).show(); }
            else Toast.makeText(this, "لازم تسمح بصلاحيات SMS وسجل المكالمات حتى يعمل النسخ.", Toast.LENGTH_LONG).show();
            refreshStatus();
        }
    }

    @Override protected void onResume() { super.onResume(); if (statusText != null) refreshStatus(); }
    @Override protected void onDestroy() { if (executor != null) executor.shutdownNow(); super.onDestroy(); }
}
