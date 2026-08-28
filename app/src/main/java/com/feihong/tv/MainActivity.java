package com.feihong.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String[] OFFICIAL_CONFIG_URLS = {
            "https://api1.066821.xyz/api.json",
            "https://api2.066821.xyz/api.json"
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SourceRepository sources;
    private LinearLayout pageContent;
    private LinearLayout categoryRow;
    private LinearLayout subCategoryRow;
    private TextView pageTitle;
    private TextView sourceName;
    private TextView sourceDot;
    private TextView status;
    private List<Models.Category> categories = new ArrayList<>();
    private String selectedCategoryId = "";
    private String selectedRootCategoryId = "";
    private String currentScreen = "home";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        sources = new SourceRepository(this);
        buildShell();
        bootstrap();
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (!"home".equals(currentScreen)) showHome();
        else super.onBackPressed();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.ink));
        root.setPadding(dp(42), dp(23), dp(42), dp(24));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView brand = text("飞鸿影院 TV", 28, R.color.text_primary, true);
        header.addView(brand, weight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout activeSource = new LinearLayout(this);
        activeSource.setGravity(Gravity.CENTER_VERTICAL);
        activeSource.setOrientation(LinearLayout.HORIZONTAL);
        activeSource.setPadding(dp(14), dp(8), dp(14), dp(8));
        activeSource.setBackgroundResource(R.drawable.tv_focusable);
        activeSource.setFocusable(true);
        activeSource.setFocusableInTouchMode(true);
        activeSource.setContentDescription("快速切换数据源");
        activeSource.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { showSourceDialog(); } });
        sourceDot = text("●", 18, R.color.unknown, false);
        activeSource.addView(sourceDot);
        sourceName = text("加载数据源", 14, R.color.text_primary, true);
        sourceName.setPadding(dp(8), 0, 0, 0);
        activeSource.addView(sourceName);
        header.addView(activeSource);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = text("正在准备电视端内容…", 13, R.color.text_secondary, false);
        status.setPadding(0, dp(9), 0, dp(12));
        root.addView(status);

        HorizontalScrollView categoryScroller = new HorizontalScrollView(this);
        categoryScroller.setHorizontalScrollBarEnabled(false);
        categoryRow = new LinearLayout(this);
        categoryRow.setGravity(Gravity.CENTER_VERTICAL);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        categoryScroller.addView(categoryRow);
        root.addView(categoryScroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        HorizontalScrollView subCategoryScroller = new HorizontalScrollView(this);
        subCategoryScroller.setHorizontalScrollBarEnabled(false);
        subCategoryRow = new LinearLayout(this);
        subCategoryRow.setGravity(Gravity.CENTER_VERTICAL);
        subCategoryRow.setOrientation(LinearLayout.HORIZONTAL);
        subCategoryScroller.addView(subCategoryRow);
        root.addView(subCategoryScroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        pageContent = new LinearLayout(this);
        pageContent.setOrientation(LinearLayout.VERTICAL);
        pageContent.setPadding(0, dp(17), 0, dp(16));
        scroller.addView(pageContent);
        root.addView(scroller, weight(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setPadding(0, dp(10), 0, 0);
        navigation.addView(navButton("首页", new View.OnClickListener() { @Override public void onClick(View v) { showHome(); } }), weight(0, dp(46), 1));
        navigation.addView(navButton("分类", new View.OnClickListener() { @Override public void onClick(View v) { showCategories(); } }), weight(0, dp(46), 1));
        root.addView(navigation);
        setContentView(root);
    }

    private void bootstrap() {
        if (sources.getActiveSource() != null) showHome();
        else syncOfficialSources();
    }

    private void syncOfficialSources() {
        setStatus("正在同步官方资源…");
        execute(new Runnable() {
            @Override public void run() {
                List<Models.Source> found = new ArrayList<>();
                for (String configUrl : OFFICIAL_CONFIG_URLS) {
                    try {
                        found = MacCmsClient.parseOfficialSources(MacCmsClient.getJson(configUrl));
                        if (!found.isEmpty()) break;
                    } catch (Exception ignored) { }
                }
                for (Models.Source source : found) sources.upsert(source);
                Models.Source active = null;
                for (Models.Source candidate : sources.getSources()) {
                    try {
                        MacCmsClient.fetchPage(candidate, "", "", 1);
                        sources.updateHealth(candidate.id, "healthy");
                        active = candidate.withHealth("healthy");
                        sources.setActiveSource(candidate.id);
                        break;
                    } catch (Exception error) {
                        sources.updateHealth(candidate.id, "unhealthy");
                    }
                }
                final Models.Source result = active;
                post(new Runnable() {
                    @Override public void run() {
                        if (result == null) {
                            updateSourceIdentity();
                            setStatus("未找到可用资源，可按确认键进入数据源并手动添加。");
                            showEmpty("尚未连接数据源", "按底部“数据源”或顶部当前资源，添加你的 MACCMS 数据源。");
                        } else showHome();
                    }
                });
            }
        });
    }

    private void showHome() {
        currentScreen = "home";
        selectedCategoryId = "";
        selectedRootCategoryId = "";
        loadPage("", "", "正在加载首页内容…");
    }

    private void showCategories() {
        currentScreen = "categories";
        selectedCategoryId = "";
        selectedRootCategoryId = "";
        loadPage("", "", "正在加载全部分类内容…");
    }

    private void loadPage(final String typeId, final String keyword, String loadingText) {
        final Models.Source active = sources.getActiveSource();
        updateSourceIdentity();
        if (active == null) {
            setStatus("尚未选择可用数据源。");
            showEmpty("尚未连接数据源", "按确认键选择或添加 MACCMS 数据源。");
            return;
        }
        setStatus(loadingText);
        showLoading();
        execute(new Runnable() {
            @Override public void run() {
                try {
                    final Models.Page page = MacCmsClient.fetchPage(active, typeId, keyword, 1);
                    sources.updateHealth(active.id, "healthy");
                    post(new Runnable() { @Override public void run() {
                        categories = mergeCategories(categories, page.categories);
                        updateSourceIdentity();
                        renderCategories();
                        renderVodGrid(page.items, keyword.length() > 0 ? "搜索结果" : (typeId.length() > 0 ? "分类影片" : "精选内容"));
                        setStatus(page.items.isEmpty() ? "当前没有可展示内容。" : "共显示 " + page.items.size() + " 部影片 · 遥控器方向键选择，确认查看详情");
                    }});
                } catch (Exception error) {
                    sources.updateHealth(active.id, "unhealthy");
                    post(new Runnable() { @Override public void run() {
                        updateSourceIdentity();
                        setStatus("当前资源连接异常，请切换数据源后重试。");
                        showEmpty("内容加载失败", "请按顶部数据源名称切换已保存资源，或在数据源管理中重新检测。");
                    }});
                }
            }
        });
    }

    private void renderCategories() {
        categoryRow.removeAllViews();
        subCategoryRow.removeAllViews();
        addSubCategoryButton("搜索", new View.OnClickListener() { @Override public void onClick(View view) { showSearchDialog(); } });
        addSubCategoryButton("数据源", new View.OnClickListener() { @Override public void onClick(View view) { showSourceDialog(); } });
        if (categories.isEmpty()) return;
        List<Models.Category> roots = new ArrayList<>();
        for (Models.Category category : categories) if (category.parentId == null || category.parentId.length() == 0) roots.add(category);
        if (roots.isEmpty()) roots.addAll(categories);
        Collections.sort(roots, new Comparator<Models.Category>() { @Override public int compare(Models.Category left, Models.Category right) { return compareIds(left.id, right.id); } });
        for (final Models.Category category : roots) {
            Button chip = navButton(category.name, new View.OnClickListener() { @Override public void onClick(View v) {
                selectedRootCategoryId = category.id;
                selectedCategoryId = category.id;
                currentScreen = "categories";
                loadPage(category.id, "", "正在加载“" + category.name + "”…");
            }});
            chip.setTextSize(14);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            params.setMargins(0, 0, dp(10), 0);
            categoryRow.addView(chip, params);
        }
        if (selectedRootCategoryId.length() == 0) return;
        final String rootId = selectedRootCategoryId;
        List<Models.Category> children = new ArrayList<>();
        for (Models.Category category : categories) if (rootId.equals(category.parentId)) children.add(category);
        Collections.sort(children, new Comparator<Models.Category>() { @Override public int compare(Models.Category left, Models.Category right) { return compareIds(left.id, right.id); } });
        addSubCategoryButton("全部", new View.OnClickListener() { @Override public void onClick(View view) {
            selectedCategoryId = rootId;
            loadPage(rootId, "", "正在加载全部分类内容…");
        } });
        for (final Models.Category child : children) {
            addSubCategoryButton(child.name, new View.OnClickListener() { @Override public void onClick(View view) {
                selectedCategoryId = child.id;
                currentScreen = "categories";
                loadPage(child.id, "", "正在加载“" + child.name + "”…");
            } });
        }
    }

    private void addSubCategoryButton(String label, View.OnClickListener listener) {
        Button button = navButton(label, listener);
        button.setTextSize(13);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        params.setMargins(0, 0, dp(10), 0);
        subCategoryRow.addView(button, params);
    }

    private void renderVodGrid(List<Models.Vod> items, String heading) {
        pageContent.removeAllViews();
        pageTitle = text(heading, 24, R.color.text_primary, true);
        pageContent.addView(pageTitle);
        TextView guidance = text("方向键浏览影片，确认键打开详情", 13, R.color.text_secondary, false);
        guidance.setPadding(0, dp(4), 0, dp(14));
        pageContent.addView(guidance);
        if (items.isEmpty()) { showEmpty("暂无影片", "请切换分类或使用搜索查找影片。"); return; }
        int index = 0;
        LinearLayout row = null;
        for (final Models.Vod vod : items) {
            if (index % 5 == 0) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.TOP);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.setMargins(0, 0, 0, dp(16));
                pageContent.addView(row, rowParams);
            }
            PosterTile tile = new PosterTile(this, vod);
            tile.setContentDescription(vod.name);
            tile.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openDetail(vod); } });
            LinearLayout.LayoutParams tileParams = weight(0, dp(220), 1);
            tileParams.setMargins(index % 5 == 0 ? 0 : dp(12), 0, 0, 0);
            row.addView(tile, tileParams);
            index++;
        }
    }

    private void openDetail(final Models.Vod vod) {
        final Models.Source active = sources.getActiveSource();
        if (active == null) return;
        setStatus("正在载入“" + vod.name + "”详情…");
        execute(new Runnable() {
            @Override public void run() {
                try {
                    final Models.VodDetail detail = MacCmsClient.fetchDetail(active, vod.id);
                    post(new Runnable() { @Override public void run() { showDetail(detail); } });
                } catch (Exception error) {
                    post(new Runnable() { @Override public void run() { setStatus("影片详情加载失败，请稍后重试。"); } });
                }
            }
        });
    }

    private void showDetail(final Models.VodDetail detail) {
        currentScreen = "detail";
        pageContent.removeAllViews();
        TextView title = text(detail.name, 27, R.color.text_primary, true);
        pageContent.addView(title);
        TextView meta = text(joinMeta(detail), 14, R.color.text_secondary, false);
        meta.setPadding(0, dp(6), 0, dp(16));
        pageContent.addView(meta);
        if (detail.content.length() > 0) {
            TextView introTitle = text("剧情简介", 18, R.color.gold, true);
            pageContent.addView(introTitle);
            TextView intro = text(detail.content, 14, R.color.text_secondary, false);
            intro.setLineSpacing(dp(3), 1f);
            intro.setPadding(0, dp(7), 0, dp(20));
            pageContent.addView(intro);
        }
        if (detail.playSources.isEmpty()) {
            showEmpty("暂无播放地址", "该资源未提供可播放剧集。");
            return;
        }
        TextView sourceTitle = text("选择播放源", 18, R.color.gold, true);
        pageContent.addView(sourceTitle);
        LinearLayout sourceRow = new LinearLayout(this);
        sourceRow.setOrientation(LinearLayout.HORIZONTAL);
        sourceRow.setPadding(0, dp(8), 0, dp(15));
        pageContent.addView(sourceRow);
        final LinearLayout episodeArea = new LinearLayout(this);
        episodeArea.setOrientation(LinearLayout.VERTICAL);
        final int[] activeIndex = {0};
        for (int index = 0; index < detail.playSources.size(); index++) {
            final int sourceIndex = index;
            Button button = navButton(detail.playSources.get(index).name, new View.OnClickListener() { @Override public void onClick(View view) {
                activeIndex[0] = sourceIndex;
                renderEpisodes(detail, activeIndex[0], episodeArea);
            }});
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            params.setMargins(0, 0, dp(10), 0);
            sourceRow.addView(button, params);
        }
        pageContent.addView(episodeArea);
        renderEpisodes(detail, 0, episodeArea);
        setStatus("选择播放源和剧集后按确认键播放；返回键回到影片列表。");
    }

    private void renderEpisodes(final Models.VodDetail detail, int sourceIndex, LinearLayout target) {
        target.removeAllViews();
        Models.PlaySource source = detail.playSources.get(sourceIndex);
        TextView heading = text(source.name + " · " + source.episodes.size() + " 集", 16, R.color.text_primary, true);
        target.addView(heading);
        int episodeIndex = 0;
        LinearLayout row = null;
        for (final Models.Episode episode : source.episodes) {
            if (episodeIndex % 6 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.setMargins(0, dp(10), 0, 0);
                target.addView(row, rowParams);
            }
            Button button = navButton(episode.name, new View.OnClickListener() { @Override public void onClick(View view) { play(detail, source, episode); } });
            button.setTextSize(13);
            LinearLayout.LayoutParams params = weight(0, dp(44), 1);
            params.setMargins(episodeIndex % 6 == 0 ? 0 : dp(8), 0, 0, 0);
            row.addView(button, params);
            episodeIndex++;
        }
    }

    private void play(Models.VodDetail detail, Models.PlaySource source, Models.Episode episode) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("title", detail.name);
        intent.putExtra("source", source.name);
        intent.putExtra("episode", episode.name);
        intent.putExtra("url", episode.url);
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> urls = new ArrayList<>();
        int current = 0;
        for (int index = 0; index < source.episodes.size(); index++) {
            Models.Episode item = source.episodes.get(index);
            names.add(item.name);
            urls.add(item.url);
            if (item.url.equals(episode.url)) current = index;
        }
        intent.putStringArrayListExtra("episode_names", names);
        intent.putStringArrayListExtra("episode_urls", urls);
        intent.putExtra("episode_index", current);
        startActivity(intent);
    }

    private void showSearchDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("输入片名、演员或关键词");
        input.setHintTextColor(color(R.color.text_secondary));
        input.setTextColor(color(R.color.text_primary));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int padding = dp(20);
        input.setPadding(padding, 0, padding, 0);
        new AlertDialog.Builder(this).setTitle("搜索影片").setView(input).setNegativeButton("取消", null).setPositiveButton("搜索", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                String keyword = input.getText().toString().trim();
                if (keyword.length() == 0) return;
                currentScreen = "search";
                loadPage("", keyword, "正在搜索“" + keyword + "”…");
            }
        }).show();
    }

    private void showSourceDialog() {
        final List<Models.Source> all = sources.getSources();
        List<String> labels = new ArrayList<>();
        final Models.Source active = sources.getActiveSource();
        for (Models.Source source : all) {
            String marker = active != null && source.id.equals(active.id) ? "当前 · " : "";
            String health = "healthy".equals(source.health) ? "连接正常" : "unhealthy".equals(source.health) ? "连接异常" : "待检测";
            labels.add(marker + source.name + " · " + health);
        }
        labels.add("＋ 添加 MACCMS 数据源");
        new AlertDialog.Builder(this).setTitle("快速切换资源").setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int index) {
                if (index == all.size()) { showAddSourceDialog(); return; }
                final Models.Source selected = all.get(index);
                sources.setActiveSource(selected.id);
                updateSourceIdentity();
                if ("categories".equals(currentScreen)) showCategories(); else showHome();
            }
        }).setNegativeButton("关闭", null).show();
    }

    private void showAddSourceDialog() {
        final LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(22);
        form.setPadding(pad, 0, pad, 0);
        final EditText name = new EditText(this);
        name.setHint("数据源名称（可选）");
        name.setSingleLine(true);
        final EditText address = new EditText(this);
        address.setHint("域名或 MACCMS API 地址");
        address.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        address.setSingleLine(true);
        form.addView(name);
        form.addView(address);
        new AlertDialog.Builder(this).setTitle("添加数据源").setView(form).setNegativeButton("取消", null).setPositiveButton("识别并保存", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                final String addressText = address.getText().toString();
                final String nameText = name.getText().toString();
                setStatus("正在识别数据源…");
                execute(new Runnable() {
                    @Override public void run() {
                        try {
                            final Models.Source discovered = MacCmsClient.discover(addressText, nameText);
                            sources.upsert(discovered);
                            sources.setActiveSource(discovered.id);
                            post(new Runnable() { @Override public void run() { showHome(); } });
                        } catch (Exception error) {
                            post(new Runnable() { @Override public void run() { setStatus("数据源识别失败，请确认地址后重试。"); } });
                        }
                    }
                });
            }
        }).show();
    }

    private void updateSourceIdentity() {
        Models.Source active = sources.getActiveSource();
        if (active == null) {
            sourceName.setText("未连接数据源");
            sourceDot.setTextColor(color(R.color.unknown));
            return;
        }
        sourceName.setText(active.name);
        sourceDot.setTextColor(color("healthy".equals(active.health) ? R.color.healthy : "unhealthy".equals(active.health) ? R.color.unhealthy : R.color.unknown));
    }

    private void showLoading() {
        pageContent.removeAllViews();
        ProgressBar progress = new ProgressBar(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(72);
        pageContent.addView(progress, params);
    }

    private void showEmpty(String title, String body) {
        pageContent.removeAllViews();
        TextView headline = text(title, 24, R.color.text_primary, true);
        headline.setGravity(Gravity.CENTER_HORIZONTAL);
        headline.setPadding(0, dp(64), 0, dp(8));
        pageContent.addView(headline);
        TextView copy = text(body, 14, R.color.text_secondary, false);
        copy.setGravity(Gravity.CENTER_HORIZONTAL);
        pageContent.addView(copy);
    }

    private Button navButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(color(R.color.text_primary));
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackgroundResource(R.drawable.tv_focusable);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setOnClickListener(listener);
        return button;
    }

    private TextView text(String value, float size, int colorId, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(colorId));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams weight(int width, int height, float value) { return new LinearLayout.LayoutParams(width, height, value); }
    private int color(int id) { return getResources().getColor(id); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private void post(Runnable action) { handler.post(action); }
    private void execute(Runnable action) { executor.execute(action); }
    private void setStatus(String value) { status.setText(value); }
    private static int compareIds(String left, String right) { try { return Integer.compare(Integer.parseInt(left), Integer.parseInt(right)); } catch (Exception error) { return left.compareTo(right); } }
    private static String joinMeta(Models.Vod detail) { String value = detail.typeName; if (detail.year.length() > 0) value += " · " + detail.year; if (detail.area.length() > 0) value += " · " + detail.area; return value; }
    private static List<Models.Category> mergeCategories(List<Models.Category> current, List<Models.Category> incoming) { java.util.LinkedHashMap<String, Models.Category> map = new java.util.LinkedHashMap<>(); for (Models.Category category : current) map.put(category.id, category); for (Models.Category category : incoming) map.put(category.id, category); return new ArrayList<>(map.values()); }
}
