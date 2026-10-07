package com.mithrilmania.blocktopograph.inventory;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.Nullable;

import com.mithrilmania.blocktopograph.map.Item;
import com.mithrilmania.blocktopograph.nbt.InventoryHolder;

import java.io.File;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

/**
 * Finds an icon Bitmap and a friendly display name for an inventory item.
 * <p>
 * Tries, in order:
 * 1. Custom addon textures bundled INSIDE the world's own folder, under
 *    resource_packs/&lt;pack&gt;/textures/items(or blocks)/&lt;identifier&gt;.png --
 *    covers non-vanilla items added by behavior/resource pack addons.
 * 2. The modern vanilla resource pack textures the user added under
 *    assets/items_modern/items/, resolved through item_texture.json's
 *    identifier-&gt;texture-file mapping (many vanilla items' file names don't
 *    match their identifier, e.g. "totem_of_undying" -&gt; "totem.png").
 * 3. Blocktopograph's old bundled 2016-era icon catalog ({@link Item}), keyed
 *    by legacy numeric id+damage -- covers old worlds/items only.
 * 4. Null (caller shows a generic placeholder + the raw id/name as text).
 */
public final class ItemIconResolver {

    private static final String MODERN_ITEMS_PATH = "items_modern/items/";
    private static final String ITEM_TEXTURE_JSON_PATH = "items_modern/item_texture.json";
    private static final Map<String, Bitmap> cache = new HashMap<>();
    private static final Map<String, Boolean> missCache = new HashMap<>();
    private static Map<String, String> textureDataMap; // identifier(no namespace) -> file name (no ext)
    private static boolean textureDataLoadAttempted = false;

    private ItemIconResolver() {
    }

    @Nullable
    public static Bitmap resolveIcon(Context context, InventoryHolder.Item item) {
        return resolveIcon(context, item, null);
    }

    @Nullable
    public static Bitmap resolveIcon(Context context, InventoryHolder.Item item, @Nullable File worldFolder) {
        String cacheKey = cacheKeyFor(item, worldFolder);
        if (cacheKey == null) return null;
        if (cache.containsKey(cacheKey)) return cache.get(cacheKey);
        if (Boolean.TRUE.equals(missCache.get(cacheKey))) return null;

        Bitmap bmp = null;
        String name = item.getName();
        String stripped = name != null ? stripNamespace(name) : null;

        if (stripped != null && worldFolder != null) {
            bmp = tryLoadFromWorldResourcePacks(worldFolder, stripped);
        }
        if (bmp == null && stripped != null && context != null) {
            String mappedFile = lookupTextureFileName(context, stripped);
            if (mappedFile != null) {
                bmp = tryLoadAsset(context, MODERN_ITEMS_PATH + mappedFile + ".png");
            }
        }
        if (bmp == null && stripped != null) {
            bmp = tryLoadAsset(context, MODERN_ITEMS_PATH + stripped + ".png");
        }
        if (bmp == null) {
            Item legacy = legacyItemFor(item);
            if (legacy != null && legacy.texPath != null) {
                bmp = tryLoadAsset(context, legacy.texPath);
            }
        }

        if (bmp != null) {
            cache.put(cacheKey, bmp);
        } else {
            missCache.put(cacheKey, true);
        }
        return bmp;
    }

    public static String resolveDisplayName(InventoryHolder.Item item) {
        String name = item.getName();
        if (name != null && !name.isEmpty()) {
            return prettify(stripNamespace(name));
        }
        Item legacy = legacyItemFor(item);
        if (legacy != null) return prettify(legacy.str);
        Short id = item.getId();
        return id == null ? "?" : ("id:" + id);
    }

    @Nullable
    private static Item legacyItemFor(InventoryHolder.Item item) {
        Short id = item.getId();
        if (id == null) return null;
        Short dmg = item.getDamage();
        return Item.getItem(id, dmg == null ? 0 : dmg);
    }

    private static String stripNamespace(String identifier) {
        int idx = identifier.indexOf(':');
        return idx >= 0 ? identifier.substring(idx + 1) : identifier;
    }

    private static String prettify(String raw) {
        if (raw == null) return "?";
        String[] parts = raw.replace('_', ' ').trim().split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.length() == 0 ? raw : sb.toString();
    }

    @Nullable
    private static String lookupTextureFileName(Context context, String strippedIdentifier) {
        loadTextureDataIfNeeded(context);
        if (textureDataMap == null) return null;
        return textureDataMap.get(strippedIdentifier);
    }

    private static synchronized void loadTextureDataIfNeeded(Context context) {
        if (textureDataLoadAttempted) return;
        textureDataLoadAttempted = true;
        Map<String, String> map = new HashMap<>();
        try (InputStream in = context.getAssets().open(ITEM_TEXTURE_JSON_PATH)) {
            byte[] buf = new byte[in.available()];
            int off = 0, len;
            while (off < buf.length && (len = in.read(buf, off, buf.length - off)) > 0) off += len;
            JSONObject root = new JSONObject(new String(buf, "UTF-8"));
            JSONObject textureData = root.optJSONObject("texture_data");
            if (textureData != null) {
                java.util.Iterator<String> keys = textureData.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String shortKey = stripNamespace(key);
                    Object entry = textureData.opt(key);
                    String texturePath = null;
                    if (entry instanceof JSONObject) {
                        Object texturesVal = ((JSONObject) entry).opt("textures");
                        if (texturesVal instanceof String) {
                            texturePath = (String) texturesVal;
                        } else if (texturesVal instanceof org.json.JSONArray
                                && ((org.json.JSONArray) texturesVal).length() > 0) {
                            texturePath = ((org.json.JSONArray) texturesVal).optString(0);
                        }
                    }
                    if (texturePath != null) {
                        int slash = texturePath.lastIndexOf('/');
                        String fileName = slash >= 0 ? texturePath.substring(slash + 1) : texturePath;
                        map.put(shortKey, fileName);
                    }
                }
            }
        } catch (Exception e) {
            // item_texture.json not added by the user (yet), or malformed -- that's fine,
            // callers just fall back to guessing the filename from the identifier.
        }
        textureDataMap = map;
    }

    @Nullable
    private static Bitmap tryLoadAsset(Context context, String assetPath) {
        try (InputStream in = context.getAssets().open(assetPath)) {
            return BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            return null;
        }
    }

    /** Looks for the texture inside any resource pack bundled with this specific
     *  world (resource_packs/&lt;pack&gt;/textures/items or blocks/&lt;name&gt;.png) --
     *  this is where addon-added items' custom textures actually live. */
    @Nullable
    private static Bitmap tryLoadFromWorldResourcePacks(File worldFolder, String strippedName) {
        File packsRoot = new File(worldFolder, "resource_packs");
        File[] packs = packsRoot.listFiles(File::isDirectory);
        if (packs == null) return null;
        String[] subfolders = {"textures/items", "textures/blocks", "textures"};
        for (File pack : packs) {
            for (String sub : subfolders) {
                File candidate = new File(pack, sub + "/" + strippedName + ".png");
                if (candidate.isFile()) {
                    Bitmap bmp = BitmapFactory.decodeFile(candidate.getAbsolutePath());
                    if (bmp != null) return bmp;
                }
            }
        }
        return null;
    }

    @Nullable
    private static String cacheKeyFor(InventoryHolder.Item item, @Nullable File worldFolder) {
        String base = cacheKeyFor(item);
        if (base == null) return null;
        return worldFolder == null ? base : (worldFolder.getAbsolutePath() + "|" + base);
    }

    @Nullable
    private static String cacheKeyFor(InventoryHolder.Item item) {
        String name = item.getName();
        if (name != null) return "n:" + name;
        Short id = item.getId();
        if (id == null) return null;
        Short dmg = item.getDamage();
        return "i:" + id + "@" + (dmg == null ? 0 : dmg);
    }
}
