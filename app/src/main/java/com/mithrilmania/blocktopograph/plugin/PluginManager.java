package com.mithrilmania.blocktopograph.plugin;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

/** Finds installed apps that declare the {@link PluginContract#ACTION_WORLD_ACTION} intent. */
public final class PluginManager {

    private PluginManager() {
    }

    public static List<ResolveInfo> findWorldActionPlugins(Context context) {
        Intent probe = new Intent(PluginContract.ACTION_WORLD_ACTION);
        PackageManager pm = context.getPackageManager();
        return pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY);
    }

    /** Builds a launchable intent for the given plugin, with the world's info attached. */
    public static Intent buildLaunchIntent(ResolveInfo plugin, String worldPath, String worldName) {
        Intent intent = new Intent(PluginContract.ACTION_WORLD_ACTION);
        intent.setClassName(plugin.activityInfo.packageName, plugin.activityInfo.name);
        intent.putExtra(PluginContract.EXTRA_WORLD_PATH, worldPath);
        intent.putExtra(PluginContract.EXTRA_WORLD_NAME, worldName);
        return intent;
    }
}
