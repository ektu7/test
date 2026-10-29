package com.halogallery.offline;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

final class Favorites {
    private Favorites() {}
    private static final String PREF = "favorites_local_only";
    private static final String KEY = "media_uris";

    static boolean contains(Context c, String uri) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(KEY, java.util.Collections.emptySet()).contains(uri);
    }

    static boolean toggle(Context c, String uri) {
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        Set<String> favorite = new HashSet<>(p.getStringSet(KEY, java.util.Collections.emptySet()));
        boolean nowFavorite;
        if (favorite.contains(uri)) {
            favorite.remove(uri);
            nowFavorite = false;
        } else {
            favorite.add(uri);
            nowFavorite = true;
        }
        p.edit().putStringSet(KEY, favorite).apply();
        return nowFavorite;
    }
}
