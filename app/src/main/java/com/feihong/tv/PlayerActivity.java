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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.ArrayList;

public final class PlayerActivity extends Activity {
    private VideoView video;
    private ArrayList<String> urls;
    private ArrayList<String> names;
    private int episodeIndex;
    private TextView caption;
    private LinearLayout episodeList;
    private String title;
    private String sourceName;
    private String posterUrl;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        urls = getIntent().getStringArrayListExtra("episode_urls");
        names = getIntent().getStringArrayListExtra("episode_names");
        episodeIndex = getIntent().getIntExtra("episode_index", 0);
        title = getIntent().getStringExtra("title");
        sourceName = getIntent().getStringExtra("source");
        posterUrl = getIntent().getStringExtra("poster_url");
        if (urls == null) urls = new ArrayList<>();
        if (names == null) names = new ArrayList<>();
        buildPlayer();
        playCurrent();
    }

    @Override protected void onPause() {
        saveProgress();
        super.onPause();
    }

    private void saveProgress() {
        if (video == null || urls.isEmpty() || episodeIndex < 0 || episodeIndex >= urls.size()) return;
        String episode = episodeIndex < names.size() ? names.get(episodeIndex) : "第 " + (episodeIndex + 1) + " 集";
        WatchHistory.save(this, title, sourceName, episode, urls.get(episodeIndex), posterUrl, episodeIndex, video.getCurrentPosition(), video.getDuration());
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
        root.setPadding(dp(42), dp(22), dp(42), dp(22));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = playerButton("返回详情");
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { finish(); } });
        header.addView(back, new LinearLayout.LayoutParams(dp(140), dp(46)));
        caption = new TextView(this);
        caption.setTextColor(getResources().getColor(R.color.text_primary));
        caption.setTextSize(20);
        caption.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        caption.setPadding(dp(18), 0, 0, 0);
        header.addView(caption, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView network = new TextView(this);
        network.setText("网络播放");
        network.setTextColor(getResources().getColor(R.color.gold));
        network.setTextSize(14);
        header.addView(network);
        root.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setPadding(0, dp(18), 0, 0);
        video = new VideoView(this);
        MediaController controls = new MediaController(this);
        controls.setAnchorView(video);
        video.setMediaController(controls);
        video.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() { @Override public void onCompletion(android.media.MediaPlayer mp) { if (episodeIndex + 1 < urls.size()) { episodeIndex++; playCurrent(); } } });
        video.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() { @Override public boolean onError(android.media.MediaPlayer mp, int what, int extra) { caption.setText("播放失败 · 请返回详情切换播放源"); return true; } });
        body.addView(video, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout playlistCard = new LinearLayout(this);
        playlistCard.setOrientation(LinearLayout.VERTICAL);
        playlistCard.setPadding(dp(18), dp(14), dp(10), dp(10));
        playlistCard.setBackgroundResource(R.drawable.tv_focusable);
        TextView playlistTitle = new TextView(this);
        playlistTitle.setText("播放列表 · " + urls.size() + " 集");
        playlistTitle.setTextColor(getResources().getColor(R.color.text_primary));
        playlistTitle.setTextSize(17);
        playlistTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        playlistCard.addView(playlistTitle);
        ScrollView episodeScroller = new ScrollView(this);
        episodeList = new LinearLayout(this);
        episodeList.setOrientation(LinearLayout.VERTICAL);
        episodeScroller.addView(episodeList);
        playlistCard.addView(episodeScroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        body.addView(playlistCard, new LinearLayout.LayoutParams(dp(310), ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView hint = new TextView(this);
        hint.setText("确认键暂停/继续 · 左右键快退/快进 15 秒 · 播放结束自动下一集 · 若不能播放请返回详情切换播放源");
        hint.setTextColor(getResources().getColor(R.color.text_secondary));
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(12), 0, 0);
        root.addView(hint);
        setContentView(root);
        renderEpisodeList();
    }

    private void renderEpisodeList() {
        if (episodeList == null) return;
        episodeList.removeAllViews();
        for (int index = 0; index < urls.size(); index++) {
            final int target = index;
            String label = index < names.size() ? names.get(index) : "第 " + (index + 1) + " 集";
            Button button = playerButton(label);
            button.setTextSize(13);
            button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            button.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { episodeIndex = target; playCurrent(); } });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            params.setMargins(0, dp(6), 0, 0);
            episodeList.addView(button, params);
        }
    }

    private Button playerButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(getResources().getColor(R.color.text_primary));
        button.setTextSize(14);
        button.setBackgroundResource(R.drawable.tv_focusable);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        return button;
    }

    private void playCurrent() {
        if (urls.isEmpty() || episodeIndex < 0 || episodeIndex >= urls.size()) {
            caption.setText("没有可播放的剧集");
            return;
        }
        String name = episodeIndex < names.size() ? names.get(episodeIndex) : "第 " + (episodeIndex + 1) + " 集";
        caption.setText(title + " · " + name);
        video.setVideoURI(Uri.parse(urls.get(episodeIndex)));
        renderEpisodeList();
        video.requestFocus();
        video.start();
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
