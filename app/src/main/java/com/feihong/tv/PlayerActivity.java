package com.feihong.tv;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.ArrayList;

public final class PlayerActivity extends Activity {
    private VideoView video;
    private ArrayList<String> urls;
    private ArrayList<String> names;
    private int episodeIndex;
    private TextView caption;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        urls = getIntent().getStringArrayListExtra("episode_urls");
        names = getIntent().getStringArrayListExtra("episode_names");
        episodeIndex = getIntent().getIntExtra("episode_index", 0);
        if (urls == null) urls = new ArrayList<>();
        if (names == null) names = new ArrayList<>();
        buildPlayer();
        playCurrent();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (video.isPlaying()) video.pause(); else video.start();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { video.seekTo(video.getCurrentPosition() + 15000); return true; }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { video.seekTo(Math.max(0, video.getCurrentPosition() - 15000)); return true; }
        return super.onKeyDown(keyCode, event);
    }

    private void buildPlayer() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.ink));
        root.setPadding(dp(30), dp(18), dp(30), dp(18));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("返回详情");
        back.setAllCaps(false);
        back.setTextColor(getResources().getColor(R.color.text_primary));
        back.setBackgroundResource(R.drawable.tv_focusable);
        back.setFocusable(true);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { finish(); } });
        header.addView(back, new LinearLayout.LayoutParams(dp(130), dp(44)));
        caption = new TextView(this);
        caption.setTextColor(getResources().getColor(R.color.text_primary));
        caption.setTextSize(18);
        caption.setPadding(dp(16), 0, 0, 0);
        header.addView(caption, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(header);
        video = new VideoView(this);
        MediaController controls = new MediaController(this);
        controls.setAnchorView(video);
        video.setMediaController(controls);
        video.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() { @Override public void onCompletion(android.media.MediaPlayer mp) { if (episodeIndex + 1 < urls.size()) { episodeIndex++; playCurrent(); } } });
        LinearLayout.LayoutParams playerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        playerParams.topMargin = dp(14);
        root.addView(video, playerParams);
        TextView hint = new TextView(this);
        hint.setText("确认键暂停/继续 · 左右键快退/快进 15 秒 · 播放完成自动下一集");
        hint.setTextColor(getResources().getColor(R.color.text_secondary));
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(12), 0, 0);
        root.addView(hint);
        setContentView(root);
    }

    private void playCurrent() {
        if (urls.isEmpty() || episodeIndex < 0 || episodeIndex >= urls.size()) {
            caption.setText("没有可播放的剧集");
            return;
        }
        String name = episodeIndex < names.size() ? names.get(episodeIndex) : "第 " + (episodeIndex + 1) + " 集";
        caption.setText(getIntent().getStringExtra("title") + " · " + name);
        video.setVideoURI(Uri.parse(urls.get(episodeIndex)));
        video.requestFocus();
        video.start();
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
