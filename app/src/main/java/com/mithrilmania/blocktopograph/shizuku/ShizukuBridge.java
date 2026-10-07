package com.mithrilmania.blocktopograph.shizuku;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;

import rikka.shizuku.Shizuku;

/**
 * High-level helper used by the UI: checks whether Shizuku is installed and
 * granted, requests permission if needed, and binds the privileged
 * {@link MinecraftDataService} so we can list/import/export Minecraft worlds
 * that live in Android/data/com.mojang.minecraftpe.
 * <p>
 * Usage from an Activity:
 * <pre>
 *   ShizukuBridge.ensureReady(activity, new ShizukuBridge.Callback() {
 *       public void onReady(IMinecraftDataService service) { ... }
 *       public void onUnavailable(String reason) { ... }
 *   });
 * </pre>
 */
public final class ShizukuBridge {

    private static final int REQUEST_CODE = 8341;

    public interface Callback {
        void onReady(IMinecraftDataService service);

        void onUnavailable(String reason);
    }

    private static IMinecraftDataService activeService;
    private static Callback pendingCallback;
    private static Context pendingContext;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final Shizuku.OnRequestPermissionResultListener PERMISSION_LISTENER =
            (requestCode, grantResult) -> {
                if (requestCode != REQUEST_CODE) return;
                Callback cb = pendingCallback;
                Context ctx = pendingContext;
                pendingCallback = null;
                pendingContext = null;
                if (cb == null || ctx == null) return;
                MAIN.post(() -> {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        bindService(ctx, cb);
                    } else {
                        cb.onUnavailable("Permiso de Shizuku denegado.");
                    }
                });
            };

    static {
        Shizuku.addRequestPermissionResultListener(PERMISSION_LISTENER);
    }

    private ShizukuBridge() {
    }

    /**
     * Makes sure Shizuku is installed, running, permitted, and the helper
     * service is bound, then calls back on the calling (UI) thread.
     */
    public static void ensureReady(@NonNull Context context, @NonNull Callback callback) {
        if (!Shizuku.pingBinder()) {
            callback.onUnavailable("Shizuku no está corriendo. Ábrelo y actívalo primero " +
                    "(por ADB inalámbrico en Android 11+, o con la app Shizuku).");
            return;
        }

        if (Shizuku.isPreV11()) {
            // Versions before 11 use a different (unsupported here) permission model.
            callback.onUnavailable("Tu versión de Shizuku es demasiado antigua, actualízala.");
            return;
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bindService(context, callback);
            return;
        }

        if (Shizuku.shouldShowRequestPermissionRationale()) {
            callback.onUnavailable("Necesitas conceder el permiso de Shizuku para acceder a " +
                    "la carpeta de Minecraft.");
            return;
        }

        pendingContext = context.getApplicationContext();
        pendingCallback = callback;
        Shizuku.requestPermission(REQUEST_CODE);
    }

    private static void bindService(@NonNull Context context, @NonNull Callback callback) {
        if (activeService != null) {
            callback.onReady(activeService);
            return;
        }

        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(context.getPackageName(),
                        MinecraftDataService.class.getName()))
                .daemon(false)
                .processNameSuffix("mc_data")
                .debuggable(false)
                .version(4);

        Shizuku.bindUserService(args, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                MAIN.post(() -> {
                    if (binder == null || !binder.pingBinder()) {
                        callback.onUnavailable("No se pudo conectar con el servicio de Shizuku.");
                        return;
                    }
                    activeService = IMinecraftDataService.Stub.asInterface(binder);
                    callback.onReady(activeService);
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                activeService = null;
            }
        });
    }
}
