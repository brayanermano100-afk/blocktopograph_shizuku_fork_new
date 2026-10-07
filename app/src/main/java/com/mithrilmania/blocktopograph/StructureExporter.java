package com.mithrilmania.blocktopograph;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.litl.leveldb.DB;
import com.litl.leveldb.Iterator;
import com.mithrilmania.blocktopograph.shizuku.IMinecraftDataService;
import com.mithrilmania.blocktopograph.shizuku.ShizukuBridge;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Estructuras del bloque de estructura (claves "structuretemplate_<namespace>:<nombre>"
 * en la db de cada mundo).
 *
 *  - showDialog(activity, world):        exporta una estructura del mundo abierto a Descargas.
 *  - showImportDialog(activity, world):  lista las estructuras de TODOS tus otros mundos
 *                                        (nombre de la estructura + nombre del mundo debajo)
 *                                        e importa la que elijas al mundo abierto.
 *                                        Boton "Desde Descargas" para importar un .mcstructure suelto.
 */
public final class StructureExporter {

    private static final String TAG = "StructureExporter";
    private static final String PREFIX_STR = "structuretemplate_";
    private static final byte[] PREFIX = PREFIX_STR.getBytes(StandardCharsets.ISO_8859_1);

    private StructureExporter() {
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    public static final class Entry {
        public final byte[] key;
        public final String id; // ej: mystructure:jubi

        Entry(byte[] key) {
            this.key = key;
            this.id = idFromKey(key);
        }
    }

    /** Estructura encontrada en otro mundo. */
    public static final class Item {
        public final byte[] key;
        public final String id;
        public final String worldName;
        public final File worldFolder;

        Item(byte[] key, String worldName, File worldFolder) {
            this.key = key;
            this.id = idFromKey(key);
            this.worldName = worldName;
            this.worldFolder = worldFolder;
        }
    }

    private static String idFromKey(byte[] key) {
        return new String(key, PREFIX.length, key.length - PREFIX.length, StandardCharsets.UTF_8);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length <= prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private static void toast(final Activity activity, final String msg) {
        activity.runOnUiThread(() -> Toast.makeText(activity, msg, Toast.LENGTH_LONG).show());
    }

    private static byte[] safeGet(DB db, byte[] key) {
        try {
            return db.get(key);
        } catch (Exception e) { // clave inexistente
            return null;
        }
    }

    /** Carpeta de mundos REAL de Minecraft Bedrock (Android/data), no la de games/. */
    private static final String MC_WORLDS_DATA =
            "Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds";

    private static String canonical(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            return f.getAbsolutePath();
        }
    }

    // ------------------------------------------------------------------
    // EXPORTAR: estructura del mundo abierto -> Descargas/*.mcstructure
    // ------------------------------------------------------------------

    /** Recorre las claves de la db del mundo abierto y devuelve las estructuras. */
    public static List<Entry> list(World world) throws WorldData.WorldDBException {
        WorldData wd = world.getWorldData();
        wd.openDB();
        List<Entry> out = new ArrayList<>();
        Iterator it = wd.db.iterator();
        try {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                byte[] key = it.getKey();
                if (startsWith(key, PREFIX)) out.add(new Entry(key));
            }
        } finally {
            it.close();
        }
        return out;
    }

    /** Escribe el valor de la clave tal cual (ya es NBT little-endian) como .mcstructure. */
    public static File export(World world, Entry entry, File outDir)
            throws IOException, WorldData.WorldDBException {
        WorldData wd = world.getWorldData();
        wd.openDB();
        byte[] data = wd.db.get(entry.key);
        if (data == null || data.length == 0) {
            throw new IOException("La estructura esta vacia o no se pudo leer");
        }
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta " + outDir);
        }
        String fileName = entry.id.replace(':', '_').replace('/', '_') + ".mcstructure";
        File out = new File(outDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(data);
        }
        return out;
    }

    /** Muestra la lista de estructuras del mundo y exporta la que elijas a Descargas. */
    public static void showDialog(final Activity activity, final World world) {
        new Thread(() -> {
            final List<Entry> list;
            try {
                list = list(world);
            } catch (Exception e) {
                toast(activity, "Error leyendo la base de datos: " + e.getMessage());
                return;
            }
            activity.runOnUiThread(() -> {
                if (list.isEmpty()) {
                    Toast.makeText(activity,
                            "No hay estructuras guardadas en este mundo",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                final String[] names = new String[list.size()];
                for (int i = 0; i < names.length; i++) names[i] = list.get(i).id;
                new AlertDialog.Builder(activity)
                        .setTitle("Estructuras (" + names.length + ")")
                        .setItems(names, (dialog, which) -> exportAsync(activity, world, list.get(which)))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }).start();
    }

    private static void exportAsync(final Activity activity, final World world, final Entry entry) {
        new Thread(() -> {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                File out = export(world, entry, dir);
                toast(activity, "Guardado en Descargas: " + out.getName());
            } catch (Exception e) {
                toast(activity, "Error al exportar: " + e.getMessage());
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // IMPORTAR desde OTROS MUNDOS: lista todas las estructuras de tus mundos
    // ------------------------------------------------------------------

    /**
     * Busca estructuras en los mundos de Minecraft (Android/data), menos el abierto.
     * Si la app no puede leer esa carpeta por si sola (Android 11+), usa Shizuku para
     * copiar solo la carpeta "db" de cada mundo a la cache y la lee desde ahi.
     *
     * @param svc    servicio de Shizuku, o null para leer directamente.
     * @param direct carpeta minecraftWorlds legible directamente (si svc == null).
     */
    private static List<Item> scanMinecraftWorlds(Activity activity, World current,
                                                  IMinecraftDataService svc, File direct,
                                                  StringBuilder diag) {
        List<Item> out = new ArrayList<>();
        int total = 0, skippedCurrent = 0, copyFail = 0, openFail = 0, opened = 0;
        String firstErr = null;
        diag.append("Modo: ").append(svc != null ? "Shizuku" : "directo").append('\n');
        String currentName = current.worldFolder.getName();

        List<String> names = new ArrayList<>();
        String mcPath = null;
        File cacheRoot = null;
        try {
            if (svc != null) {
                mcPath = svc.getDefaultMinecraftWorldsPath();
                String[] list = svc.listWorlds();
                diag.append("Carpeta Minecraft: ").append(mcPath).append('\n');
                if (mcPath == null || list == null) {
                    diag.append("No se encontro la carpeta de mundos de Minecraft.");
                    return out;
                }
                for (String n : list) names.add(n);
                File base = activity.getExternalCacheDir();
                if (base == null) base = activity.getCacheDir();
                cacheRoot = new File(base, "mc_structure_scan");
                FileUtils.deleteQuietly(cacheRoot);
                //noinspection ResultOfMethodCallIgnored
                cacheRoot.mkdirs();
            } else {
                File[] dirs = direct.listFiles(f -> f.isDirectory() && new File(f, "db").isDirectory());
                if (dirs != null) for (File d : dirs) names.add(d.getName());
            }
        } catch (Exception e) {
            Log.d(TAG, "No se pudo listar los mundos de Minecraft: " + e);
            diag.append("Error listando mundos: ").append(e);
            return out;
        }

        total = names.size();
        for (String name : names) {
            if (name.equals(currentName)) {
                skippedCurrent++;
                continue;
            }

            File dir;           // carpeta que contiene "db"
            String worldName = name;
            try {
                if (svc != null) {
                    String n = svc.getWorldDisplayName(name);
                    if (n != null && !n.trim().isEmpty()) worldName = n;
                    dir = new File(cacheRoot, name);
                    String err = svc.copyPath(mcPath + "/" + name + "/db",
                            new File(dir, "db").getAbsolutePath());
                    if (err != null) {
                        Log.d(TAG, "No se pudo copiar " + name + ": " + err);
                        copyFail++;
                        if (firstErr == null) firstErr = "copia: " + err;
                        continue;
                    }
                } else {
                    dir = new File(direct, name);
                    try {
                        String n = new World(dir, null, activity).getWorldDisplayName();
                        if (n != null && !n.trim().isEmpty()) worldName = n;
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "No se pudo preparar " + name + ": " + e);
                copyFail++;
                if (firstErr == null) firstErr = "preparar: " + e;
                continue;
            }

            DB db = null;
            try {
                db = new DB(new File(dir, "db"));
                db.open();
                opened++;
                Iterator it = db.iterator();
                try {
                    for (it.seekToFirst(); it.isValid(); it.next()) {
                        byte[] key = it.getKey();
                        if (startsWith(key, PREFIX)) out.add(new Item(key, worldName, dir));
                    }
                } finally {
                    it.close();
                }
            } catch (Exception e) {
                // dañado o sin permiso: se omite
                Log.d(TAG, "No se pudo leer " + name + ": " + e);
                openFail++;
                if (firstErr == null) firstErr = "abrir db: " + e;
            } finally {
                if (db != null) {
                    try {
                        db.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        diag.append("Mundos en Minecraft: ").append(total)
                .append("\nOmitidos (el mundo abierto): ").append(skippedCurrent)
                .append("\nCopias fallidas: ").append(copyFail)
                .append("\ndb abiertas: ").append(opened)
                .append(" (fallaron ").append(openFail).append(")")
                .append("\nEstructuras halladas: ").append(out.size());
        if (firstErr != null) diag.append("\nPrimer error: ").append(firstErr);

        Collections.sort(out, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                int c = a.id.compareToIgnoreCase(b.id);
                return c != 0 ? c : a.worldName.compareToIgnoreCase(b.worldName);
            }
        });
        return out;
    }

    private static byte[] readFromWorld(Item item) {
        DB db = null;
        try {
            db = new DB(new File(item.worldFolder, "db"));
            db.open();
            return safeGet(db, item.key);
        } finally {
            if (db != null) {
                try {
                    db.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Lista estructuras de los mundos de Minecraft (Android/data), con el nombre del mundo debajo, e importa la elegida. */
    public static void showImportDialog(final Activity activity, final World world) {
        Toast.makeText(activity, "Buscando estructuras en los mundos de Minecraft...",
                Toast.LENGTH_SHORT).show();

        // 1) Lectura directa de Android/data (solo funciona en Android 10 o menos / root).
        final File direct = new File(Environment.getExternalStorageDirectory(), MC_WORLDS_DATA);
        File[] probe = direct.listFiles();
        if (probe != null && probe.length > 0) {
            scanAndShow(activity, world, null, direct);
            return;
        }

        // 2) Android 11+: acceso a Android/data mediante Shizuku.
        ShizukuBridge.ensureReady(activity, new ShizukuBridge.Callback() {
            @Override
            public void onReady(IMinecraftDataService service) {
                scanAndShow(activity, world, service, direct);
            }

            @Override
            public void onUnavailable(String reason) {
                new AlertDialog.Builder(activity)
                        .setTitle("Importar estructura")
                        .setMessage("No pude leer la carpeta de Minecraft (Android/data): " + reason)
                        .setPositiveButton("Desde Descargas",
                                (d, w) -> showFileImportDialog(activity, world))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
    }

    private static void scanAndShow(final Activity activity, final World world,
                                    final IMinecraftDataService svc, final File direct) {
        new Thread(() -> {
            final StringBuilder diag = new StringBuilder();
            final List<Item> items = scanMinecraftWorlds(activity, world, svc, direct, diag);
            activity.runOnUiThread(() -> {
                if (items.isEmpty()) {
                    new AlertDialog.Builder(activity)
                            .setTitle("Importar estructura")
                            .setMessage("No encontre estructuras en los mundos de Minecraft."
                                    + "\n(Cierra Minecraft si tiene algun mundo abierto.)"
                                    + "\n\n" + diag)
                            .setPositiveButton("Desde Descargas",
                                    (d, w) -> showFileImportDialog(activity, world))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    return;
                }
                ArrayAdapter<Item> adapter = new ArrayAdapter<Item>(activity,
                        android.R.layout.simple_list_item_2, android.R.id.text1, items) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View v = super.getView(position, convertView, parent);
                        Item it = getItem(position);
                        ((TextView) v.findViewById(android.R.id.text1)).setText(it.id);
                        ((TextView) v.findViewById(android.R.id.text2)).setText(it.worldName);
                        return v;
                    }
                };
                new AlertDialog.Builder(activity)
                        .setTitle("Importar estructura (" + items.size() + ")")
                        .setAdapter(adapter, (dialog, which) ->
                                confirmAndImport(activity, world, items.get(which)))
                        .setNeutralButton("Desde Descargas",
                                (d, w) -> showFileImportDialog(activity, world))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }).start();
    }

    private static void confirmAndImport(final Activity activity, final World world, final Item item) {
        new Thread(() -> {
            boolean exists;
            try {
                WorldData wd = world.getWorldData();
                wd.openDB();
                exists = safeGet(wd.db, item.key) != null;
            } catch (Exception e) {
                toast(activity, "Error leyendo el mundo abierto: " + e.getMessage());
                return;
            }
            final boolean replace = exists;
            activity.runOnUiThread(() -> {
                if (!replace) {
                    importFromWorldAsync(activity, world, item);
                    return;
                }
                new AlertDialog.Builder(activity)
                        .setTitle("Ya existe")
                        .setMessage("Este mundo ya tiene \"" + item.id + "\". Quieres reemplazarla?")
                        .setPositiveButton("Reemplazar",
                                (d, w) -> importFromWorldAsync(activity, world, item))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }).start();
    }

    private static void importFromWorldAsync(final Activity activity, final World world, final Item item) {
        new Thread(() -> {
            try {
                byte[] data = readFromWorld(item);
                if (data == null || data.length == 0) {
                    throw new IOException("No se pudo leer la estructura del mundo \""
                            + item.worldName + "\"");
                }
                WorldData wd = world.getWorldData();
                wd.openDB();
                wd.db.put(item.key, data);
                toast(activity, "Importada: " + item.id
                        + ". Sal del mundo antes de abrir Minecraft.");
            } catch (Exception e) {
                toast(activity, "Error al importar: " + e.getMessage());
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // IMPORTAR desde un archivo suelto: Descargas/*.mcstructure
    // ------------------------------------------------------------------

    /** Lee un .mcstructure y lo escribe en la db como structuretemplate_<id>. */
    public static void importStructure(World world, File file, String id)
            throws IOException, WorldData.WorldDBException {
        if (id == null || !id.contains(":") || id.startsWith(":") || id.endsWith(":")) {
            throw new IOException("El nombre debe tener el formato namespace:nombre");
        }
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) break;
                off += n;
            }
            if (off != data.length) throw new IOException("No se pudo leer el archivo completo");
        }
        // Un .mcstructure es NBT sin comprimir: debe empezar con un tag Compound (0x0A)
        if (data.length < 8 || data[0] != 0x0A) {
            throw new IOException("No parece un archivo .mcstructure valido");
        }
        byte[] key = (PREFIX_STR + id).getBytes(StandardCharsets.UTF_8);
        WorldData wd = world.getWorldData();
        wd.openDB();
        wd.db.put(key, data);
    }

    /** Lista los .mcstructure de Descargas, pide el nombre y los importa al mundo. */
    public static void showFileImportDialog(final Activity activity, final World world) {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        final File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".mcstructure"));
        if (files == null || files.length == 0) {
            Toast.makeText(activity, "No hay archivos .mcstructure en Descargas",
                    Toast.LENGTH_LONG).show();
            return;
        }
        final String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) names[i] = files[i].getName();
        new AlertDialog.Builder(activity)
                .setTitle("Importar desde Descargas")
                .setItems(names, (dialog, which) -> askName(activity, world, files[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String guessId(String fileName) {
        String base = fileName.substring(0, fileName.length() - ".mcstructure".length());
        if (base.contains(":")) return base;
        int u = base.indexOf('_');
        if (u > 0 && u < base.length() - 1) {
            return base.substring(0, u) + ":" + base.substring(u + 1);
        }
        return "mystructure:" + base;
    }

    private static void askName(final Activity activity, final World world, final File file) {
        final EditText edit = new EditText(activity);
        edit.setSingleLine(true);
        edit.setText(guessId(file.getName()));
        new AlertDialog.Builder(activity)
                .setTitle("Nombre en el bloque de estructura")
                .setMessage("Con este nombre la cargaras en modo Cargar. Si ya existe, se reemplaza.")
                .setView(edit)
                .setPositiveButton("Importar", (dialog, which) -> {
                    final String id = edit.getText().toString().trim();
                    new Thread(() -> {
                        try {
                            importStructure(world, file, id);
                            toast(activity, "Importada como " + id
                                    + ". Sal del mundo antes de abrir Minecraft.");
                        } catch (Exception e) {
                            toast(activity, "Error al importar: " + e.getMessage());
                        }
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
