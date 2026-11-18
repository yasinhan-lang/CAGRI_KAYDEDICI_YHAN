package com.yhan.cagrikaydedici;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements CallRecorder.Listener {

    static final int C_BG = 0xFFFFF7F0, C_CARD = 0xFFFFFFFF, C_CORAL = 0xFFFF6B6B, C_INK = 0xFF2D3047,
            C_SUB = 0xFF8A8FA3, C_SOFT = 0xFFFFEDE5, C_GREEN = 0xFF22A565, C_GREEN_S = 0xFFE3F7EC,
            C_BLUE = 0xFF3D7BF0, C_BLUE_S = 0xFFE5EEFF, C_LINE = 0xFFF1E6E0;

    private static final int REQ_PERMS = 1, REQ_DEL = 2;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<RecordingItem> all = new ArrayList<>();
    private final List<RecordingItem> shown = new ArrayList<>();
    private final Locale tr = new Locale("tr", "TR");

    private ListView list;
    private RecAdapter adapter;
    private MascotView mascot;
    private Switch mainSwitch;
    private TextView statusText, countText, emptyText;
    private LinearLayout permCard;
    private TextView sourceChip;
    private String query = "";

    private LinearLayout playerBar;
    private TextView playerTitle, playerTime, playerBtn;
    private SeekBar seek;
    private MediaPlayer mp;
    private RecordingItem playing;
    private RecordingItem pendingDelete;

    // ------------------------------------------------------------------ yaşam döngüsü

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Notif.ensure(this);
        setContentView(buildUi());
        if (!Prefs.askedPerms(this) && missingPerms().length > 0) {
            h.postDelayed(this::askPerms, 600);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        CallRecorder.addListener(this);
        CallAccessibilityService.refreshListener();
        refreshStatus();
        loadRecordings();
    }

    @Override
    protected void onPause() {
        super.onPause();
        CallRecorder.removeListener(this);
    }

    @Override
    protected void onDestroy() {
        stopPlayer();
        io.shutdown();
        super.onDestroy();
    }

    @Override
    public void onRecordingChanged(boolean recording) {
        refreshStatus();
        if (!recording) {
            h.removeCallbacks(reload);
            h.postDelayed(reload, 1500);
        }
    }

    private final Runnable reload = this::loadRecordings;

    // ------------------------------------------------------------------ arayüz

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        l.setBackground(round(C_CARD, 22));
        l.setElevation(dp(1.5f));
        return l;
    }

    private View wrap(View v, int top, int bottom) {
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(14), dp(top), dp(14), dp(bottom));
        f.setClipToPadding(false);
        f.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        f.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return f;
    }

    private TextView pillButton(String s, int bg, int fg) {
        TextView t = text(s, 13, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setBackground(round(bg, 50));
        return t;
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        root.setFitsSystemWindows(true);

        root.addView(buildHeader());

        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setItemsCanFocus(true);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(12));
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);

        list.addHeaderView(wrap(buildHero(), 6, 6), null, false);
        permCard = card();
        list.addHeaderView(wrap(permCard, 6, 6), null, false);
        list.addHeaderView(wrap(buildSettings(), 6, 6), null, false);
        list.addHeaderView(buildListHeader(), null, false);
        list.addFooterView(buildFooter(), null, false);

        adapter = new RecAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildPlayer());
        return root;
    }

    private View buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(10), dp(14), dp(10));

        LogoView logo = new LogoView(this);
        bar.addView(logo, new LinearLayout.LayoutParams(dp(74), dp(46)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        TextView brand = text("BozokluHAN", 12, C_CORAL, true);
        brand.setLetterSpacing(0.12f);
        brand.setGravity(Gravity.CENTER);
        TextView title = text("Çağrı Kaydedici", 20, C_INK, true);
        title.setGravity(Gravity.CENTER);
        mid.addView(brand);
        mid.addView(title);
        bar.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FlagView flag = new FlagView(this);
        bar.addView(flag, new LinearLayout.LayoutParams(dp(74), dp(46)));
        return bar;
    }

    private View buildHero() {
        LinearLayout c = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        mascot = new MascotView(this);
        row.addView(mascot, new LinearLayout.LayoutParams(dp(100), dp(100)));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(12), 0, 0, 0);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text("Otomatik kayıt", 17, C_INK, true);
        top.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mainSwitch = new Switch(this);
        mainSwitch.setChecked(Prefs.enabled(this));
        mainSwitch.setOnCheckedChangeListener((bv, on) -> {
            Prefs.setEnabled(this, on);
            refreshStatus();
        });
        top.addView(mainSwitch);
        right.addView(top);

        statusText = text("", 13.5f, C_SUB, false);
        statusText.setPadding(0, dp(4), 0, 0);
        right.addView(statusText);

        row.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(row);

        TextView legal = text("⚖️ Görüşmeyi kaydettiğinizi karşı tarafa bildirmeyi unutmayın.", 11.5f, C_SUB, false);
        legal.setPadding(dp(10), dp(8), dp(10), dp(8));
        legal.setBackground(round(C_SOFT, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        c.addView(legal, lp);
        return c;
    }

    private View settingSwitch(String label, String hint, boolean value, android.widget.CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(label, 15, C_INK, false));
        if (hint != null) col.addView(text(hint, 12, C_SUB, false));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch s = new Switch(this);
        s.setChecked(value);
        s.setOnCheckedChangeListener(l);
        row.addView(s);
        return row;
    }

    private View buildSettings() {
        LinearLayout c = card();
        c.addView(text("⚙️ Ayarlar", 16, C_INK, true));
        c.addView(settingSwitch("Gelen aramalar", null, Prefs.incoming(this), (b, on) -> Prefs.setIncoming(this, on)));
        c.addView(settingSwitch("Giden aramalar", null, Prefs.outgoing(this), (b, on) -> Prefs.setOutgoing(this, on)));
        c.addView(settingSwitch("Hoparlörü otomatik aç", "Karşı tarafın sesi daha net kaydedilir",
                Prefs.speaker(this), (b, on) -> Prefs.setSpeaker(this, on)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(4));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text("Ses kaynağı", 15, C_INK, false));
        col.addView(text("Ses az geliyorsa başka kaynak deneyin", 12, C_SUB, false));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sourceChip = pillButton(Prefs.SOURCE_NAMES[Prefs.source(this)] + "  ▾", C_SOFT, C_CORAL);
        sourceChip.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Ses kaynağı")
                .setSingleChoiceItems(Prefs.SOURCE_NAMES, Prefs.source(this), (d, which) -> {
                    Prefs.setSource(this, which);
                    sourceChip.setText(Prefs.SOURCE_NAMES[which] + "  ▾");
                    d.dismiss();
                })
                .show());
        row.addView(sourceChip);
        c.addView(row);

        TextView folder = text("📁 Kayıtlar: Müzik / " + CallRecorder.FOLDER, 12, C_SUB, false);
        folder.setPadding(0, dp(8), 0, 0);
        c.addView(folder);
        return c;
    }

    private View buildListHeader() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(12), dp(18), dp(4));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text("🎧 Kayıtlar", 18, C_INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        countText = pillButton("0", C_SOFT, C_CORAL);
        countText.setPadding(dp(10), dp(3), dp(10), dp(3));
        row.addView(countText);
        l.addView(row);

        EditText search = new EditText(this);
        search.setHint("🔍  İsim veya numara ara");
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setTextColor(C_INK);
        search.setHintTextColor(C_SUB);
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.setBackground(round(C_CARD, 16));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                applyFilter();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        l.addView(search, lp);

        emptyText = text("Henüz kayıt yok.\nBir arama yaptığınızda burada görünecek 🎙️", 14, C_SUB, false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(0, dp(28), 0, dp(20));
        l.addView(emptyText);

        l.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    private View buildFooter() {
        TextView t = text("© 2026 Yasin HAN · Tüm hakları saklıdır.", 11.5f, C_SUB, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(18), dp(16), dp(10));
        t.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private View buildPlayer() {
        playerBar = new LinearLayout(this);
        playerBar.setOrientation(LinearLayout.VERTICAL);
        playerBar.setPadding(dp(16), dp(10), dp(16), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(C_INK);
        g.setCornerRadii(new float[]{dp(22), dp(22), dp(22), dp(22), 0, 0, 0, 0});
        playerBar.setBackground(g);
        playerBar.setVisibility(View.GONE);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        playerTitle = text("", 14, Color.WHITE, true);
        playerTitle.setSingleLine(true);
        playerTitle.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(playerTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = text("✕", 18, 0xFFBFC3D6, true);
        close.setPadding(dp(10), 0, 0, 0);
        close.setOnClickListener(v -> stopPlayer());
        top.addView(close);
        playerBar.addView(top);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, dp(6), 0, 0);
        playerBtn = text("❚❚", 16, Color.WHITE, true);
        playerBtn.setGravity(Gravity.CENTER);
        playerBtn.setBackground(round(C_CORAL, 50));
        playerBtn.setOnClickListener(v -> togglePlay());
        bottom.addView(playerBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));
        seek = new SeekBar(this);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && mp != null) mp.seekTo(p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        bottom.addView(seek, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        playerTime = text("00:00", 12, 0xFFBFC3D6, false);
        bottom.addView(playerTime);
        playerBar.addView(bottom);
        return playerBar;
    }

    // ------------------------------------------------------------------ durum & izinler

    private String[] wantedPerms() {
        List<String> l = new ArrayList<>();
        l.add(Manifest.permission.RECORD_AUDIO);
        l.add(Manifest.permission.READ_PHONE_STATE);
        l.add(Manifest.permission.READ_CALL_LOG);
        l.add(Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= 33) {
            l.add(Manifest.permission.POST_NOTIFICATIONS);
            l.add(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            l.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        return l.toArray(new String[0]);
    }

    private String[] missingPerms() {
        List<String> l = new ArrayList<>();
        for (String p : wantedPerms()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) l.add(p);
        }
        return l.toArray(new String[0]);
    }

    private boolean corePermsOk() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    private void askPerms() {
        String[] miss = missingPerms();
        if (miss.length == 0) return;
        boolean blocked = Prefs.askedPerms(this);
        for (String p : miss) if (shouldShowRequestPermissionRationale(p)) blocked = false;
        if (blocked) {
            Toast.makeText(this, "İzinleri uygulama ayarlarından açın", Toast.LENGTH_LONG).show();
            openAppDetails();
            return;
        }
        Prefs.setAskedPerms(this);
        requestPermissions(miss, REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        CallAccessibilityService.refreshListener();
        refreshStatus();
        loadRecordings();
    }

    private boolean accessibilityOn() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return CallAccessibilityService.isRunning();
        ComponentName cn = new ComponentName(this, CallAccessibilityService.class);
        String low = s.toLowerCase(Locale.US);
        return low.contains(cn.flattenToString().toLowerCase(Locale.US))
                || low.contains(cn.flattenToShortString().toLowerCase(Locale.US))
                || CallAccessibilityService.isRunning();
    }

    private boolean batteryOk() {
        PowerManager pm = getSystemService(PowerManager.class);
        return pm == null || pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) { }
    }

    private void refreshStatus() {
        if (mascot == null) return;
        boolean enabled = Prefs.enabled(this);
        boolean perms = corePermsOk();
        boolean acc = accessibilityOn();
        boolean rec = CallRecorder.get().isRecording();
        if (mainSwitch.isChecked() != enabled) mainSwitch.setChecked(enabled);

        if (rec) {
            mascot.setState(MascotView.REC);
            statusText.setText("🔴 Görüşme kaydediliyor…");
            statusText.setTextColor(C_CORAL);
        } else if (!enabled) {
            mascot.setState(MascotView.SLEEP);
            statusText.setText("Kapalı — aramalar kaydedilmiyor.");
            statusText.setTextColor(C_SUB);
        } else if (!perms || !acc) {
            mascot.setState(MascotView.SLEEP);
            statusText.setText("Birkaç adım kaldı, aşağıdaki kurulumu tamamlayın 👇");
            statusText.setTextColor(0xFFE08A00);
        } else {
            mascot.setState(MascotView.READY);
            statusText.setText("Hazırım! Aramalarınız otomatik kaydedilecek 😊");
            statusText.setTextColor(C_GREEN);
        }
        buildPermCard(perms, acc);
    }

    private void buildPermCard(boolean perms, boolean acc) {
        permCard.removeAllViews();
        boolean optionalMissing = missingPerms().length > 0;
        boolean batt = batteryOk();
        if (perms && acc && !optionalMissing && batt) {
            ((View) permCard.getParent()).setPadding(0, 0, 0, 0);
            permCard.setVisibility(View.GONE);
            return;
        }
        ((View) permCard.getParent()).setPadding(dp(14), dp(6), dp(14), dp(6));
        permCard.setVisibility(View.VISIBLE);
        permCard.addView(text("🧩 Kurulum", 16, C_INK, true));

        int step = 1;
        if (!perms || optionalMissing) {
            permCard.addView(stepRow(step++, "İzinleri verin",
                    "Mikrofon, telefon, arama geçmişi (numara için), rehber (isim için), bildirim ve dosya.",
                    "İzin ver", v -> askPerms(), !perms));
        }
        if (!acc) {
            String hint = "Ayarlar → Erişilebilirlik → Yüklü uygulamalar → \"BozokluHAN Çağrı Kaydedici\" → Aç.";
            if (Build.VERSION.SDK_INT >= 33) {
                hint += "\nAçılmıyor / gri görünüyorsa: Uygulama bilgisi → sağ üst ⋮ → \"Kısıtlanmış ayarlara izin ver\", sonra tekrar deneyin.";
            }
            permCard.addView(stepRow(step++, "Erişilebilirlik hizmetini açın", hint, "Aç", v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Exception e) { openAppDetails(); }
            }, true));
            if (Build.VERSION.SDK_INT >= 33) {
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.END);
                TextView b = pillButton("Uygulama bilgisi", C_SOFT, C_CORAL);
                b.setOnClickListener(v -> openAppDetails());
                r.addView(b);
                permCard.addView(r);
            }
        }
        if (!batt) {
            permCard.addView(stepRow(step, "Pil kısıtlamasını kaldırın (önerilir)",
                    "Telefon uygulamayı uyutursa kayıt kaçabilir. Listeden uygulamayı bulup \"Kısıtlama yok\" seçin.",
                    "Ayarlar", v -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                        } catch (Exception e) { openAppDetails(); }
                    }, false));
        }
    }

    private View stepRow(int n, String title, String desc, String btn, View.OnClickListener l, boolean important) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(6));

        TextView num = text(String.valueOf(n), 14, Color.WHITE, true);
        num.setGravity(Gravity.CENTER);
        num.setBackground(round(important ? C_CORAL : C_SUB, 50));
        row.addView(num, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), 0, dp(10), 0);
        col.addView(text(title, 14.5f, C_INK, true));
        col.addView(text(desc, 12, C_SUB, false));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView b = pillButton(btn, important ? C_CORAL : C_SOFT, important ? Color.WHITE : C_CORAL);
        b.setOnClickListener(l);
        row.addView(b);
        return row;
    }

    // ------------------------------------------------------------------ kayıt listesi

    private void loadRecordings() {
        final ContentResolver cr = getContentResolver();
        io.execute(() -> {
            final List<RecordingItem> res = new ArrayList<>();
            Uri col = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            String[] proj = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.SIZE};
            String sel = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
            String[] args = {"%" + CallRecorder.FOLDER + "%"};
            try (Cursor c = cr.query(col, proj, sel, args, MediaStore.Audio.Media.DATE_ADDED + " DESC")) {
                if (c != null) {
                    while (c.moveToNext()) {
                        RecordingItem it = new RecordingItem();
                        it.id = c.getLong(0);
                        it.uri = ContentUris.withAppendedId(col, it.id);
                        it.displayName = c.getString(1) == null ? "" : c.getString(1);
                        it.durationMs = c.isNull(2) ? 0 : c.getLong(2);
                        it.size = c.isNull(4) ? 0 : c.getLong(4);
                        it.parseName(c.getLong(3) * 1000L);
                        res.add(it);
                    }
                }
            } catch (Exception ignored) { }
            Collections.sort(res, (a, b) -> Long.compare(b.dateMs, a.dateMs));
            h.post(() -> {
                all.clear();
                all.addAll(res);
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        shown.clear();
        for (RecordingItem it : all) if (it.matches(query)) shown.add(it);
        if (countText != null) countText.setText(String.valueOf(all.size()));
        if (emptyText != null) {
            emptyText.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
            emptyText.setText(all.isEmpty()
                    ? "Henüz kayıt yok.\nBir arama yaptığınızda burada görünecek 🎙️"
                    : "Aramanızla eşleşen kayıt yok.");
        }
        adapter.notifyDataSetChanged();
    }

    private String fmtDate(long ms) {
        return new SimpleDateFormat("d MMM yyyy, HH:mm", tr).format(new Date(ms));
    }

    private final class Holder {
        LinearLayout card;
        TextView avatar, title, sub, play, share, del;
    }

    private TextView iconBtn(String s, int bg, int fg) {
        TextView t = text(s, 15, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(bg, 50));
        return t;
    }

    private final class RecAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return shown.get(p).id; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            Holder hd;
            View v = convert;
            if (v == null) {
                hd = new Holder();
                hd.card = new LinearLayout(MainActivity.this);
                hd.card.setOrientation(LinearLayout.HORIZONTAL);
                hd.card.setGravity(Gravity.CENTER_VERTICAL);
                hd.card.setPadding(dp(12), dp(10), dp(10), dp(10));

                hd.avatar = text("", 17, C_INK, true);
                hd.avatar.setGravity(Gravity.CENTER);
                hd.card.addView(hd.avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));

                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(dp(12), 0, dp(6), 0);
                hd.title = text("", 15, C_INK, true);
                hd.title.setSingleLine(true);
                hd.title.setEllipsize(TextUtils.TruncateAt.END);
                hd.sub = text("", 12, C_SUB, false);
                hd.sub.setSingleLine(true);
                hd.sub.setEllipsize(TextUtils.TruncateAt.END);
                col.addView(hd.title);
                col.addView(hd.sub);
                hd.card.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                hd.play = iconBtn("▶", C_CORAL, Color.WHITE);
                hd.share = iconBtn("⤴", C_SOFT, C_CORAL);
                hd.del = iconBtn("🗑", C_SOFT, C_CORAL);
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(36), dp(36));
                bp.leftMargin = dp(6);
                hd.card.addView(hd.play, bp);
                hd.card.addView(hd.share, new LinearLayout.LayoutParams(bp));
                hd.card.addView(hd.del, new LinearLayout.LayoutParams(bp));

                v = wrap(hd.card, 4, 4);
                v.setTag(hd);
            } else {
                hd = (Holder) v.getTag();
            }

            final RecordingItem it = shown.get(pos);
            boolean isPlaying = playing != null && playing.id == it.id;
            hd.card.setBackground(round(isPlaying ? 0xFFFFF0EC : C_CARD, 18));

            int avBg = C_SOFT, avFg = C_CORAL;
            String arrow = "";
            String dir = "";
            if (Boolean.TRUE.equals(it.incoming)) { avBg = C_GREEN_S; avFg = C_GREEN; arrow = "↙"; dir = "↙ Gelen"; }
            else if (Boolean.FALSE.equals(it.incoming)) { avBg = C_BLUE_S; avFg = C_BLUE; arrow = "↗"; dir = "↗ Giden"; }
            hd.avatar.setBackground(round(avBg, 50));
            hd.avatar.setTextColor(avFg);
            if (!it.name.isEmpty()) hd.avatar.setText(it.name.substring(0, 1).toUpperCase(tr));
            else hd.avatar.setText(arrow.isEmpty() ? "☎" : arrow);

            hd.title.setText(it.title());
            StringBuilder sb = new StringBuilder();
            if (!dir.isEmpty()) sb.append(dir).append(" · ");
            sb.append(fmtDate(it.dateMs));
            if (it.durationMs > 0) sb.append(" · ").append(CallRecorder.fmtDur(it.durationMs));
            if (!it.name.isEmpty() && !it.number.isEmpty()) sb.append(" · ").append(it.number);
            hd.sub.setText(sb.toString());

            hd.play.setText(isPlaying && mp != null && mp.isPlaying() ? "❚❚" : "▶");
            hd.play.setOnClickListener(x -> play(it));
            hd.card.setOnClickListener(x -> play(it));
            hd.share.setOnClickListener(x -> share(it));
            hd.del.setOnClickListener(x -> confirmDelete(it));
            return v;
        }
    }

    // ------------------------------------------------------------------ oynatma / paylaş / sil

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (mp == null) return;
            try {
                int p = mp.getCurrentPosition();
                seek.setProgress(p);
                playerTime.setText(CallRecorder.fmtDur(p) + " / " + CallRecorder.fmtDur(mp.getDuration()));
            } catch (Exception ignored) { }
            h.postDelayed(this, 250);
        }
    };

    private void play(RecordingItem it) {
        if (playing != null && playing.id == it.id && mp != null) {
            togglePlay();
            return;
        }
        stopPlayer();
        MediaPlayer m = new MediaPlayer();
        try {
            m.setDataSource(this, it.uri);
            m.prepare();
        } catch (Exception e) {
            m.release();
            Toast.makeText(this, "Kayıt oynatılamadı", Toast.LENGTH_SHORT).show();
            return;
        }
        mp = m;
        playing = it;
        mp.setOnCompletionListener(x -> {
            playerBtn.setText("▶");
            seek.setProgress(0);
            adapter.notifyDataSetChanged();
        });
        mp.start();
        playerTitle.setText("🎧 " + it.title());
        seek.setMax(Math.max(1, mp.getDuration()));
        playerBtn.setText("❚❚");
        playerBar.setVisibility(View.VISIBLE);
        h.removeCallbacks(ticker);
        h.post(ticker);
        adapter.notifyDataSetChanged();
    }

    private void togglePlay() {
        if (mp == null) return;
        if (mp.isPlaying()) {
            mp.pause();
            playerBtn.setText("▶");
        } else {
            mp.start();
            playerBtn.setText("❚❚");
        }
        adapter.notifyDataSetChanged();
    }

    private void stopPlayer() {
        h.removeCallbacks(ticker);
        if (mp != null) {
            try { mp.stop(); } catch (Exception ignored) { }
            mp.release();
            mp = null;
        }
        playing = null;
        if (playerBar != null) playerBar.setVisibility(View.GONE);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void share(RecordingItem it) {
        Intent s = new Intent(Intent.ACTION_SEND);
        s.setType("audio/mp4");
        s.putExtra(Intent.EXTRA_STREAM, it.uri);
        s.putExtra(Intent.EXTRA_SUBJECT, "Görüşme kaydı - " + it.title());
        s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(s, "Kaydı paylaş"));
        } catch (Exception e) {
            Toast.makeText(this, "Paylaşılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(RecordingItem it) {
        new AlertDialog.Builder(this)
                .setTitle("Kaydı sil")
                .setMessage(it.title() + "\n" + fmtDate(it.dateMs) + "\n\nBu kayıt kalıcı olarak silinsin mi?")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sil", (d, w) -> delete(it))
                .show();
    }

    private void delete(RecordingItem it) {
        if (playing != null && playing.id == it.id) stopPlayer();
        try {
            getContentResolver().delete(it.uri, null, null);
            removeItem(it);
        } catch (SecurityException se) {
            pendingDelete = it;
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), Collections.singletonList(it.uri));
                    startIntentSenderForResult(pi.getIntentSender(), REQ_DEL, null, 0, 0, 0);
                } else if (se instanceof RecoverableSecurityException) {
                    startIntentSenderForResult(((RecoverableSecurityException) se).getUserAction().getActionIntent().getIntentSender(),
                            REQ_DEL, null, 0, 0, 0);
                } else {
                    Toast.makeText(this, "Silme izni yok", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Silinemedi", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Silinemedi", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeItem(RecordingItem it) {
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).id == it.id) all.remove(i);
        applyFilter();
        Toast.makeText(this, "Kayıt silindi", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DEL && resultCode == RESULT_OK && pendingDelete != null) {
            RecordingItem it = pendingDelete;
            pendingDelete = null;
            if (Build.VERSION.SDK_INT < 30) {
                try { getContentResolver().delete(it.uri, null, null); } catch (Exception ignored) { }
            }
            removeItem(it);
        }
    }
}
