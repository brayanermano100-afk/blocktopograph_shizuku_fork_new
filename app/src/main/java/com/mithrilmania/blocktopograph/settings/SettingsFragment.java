package com.mithrilmania.blocktopograph.settings;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.mithrilmania.blocktopograph.R;
import com.mithrilmania.blocktopograph.plugin.PluginManager;
import com.mithrilmania.blocktopograph.util.UiUtil;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.List;

import rikka.shizuku.Shizuku;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        setupShizukuStatus();
        setupPluginsInstalled();
        setupPluginsHowTo();
        setupClearCache();
        setupVersion();
    }

    private void setupShizukuStatus() {
        Preference pref = findPreference("pref_shizuku_status");
        if (pref == null) return;
        refreshShizukuSummary(pref);
        pref.setOnPreferenceClickListener(p -> {
            refreshShizukuSummary(p);
            return true;
        });
    }

    private void refreshShizukuSummary(Preference pref) {
        String summary;
        try {
            if (!Shizuku.pingBinder()) {
                summary = getString(R.string.settings_shizuku_status_summary_not_running);
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                summary = getString(R.string.settings_shizuku_status_summary_granted);
            } else {
                summary = getString(R.string.settings_shizuku_status_summary_not_granted);
            }
        } catch (Exception e) {
            summary = getString(R.string.settings_shizuku_status_summary_not_running);
        }
        pref.setSummary(summary);
    }

    private void setupPluginsInstalled() {
        Preference pref = findPreference("pref_plugins_installed");
        if (pref == null) return;
        List<ResolveInfo> plugins = PluginManager.findWorldActionPlugins(requireContext());
        pref.setSummary(String.format(getString(R.string.settings_plugins_installed_summary), plugins.size()));
        pref.setOnPreferenceClickListener(p -> {
            List<ResolveInfo> current = PluginManager.findWorldActionPlugins(requireContext());
            if (current.isEmpty()) {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.settings_plugins_installed_title)
                        .setMessage(R.string.settings_plugins_none_found)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return true;
            }
            StringBuilder sb = new StringBuilder();
            PackageManager pm = requireContext().getPackageManager();
            for (ResolveInfo ri : current) {
                sb.append("• ").append(ri.loadLabel(pm)).append('\n');
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.settings_plugins_installed_title)
                    .setMessage(sb.toString().trim())
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return true;
        });
    }

    private void setupPluginsHowTo() {
        Preference pref = findPreference("pref_plugins_howto");
        if (pref == null) return;
        pref.setOnPreferenceClickListener(p -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.settings_plugins_howto_title)
                    .setMessage(R.string.settings_plugins_howto_body)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return true;
        });
    }

    private void setupClearCache() {
        Preference pref = findPreference("pref_clear_cache");
        if (pref == null) return;
        pref.setOnPreferenceClickListener(p -> {
            File cache = requireContext().getCacheDir();
            File[] children = cache.listFiles();
            long freed = 0;
            if (children != null) {
                for (File f : children) {
                    try {
                        freed += FileUtils.sizeOf(f);
                        if (f.isDirectory()) FileUtils.deleteDirectory(f);
                        else f.delete();
                    } catch (Exception ignored) {
                    }
                }
            }
            UiUtil.snack(requireActivity(), String.format(
                    getString(R.string.settings_clear_cache_done), freed / 1024 / 1024));
            return true;
        });
    }

    private void setupVersion() {
        Preference pref = findPreference("pref_version");
        if (pref == null) return;
        try {
            PackageInfo info = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            pref.setSummary(info.versionName + " (" + info.versionCode + ")");
        } catch (Exception ignored) {
        }
    }
}
