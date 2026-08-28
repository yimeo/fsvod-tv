package com.feihong.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PosterTile extends FrameLayout {
    private static final String CACHE_PREFIX = "feihong-poster-";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> MEMORY_CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private final Context appContext;
    private final FrameLayout fallback;
    private final ImageView image;

    PosterTile(Context context, Models.Vod vod) {
        super(context);
        appContext = context.getApplicationContext();
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundResource(com.feihong.tv.R.drawable.tv_focusable);
        setPadding(dp(4), dp(4), dp(4), dp(4));

        fallback = createGeneratedPoster(context, vod.name);
        addView(fallback, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.TRANSPARENT);
        image.setAlpha(0f);
        addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(context);
        title.setText(vod.name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setMaxLines(2);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(10), dp(7), dp(10), dp(6));
        GradientDrawable titleBackground = new GradientDrawable();
        titleBackground.setColor(Color.argb(220, 9, 14, 28));
        titleBackground.setCornerRadii(new float[]{0, 0, 0, 0, dp(8), dp(8), dp(8), dp(8)});
        title.setBackground(titleBackground);
        addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        if (vod.remarks.length() > 0) addRemark(context, vod.remarks);
        load(vod.posterUrl);
    }

    static int getCachedPosterCount(Context context) {
        File[] files = context.getCacheDir().listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File file : files) if (file.getName().startsWith(CACHE_PREFIX)) count++;
        return count;
    }

    static void clearPosterCache(Context context) {
        MEMORY_CACHE.evictAll();
        File[] files = context.getCacheDir().listFiles();
        if (files == null) return;
        for (File file : files) if (file.getName().startsWith(CACHE_PREFIX)) file.delete();
    }

    private FrameLayout createGeneratedPoster(Context context, String title) {
        FrameLayout generated = new FrameLayout(context);
        int[] tones = generatedTones(title);
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR, tones);
        background.setCornerRadius(dp(8));
        generated.setBackground(background);
        TextView label = new TextView(context);
        label.setText("飞鸿 · 影院");
        label.setTextColor(Color.rgb(255, 209, 132));
        label.setTextSize(10);
        label.setPadding(dp(13), dp(14), dp(13), 0);
        generated.addView(label, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        TextView generatedTitle = new TextView(context);
        generatedTitle.setText(title);
        generatedTitle.setTextColor(Color.WHITE);
        generatedTitle.setTextSize(20);
        generatedTitle.setMaxLines(3);
        generatedTitle.setGravity(Gravity.BOTTOM);
        generatedTitle.setPadding(dp(13), 0, dp(13), dp(43));
        generated.addView(generatedTitle, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return generated;
    }

    private void addRemark(Context context, String value) {
        TextView mark = new TextView(context);
        mark.setText(value);
        mark.setTextColor(Color.rgb(20, 24, 34));
        mark.setTextSize(10);
        mark.setPadding(dp(6), dp(3), dp(6), dp(3));
        GradientDrawable markBackground = new GradientDrawable();
        markBackground.setColor(Color.rgb(255, 184, 77));
        markBackground.setCornerRadius(dp(5));
        mark.setBackground(markBackground);
        FrameLayout.LayoutParams markParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        markParams.setMargins(0, dp(8), dp(8), 0);
        addView(mark, markParams);
    }

    private void load(final String address) {
        if (address == null || address.trim().length() == 0) return;
        Bitmap memory = MEMORY_CACHE.get(address);
        if (memory != null) { reveal(memory); return; }
        EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                Bitmap result = readCached(address);
                if (result == null) result = download(address);
                if (result != null) {
                    MEMORY_CACHE.put(address, result);
                    final Bitmap bitmap = result;
                    post(new Runnable() { @Override public void run() { reveal(bitmap); } });
                }
            }
        });
    }

    private Bitmap readCached(String address) {
        InputStream stream = null;
        try {
            File file = cacheFile(address);
            if (!file.exists() || file.length() == 0) return null;
            stream = new FileInputStream(file);
            return decode(stream);
        } catch (Exception ignored) {
            return null;
        } finally {
            close(stream);
        }
    }

    private Bitmap download(String address) {
        HttpURLConnection connection = null;
        InputStream stream = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(9000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36 Chrome/120 Safari/537.36");
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) return null;
            stream = connection.getInputStream();
            Bitmap bitmap = decode(stream);
            if (bitmap != null) saveCached(address, bitmap);
            return bitmap;
        } catch (Exception ignored) {
            return null;
        } finally {
            close(stream);
            if (connection != null) connection.disconnect();
        }
    }

    private Bitmap decode(InputStream stream) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeStream(stream, null, options);
    }

    private void saveCached(String address, Bitmap bitmap) {
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(cacheFile(address));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 84, stream);
        } catch (Exception ignored) {
        } finally {
            close(stream);
        }
    }

    private void reveal(Bitmap bitmap) {
        if (!isAttachedToWindow()) return;
        image.setImageBitmap(bitmap);
        fallback.setVisibility(View.GONE);
        image.animate().alpha(1f).setDuration(220).start();
    }

    private File cacheFile(String address) { return new File(appContext.getCacheDir(), CACHE_PREFIX + Integer.toHexString(address.hashCode()) + ".jpg"); }
    private int[] generatedTones(String title) {
        int[][] values = {{0xFF182B50, 0xFF51306B}, {0xFF153B42, 0xFF1B5A63}, {0xFF392235, 0xFF724D3C}, {0xFF253042, 0xFF4B3D61}};
        int hash = title == null ? 0 : title.hashCode();
        return values[Math.abs(hash == Integer.MIN_VALUE ? 0 : hash) % values.length];
    }
    private void close(InputStream stream) { try { if (stream != null) stream.close(); } catch (Exception ignored) { } }
    private void close(FileOutputStream stream) { try { if (stream != null) stream.close(); } catch (Exception ignored) { } }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
