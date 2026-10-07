package com.mithrilmania.blocktopograph.plugin;

/**
 * Public contract for third-party "world action" plugins.
 * <p>
 * A plugin is any installed Android app (does NOT need to depend on
 * Blocktopograph's code in any way) that declares an activity like this in
 * its own AndroidManifest.xml:
 * <pre>{@code
 * <activity android:name=".MyPluginActivity" android:label="My Plugin Name">
 *     <intent-filter>
 *         <action android:name="com.mithrilmania.blocktopograph.action.WORLD_ACTION" />
 *         <category android:name="android.intent.category.DEFAULT" />
 *     </intent-filter>
 * </activity>
 * }</pre>
 * <p>
 * Blocktopograph discovers every app on the device that declares this
 * intent-filter and lists it under "Plugins" in a world's detail screen.
 * When the user taps it, Blocktopograph launches the plugin's activity with:
 * <pre>{@code
 * intent.getStringExtra(PluginContract.EXTRA_WORLD_PATH)   // absolute path to the world folder
 * intent.getStringExtra(PluginContract.EXTRA_WORLD_NAME)   // the world's display name
 * }</pre>
 * The plugin can then read/write files inside that folder itself (using its
 * own storage permissions, or its own Shizuku integration if it needs
 * protected paths) -- Blocktopograph does not need to know anything about
 * what the plugin does.
 * <p>
 * That's the whole contract: one activity, one intent-filter, two extras to
 * read. No SDK, no library, no compiling against Blocktopograph.
 */
public final class PluginContract {

    /** Action a plugin's activity must declare in its intent-filter. */
    public static final String ACTION_WORLD_ACTION =
            "com.mithrilmania.blocktopograph.action.WORLD_ACTION";

    /** String extra: absolute path to the world's folder on disk. */
    public static final String EXTRA_WORLD_PATH = "com.mithrilmania.blocktopograph.extra.WORLD_PATH";

    /** String extra: the world's display name (may contain spaces/emoji, not filesystem-safe). */
    public static final String EXTRA_WORLD_NAME = "com.mithrilmania.blocktopograph.extra.WORLD_NAME";

    private PluginContract() {
    }
}
