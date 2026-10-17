# 追光的小白球 · APK 打包工程（Cordova）

支持 Android 7.0（API 24）及以上，横屏全屏。

## 获得 APK（推荐用电脑，约 10 分钟）
1. 在 GitHub 新建一个仓库（公开仓库即可）。
2. 把本文件夹里的所有文件上传到仓库根目录。注意一定要包含隐藏文件夹 `.github`
   （里面是 `workflows/build-apk.yml`）。如果网页上传漏了它，就在仓库里点
   Add file → Create new file，文件名填 `.github/workflows/build-apk.yml`，粘贴同名文件的内容。
3. 上传后会自动开始构建；也可以到 Actions 页，选 Build APK → Run workflow 手动触发。
4. 构建约 5～8 分钟。完成后在仓库右侧 Releases 里下载 `app-debug.apk`
   （也可在该次 Actions 运行页的 Artifacts 里下载）。
5. 手机安装时，需要允许“安装未知来源应用”。

## 说明
- 游戏页面在 `www/index.html`，改它再提交就会重新打包。
- 这是调试签名的 APK，可直接安装使用，但不能上架应用商店。
- 导入故事、保存的故事存放在应用自己的本地存储里。

## 接入 TapADN 真实广告（Cordova 插件）
1. 把 `dirichlet_ad_5.3.0.4.aar`（不要解压）放进 `plugins/cordova-plugin-tapad/src/android/libs/`，和其余文件一起提交到仓库。
2. 打开 `www/index.html`，搜索 `TODO(MEDIA)`、`TODO(SPACE_ID)`，改 `AD_CFG` 里的媒体 ID/名称/密钥和三个广告位 ID（开屏、激励、插屏）。
   - 这些信息没填之前，APK 里仍然使用模拟的倒计时广告，游戏不受影响。
   - 上线前把 `debug:true` 改成 `false`。
3. 提交后 GitHub Actions 会自动：检查 AAR → 添加插件 → 打包。缺少 AAR 会在 “Check TapADN AAR” 一步报错。
4. 合规：用户在协议弹窗点“同意”之后才会初始化 SDK 并预加载广告；没同意不会初始化、不会发任何广告请求。
5. 激励视频只有收到 `onRewardVerify(true)` 才复活；加载失败、跳过、视频出错都不发奖励，也不会卡住游戏。
6. 调试版 APK 不做代码混淆；如果以后打 release 包，请在混淆规则里追加：
   `-dontwarn com.tapsdk.tapad.**`、`-keep class com.tapsdk.tapad.** { *; }`、`-keep interface com.tapsdk.tapad.** { *; }`

### 真机验收清单
- 首次启动：同意协议前不应有任何广告请求（可用 `adb logcat -s TapAd` 观察）；点同意后才出现初始化日志。
- 杀进程重开：已同意的用户启动后自动初始化，几秒内菜单上方可能出现开屏广告，超过 3 秒没加载好就直接进游戏。
- 游戏结束弹窗点“看广告复活”：视频播放到可领奖后关闭，才会复活；中途跳过不复活。
- 回到主菜单或重新开始（游戏结束后）：有缓存的插屏广告才会弹，且两次间隔不少于 2 分钟。
- 断网：不崩溃，不卡流程，提示“广告暂不可用”。
