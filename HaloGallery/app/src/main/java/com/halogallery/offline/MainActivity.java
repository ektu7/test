package com.halogallery.offline;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_MEDIA = 200;
    private static final String[] TABS = {"Pictures", "Albums", "Videos", "Favorites"};
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private List<MediaItem> all = new ArrayList<>();
    private ThumbnailLoader thumbnails;
    private LinearLayout root, content, tabStrip, header;
    private TextView sectionTitle;
    private EditText search;
    private int tab = 0;
    private String albumId = null;
    private String albumLabel = null;
    private String filter = "";
    private int generation = 0;
    private boolean loading;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        thumbnails = new ThumbnailLoader(this);
        buildShell();
        if (!hasAccess()) showPermissionScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        if (hasAccess()) refreshLibrary();
        else showPermissionScreen();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        generation++;
        worker.shutdownNow();
        thumbnails.shutdown();
    }

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 33) {
            boolean full = checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
            if (Build.VERSION.SDK_INT >= 34) {
                return full || checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                        == PackageManager.PERMISSION_GRANTED;
            }
            return full;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLibraryAccess() {
        if (Build.VERSION.SDK_INT >= 34) {
            requestPermissions(new String[]{ Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }, REQUEST_MEDIA);
        } else if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{ Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO }, REQUEST_MEDIA);
        } else {
            requestPermissions(new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE }, REQUEST_MEDIA);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQUEST_MEDIA) {
            if (hasAccess()) refreshLibrary();
            else showPermissionScreen();
        }
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        Ui.respectSystemBars(root);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(header, this, 20, 22, 20, 10);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout mainBar = new LinearLayout(this);
        mainBar.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(mainBar, new LinearLayout.LayoutParams(-1, Ui.dp(this, 54)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        mainBar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = Ui.text(this, "Gallery", 30, Ui.INK, true);
        title.setLetterSpacing(-.025f);
        heading.addView(title);
        TextView desc = Ui.text(this, "Offline & private", 12, Ui.MUTED, false);
        heading.addView(desc);

        TextView searchButton = Ui.action(this, "⌕", 27);
        LinearLayout.LayoutParams sb = new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42));
        sb.rightMargin = Ui.dp(this, 9);
        mainBar.addView(searchButton, sb);
        searchButton.setContentDescription("Search gallery");
        searchButton.setOnClickListener(v -> toggleSearch());
        TextView aboutButton = Ui.action(this, "⋮", 24);
        mainBar.addView(aboutButton, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        aboutButton.setContentDescription("About and privacy");
        aboutButton.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Your private gallery")
            .setMessage("Halo Gallery works offline. It has no internet permission, no ads, no tracking, "
                + "and cannot delete or edit your original photos or videos. Favorites are stored "
                + "only on this phone. You control which photos it may access through Android. "
                + "Some Samsung-only features such as AI editing and cloud sync are not included.")
            .setNegativeButton("Close", null)
            .setPositiveButton("Change access", (dialog, which) -> requestLibraryAccess()).show());

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setHint("Search names or albums");
        search.setHintTextColor(Ui.MUTED);
        search.setBackground(Ui.bg(Color.WHITE, 18, this));
        Ui.pad(search, this, 16, 4, 14, 4);
        LinearLayout.LayoutParams sep = new LinearLayout.LayoutParams(-1, Ui.dp(this, 46));
        sep.topMargin = Ui.dp(this, 10);
        header.addView(search, sep);
        search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter = s.toString().trim().toLowerCase(Locale.ROOT);
                renderContent();
            }
            @Override public void afterTextChanged(Editable e) {}
        });

        tabStrip = new LinearLayout(this);
        tabStrip.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 56));
        tabsParams.topMargin = Ui.dp(this, 10);
        header.addView(tabStrip, tabsParams);
        rebuildTabs();

        LinearLayout section = new LinearLayout(this);
        section.setGravity(Gravity.CENTER_VERTICAL);
        Ui.pad(section, this, 20, 10, 20, 10);
        root.addView(section, new LinearLayout.LayoutParams(-1, Ui.dp(this, 58)));
        sectionTitle = Ui.text(this, "Recent", 19, Ui.INK, true);
        section.addView(sectionTitle, new LinearLayout.LayoutParams(0, -1, 1));
        TextView reset = Ui.text(this, "All photos", 13, Ui.ACCENT, true);
        reset.setOnClickListener(v -> { albumId = null; albumLabel = null; tab = 0; rebuildTabs(); renderContent(); });
        section.addView(reset);
        reset.setTag("reset");

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void toggleSearch() {
        if (search.getVisibility() == View.VISIBLE) {
            search.setText("");
            search.setVisibility(View.GONE);
            ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(search.getWindowToken(), 0);
        } else {
            search.setVisibility(View.VISIBLE);
            search.requestFocus();
            ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
                .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void rebuildTabs() {
        tabStrip.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int selected = i;
            boolean active = tab == i;
            TextView t = Ui.text(this, TABS[i], 13, active ? Color.WHITE : Ui.MUTED, active);
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.bg(active ? Ui.ACCENT : Color.TRANSPARENT, 18, this));
            t.setSingleLine(true);
            t.setOnClickListener(v -> {
                tab = selected;
                albumId = null;
                albumLabel = null;
                rebuildTabs();
                renderContent();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(this, 38), 1);
            if (i != TABS.length - 1) p.rightMargin = Ui.dp(this, 5);
            tabStrip.addView(t, p);
        }
    }

    private void refreshLibrary() {
        int current = ++generation;
        loading = true;
        showStatus("Loading your library…", false);
        worker.submit(() -> {
            List<MediaItem> result = MediaRepository.load(getApplicationContext());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || current != generation) return;
                loading = false;
                all = result;
                renderContent();
            });
        });
    }

    private void showPermissionScreen() {
        sectionTitle.setText("Welcome");
        content.removeAllViews();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        Ui.pad(panel, this, 30, 30, 30, 30);
        content.addView(panel, new LinearLayout.LayoutParams(-1, -1));
        TextView icon = Ui.text(this, "▧", 70, Ui.ACCENT, true);
        icon.setGravity(Gravity.CENTER);
        panel.addView(icon);
        TextView h = Ui.text(this, "Your moments, privately", 23, Ui.INK, true);
        h.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1,-2);
        hp.topMargin = Ui.dp(this, 20);
        panel.addView(h, hp);
        TextView p = Ui.text(this,
            "Choose which photos and videos Halo may see. Nothing is uploaded or modified.",
            15, Ui.MUTED, false);
        p.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1,-2);
        pp.topMargin = Ui.dp(this, 12);
        panel.addView(p, pp);
        TextView button = Ui.text(this, "Choose photos & videos", 16, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.bg(Ui.ACCENT, 18, this));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 55));
        bp.topMargin = Ui.dp(this, 28);
        panel.addView(button, bp);
        button.setOnClickListener(v -> requestLibraryAccess());
    }

    private void showStatus(String message, boolean withAccessButton) {
        content.removeAllViews();
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        Ui.pad(center, this, 24, 16, 24, 16);
        content.addView(center, new LinearLayout.LayoutParams(-1, -1));
        TextView t = Ui.text(this, message, 16, Ui.MUTED, false);
        t.setGravity(Gravity.CENTER);
        center.addView(t);
        if (withAccessButton) {
            TextView change = Ui.text(this, "Choose more photos", 14, Ui.ACCENT, true);
            change.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Ui.dp(this, 54));
            p.topMargin = Ui.dp(this, 12);
            center.addView(change, p);
            change.setOnClickListener(v -> requestLibraryAccess());
        }
    }

    private List<MediaItem> visible() {
        List<MediaItem> selected = new ArrayList<>();
        for (MediaItem item : all) {
            if (tab == 2 && !item.isVideo) continue;
            if (tab == 3 && !Favorites.contains(this, item.key())) continue;
            if (albumId != null && !albumId.equals(item.bucketId)) continue;
            if (!filter.isEmpty() && !item.title.toLowerCase(Locale.ROOT).contains(filter)
                && !item.bucketName.toLowerCase(Locale.ROOT).contains(filter)) continue;
            selected.add(item);
        }
        return selected;
    }

    private void renderContent() {
        if (content == null || loading || !hasAccess()) return;
        if (tab == 1) {
            sectionTitle.setText("Albums");
            showAlbums();
            return;
        }
        List<MediaItem> items = visible();
        sectionTitle.setText(albumLabel != null ? albumLabel + "  ·  " + items.size()
            : tab == 2 ? "Videos  ·  " + items.size()
            : tab == 3 ? "Favorites  ·  " + items.size()
            : "Recent  ·  " + items.size());
        if (items.isEmpty()) {
            showStatus(all.isEmpty() ? "No photos found. You can change media access in Android."
                    : tab == 3 ? "Tap ♡ on a photo or video to save it here."
                    : "No matching photos or videos.", true);
            return;
        }
        content.removeAllViews();
        GridView grid = makeGrid(3);
        grid.setAdapter(new MediaAdapter(items, 3, false));
        grid.setOnItemClickListener((parent, view, position, id) -> openItem(items.get(position), items));
        content.addView(grid, new LinearLayout.LayoutParams(-1, -1));
    }

    private GridView makeGrid(int columns) {
        GridView grid = new GridView(this);
        grid.setNumColumns(columns);
        grid.setHorizontalSpacing(Ui.dp(this, 4));
        grid.setVerticalSpacing(Ui.dp(this, columns == 3 ? 4 : 14));
        Ui.pad(grid, this, 8, 4, 8, 20);
        grid.setClipToPadding(false);
        grid.setBackgroundColor(Ui.BG);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        return grid;
    }

    private void openItem(MediaItem item, List<MediaItem> visibleItems) {
        SelectionSession.set(visibleItems);
        Intent intent = new Intent(this, item.isVideo ? VideoPlayerActivity.class : ViewerActivity.class);
        intent.setData(item.uri);
        intent.putExtra("title", item.title);
        startActivity(intent);
    }

    private void showAlbums() {
        Map<String, Album> grouped = new LinkedHashMap<>();
        for (MediaItem item : all) {
            if (!filter.isEmpty() && !item.bucketName.toLowerCase(Locale.ROOT).contains(filter)
                    && !item.title.toLowerCase(Locale.ROOT).contains(filter)) continue;
            Album album = grouped.get(item.bucketId);
            if (album == null) {
                album = new Album(item.bucketId, item.bucketName, item);
                grouped.put(item.bucketId, album);
            }
            album.count++;
        }
        List<Album> albums = new ArrayList<>(grouped.values());
        sectionTitle.setText("Albums  ·  " + albums.size());
        if (albums.isEmpty()) {
            showStatus("No albums found.", true);
            return;
        }
        content.removeAllViews();
        GridView grid = makeGrid(2);
        grid.setAdapter(new AlbumAdapter(albums));
        grid.setOnItemClickListener((parent, view, position, id) -> {
            Album a = albums.get(position);
            albumId = a.id;
            albumLabel = a.name;
            tab = 0;
            rebuildTabs();
            renderContent();
        });
        content.addView(grid, new LinearLayout.LayoutParams(-1, -1));
    }

    private static final class Album {
        final String id, name;
        final MediaItem cover;
        int count = 0;
        Album(String id, String name, MediaItem cover) { this.id=id; this.name=name; this.cover=cover; }
    }

    private final class MediaAdapter extends BaseAdapter {
        private final List<MediaItem> items;
        private final int columns;
        private final boolean albums;
        MediaAdapter(List<MediaItem> items, int columns, boolean albums) {
            this.items = items; this.columns = columns; this.albums = albums;
        }
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View converted, ViewGroup parent) {
            MediaItem item = items.get(position);
            FrameLayout tile = converted instanceof FrameLayout ? (FrameLayout) converted : createTile();
            ImageView image = (ImageView) tile.getChildAt(0);
            TextView videoLabel = (TextView) tile.getChildAt(1);
            thumbnails.bind(image, item);
            videoLabel.setVisibility(item.isVideo ? View.VISIBLE : View.GONE);
            if (item.isVideo) videoLabel.setText("▶  " + MediaRepository.duration(item.durationMs));
            return tile;
        }
        private FrameLayout createTile() {
            FrameLayout tile = new FrameLayout(MainActivity.this);
            int width = getResources().getDisplayMetrics().widthPixels - Ui.dp(MainActivity.this, 24);
            int dimension = Math.max(Ui.dp(MainActivity.this, 90), (width - Ui.dp(MainActivity.this, 8)) / columns);
            tile.setLayoutParams(new AbsListView.LayoutParams(-1, dimension));
            tile.setBackground(Ui.bg(Ui.LINE, 8, MainActivity.this));
            tile.setClipToOutline(true);
            ImageView image = new ImageView(MainActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(image, new FrameLayout.LayoutParams(-1, -1));
            TextView videoLabel = Ui.text(MainActivity.this, "", 11, Color.WHITE, true);
            videoLabel.setGravity(Gravity.CENTER);
            Ui.pad(videoLabel, MainActivity.this, 7, 3, 7, 3);
            videoLabel.setBackground(Ui.bg(0x99000000, 6, MainActivity.this));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
            lp.setMargins(0,0,Ui.dp(MainActivity.this, 5),Ui.dp(MainActivity.this, 5));
            tile.addView(videoLabel, lp);
            return tile;
        }
    }

    private final class AlbumAdapter extends BaseAdapter {
        private final List<Album> albums;
        AlbumAdapter(List<Album> albums) { this.albums = albums; }
        @Override public int getCount() { return albums.size(); }
        @Override public Object getItem(int p) { return albums.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int position, View convert, ViewGroup parent) {
            LinearLayout tile;
            if (convert instanceof LinearLayout) tile = (LinearLayout) convert;
            else {
                tile = new LinearLayout(MainActivity.this);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setLayoutParams(new AbsListView.LayoutParams(-1, Ui.dp(MainActivity.this, 213)));
                ImageView cover = new ImageView(MainActivity.this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cover.setBackground(Ui.bg(Ui.LINE, 14, MainActivity.this));
                cover.setClipToOutline(true);
                tile.addView(cover, new LinearLayout.LayoutParams(-1, Ui.dp(MainActivity.this, 165)));
                TextView name = Ui.text(MainActivity.this, "", 15, Ui.INK, true);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams n = new LinearLayout.LayoutParams(-1, Ui.dp(MainActivity.this, 27));
                n.topMargin = Ui.dp(MainActivity.this, 4);
                tile.addView(name, n);
                TextView count = Ui.text(MainActivity.this, "", 12, Ui.MUTED, false);
                tile.addView(count, new LinearLayout.LayoutParams(-1, -2));
            }
            Album album = albums.get(position);
            thumbnails.bind((ImageView) tile.getChildAt(0), album.cover);
            ((TextView) tile.getChildAt(1)).setText(album.name);
            ((TextView) tile.getChildAt(2)).setText(album.count + " items");
            return tile;
        }
    }
}
