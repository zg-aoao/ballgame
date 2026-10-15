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
