# 壁画音响

基于众云世纪 ZYSJ1739A（RK3399）开发的壁挂式联网音箱。

## 硬件与系统

- 板卡：ZYSJ1739A / RK3399，序列号 K71V7BTYKP
- 系统：Android 9（SDK 28），userdebug/test-keys，arm64-v8a
- 内存/存储：4GB RAM，/data 约 53GB
- 显示：HDMI 1920x1080 @ 240dpi
- 音频：RT5651 3.5mm 输出（card 0）、SPDIF（card 1）、HDMI/DP（card 2）
- 网络：wlan0 已连接 `CMCC-u5sh-5G`，IP 192.168.1.66，外网可达
- 开发通道：adb root 可用，tinyplay / tinymix 可用

## 当前接线状态

- 已接：电源、HDMI（已确认 1920x1080@60）、USB 调试
- 待接：3.5mm 到有源音箱/功放、USB 键鼠（可选）

## 目标架构

Android 9 系统上运行流媒体音乐应用，HDMI 输出封面/歌词界面，3.5mm 输出到功放或有源音箱。不做语音，先做成稳定的联网播放 + 开机自启 kiosk，再进入画框整机阶段。

## 里程碑

1. 板卡识别与环境确认（进行中）
2. 联网并安装流媒体应用
3. 3.5mm 音频通路验证
4. kiosk 化：开机自启、隐藏系统栏、常亮
5. 整机联调：画框结构、供电、散热

## 脚本

- `scripts/device-info.ps1`：抓取板卡系统、声卡、显示、网络信息
- `scripts/test-audio.ps1`：推送并播放 `assets/test-tone-1khz.wav`
- `scripts/setup-kiosk.ps1`：kiosk 基础设置（常亮、关闭锁屏等）
- `scripts/build-standby-apk.ps1`：编译待机宋画轮播 APK
- `scripts/build-apk.ps1`：通用 APK 编译（aapt2 + javac + dx + apksigner）
- `scripts/setup-remote-receiver.ps1`：构建、安装并启动板端遥控接收端
- `scripts/push-song-images.ps1`：把 `E:\BaiduNetdiskDownload\宋` 图片推到设备
- `scripts/setup-standby-slideshow.ps1`：推图、安装并启用待机轮播屏保

## 待机轮播

`standby/` 是一个独立的 Android 屏保（Dream）应用：设备空闲 60 秒后自动进入全屏轮播，图片来自 `/sdcard/Pictures/Song`，每 15 秒切换并带淡入淡出。应用提供“宋画屏保设置”页面，可勾选要播放的画，并选择顺序播放或随机播放；回到音乐应用或唤醒屏幕即可退出屏保。

## 手机遥控

板端常驻接收端（`remote-receiver/`）在 8080 端口提供遥控服务，手机端（`remote-phone/`）是专用遥控 App，板端同时内置网页版遥控页。

- 板端安装：`scripts/setup-remote-receiver.ps1`（构建、安装、授权通知监听、启动）
- 手机安装：`apk/remote-phone.apk`，手机连同一 WiFi 后会自动发现板子，也可手动填 IP
- 网页遥控：浏览器打开 `http://192.168.1.66:8080`
- 能力：播放/暂停、上一首/下一首、音量调节、亮屏退出屏保、一键进入壁画（宋画屏保）、快捷启动网易云/知悦/哔哩
- 触控板：默认光标模式，滑动移动电视屏幕上的红色十字光标，轻点确认点击；也保留原来的绝对点击模式
- 主界面触控板右侧有“全屏”按钮，可进入沉浸式全屏触控板，滑动区域更大且不会带动页面滚动
- 双指上下滑动触控板即可滚动页面，触控板下方也提供“上滚/下滚”按钮，适合刷 B 站等纵向列表
- 方向键：上/下/左/右/OK 按钮通过 root 输入通道注入遥控器按键，B 站等 TV 应用可正常移动焦点和滚动
- 系统键：返回/主页/后台/电源对应板端底部导航栏；电源为长按电源键，弹出关机/重启菜单
- 后台按钮会自动唤醒隐藏的导航栏（底部或顶部边缘拖动一次），再单击“概览”进入后台任务，不会重复点击或误切应用
- 后台清理：手机端“清理”按钮会打开后台任务、滑到“全部清除”并点击，随后回到主页，不中断接收端服务
- 板端悬浮光标由遥控接收端绘制，点击/滑动终点会闪烁提示落点；网页遥控同样带触控板
- 歌名与播放状态依赖音乐 App 的播放通知，网易云正在播放时会自动显示

遥控 API：`GET /api/status` 获取状态；`POST /api/cmd?cmd=...` 执行命令，`cmd` 支持 `toggle`、`play`、`pause`、`next`、`prev`、`vol_up`、`vol_down`、`vol_set`、`wake`、`wall`（进入壁画屏保）、`launch`、`tap`、`swipe`、`pointer`（相对移动光标）、`click`（在光标处点击）、`key`（方向键/系统键，`cmd=key&code=up|down|left|right|ok|back|home|recents|power`）、`nav`（点击板端导航栏，`cmd=nav&key=home|back|recents|power`）、`clean`（清理后台任务）。
滚动命令为 `cmd=scroll&dir=up|down|left|right&count=1`，接收端会依次尝试无障碍滚动节点、屏幕滑动和 root 方向键注入三条通道；状态接口会返回 `inputHelper` 字段表示 root 输入通道是否在线。

root 输入通道由 `scripts/input_helper.sh` 提供：`setup-remote-receiver.ps1` 会自动推送并启动它。板子重启后 root 进程不会保留，需要重跑一次安装脚本（或单独执行 `adb shell setsid sh /data/local/tmp/input_helper.sh >/dev/null 2>&1 &`）。

## 手机遥控验证状态（2026-08-17）

- 板端 `apk/remote-receiver.apk` 已安装并常驻运行，地址 `http://192.168.1.66:8080`
- 手机端 `apk/remote-phone.apk` 已安装到华为 PRA-AL00X，IP 已自动保存
- 已验证：播放/暂停、上一首/下一首、音量+/-、亮屏、快捷启动网易云/知悦/哔哩
- 自动发现：App 先监听 UDP 广播，同时扫描当前 /24 网段，任一方式找到板子后自动填入 IP 并连接
- 播放状态来自板端通知监听，网易云播放时手机端会显示歌名、歌手和播放状态
- 触控板光标模式已实测：相对移动、光标处点击、网页触控板均可用；点击桌面图标能正确打开应用
- 触控板滑动已固定页面：在主界面触控板上滑动时，遥控页面不再上下滚动
- 滚动功能已加入：双指上下滑动和上滚/下滚按钮均可用，命令支持多通道回退
- 方向键与系统键已实测：B 站推荐页方向键滚动正常，返回/主页/后台/电源四个导航栏按键均可从手机端触发
- 后台清理已实测：打开过网易云/B站/智悦后执行 `clean`，任务卡片全部清除并回到主页，接收端服务保持运行
- 安装或更新接收端后，如果点击注入不可用，需重启一次系统（`adb shell stop && start` 或整机重启）让无障碍服务生效
