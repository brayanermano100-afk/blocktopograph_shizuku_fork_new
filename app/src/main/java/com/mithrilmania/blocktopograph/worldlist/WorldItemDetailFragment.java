package com.mithrilmania.blocktopograph.worldlist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.snackbar.Snackbar;
import com.mithrilmania.blocktopograph.BackupActivity;
import com.mithrilmania.blocktopograph.Log;
import com.mithrilmania.blocktopograph.R;
import com.mithrilmania.blocktopograph.World;
import com.mithrilmania.blocktopograph.WorldActivity;
import com.mithrilmania.blocktopograph.backup.WorldBackups;
import com.mithrilmania.blocktopograph.databinding.WorlditemDetailBinding;
import com.mithrilmania.blocktopograph.shizuku.IMinecraftDataService;
import com.mithrilmania.blocktopograph.shizuku.McWorldZipper;
import com.mithrilmania.blocktopograph.shizuku.ShizukuBridge;
import com.mithrilmania.blocktopograph.test.MainTestActivity;
import com.mithrilmania.blocktopograph.util.IoUtil;
import com.mithrilmania.blocktopograph.util.UiUtil;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Date;

/**
 * A fragment representing a single WorldItem detail screen.
 * This fragment is either contained in a {@link WorldItemListActivity}
 * in two-pane mode (on tablets) or a {@link WorldItemDetailActivity}
 * on handsets.
 */
public class WorldItemDetailFragment extends Fragment implements View.OnClickListener {

    /**
     * The dummy content this fragment is presenting.
     */
    private static final byte[] SEQ_TEST = {0, 1, 1, 0};

    private World mWorld;
    private byte[] mSequence;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public WorldItemDetailFragment() {
    }

//    private String getDate(long time) {
//        Calendar cal = Calendar.getInstance();
//        TimeZone tz = cal.getTimeZone();//get your local time zone.
//        SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.full_date_format), Locale.ENGLISH);
//        sdf.setTimeZone(tz);//set time zone.
//        return sdf.format(new Date(time * 1000));
//    }

    @SuppressLint("StaticFieldLeak")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        WorlditemDetailBinding binding = DataBindingUtil.inflate(inflater, R.layout.worlditem_detail, container, false);

        Activity activity = this.getActivity();
        assert activity != null;

        String barTitle;
        Bundle arguments = getArguments();
        if (arguments == null || !arguments.containsKey(World.ARG_WORLD_SERIALIZED)) {
            Snackbar.make(binding.worlditemDetail,
                    R.string.error_could_not_open_world_details_lost_track_world, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            barTitle = activity.getString(R.string.error_could_not_open_world);
        } else {
            mWorld = (World) arguments.getSerializable(World.ARG_WORLD_SERIALIZED);
            barTitle = mWorld == null ? activity.getString(R.string.error_could_not_open_world) : mWorld.getWorldDisplayName();
        }

        CollapsingToolbarLayout appBarLayout = activity.findViewById(R.id.toolbar_layout);
        if (appBarLayout != null) {
            appBarLayout.setTitle(barTitle);
        }

        try {
            if (mWorld != null && mWorld.getLevel() != null) {
                binding.setName(mWorld.getWorldDisplayName());
                binding.setSize(IoUtil.getFileSizeInText(FileUtils.sizeOf(mWorld.worldFolder)));
                binding.setMode(WorldListUtil.getWorldGamemodeText(activity, mWorld));
                binding.setTime(WorldListUtil.getLastPlayedText(activity, mWorld));
                binding.setSeed(String.valueOf(mWorld.getWorldSeed()));
                binding.setPath(mWorld.levelFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.d(this, e);
        }

        binding.fabOpenWorld.setOnClickListener(view -> {
            WorldBackups worldBackups = new WorldBackups(mWorld);
            worldBackups.loadConfig();
            if (worldBackups.autoBackup) {
                AlertDialog dia = UiUtil.buildProgressWaitDialog(view.getContext(),
                        R.string.auto_backup_caption, null);
                dia.show();
                new AsyncTask<WorldBackups, Void, Boolean>() {
                    @Override
                    protected Boolean doInBackground(WorldBackups... worldBackups) {
                        return worldBackups[0].createNewBackup(getString(R.string.auto_backup_name), new Date());
                    }

                    @Override
                    protected void onPostExecute(Boolean aBoolean) {
                        if (!aBoolean) {
                            Activity activity1 = getActivity();
                            if (activity1 != null)
                                UiUtil.snack(activity1, R.string.general_failed);
                        }
                        synchronized (dia) {
                            if (dia.isShowing()) dia.dismiss();
                        }
                        startWorldActivity();
                    }
                }.execute(worldBackups);
            } else startWorldActivity();
        });

        mSequence = new byte[]{-1, -1, -1, -1};
        binding.buttonLeft.setOnClickListener(this);
        binding.buttonRight.setOnClickListener(this);
        binding.buttonBackup.setOnClickListener(this);
        binding.buttonExportShizuku.setOnClickListener(this);
        binding.buttonPlugins.setOnClickListener(this);

        return binding.getRoot();
    }

    private void startWorldActivity() {

        Activity activity = getActivity();
        assert activity != null;
        activity.startActivity(
                new Intent(activity, WorldActivity.class)
                        .putExtra(World.ARG_WORLD_SERIALIZED, mWorld));
    }

    private void sequence(byte code) {
        int pos = mSequence.length - 1;
        System.arraycopy(mSequence, 1, mSequence, 0, pos);
        mSequence[pos] = code;
        if (Arrays.equals(mSequence, SEQ_TEST)) {
            startActivity(
                    new Intent(getActivity(), MainTestActivity.class)
                            .putExtra(World.ARG_WORLD_SERIALIZED, mWorld)
            );
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_left:
                sequence((byte) 0);
                break;
            case R.id.button_right:
                sequence((byte) 1);
                break;
            case R.id.button_backup:
                startActivity(new Intent(getActivity(), BackupActivity.class).putExtra(World.ARG_WORLD_SERIALIZED, mWorld));
                break;
            case R.id.button_export_shizuku:
                onClickExportToMinecraftShizuku();
                break;
            case R.id.button_plugins:
                onClickPlugins();
                break;
        }
    }

    private void onClickPlugins() {
        Activity activity = getActivity();
        if (activity == null || mWorld == null || mWorld.worldFolder == null) return;

        java.util.List<android.content.pm.ResolveInfo> plugins =
                com.mithrilmania.blocktopograph.plugin.PluginManager.findWorldActionPlugins(activity);

        if (plugins.isEmpty()) {
            UiUtil.snack(activity, R.string.plugins_none_for_world);
            return;
        }

        android.content.pm.PackageManager pm = activity.getPackageManager();
        CharSequence[] labels = new CharSequence[plugins.size()];
        for (int i = 0; i < plugins.size(); i++) labels[i] = plugins.get(i).loadLabel(pm);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.action_plugins)
                .setItems(labels, (dialog, which) -> {
                    android.content.Intent intent = com.mithrilmania.blocktopograph.plugin.PluginManager
                            .buildLaunchIntent(plugins.get(which),
                                    mWorld.worldFolder.getAbsolutePath(),
                                    mWorld.getWorldDisplayName());
                    try {
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        UiUtil.snack(activity, R.string.error_opening_world);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Copies this (locally-edited) world back into Minecraft's protected
     * Android/data/com.mojang.minecraftpe folder via Shizuku, so the game
     * picks up the changes.
     */
    private void onClickExportToMinecraftShizuku() {
        Activity activity = getActivity();
        if (activity == null || mWorld == null || mWorld.worldFolder == null) return;

        UiUtil.snack(activity, R.string.shizuku_exporting);
        new Thread(() -> {
            File mcworldFile = new File(activity.getExternalFilesDir("exported_worlds"),
                    mWorld.worldFolder.getName() + ".mcworld");
            String error = null;
            try {
                McWorldZipper.zipWorld(mWorld.worldFolder, mcworldFile);
            } catch (Exception e) {
                error = e.getMessage();
            }
            String finalError = error;
            Activity a = getActivity();
            if (a == null) return;
            a.runOnUiThread(() -> {
                if (finalError != null) {
                    new AlertDialog.Builder(a)
                            .setTitle(R.string.shizuku_export_action)
                            .setMessage(finalError)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }
                try {
                    android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                            a, a.getPackageName() + ".shizuku.fileprovider", mcworldFile);
                    Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                    viewIntent.setDataAndType(uri, "application/octet-stream");
                    viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    a.startActivity(viewIntent);
                } catch (Exception e) {
                    new AlertDialog.Builder(a)
                            .setTitle(R.string.shizuku_export_action)
                            .setMessage("No se encontró una app para abrir el .mcworld (" +
                                    "¿Minecraft está instalado?): " + e.getMessage())
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            });
        }).start();
    }
}

