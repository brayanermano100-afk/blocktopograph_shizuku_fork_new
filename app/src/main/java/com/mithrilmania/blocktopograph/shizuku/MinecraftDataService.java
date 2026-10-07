package com.mithrilmania.blocktopograph.shizuku;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Runs in a separate process started by Shizuku with shell (uid 2000) or root
 * (uid 0) privileges -- NOT as a normal Android app component. Because of that
 * elevated identity, plain java.io.File calls here can reach directories that
 * are off-limits to our app's own process under scoped storage, in particular:
 * <p>
 * /storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds
 * <p>
 * Keep this class free of Android SDK calls that need a real app Context --
 * most of them (registerReceiver, getContentResolver, etc.) don't work here.
 */
public class MinecraftDataService extends IMinecraftDataService.Stub {

    // Constructor required by Shizuku's UserService mechanism.
    public MinecraftDataService() {
    }

    // Some Shizuku versions instantiate the service with a Context-like arg; keep a
    // no-op fallback constructor too, harmless if unused.
    public MinecraftDataService(Object context) {
    }

    private static final String[] CANDIDATE_ROOTS = {
            "/storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds",
            "/sdcard/Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds",
    };

    private File findWorldsDir() {
        for (String path : CANDIDATE_ROOTS) {
            File f = new File(path);
            if (f.isDirectory()) return f;
        }
        return null;
    }

    @Override
    public String[] listWorlds() {
        File worldsDir = findWorldsDir();
        if (worldsDir == null) return new String[0];
        File[] children = worldsDir.listFiles(File::isDirectory);
        if (children == null) return new String[0];
        String[] names = new String[children.length];
        for (int i = 0; i < children.length; i++) names[i] = children[i].getName();
        return names;
    }

    @Override
    public String[] listDir(String path) {
        if (path == null) return new String[0];
        File dir = new File(path);
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) return new String[0];
        String[] names = new String[children.length];
        for (int i = 0; i < children.length; i++) names[i] = children[i].getName();
        java.util.Arrays.sort(names);
        return names;
    }

    @Override
    public String getDefaultMinecraftWorldsPath() {
        File worldsDir = findWorldsDir();
        return worldsDir == null ? null : worldsDir.getAbsolutePath();
    }

    @Override
    public String getWorldDisplayName(String worldName) {
        File worldsDir = findWorldsDir();
        if (worldsDir == null) return worldName;
        File levelFile = new File(new File(worldsDir, worldName), "level.dat");
        if (!levelFile.isFile()) return worldName;
        try {
            com.mithrilmania.blocktopograph.nbt.tags.CompoundTag level =
                    com.mithrilmania.blocktopograph.nbt.convert.LevelDataConverter.read(levelFile);
            com.mithrilmania.blocktopograph.nbt.tags.Tag nameTag = level.getChildTagByKey("LevelName");
            if (nameTag instanceof com.mithrilmania.blocktopograph.nbt.tags.StringTag) {
                String name = ((com.mithrilmania.blocktopograph.nbt.tags.StringTag) nameTag).getValue();
                if (name != null && !name.trim().isEmpty()) return name;
            }
        } catch (Exception ignored) {
        }
        return worldName;
    }

    @Override
    public String copyPath(String src, String dest) {
        try {
            copyRecursive(new File(src), new File(dest));
            return null;
        } catch (Exception e) {
            return "Error copiando: " + e.getMessage();
        }
    }

    @Override
    public long getDirSize(String path) {
        File f = new File(path);
        if (!f.exists()) return -1;
        return dirSize(f);
    }

    private static long dirSize(File f) {
        if (f.isFile()) return f.length();
        File[] children = f.listFiles();
        if (children == null) return 0;
        long total = 0;
        for (File child : children) total += dirSize(child);
        return total;
    }

    @Override
    public long getLastModified(String path) {
        File f = new File(path);
        return f.exists() ? f.lastModified() : -1;
    }

    @Override
    public String importWorld(String worldName, String destDir) {
        File worldsDir = findWorldsDir();
        if (worldsDir == null)
            return "No se encontró la carpeta de datos de Minecraft (¿está instalado?).";
        File source = new File(worldsDir, worldName);
        if (!source.isDirectory()) return "El mundo \"" + worldName + "\" no existe.";
        File dest = new File(destDir, worldName);
        try {
            copyRecursive(source, dest);
            return null;
        } catch (IOException e) {
            return "Error copiando el mundo: " + e.getMessage();
        }
    }

    @Override
    public String exportWorld(String worldName, String srcDir) {
        File worldsDir = findWorldsDir();
        if (worldsDir == null)
            return "No se encontró la carpeta de datos de Minecraft (¿está instalado?).";
        File source = new File(srcDir, worldName);
        if (!source.isDirectory()) return "No se encontró el mundo local \"" + worldName + "\".";
        File dest = new File(worldsDir, worldName);
        try {
            copyRecursive(source, dest);
            return null;
        } catch (IOException e) {
            return "Error copiando el mundo de vuelta: " + e.getMessage();
        }
    }

    @Override
    public boolean ping() {
        return true;
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    private static void copyRecursive(File source, File dest) throws IOException {
        if (source.isDirectory()) {
            if (!dest.exists() && !dest.mkdirs())
                throw new IOException("No se pudo crear " + dest);
            File[] children = source.listFiles();
            if (children != null) for (File child : children) {
                copyRecursive(child, new File(dest, child.getName()));
            }
        } else {
            copyFile(source, dest);
        }
    }

    private static void copyFile(File source, File dest) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
    }
}
