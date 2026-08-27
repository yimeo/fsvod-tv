package com.feihong.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PosterTile extends FrameLayout {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private final ImageView image;

    PosterTile(Context context, Models.Vod vod) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundResource(com.feihong.tv.R.drawable.tv_focusable);
        setPadding(dp(4), dp(4), dp(4), dp(4));
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(31, 41, 64));
        addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(178)));
        TextView title = new TextView(context);
        title.setText(vod.name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setMaxLines(2);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8), dp(6), dp(8), dp(5));
        GradientDrawable titleBackground = new GradientDrawable();
        titleBackground.setColor(Color.argb(218, 10, 15, 30));
        titleBackground.setCornerRadii(new float[]{0, 0, 0, 0, dp(8), dp(8), dp(8), dp(8)});
        title.setBackground(titleBackground);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        addView(title, titleParams);
        if (vod.remarks.length() > 0) {
            TextView mark = new TextView(context);
            mark.setText(vod.remarks);
            mark.setTextColor(Color.rgb(20, 24, 34));
            mark.setTextSize(10);
            mark.setPadding(dp(5), dp(3), dp(5), dp(3));
            GradientDrawable markBackground = new GradientDrawable();
            markBackground.setColor(Color.rgb(255, 184, 77));
            markBackground.setCornerRadius(dp(5));
            mark.setBackground(markBackground);
            FrameLayout.LayoutParams markParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
            markParams.setMargins(0, dp(7), dp(7), 0);
            addView(mark, markParams);
        }
        load(vod.posterUrl);
    }

    private void load(final String address) {
        if (address == null || address.length() == 0) return;
        EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                Bitmap bitmap = null;
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(address).openConnection();
                    connection.setConnectTimeout(7000);
                    connection.setReadTimeout(7000);
                    InputStream stream = connection.getInputStream();
                    bitmap = BitmapFactory.decodeStream(stream);
                    stream.close();
                } catch (Exception ignored) {
                } finally {
                    if (connection != null) connection.disconnect();
                }
                final Bitmap result = bitmap;
                if (result != null) post(new Runnable() { @Override public void run() { image.setImageBitmap(result); } });
            }
        });
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
