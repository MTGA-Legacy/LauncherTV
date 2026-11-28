package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.os.AsyncTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private TextView onlineWallpaperChoice, showWallpaperChoice, showDateChoice, showNamesChoice, showNetworkChoice, showBatteryChoice, keepScreenOnChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;
    private boolean showWallpaper, showDate, showNames, showNetwork, showBattery, keepScreenOn, onlineWallpaper;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;
        showDate = config.showDate;
        showWallpaper = choices.getBoolean(Config.SHOW_WALLPAPER_KEY, true);
        showNames = config.alwaysShowNames;
        showNetwork = config.showNetworkPill;
        showBattery = config.showBattery;
        keepScreenOn = config.keepScreenOn;
        onlineWallpaper = choices.getBoolean(Config.ONLINE_WALLPAPER_KEY, false);

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
            reverseRow(clockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        showWallpaperChoice = findViewById(R.id.showWallpaperChoice);
        showWallpaperChoice.setOnClickListener(v -> { showWallpaper = !showWallpaper; choices.edit().putBoolean(Config.SHOW_WALLPAPER_KEY, showWallpaper).apply(); changed(); refresh(); });
        onlineWallpaperChoice = findViewById(R.id.onlineWallpaperChoice);
        onlineWallpaperChoice.setOnClickListener(v -> showOnlineWallpapers());
        onlineWallpaperChoice.setOnLongClickListener(v -> {
            String url = choices.getString("online_wallpaper_url", "");
            if (url.isEmpty()) return false;
            open(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
            return true;
        });
        showDateChoice = findViewById(R.id.showDateChoice);
        showDateChoice.setOnClickListener(v -> { showDate = !showDate; choices.edit().putBoolean(Config.SHOW_DATE_KEY, showDate).apply(); changed(); refresh(); });
        showNamesChoice = findViewById(R.id.showNamesChoice);
        showNamesChoice.setOnClickListener(v -> { showNames = !showNames; choices.edit().putBoolean(Config.SHOW_NAMES_KEY, showNames).apply(); changed(); refresh(); });
        showNetworkChoice = findViewById(R.id.showNetworkChoice);
        showNetworkChoice.setOnClickListener(v -> { showNetwork = !showNetwork; choices.edit().putBoolean("show_network", showNetwork).apply(); changed(); refresh(); });
        showBatteryChoice = findViewById(R.id.showBatteryChoice);
        showBatteryChoice.setOnClickListener(v -> { showBattery = !showBattery; choices.edit().putBoolean(Config.SHOW_BATTERY_KEY, showBattery).apply(); changed(); refresh(); });
        keepScreenOnChoice = findViewById(R.id.keepScreenOnChoice);
        keepScreenOnChoice.setOnClickListener(v -> { keepScreenOn = !keepScreenOn; choices.edit().putBoolean(Config.KEEP_SCREEN_ON_KEY, keepScreenOn).apply(); changed(); refresh(); });
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.CLOCK_FORMAT_KEY).remove(OpenOnStart.KEY)
                    .remove(Config.SHOW_DATE_KEY).remove(Config.SHOW_NAMES_KEY).remove("show_network")
                    .remove(Config.SHOW_BATTERY_KEY)
                    .remove(Config.KEEP_SCREEN_ON_KEY).remove(Config.SHOW_WALLPAPER_KEY)
                    .remove(Config.ONLINE_WALLPAPER_KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            clockFormat = current.clockFormat;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            showDate = current.showDate;
            showWallpaper = true;
            showNames = current.alwaysShowNames;
            showNetwork = current.showNetworkPill;
            showBattery = current.showBattery;
            keepScreenOn = current.keepScreenOn;
            onlineWallpaper = false;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v ->
                open(new Intent(this, HomeSettingsActivity.class)));
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showOnlineWallpapers() {
        onlineWallpaperChoice.setText(R.string.wallpaper_online_loading);
        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            try {
                List<OnlineWallpaperSource.Item> items = OnlineWallpaperSource.fetch();
                runOnUiThread(() -> {
                    if (items.isEmpty()) { Toast.makeText(this, R.string.wallpaper_online_empty, Toast.LENGTH_LONG).show(); refresh(); return; }
                    String[] labels = new String[items.size()];
                    for (int i = 0; i < items.size(); i++) {
                        OnlineWallpaperSource.Item item = items.get(i);
                        labels[i] = item.credit().isEmpty() ? item.title : item.title + " · " + item.credit();
                    }
                    new AlertDialog.Builder(this).setTitle(R.string.wallpaper_online)
                            .setItems(labels, (dialog, which) -> applyOnlineWallpaper(items.get(which)))
                            .setNegativeButton(android.R.string.cancel, null).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> { Toast.makeText(this, R.string.wallpaper_online_failed, Toast.LENGTH_LONG).show(); refresh(); });
            }
        });
    }

    private void applyOnlineWallpaper(OnlineWallpaperSource.Item item) {
        Toast.makeText(this, R.string.wallpaper_online_loading, Toast.LENGTH_SHORT).show();
        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            try {
                OnlineWallpaperSource.download(this, item);
                choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).putBoolean(Config.ONLINE_WALLPAPER_KEY, true)
                        .putString("online_wallpaper_title", item.title)
                        .putString("online_wallpaper_credit", item.credit())
                        .putString("online_wallpaper_url", item.pageUrl).apply();
                runOnUiThread(() -> { onlineWallpaper = true; changed(); refresh(); Toast.makeText(this, R.string.wallpaper_online_applied, Toast.LENGTH_SHORT).show(); });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, R.string.wallpaper_online_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        onlineWallpaperChoice.setText(getString(R.string.wallpaper_online_state,
                onlineWallpaper ? choices.getString("online_wallpaper_title", getString(R.string.on)) : getString(R.string.off)));
        showWallpaperChoice.setText(getString(R.string.show_wallpaper_setting) + ": " + getString(showWallpaper ? R.string.on : R.string.off));
        showDateChoice.setText(getString(R.string.show_date_setting) + ": " + getString(showDate ? R.string.on : R.string.off));
        showNamesChoice.setText(getString(R.string.show_names_setting) + ": " + getString(showNames ? R.string.on : R.string.off));
        showNetworkChoice.setText(getString(R.string.show_network_setting) + ": " + getString(showNetwork ? R.string.on : R.string.off));
        showBatteryChoice.setText(getString(R.string.show_battery_setting) + ": " + getString(showBattery ? R.string.on : R.string.off));
        keepScreenOnChoice.setText(getString(R.string.keep_screen_on_setting) + ": " + getString(keepScreenOn ? R.string.on : R.string.off));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(OpenOnStart.KEY)
                || choices.contains(Config.SHOW_DATE_KEY) || choices.contains(Config.SHOW_NAMES_KEY)
                || choices.contains("show_network") || choices.contains(Config.SHOW_BATTERY_KEY) || choices.contains(Config.KEEP_SCREEN_ON_KEY)
                || choices.contains(Config.SHOW_WALLPAPER_KEY) || choices.contains(Config.ONLINE_WALLPAPER_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
