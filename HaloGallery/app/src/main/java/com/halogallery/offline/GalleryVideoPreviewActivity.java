package com.halogallery.offline;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.MediaController;
import android.widget.PopupMenu;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Gallery-side preview like Samsung Gallery; full playback is in Halo Video. */
public final class GalleryVideoPreviewActivity extends Activity {
    private final List<MediaItem> videos = new ArrayList<>();
    private int index;
    private Uri uri;
    private String fileTitle;
    private VideoView preview;
    private TextView title, favorite, counter;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        uri = getIntent().getData();
        if (uri == null) { finish(); return; }
        videos.addAll(SelectionSession.videos);
        index = -1;
        for (int i=0;i<videos.size();i++) if (uri.equals(videos.get(i).uri)) { index=i; break; }
        if (index < 0) { videos.clear(); videos.add(new MediaItem(uri, getIntent().getStringExtra("title"), "", "", true, System.currentTimeMillis(), 0)); index=0; }
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi(); showCurrent();
    }
    private TextView action(String text, int size) {
        TextView t=Ui.text(this,text,size,Color.WHITE,true);
        t.setGravity(Gravity.CENTER); t.setClickable(true); return t;
    }
    private void buildUi() {
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF090A10);
        setContentView(root);Ui.respectSystemBars(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        Ui.pad(top,this,12,5,12,5);root.addView(top,new LinearLayout.LayoutParams(-1,Ui.dp(this,60)));
        TextView back=action("‹",34);top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),-1));back.setOnClickListener(v->finish());
        title=Ui.text(this,"",16,Color.WHITE,true);title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView menu=action("⋮",26);top.addView(menu,new LinearLayout.LayoutParams(Ui.dp(this,44),-1));
        menu.setOnClickListener(v->{PopupMenu pop=new PopupMenu(this,menu);
            pop.getMenu().add("Open in Halo Video Player");pop.getMenu().add("Open with another app");pop.getMenu().add("Share video");
            pop.setOnMenuItemClickListener(i->{String label=i.getTitle().toString();
                if(label.startsWith("Open in Halo"))VideoBridge.open(this,uri,fileTitle);
                else if(label.startsWith("Open with")){Intent it=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                  startActivity(Intent.createChooser(it,"Open in video player"));}
                else share(); return true;});pop.show();});
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(Color.BLACK);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        preview=new VideoView(this);stage.addView(preview,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        MediaController controller=new MediaController(this);preview.setMediaController(controller);
        TextView cta=action("▶   Open in Video Player",17);
        cta.setBackground(Ui.bg(0xFF414EE7,18,this));Ui.pad(cta,this,18,10,18,10);
        LinearLayout.LayoutParams c=new LinearLayout.LayoutParams(-1,Ui.dp(this,60));c.setMargins(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,8));root.addView(cta,c);
        cta.setOnClickListener(v->VideoBridge.open(this,uri,fileTitle));
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);root.addView(nav,new LinearLayout.LayoutParams(-1,Ui.dp(this,64)));
        TextView left=action("❮",27);nav.addView(left,new LinearLayout.LayoutParams(0,-1,1));left.setOnClickListener(v->move(-1));
        favorite=action("♡",29);nav.addView(favorite,new LinearLayout.LayoutParams(0,-1,1));favorite.setOnClickListener(v->{Favorites.toggle(this,uri.toString());syncFavorite();});
        counter=action("1 / 1",14);nav.addView(counter,new LinearLayout.LayoutParams(0,-1,1));
        TextView right=action("❯",27);nav.addView(right,new LinearLayout.LayoutParams(0,-1,1));right.setOnClickListener(v->move(1));
        TextView share=action("↗",27);nav.addView(share,new LinearLayout.LayoutParams(0,-1,1));share.setOnClickListener(v->share());
    }
    private void showCurrent() {
        MediaItem item=videos.get(index);uri=item.uri;fileTitle=item.title==null ? "Video" : item.title;
        title.setText(fileTitle);counter.setText((index+1)+" / "+videos.size());syncFavorite();
        preview.stopPlayback();preview.setVideoURI(uri);preview.seekTo(100);
    }
    private void syncFavorite(){favorite.setText(Favorites.contains(this,uri.toString())?"♥":"♡");}
    private void move(int step){int n=index+step;if(n<0||n>=videos.size())return;index=n;showCurrent();}
    private void share(){Intent s=new Intent(Intent.ACTION_SEND);s.setType("video/*");s.putExtra(Intent.EXTRA_STREAM,uri);
        s.setClipData(ClipData.newUri(getContentResolver(),"Video",uri));s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(s,"Share video"));}
    @Override protected void onStop(){preview.pause();super.onStop();}
    @Override protected void onDestroy(){preview.stopPlayback();super.onDestroy();}
}
