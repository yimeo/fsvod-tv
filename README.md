# 飞鸿影院 TV

飞鸿影院 TV 是基于现有飞鸿影院功能重新实现的原生 Android TV 版本，目标系统为 **Android 5.0（API 21）及以上**。项目采用 Java 与 Android 原生组件，不依赖 Expo 或高版本 Jetpack，因此可用于较早的电视盒子和 Android TV 模拟器。

## 已实现能力

| 模块 | 电视端能力 |
|---|---|
| 数据源 | 自动从官方 JSON 同步资源；支持手动识别 MACCMS 域名、保存并快速切换已有资源 |
| 首页与分类 | 黑金大屏布局、五列海报墙、一级分类选择、连接状态显示 |
| 搜索与详情 | 遥控器确认键搜索、影片详情、播放源选择、剧集网格 |
| 播放 | 原生 `VideoView`、确认暂停/继续、左右快进后退、完播自动下一集 |
| 遥控器 | 可聚焦卡片和按钮，方向键选择、确认执行、返回键返回上一层 |

## 构建

安装 Android SDK Platform 35 与 Build Tools 后，在项目根目录运行：

```bash
./gradlew assembleDebug
```

生成的安装包路径为 `app/build/outputs/apk/debug/app-debug.apk`。工程的 `minSdk` 为 **21**，对应 Android 5.0；TV 启动入口同时包含 `LEANBACK_LAUNCHER`，会显示在 Android TV 启动器中。
