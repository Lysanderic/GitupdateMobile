# 版本监视 (VerWatch)

一个只有 200 KB 的 Android 小工具：把手机上装的应用和你指定的 GitHub 仓库对上，每天定时检查有没有**正式版**（自动排除 beta / rc / draft）更新，发现新版本就推一条通知，点一下就能下载安装。

## 功能

- 列出本机已装应用（名称 / 包名 / 版本），勾选要监控的
- 每个应用绑定一个 GitHub 仓库（`owner/repo`），也可以按应用名模糊匹配
- 每天 10:00 自动检查（AlarmManager，开机 / 改时间 / 覆盖安装后自动重排）
- 版本号归一化比较：`v0.29.2` / `13.6.0.r1086...` / `2.11.36.Meta` 都能正确对上
- 有新版本：下载 Release 里的 APK → 安装；已下载未安装时按钮变「安装」，不会重复下载
- 可选 Shizuku 静默安装（shell 身份 `pm install`，经 `/data/local/tmp` 中转）
- 文本批量导入 / 导出（`包名 或 应用名 + owner/repo`，一行一条）
- 自动排序：未配置 → 未安装 → 有更新 → 其他 → 已最新（按发布日期倒序）
- 无更新时静默，可切换为每次都通知
- 支持可选 GitHub Token（60 次/小时 → 5000 次/小时），带 ETag 条件请求，304 不消耗额度

## 权限说明

| 权限 | 用途 |
| --- | --- |
| `QUERY_ALL_PACKAGES` | 读取已安装应用及版本；侧载可用，上架 Google Play 会被限制 |
| `POST_NOTIFICATIONS` | 每天推送检查结果 |
| `REQUEST_INSTALL_PACKAGES` | 下载后调起安装器 |
| `RECEIVE_BOOT_COMPLETED` | 重启后恢复定时 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 提升 10:00 准时率 |
| `SCHEDULE_EXACT_ALARM` | 精确闹钟（可选） |
| Shizuku | 可选，用于免确认静默安装 |

## 构建

不需要 Android Studio / Gradle，脚本直接用 `aapt` + `javac` + `d8` + `apksigner` 打包（在 arm64 的 Ubuntu 上也能跑）。

```bash
python3 scripts/fetch-deps.py      # 下载 android.jar / r8.jar / Shizuku SDK
keytool -genkeypair -keystore out/verwatch.keystore -alias verwatch \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass verwatch123 -keypass verwatch123 -dname "CN=VerWatch,O=DSH,C=CN"
./build.sh                          # 产物：out/verwatch.apk
```

依赖：JDK 17+、`aapt`、`zipalign`、`apksigner`（Debian/Ubuntu 可直接 `apt install aapt apksigner zipalign openjdk-17-jdk-headless`）。

## 目录

```
src/             Java 源码（无第三方 UI 库，界面全部代码构建）
srcres/          清单与资源（图标、字符串）
tools/           附带的手写 AXML 生成器（构建流程未使用）
scripts/         依赖下载
build.sh         一键打包
```

## 已知限制

- 只能对比**在 GitHub 发布 Release** 的项目；只打 tag 的（如 `videolan/vlc-android`）拿不到版本
- 被 DMCA 下架的仓库会返回 HTTP 451
- versionName 与 tag 差异过大时显示「无法比较」
- Shizuku 安装依赖 shell 身份，部分 ROM 会拦截（失败会自动回退系统安装器）
- 免 root，无法读取隐私数据；不联网上传任何信息，只请求 `api.github.com`

## 许可

MIT
