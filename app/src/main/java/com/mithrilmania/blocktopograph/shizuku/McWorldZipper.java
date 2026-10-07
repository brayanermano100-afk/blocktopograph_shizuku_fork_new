package com.mithrilmania.blocktopograph.shizuku;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Packs a Blocktopograph world folder into a .mcworld file (which is simply a
 * zip of the world's contents with the folder itself as the zip root). Handing
 * this file to Minecraft via an ACTION_VIEW intent lets Minecraft import it
 * using its own permissions, so we never need write access to Minecraft's
 * protected Android/data folder ourselves.
 */
public final class McWorldZipper {

    private McWorldZipper() {
    }

    public static void zipWorld(File worldFolder, File destMcworldFile) throws IOException {
        File parent = destMcworldFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("No se pudo crear " + parent);
        }
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(destMcworldFile))) {
            addFolderToZip(worldFolder, "", zos);
        }
    }

    private static void addFolderToZip(File folder, String basePath, ZipOutputStream zos) throws IOException {
        File[] children = folder.listFiles();
        if (children == null) return;
        byte[] buffer = new byte[64 * 1024];
        for (File child : children) {
            String entryPath = basePath + child.getName();
            if (child.isDirectory()) {
                addFolderToZip(child, entryPath + "/", zos);
            } else {
                zos.putNextEntry(new ZipEntry(entryPath));
                try (InputStream in = new FileInputStream(child)) {
                    int len;
                    while ((len = in.read(buffer)) > 0) zos.write(buffer, 0, len);
                }
                zos.closeEntry();
            }
        }
    }

    /** Extracts a .mcworld (zip) into destFolder, which is created if needed. */
    public static void unzipToFolder(InputStream zipInput, File destFolder) throws IOException {
        if (!destFolder.exists() && !destFolder.mkdirs()) {
            throw new IOException("No se pudo crear " + destFolder);
        }
        byte[] buffer = new byte[64 * 1024];
        try (ZipInputStream zis = new ZipInputStream(zipInput)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destFolder, entry.getName());
                // Guard against zip-slip (entries trying to escape destFolder).
                if (!outFile.getCanonicalPath().startsWith(destFolder.getCanonicalPath() + File.separator)
                        && !outFile.getCanonicalPath().equals(destFolder.getCanonicalPath())) {
                    continue;
                }
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (OutputStream out = new FileOutputStream(outFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) out.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
