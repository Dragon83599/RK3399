# RK3399 壁画音响

基于众云世纪 ZYSJ1739A（RK3399，Android 9）开发的壁挂式联网音箱项目。设备通过 HDMI 输出音乐与封面界面，3.5mm 输出到功放，支持手机遥控、触控板、方向键、系统键、后台清理和一键进入宋画屏保。

## 今日更新（2026-08-18）

- 修复重启后部分按键失灵：接收端开机自动启动 root 输入助手（看门狗带快速重试），任意遥控按键（方向键、系统键、播放/音量、触控）在空闲进入宋画屏保或熄屏后都会先亮屏/退出屏保再执行。
- 后台清理改为直接打开系统 RecentsActivity 并点击“全部清除”，不再依赖脆弱的坐标点击。
- 电源键拆分为“关机”和“重启”两个独立按钮（网页遥控和手机 App 均已更新）。
- 手机 App 命令改为并行发送，一条慢命令不再堵住其他按键。
- 屏保叠加时钟/日期，可在“宋画屏保设置”里开关。
- 增加音量上限（默认 80%），网页遥控滑块和手机 App 均可调节，防止误触后音量过大。
- 网页遥控升级为 PWA，浏览器可“添加到主屏幕”后全屏使用。
- 遥控界面整体重做：网页和手机 App 统一为暗色控制面板，方向键改为十字布局，顶部显示连接状态，触控板增加光标位置指示。
- 修复触控点击偏差：悬浮光标不再叠加状态栏高度，电视上的十字中心与实际点击位置一致；网页遥控新增 5px 微调按钮和当前坐标显示，设置屏保等待时间等小控件时更精确。
- 修复遥控在屏保设置页操作时误拉通知栏：设置页不再被误判为屏保，滑动改用 root 注入，standby 包强制沉浸式全屏（下拉框弹出时状态栏不再出现）；网页遥控新增上滚/下滚按钮，可像滚轮一样滚动设置列表。
- 点击“壁画”后自动隐藏遥控光标，按任意键退出屏保时自动恢复光标显示。
- 宋画屏保设置页新增时钟大小和位置设置，可调整屏保中时间日期的字号与显示方位。

## 今日更新（2026-08-17）

今天完成的主要工作：

- 手机遥控 App：自动发现板子、媒体控制、触控板（光标/绝对模式）、全屏触控板、方向键、系统键（返回/主页/后台/电源）、后台清理、亮屏与进入壁画。
- 板端遥控接收端：HTTP 遥控服务、媒体键/音量/亮屏、通知监听、悬浮光标、无障碍手势注入、root 输入通道。
- 待机宋画屏保：独立 Dream 应用，全屏宋画轮播，可配置图片与播放方式。
- 下载并安装第三方应用：智悦TV、Fliigo、网易云音乐、哔哩哔哩（APK 已收录在本仓库 `apk/`）。
- 整理仓库：统一项目结构、补齐 README、收录需求与任务文档。

## 项目结构

```text
RK3399/
├── README.md                  # 项目说明（本文件）
├── .gitignore
├── .gitattributes             # APK 使用 Git LFS
├── docs/
│   ├── device-report.md       # 设备状态报告
│   ├── planning/              # 产品需求、准备清单、启动流程、周任务等
│   ├── 对话记录/               # 每日开发对话整理
│   └── 规格书/                 # ZYSJ1739A 规格书
├── remote-receiver/           # 板端遥控接收端（HTTP 服务 + 无障碍 + root 通道）
├── remote-phone/              # 手机遥控 App
├── standby/                   # 待机宋画屏保（Dream）
├── scripts/                   # 构建、安装、调试脚本
├── assets/                    # 测试音频等资源
└── apk/                       # 已编译 APK（本仓库 APK 走 Git LFS）
```

## 硬件与系统

- 板卡：ZYSJ1739A / RK3399，序列号 `K71V7BTYKP`
- 系统：Android 9（SDK 28），userdebug/test-keys，arm64-v8a
- 内存/存储：4GB RAM，/data 约 53GB
- 显示：HDMI 1920x1080 @ 240dpi
- 音频：RT5651 3.5mm 输出（card 0）、SPDIF（card 1）、HDMI/DP（card 2）
- 网络：wlan0 连接 `CMCC-u5sh-5G`，IP `192.168.1.66`
- 开发通道：adb root 可用，tinyplay / tinymix 可用

## 遥控系统

板端常驻接收端在 `8080` 端口提供遥控服务，手机端是专用遥控 App，同时内置网页遥控页。

- 板端安装：`scripts/setup-remote-receiver.ps1`
- 手机安装：`apk/remote-phone.apk`，同一 WiFi 下自动发现，也可手动填 IP
- 网页遥控：浏览器打开 `http://192.168.1.66:8080`
- 能力：播放/暂停、上一首/下一首、音量、亮屏、一键进入壁画、快捷启动网易云/知悦/哔哩
- 触控板：光标模式（推荐）与绝对模式，滑动移动电视上的红色十字光标，轻点确认点击
- 方向键：上/下/左/右/OK 通过 root 输入通道注入，B 站等 TV 应用可正常移动焦点和滚动
- 系统键：返回/主页/后台直接注入系统按键；关机/重启为独立按钮（原电源键已拆分）
- 后台清理：一键打开后台任务并点击“全部清除”，随后回到主页
- 屏幕保护：空闲进入宋画屏保或熄屏后，按任意遥控按键会自动亮屏、退出屏保并执行对应操作
- 状态显示依赖音乐 App 的播放通知，网易云播放时会显示歌名、歌手和播放状态

### 遥控 API

`GET /api/status` 获取状态；`POST /api/cmd?cmd=...` 执行命令：

- 媒体：`toggle`、`play`、`pause`、`next`、`prev`
- 音量：`vol_up`、`vol_down`、`vol_set`
- 显示：`wake`、`wall`（进入壁画屏保）
- 应用：`launch`（`netease` / `zhiyue` / `bili`）
- 触控：`tap`、`swipe`、`pointer`（相对移动光标）、`click`（光标处点击）
- 按键：`key`（`up|down|left|right|ok|back|home|recents|power`）
- 导航栏：`nav`（`home|back|recents|power`）
- 后台：`clean`（清理后台任务）
- 电源：`poweroff`（关机）、`reboot`（重启）

滚动命令为 `cmd=scroll&dir=up|down|left|right&count=1`，接收端会依次尝试无障碍滚动节点、屏幕滑动和 root 方向键注入。状态接口返回 `inputHelper` 表示 root 输入通道是否在线。

## 待机宋画屏保

`standby/` 是独立 Android 屏保（Dream）应用：设备空闲后自动进入全屏轮播，图片来自 `/sdcard/Pictures/Song`。设置页可调整进入屏保前的等待时间和每幅画的持续时间，也可勾选要播放的画并选择顺序或随机播放。

- 安装：`scripts/setup-standby-slideshow.ps1`
- 设置：板端“宋画屏保设置”或手机遥控

## 应用与下载

设备上使用的第三方应用 APK 收录在 `apk/`（通过 Git LFS 管理）：

- 智悦TV（TV 端 3.6.6）：`apk/zhiyue-tv-3.6.6.apk`
- Fliigo（TV 端 3.6.6.103124）：`apk/fliigo-tv-3.6.6.103124.apk`
- 网易云音乐：`apk/netease-cloud-music.apk`
- 哔哩哔哩：`apk/bilibili.apk`

下载来源：

- 智悦TV 3.6.6：https://wwbpz.lanzn.com/iO8kJ2e6z3kb ，提取码 `6er9`
- Fliigo 3.6.6.103124：https://wwbpz.lanzn.com/ij9wh2e6yylc ，提取码 `ddgb`
- 站点导航：https://dh.myg8.cc/ （星途导航，本设备优先选 TV 端）

## 脚本

- `scripts/device-info.ps1`：抓取板卡系统、声卡、显示、网络信息
- `scripts/test-audio.ps1`：推送并播放测试音频
- `scripts/setup-kiosk.ps1`：kiosk 基础设置（常亮、关闭锁屏等）
- `scripts/build-apk.ps1`：通用 APK 编译（aapt2 + javac + dx + apksigner）
- `scripts/build-standby-apk.ps1`：编译待机宋画屏保 APK
- `scripts/setup-remote-receiver.ps1`：构建、安装并启动板端遥控接收端
- `scripts/setup-standby-slideshow.ps1`：推图、安装并启用待机轮播屏保
- `scripts/push-song-images.ps1`：把宋画图片推到设备

## 验证状态（2026-08-17）

- 板端接收端已安装并常驻运行，地址 `http://192.168.1.66:8080`
- 手机端遥控 App 已安装到华为 PRA-AL00X，IP 已自动保存
- 已验证：播放/暂停、上一首/下一首、音量+/-、亮屏、快捷启动网易云/知悦/哔哩
- 触控板光标模式已实测：相对移动、光标处点击、网页触控板均可用
- 方向键与系统键已实测：B 站推荐页方向键滚动正常，返回/主页/后台/电源四个导航栏按键均可触发
- 后台清理已实测：执行 `clean` 后任务卡片清除并回到主页，接收端服务保持运行
- 一键进入壁画已实测：执行 `wall` 后进入宋画全屏轮播
