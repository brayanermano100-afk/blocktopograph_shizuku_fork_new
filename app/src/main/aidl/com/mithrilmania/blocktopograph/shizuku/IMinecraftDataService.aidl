// AIDL definition for the privileged (shell/root) helper started by Shizuku.
//
// This runs OUTSIDE our normal app process, as the Shizuku/ADB shell identity
// (uid 2000, or 0 if the backend is root). That identity is not restricted by
// scoped storage the way our app's own uid is, so it can see
// Android/data/com.mojang.minecraftpe even though our app cannot.
package com.mithrilmania.blocktopograph.shizuku;

interface IMinecraftDataService {

    /**
     * Lists Bedrock world folder names found under
     * Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds
     * (and, as a fallback, the same path under com.mojang.minecraftpe's other
     * possible install locations). Returns an empty array if none found.
     */
    String[] listWorlds();

    /**
     * Lists subfolder names directly inside the given absolute path, using
     * the shell/root identity. Used to browse arbitrary protected folders
     * (e.g. anywhere under Android/data) that our app can't read on its own.
     * Returns an empty array if the path doesn't exist or has no subfolders.
     */
    String[] listDir(String path);

    /**
     * Returns the absolute path to Minecraft's minecraftWorlds folder under
     * its protected Android/data directory, or null if not found.
     */
    String getDefaultMinecraftWorldsPath();

    /**
     * Reads the world's real display name (LevelName) straight from its
     * level.dat, so pickers can show "My Survival World" instead of the
     * random folder name Minecraft actually uses on disk.
     * Returns worldName itself if level.dat can't be read.
     */
    String getWorldDisplayName(String worldName);

    /**
     * Copies an arbitrary file or folder (recursively) from src to dest using
     * the shell/root identity. Used for both single-file previews (copying
     * just level.dat out) and full world imports from any browsed path.
     *
     * @return null on success, or a short error message on failure.
     */
    String copyPath(String src, String dest);

    /** Sum of file sizes under path (recursive), or -1 if it doesn't exist. */
    long getDirSize(String path);

    /** Last-modified timestamp (millis) of path, or -1 if it doesn't exist. */
    long getLastModified(String path);

    /**
     * Copies one world folder from Minecraft's protected data directory into
     * destDir (a path our app CAN normally read/write, e.g. under
     * /sdcard/games/com.mojang/minecraftWorlds). Overwrites existing files.
     *
     * @return null on success, or a short error message on failure.
     */
    String importWorld(String worldName, String destDir);

    /**
     * Copies a locally-edited world folder back into Minecraft's protected
     * data directory, so the game picks up the changes.
     *
     * @return null on success, or a short error message on failure.
     */
    String exportWorld(String worldName, String srcDir);

    /** Lets the app confirm the privileged process is alive and working. */
    boolean ping();

    /** Terminates the remote helper process (called when the app no longer needs it). */
    void destroy();
}
