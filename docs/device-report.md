# 设备报告（2026-08-17）

## 系统

- 型号：rk3399-mid
- 版本：Android 9 / SDK 28
- 构建：rk3399_mid-userdebug 9 PQ3B.190801.002 164418 test-keys
- 平台：rk3399 / rk30board
- 序列号：K71V7BTYKP
- RK SDK：RK30_ANDROID9-SDK-v1.00.00
- 构建日期：2021-04-20

## 显示

- 1920x1080 @ 240dpi（HDMI）
- 2026-08-17：HDMI-A 连接器已识别为 connected，Android 显示服务输出 `HDMI 屏幕` 1920x1080@60
- 注意：板上有 HDMI 输入（TC358749 采集）和 HDMI 输出两个口，显示器必须接输出口

## 音频

- card 0：realtekrt5651codec_hdmiin（RT5651，3.5mm）
- card 1：ROCKCHIP_SPDIF
- card 2：rk-hdmi-dp-sound
- Android 音频策略：primary 输出支持 Speaker / Wired Headset / Wired Headphone / HDMI / SPDIF
- 当前媒体路由：speaker

## 网络

- eth0：DOWN
- wlan0：UP，已连接 `CMCC-u5sh-5G`（5GHz），IP 192.168.1.66/24
- 网关/DNS：192.168.1.1，外网 ping 223.5.5.5 正常

## 遥控接收端

- 已安装 `com.zysj.speaker.remote`，HTTP 遥控服务 `http://192.168.1.66:8080`
- 通知监听已授权，开机自动启动；音量、亮屏、媒体键控制已验证

## 预装应用

- com.android.music
- com.android.musicfx
- android.rk.RockVideoPlayer
- 第三方应用：知悦TV（zhiyue.go.fmzonghe.hecheng）、网易云音乐（com.netease.cloudmusic）、哔哩哔哩（tv.danmaku.bili）、待机宋画屏保（com.zysj.standby）

## 手机遥控验证（2026-08-17）

- 遥控手机：华为 PRA-AL00X（Android 8.0），安装 `apk/remote-phone.apk`
- 板端与手机同一局域网（192.168.1.x），已实测播放/暂停、音量、上一首/下一首、亮屏、App 快捷启动
- 手机自动发现：UDP 广播 + /24 网段 HTTP 扫描兜底，清空 App 数据后约 3 秒内自动找到 `192.168.1.66`
- 当前板端 IP：192.168.1.66；板端接收服务随开机启动
