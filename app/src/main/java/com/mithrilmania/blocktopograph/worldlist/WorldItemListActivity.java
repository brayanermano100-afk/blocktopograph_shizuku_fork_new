package com.mithrilmania.blocktopograph.worldlist;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.mithrilmania.blocktopograph.CreateWorldActivity;
import com.mithrilmania.blocktopograph.Log;
import com.mithrilmania.blocktopograph.R;
import com.mithrilmania.blocktopograph.World;
import com.mithrilmania.blocktopograph.backup.WorldBackups;
import com.mithrilmania.blocktopograph.shizuku.IMinecraftDataService;
import com.mithrilmania.blocktopograph.shizuku.McWorldZipper;
import com.mithrilmania.blocktopograph.shizuku.ShizukuBridge;
import com.mithrilmania.blocktopograph.util.IoUtil;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WorldItemListActivity extends AppCompatActivity {

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 4242;
    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    public static final int REQUEST_CODE_CREATE_WORLD = 2012;
    public static final int REQUEST_CODE_PICK_MCWORLD = 2013;

    /** The folder we scan for/import worlds into: the user's custom path from Settings
     *  if set, otherwise the standard games/com.mojang/minecraftWorlds location. */
    private File getWorldsDestDir() {
        String customPath = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString("pref_custom_worlds_path", null);
        if (customPath != null && !customPath.trim().isEmpty()) {
            return new File(customPath.trim());
        }
        return new File(Environment.getExternalStorageDirectory(), "games/com.mojang/minecraftWorlds");
    }
    public static final String PREF_KEY_ACCEPT_DATA_USAGE = "accept_data_usage";
    /**
     * Whether or not the activity is in two-pane mode, d.e. running on a tablet.
     */
    private boolean mTwoPane;
    private WorldItemRecyclerViewAdapter worldItemAdapter;

    /**
     * Checks if the app has permission to write to device storage
     * <p>
     * If the app does not has permission then the user will be prompted to grant permissions
     * </p>
     */
    public static boolean verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        boolean hasPermission = true;
        for (String permission : PERMISSIONS_STORAGE) {
            int state = ActivityCompat.checkSelfPermission(activity, permission);
            if (state != PackageManager.PERMISSION_GRANTED) {
                hasPermission = false;
                break;
            }
        }

        if (!hasPermission) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
            return false;
        } else return true;
    }

    private void showFeedbackRequestDialogIfNeeded() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        if (prefs.getInt(PREF_KEY_ACCEPT_DATA_USAGE, 0) == 1) {
            Log.enableCrashlytics();
            Log.enableFirebaseAnalytics(this);
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.privacy_promo_title)
                .setMessage(R.string.privacy_promo_text)
                .setPositiveButton(R.string.privacy_promo_accept_btn, this::onAcceptedRequestDialog)
                .setNegativeButton(R.string.privacy_promo_reject_btn, this::onRejectedRequestDialog)
                .setCancelable(false)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        View view = dialog.findViewById(android.R.id.message);
        if (view instanceof TextView)
            ((TextView) view).setMovementMethod(LinkMovementMethod.getInstance());
        else Log.d(this, "cannot find android.R.id.message for privacy request dialog.");
    }

    private void onAcceptedRequestDialog(DialogInterface dialogInterface, int i) {
        Log.enableFirebaseAnalytics(this);
        Log.enableCrashlytics();
        getPreferences(MODE_PRIVATE).edit().putInt(PREF_KEY_ACCEPT_DATA_USAGE, 1).apply();
    }

    private void onRejectedRequestDialog(DialogInterface dialogInterface, int i) {
        finishAffinity();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_worldlist);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());
        setSupportActionBar(toolbar);
        showFeedbackRequestDialogIfNeeded();

        if (findViewById(R.id.worlditem_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }

        FloatingActionButton fabChooseWorldFile = findViewById(R.id.fab_create);
        fabChooseWorldFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClickCreateWorld();
            }
        });

        RecyclerView recyclerView = findViewById(R.id.worlditem_list);
        worldItemAdapter = new WorldItemRecyclerViewAdapter();
        recyclerView.setAdapter(this.worldItemAdapter);

        if (verifyStoragePermissions(this)) {
            //directly open the world list if we already have access
            worldItemAdapter.enable();
        }


    }

    private void onClickCreateWorld() {
        if (worldItemAdapter.isDisabled()) {
            Snackbar.make(getWindow().getDecorView(), R.string.no_read_write_access, Snackbar.LENGTH_SHORT).show();
            return;
        }
        startActivityForResult(new Intent(this, CreateWorldActivity.class), REQUEST_CODE_CREATE_WORLD);
    }

    /**
     * Entry point for the "Importar de Minecraft (Shizuku)" menu action.
     * Asks Shizuku for permission if needed, lists the worlds Minecraft has
     * stored under its protected Android/data folder, lets the user pick one,
     * and copies it into the folder this app already scans for worlds.
     */
    private void onClickImportFromMinecraftShizuku() {
        if (worldItemAdapter.isDisabled()) {
            Snackbar.make(getWindow().getDecorView(), R.string.no_read_write_access, Snackbar.LENGTH_SHORT).show();
            return;
        }
        ShizukuBridge.ensureReady(this, new ShizukuBridge.Callback() {
            @Override
            public void onReady(IMinecraftDataService service) {
                new Thread(() -> {
                    String[] worlds;
                    try {
                        worlds = service.listWorlds();
                    } catch (Exception e) {
                        runOnUiThread(() -> showShizukuError(e.getMessage()));
                        return;
                    }
                    runOnUiThread(() -> showWorldPicker(service, worlds));
                }).start();
            }

            @Override
            public void onUnavailable(String reason) {
                showShizukuError(reason);
            }
        });
    }

    private void showWorldPicker(IMinecraftDataService service, String[] worlds) {
        if (worlds == null || worlds.length == 0) {
            Snackbar.make(getWindow().getDecorView(), R.string.shizuku_no_worlds_found, Snackbar.LENGTH_LONG).show();
            return;
        }
        // Resolve each folder's real LevelName off the main thread before showing the
        // picker, so people see "My Survival World" instead of the random folder name.
        new Thread(() -> {
            String[] displayNames = new String[worlds.length];
            for (int i = 0; i < worlds.length; i++) {
                String resolved;
                try {
                    resolved = service.getWorldDisplayName(worlds[i]);
                } catch (Exception e) {
                    resolved = null;
                }
                displayNames[i] = (resolved == null || resolved.trim().isEmpty()) ? worlds[i] : resolved;
            }
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle(R.string.shizuku_pick_world_title)
                    .setItems(displayNames, (dialog, which) -> importWorldViaShizuku(service, worlds[which]))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());
        }).start();
    }

    private void importWorldViaShizuku(IMinecraftDataService service, String worldName) {
        Snackbar.make(getWindow().getDecorView(), R.string.shizuku_importing, Snackbar.LENGTH_SHORT).show();
        File destDir = getWorldsDestDir();
        new Thread(() -> {
            String error;
            try {
                error = service.importWorld(worldName, destDir.getAbsolutePath());
            } catch (Exception e) {
                error = e.getMessage();
            }
            String finalError = error;
            runOnUiThread(() -> {
                if (finalError == null) {
                    Snackbar.make(getWindow().getDecorView(), R.string.shizuku_import_done, Snackbar.LENGTH_SHORT).show();
                    worldItemAdapter.loadWorldList();
                } else {
                    showShizukuError(finalError);
                }
            });
        }).start();
    }

    private void showShizukuError(String reason) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_import_shizuku)
                .setMessage(reason)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Entry point for the "Importar .mcworld del dispositivo" menu action: lets the
     *  user pick any .mcworld file anywhere on the device (not just Minecraft's own
     *  folder) via the system file picker, then shows a preview before importing. */
    private void onClickImportMcworldFromDevice() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_MCWORLD);
        } catch (Exception e) {
            Snackbar.make(getWindow().getDecorView(), R.string.error_opening_world, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void onMcworldFilePicked(android.net.Uri uri) {
        Snackbar.make(getWindow().getDecorView(), R.string.mcworld_reading, Snackbar.LENGTH_SHORT).show();

        androidx.documentfile.provider.DocumentFile doc =
                androidx.documentfile.provider.DocumentFile.fromSingleUri(this, uri);
        String fileName = doc != null && doc.getName() != null ? doc.getName() : "mundo.mcworld";
        long fileSize = doc != null ? doc.length() : -1;
        long lastModified = doc != null ? doc.lastModified() : -1;

        File tempFolder = new File(getCacheDir(), "mcworld_preview_" + System.currentTimeMillis());

        new Thread(() -> {
            String errorMsg = null;
            World previewWorld = null;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("No se pudo abrir el archivo.");
                McWorldZipper.unzipToFolder(in, tempFolder);
                previewWorld = new World(tempFolder, null, WorldItemListActivity.this);
            } catch (Exception e) {
                errorMsg = e.getMessage();
            }

            String finalErrorMsg = errorMsg;
            World finalPreviewWorld = previewWorld;
            runOnUiThread(() -> {
                if (finalErrorMsg != null || finalPreviewWorld == null) {
                    FileUtils.deleteQuietly(tempFolder);
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.action_import_mcworld_file)
                            .setMessage(String.format(getString(R.string.mcworld_read_error), finalErrorMsg))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }

                String worldName = finalPreviewWorld.getWorldDisplayName();
                String sizeText = IoUtil.getFileSizeInText(fileSize >= 0 ? fileSize : FileUtils.sizeOf(tempFolder));
                String version = "?";
                try {
                    Bundle mapVersion = finalPreviewWorld.getMapVersionData();
                    if (mapVersion != null) {
                        String v = mapVersion.getString(World.KEY_LAST_VERSION_SHORT);
                        if (v != null && v.endsWith(".")) v = v.substring(0, v.length() - 1);
                        if (v != null && !v.isEmpty()) version = v;
                    }
                } catch (Exception ignored) {
                }
                String lastModText = lastModified > 0
                        ? android.text.format.DateFormat.getDateFormat(this).format(new java.util.Date(lastModified))
                        : "?";

                String body = String.format(getString(R.string.mcworld_preview_body),
                        worldName, fileName, sizeText, version, lastModText);

                new AlertDialog.Builder(this)
                        .setTitle(R.string.mcworld_preview_title)
                        .setMessage(body)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> FileUtils.deleteQuietly(tempFolder))
                        .setOnCancelListener(d -> FileUtils.deleteQuietly(tempFolder))
                        .setPositiveButton(R.string.mcworld_import_action, (d, w) ->
                                finishMcworldImport(tempFolder, fileName))
                        .show();
            });
        }).start();
    }

    /** Moves the already-extracted temp folder into our normal worlds folder. */
    private void finishMcworldImport(File tempFolder, String fileName) {
        String baseName = fileName.toLowerCase().endsWith(".mcworld")
                ? fileName.substring(0, fileName.length() - ".mcworld".length())
                : fileName;
        baseName = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (baseName.isEmpty()) baseName = "imported_world";

        File destRoot = getWorldsDestDir();
        File dest = new File(destRoot, baseName);
        int suffix = 1;
        while (dest.exists()) {
            dest = new File(destRoot, baseName + "_" + suffix);
            suffix++;
        }
        File finalDest = dest;

        new Thread(() -> {
            boolean ok = tempFolder.renameTo(finalDest);
            if (!ok) {
                try {
                    FileUtils.copyDirectory(tempFolder, finalDest);
                    FileUtils.deleteQuietly(tempFolder);
                    ok = true;
                } catch (IOException e) {
                    Log.d(this, e);
                }
            }
            boolean finalOk = ok;
            runOnUiThread(() -> {
                if (finalOk) {
                    worldItemAdapter.loadWorldList();
                } else {
                    Snackbar.make(getWindow().getDecorView(), R.string.error_opening_world, Snackbar.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void onClickDeleteSelected() {
        int count = worldItemAdapter.getSelectedCount();
        if (count == 0) {
            Snackbar.make(getWindow().getDecorView(), R.string.delete_selected_none, Snackbar.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(String.format(getString(R.string.delete_selected_confirm_title), count))
                .setMessage(R.string.delete_selected_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    worldItemAdapter.deleteSelected();
                    Snackbar.make(getWindow().getDecorView(), R.string.delete_selected_done, Snackbar.LENGTH_SHORT).show();
                })
                .show();
    }

    /** Opens a folder (already validated to contain level.dat) as a world. */
    private void openWorldAtFolder(File worldFolder) {
        try {
            World world = new World(worldFolder, null, WorldItemListActivity.this);

            if (mTwoPane) {
                Bundle arguments = new Bundle();
                arguments.putSerializable(World.ARG_WORLD_SERIALIZED, world);
                WorldItemDetailFragment fragment = new WorldItemDetailFragment();
                fragment.setArguments(arguments);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.worlditem_detail_container, fragment)
                        .commit();
            } else {
                Intent intent = new Intent(WorldItemListActivity.this, WorldItemDetailActivity.class);
                intent.putExtra(World.ARG_WORLD_SERIALIZED, world);
                startActivity(intent);
            }
        } catch (Exception e) {
            Snackbar.make(getWindow().getDecorView(), R.string.error_opening_world, Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show();
        }
    }

    /** Entry point for the "Explorar (Shizuku)" button next to the custom-path dialog. */
    private void onClickBrowseWithShizuku() {
        ShizukuBridge.ensureReady(this, new ShizukuBridge.Callback() {
            @Override
            public void onReady(IMinecraftDataService service) {
                new Thread(() -> {
                    String startPath;
                    try {
                        startPath = service.getDefaultMinecraftWorldsPath();
                    } catch (Exception e) {
                        startPath = null;
                    }
                    if (startPath == null) {
                        startPath = getWorldsDestDir().getAbsolutePath();
                    }
                    String finalStartPath = startPath;
                    runOnUiThread(() -> showShizukuFolderBrowser(service, finalStartPath));
                }).start();
            }

            @Override
            public void onUnavailable(String reason) {
                showShizukuError(reason);
            }
        });
    }

    /** Shows a folder listing (via the privileged Shizuku service) for the given path,
     *  letting the user drill into subfolders, go up, or pick the current folder as a world. */
    private void showShizukuFolderBrowser(IMinecraftDataService service, String path) {
        new Thread(() -> {
            String[] children;
            try {
                children = service.listDir(path);
            } catch (Exception e) {
                children = new String[0];
            }
            String[] finalChildren = children == null ? new String[0] : children;
            runOnUiThread(() -> {
                String[] items = new String[finalChildren.length + 1];
                items[0] = "⬆  .. (subir un nivel)";
                System.arraycopy(finalChildren, 0, items, 1, finalChildren.length);

                new AlertDialog.Builder(this)
                        .setTitle(path)
                        .setItems(items, (dialog, which) -> {
                            if (which == 0) {
                                File parent = new File(path).getParentFile();
                                if (parent != null) showShizukuFolderBrowser(service, parent.getAbsolutePath());
                            } else {
                                String childName = finalChildren[which - 1];
                                showShizukuFolderBrowser(service, new File(path, childName).getAbsolutePath());
                            }
                        })
                        .setPositiveButton(R.string.shizuku_pick_world_title, (dialog, which) ->
                                importFolderViaShizukuThenOpen(service, path))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }).start();
    }

    /** Imports the chosen folder (a world living under Minecraft's protected data) into
     *  our normal minecraftWorlds folder via Shizuku, then opens it. */
    /** Shows a preview (name/size/version/last-modified) of the browsed folder before
     *  copying the whole thing in, by first copying out just level.dat + levelname.txt. */
    private void importFolderViaShizukuThenOpen(IMinecraftDataService service, String path) {
        File pathFile = new File(path);
        String worldName = pathFile.getName();
        File previewFolder = new File(getCacheDir(), "shizuku_preview_" + System.currentTimeMillis());

        new Thread(() -> {
            String errorMsg = null;
            World previewWorld = null;
            long size = -1;
            long lastModified = -1;
            try {
                previewFolder.mkdirs();
                String err1 = service.copyPath(path + "/level.dat", previewFolder.getAbsolutePath() + "/level.dat");
                // levelname.txt may not exist on every world; ignore its individual error.
                service.copyPath(path + "/levelname.txt", previewFolder.getAbsolutePath() + "/levelname.txt");
                if (err1 != null) throw new IOException(err1);
                previewWorld = new World(previewFolder, null, WorldItemListActivity.this);
                size = service.getDirSize(path);
                lastModified = service.getLastModified(path);
            } catch (Exception e) {
                errorMsg = e.getMessage();
            }

            String finalErrorMsg = errorMsg;
            World finalPreviewWorld = previewWorld;
            long finalSize = size;
            long finalLastModified = lastModified;
            runOnUiThread(() -> {
                FileUtils.deleteQuietly(previewFolder);
                if (finalErrorMsg != null || finalPreviewWorld == null) {
                    showShizukuError(finalErrorMsg != null ? finalErrorMsg
                            : "No se encontró un mundo válido (level.dat) en esa carpeta.");
                    return;
                }

                String worldDisplayName = finalPreviewWorld.getWorldDisplayName();
                String sizeText = finalSize >= 0 ? IoUtil.getFileSizeInText(finalSize) : "?";
                String version = "?";
                try {
                    Bundle mapVersion = finalPreviewWorld.getMapVersionData();
                    if (mapVersion != null) {
                        String v = mapVersion.getString(World.KEY_LAST_VERSION_SHORT);
                        if (v != null && v.endsWith(".")) v = v.substring(0, v.length() - 1);
                        if (v != null && !v.isEmpty()) version = v;
                    }
                } catch (Exception ignored) {
                }
                String lastModText = finalLastModified > 0
                        ? android.text.format.DateFormat.getDateFormat(this).format(new java.util.Date(finalLastModified))
                        : "?";

                String body = String.format(getString(R.string.mcworld_preview_body),
                        worldDisplayName, worldName, sizeText, version, lastModText);

                new AlertDialog.Builder(this)
                        .setTitle(R.string.mcworld_preview_title)
                        .setMessage(body)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.mcworld_import_action, (d, w) ->
                                copyWholeWorldViaShizuku(service, path, worldName))
                        .show();
            });
        }).start();
    }

    private void copyWholeWorldViaShizuku(IMinecraftDataService service, String srcPath, String worldName) {
        File destDir = getWorldsDestDir();
        File dest = new File(destDir, worldName);
        new Thread(() -> {
            String error;
            try {
                error = service.copyPath(srcPath, dest.getAbsolutePath());
            } catch (Exception e) {
                error = e.getMessage();
            }
            String finalError = error;
            runOnUiThread(() -> {
                if (finalError != null) {
                    showShizukuError(finalError);
                    return;
                }
                if (new File(dest, "level.dat").exists()) {
                    openWorldAtFolder(dest);
                } else {
                    worldItemAdapter.loadWorldList();
                }
            });
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_CREATE_WORLD:
                    worldItemAdapter.loadWorldList();
                    return;
                case REQUEST_CODE_PICK_MCWORLD:
                    if (data != null && data.getData() != null) {
                        onMcworldFilePicked(data.getData());
                    }
                    return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private boolean checkPermissions(@NonNull int[] grantResults) {
        for (int result : grantResults)
            if (result != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length == PERMISSIONS_STORAGE.length && checkPermissions(grantResults)) {

                    // permission was granted, yay!
                    this.worldItemAdapter.enable();

                } else {

                    // permission denied, boo! Disable the
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    TextView msg = new TextView(this);
                    float dpi = this.getResources().getDisplayMetrics().density;
                    msg.setPadding((int) (19 * dpi), (int) (5 * dpi), (int) (14 * dpi), (int) (5 * dpi));
                    msg.setMaxLines(20);
                    msg.setMovementMethod(LinkMovementMethod.getInstance());
                    msg.setText(R.string.no_sdcard_access);
                    builder.setView(msg)
                            .setTitle(R.string.action_help)
                            .setCancelable(true)
                            .setNeutralButton(android.R.string.ok, null)
                            .show();
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.world, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        Bundle params = new Bundle();
        int type;
        switch (item.getItemId()) {
            case R.id.action_open:
                type = Log.ANA_PARAM_MAINACT_MENU_TYPE_OPEN;
                break;
            case R.id.action_help:
                type = Log.ANA_PARAM_MAINACT_MENU_TYPE_HELP;
                break;
            case R.id.action_about:
                type = Log.ANA_PARAM_MAINACT_MENU_TYPE_ABOUT;
                break;
            default:
                type = 0;
        }
        params.putInt(Log.ANA_PARAM_MAINACT_MENU_TYPE, type);
        Log.logFirebaseEvent(this, Log.CustomFirebaseEvent.MAINACT_MENU_OPEN, params);

        //some text pop-up dialogs, some with simple HTML tags.
        switch (item.getItemId()) {
            case R.id.action_settings: {
                startActivity(new Intent(this, com.mithrilmania.blocktopograph.settings.SettingsActivity.class));
                return true;
            }
            case R.id.action_delete_selected: {
                onClickDeleteSelected();
                return true;
            }
            case R.id.action_open: {
                if (worldItemAdapter.isDisabled()) {
                    Snackbar.make(getWindow().getDecorView(), R.string.no_read_write_access, Snackbar.LENGTH_SHORT).show();
                    return true;
                }
                new AlertDialog.Builder(WorldItemListActivity.this)
                        .setTitle(R.string.action_import_world)
                        .setItems(new CharSequence[]{
                                getString(R.string.action_import_shizuku),
                                getString(R.string.action_import_mcworld_file)
                        }, (dialog, which) -> {
                            if (which == 0) onClickImportFromMinecraftShizuku();
                            else onClickImportMcworldFromDevice();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            }
            case R.id.action_about: {

                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                TextView msg = new TextView(this);
                msg.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                float dpi = getResources().getDisplayMetrics().density;
                msg.setPadding((int) (19 * dpi), (int) (5 * dpi), (int) (14 * dpi), (int) (5 * dpi));
                msg.setMaxLines(20);
                msg.setMovementMethod(LinkMovementMethod.getInstance());
                msg.setText(R.string.app_about);
                builder.setView(msg)
                        .setTitle(R.string.action_about)
                        .setCancelable(true)
                        .setNeutralButton(android.R.string.ok, null)
                        .show();

                return true;
            }
            case R.id.action_help: {
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                TextView msg = new TextView(this);
                float dpi = getResources().getDisplayMetrics().density;
                msg.setPadding((int) (19 * dpi), (int) (5 * dpi), (int) (14 * dpi), (int) (5 * dpi));
                msg.setMaxLines(20);
                msg.setMovementMethod(LinkMovementMethod.getInstance());
                msg.setText(R.string.app_help);
                builder.setView(msg)
                        .setTitle(R.string.action_help)
                        .setCancelable(true)
                        .setNeutralButton(android.R.string.ok, null)
                        .show();

                return true;
            }
//            case R.id.action_changelog: {
//                AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
//                TextView msg = new TextView(ctx);
//                float dpi = ctx.getResources().getDisplayMetrics().density;
//                msg.setPadding((int) (19 * dpi), (int) (5 * dpi), (int) (14 * dpi), (int) (5 * dpi));
//                msg.setMaxLines(20);
//                msg.setMovementMethod(LinkMovementMethod.getInstance());
//                String content = String.format(ctx.getResources().getString(R.string.app_changelog), BuildConfig.VERSION_NAME);
//                //noinspection deprecation
//                msg.setText(Html.fromHtml(content));
//                builder.setView(msg)
//                        .setTitle(R.string.action_changelog)
//                        .setCancelable(true)
//                        .setNeutralButton(android.R.string.ok, null)
//                        .show();
//
//                return true;
//            }
            default: {
                return false;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        worldItemAdapter.loadWorldList();
    }

    public class WorldItemRecyclerViewAdapter extends RecyclerView.Adapter<WorldItemRecyclerViewAdapter.ViewHolder> {

        private final List<World> mWorlds;
        private final java.util.Set<Integer> mSelectedPositions = new java.util.HashSet<>();
        private final java.util.Map<String, String> mSizeCache = new java.util.HashMap<>();
        private boolean mSelectionMode = false;
        private long mLoadRequestId = 0;

        private boolean disabled;

        WorldItemRecyclerViewAdapter() {
            mWorlds = new ArrayList<>(16);
            disabled = true;
        }

        void enable() {
            disabled = false;
        }

        public boolean isDisabled() {
            return disabled;
        }

        //returns true if it has loaded a new list of worlds, false otherwise
        void loadWorldList() {
            if (disabled) return;
            mSelectedPositions.clear();
            mSelectionMode = false;
            long requestId = ++mLoadRequestId;

            // Scanning folders and parsing each world's level.dat is disk I/O that gets
            // slow with many/large worlds, so do it off the main thread to avoid freezing
            // the UI. Only the final list swap + notifyDataSetChanged happens on the UI thread.
            new Thread(() -> {
                List<World> loaded = new ArrayList<>(16);
                List<File> saveFolders = new ArrayList<>(4);
                List<String> marks = new ArrayList<>(4);

                File sd = Environment.getExternalStorageDirectory();

                saveFolders.add(getWorldsDestDir());
                marks.add(null);

                //noinspection ResultOfMethodCallIgnored
                new File(sd, "Android/data").listFiles(
                        file -> {
                            if (file.getName().startsWith("com.netease")) {
                                File worldsFolder = new File(file, "files/minecraftWorlds");
                                if (worldsFolder.exists()) {
                                    saveFolders.add(worldsFolder);
                                    marks.add(getString(R.string.world_mark_neteas));
                                }
                            }
                            return false;
                        }
                );

                for (int i = 0, saveFoldersSize = saveFolders.size(); i < saveFoldersSize; i++) {
                    File dir = saveFolders.get(i);
                    File[] files = dir.listFiles(file -> {
                        if (!file.isDirectory()) return false;
                        return (new File(file, "level.dat").exists()
                                || new File(file, WorldBackups.BTG_BACKUPS).exists());
                    });
                    if (files != null) for (File f : files) {
                        try {
                            loaded.add(new World(f, marks.get(i), WorldItemListActivity.this));
                        } catch (World.WorldLoadException e) {
                            Log.d(this, e);
                        }
                    }
                }

                Collections.sort(loaded, new Comparator<World>() {
                    @Override
                    public int compare(World a, World b) {
                        try {
                            long tA = WorldListUtil.getLastPlayedTimestamp(a);
                            long tB = WorldListUtil.getLastPlayedTimestamp(b);
                            return Long.compare(tB, tA);
                        } catch (Exception e) {
                            Log.d(this, e);
                            return 0;
                        }
                    }
                });

                runOnUiThread(() -> {
                    // A newer load started while we were scanning; drop this stale result.
                    if (requestId != mLoadRequestId) return;

                    mWorlds.clear();
                    mWorlds.addAll(loaded);
                    notifyDataSetChanged();

                    if (mWorlds.size() == 0) {
                        AlertDialog dia = new AlertDialog.Builder(WorldItemListActivity.this)
                                .setTitle(R.string.err_noworld_1)
                                .setView(R.layout.dialog_noworlds)
                                .setPositiveButton(android.R.string.ok, null)
                                .create();
                        dia.show();
                    }
                });
            }).start();
        }


        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.worlditem_list_content, parent, false);
            return new ViewHolder(view);
        }


        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
            holder.mWorld = mWorlds.get(position);
            holder.mWorldNameView.setText(holder.mWorld.getWorldDisplayName());
            bindWorldSize(holder, holder.mWorld);
            holder.mWorldGamemode.setText(WorldListUtil.getWorldGamemodeText(WorldItemListActivity.this, holder.mWorld));
            holder.mWorldLastPlayed.setText(WorldListUtil.getLastPlayedText(WorldItemListActivity.this, holder.mWorld));
            holder.mWorldPath.setText(holder.mWorld.worldFolder.getName());
            holder.mWorldMark.setText(holder.mWorld.mark);

            boolean selected = mSelectedPositions.contains(position);
            holder.mView.setAlpha(selected ? 0.5f : 1f);
            holder.mView.setBackgroundColor(selected ? 0x330000FF : 0x00000000);

            holder.mView.setOnClickListener(v -> {
                if (mSelectionMode) {
                    toggleSelection(holder.getAdapterPosition());
                    return;
                }
                if (mTwoPane) {
                    Bundle arguments = new Bundle();
                    arguments.putSerializable(World.ARG_WORLD_SERIALIZED, holder.mWorld);
                    WorldItemDetailFragment fragment = new WorldItemDetailFragment();
                    fragment.setArguments(arguments);
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.worlditem_detail_container, fragment)
                            .commit();
                } else {
                    Context context = v.getContext();
                    Intent intent = new Intent(context, WorldItemDetailActivity.class);
                    intent.putExtra(World.ARG_WORLD_SERIALIZED, holder.mWorld);

                    context.startActivity(intent);
                }
            });

            holder.mView.setOnLongClickListener(v -> {
                mSelectionMode = true;
                toggleSelection(holder.getAdapterPosition());
                return true;
            });
        }

        /** Shows the cached folder size instantly if we have it; otherwise shows a
         *  placeholder and computes it once on a background thread (this recursive
         *  disk-size scan is what used to freeze the list with many/large worlds). */
        private void bindWorldSize(ViewHolder holder, World world) {
            String path = world.worldFolder.getAbsolutePath();
            String cached = mSizeCache.get(path);
            if (cached != null) {
                holder.mWorldSize.setText(cached);
                return;
            }
            holder.mWorldSize.setText("…");
            new Thread(() -> {
                String size = IoUtil.getFileSizeInText(FileUtils.sizeOf(world.worldFolder));
                mSizeCache.put(path, size);
                runOnUiThread(() -> {
                    if (holder.mWorld == world) holder.mWorldSize.setText(size);
                });
            }).start();
        }

        private void toggleSelection(int position) {
            if (position == RecyclerView.NO_POSITION) return;
            if (!mSelectedPositions.remove(position)) {
                mSelectedPositions.add(position);
            }
            if (mSelectedPositions.isEmpty()) mSelectionMode = false;
            notifyItemChanged(position);
        }

        boolean isSelectionMode() {
            return mSelectionMode;
        }

        int getSelectedCount() {
            return mSelectedPositions.size();
        }

        void clearSelection() {
            mSelectionMode = false;
            mSelectedPositions.clear();
            notifyDataSetChanged();
        }

        /** Deletes the currently selected world folders from disk and reloads the list.
         *  Runs off the main thread since deleting a large world's files can be slow. */
        void deleteSelected() {
            List<World> toDelete = new ArrayList<>();
            for (int position : mSelectedPositions) {
                if (position < 0 || position >= mWorlds.size()) continue;
                toDelete.add(mWorlds.get(position));
            }
            mSelectedPositions.clear();
            mSelectionMode = false;
            new Thread(() -> {
                for (World w : toDelete) {
                    try {
                        FileUtils.deleteDirectory(w.worldFolder);
                    } catch (Exception e) {
                        Log.d(this, e);
                    }
                }
                runOnUiThread(this::loadWorldList);
            }).start();
        }

        @Override
        public int getItemCount() {
            return mWorlds.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final View mView;
            final TextView mWorldNameView;
            final TextView mWorldMark;
            final TextView mWorldSize;
            final TextView mWorldGamemode;
            final TextView mWorldLastPlayed;
            final TextView mWorldPath;
            World mWorld;

            ViewHolder(View view) {
                super(view);
                mView = view;
                mWorldNameView = view.findViewById(R.id.world_name);
                mWorldMark = view.findViewById(R.id.world_mark);
                mWorldSize = view.findViewById(R.id.world_size);
                mWorldGamemode = view.findViewById(R.id.world_gamemode);
                mWorldLastPlayed = view.findViewById(R.id.world_last_played);
                mWorldPath = view.findViewById(R.id.world_path);
            }

            @Override
            public String toString() {
                return super.toString() + " '" + mWorldNameView.getText() + "'";
            }
        }
    }
}

