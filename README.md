# E-Ink Nav

把一台 Android 墨水屏设备变成车把上的**导航副屏**：手机端抓取画面或用 GPS 采集骑行数据，
通过蓝牙推送到墨水屏端全屏显示。纯黑白、无动画、内容不变不重绘，专为电子墨水设计。

Turn an Android e-ink reader into a handlebar second screen: the phone mirrors a captured
app window (or streams GPS riding data) to the e-ink device over Bluetooth.

## 特性

- **截图镜像模式**：`MediaProjection` 单应用捕获，虚拟屏按 480×800 原生渲染（可 1.5×/2.0× 超采样），
  自动裁掉应用留下的白边/黑边，自动识别横竖屏并让墨水屏旋转画布
- **GPS 骑行数据模式**：速度（自适应字号）/ 海拔 / 方向 / 罗盘 / 精度，无需截图也无需导航
- **文字导航模式**（早期实现，保留）：手机解析导航信息推 JSON，墨水屏手绘箭头+大字
- **蓝牙 RFCOMM（SPP）免配对直连**，自动重连（唯一传输通道，无任何网络权限）
- 墨水屏友好：只在内容变化时重绘，图片帧做字节级去重

## 架构

```
┌──────────────── 手机 (Android) ────────────────┐      ┌──── 墨水屏设备 ────┐
│ MediaProjection 单应用捕获 → 虚拟屏 480×800@200dpi │      │                    │
│   ↓ 裁剪白边 / 横竖识别 / 灰度 / JPEG               │      │  BtServer (SPP)   │
│ Mirror ──┐                                        │ 蓝牙  │   ↓                │
│ GPS 采集 ─┼→ Sender ── 蓝牙 RFCOMM (SPP) ─────────┼─────→│ Frames.loop        │
│ 文字解析 ─┘                                        │      │   ↓                │
└──────────────────────────────────────────────────┘      │ HudView 全屏绘制    │
                                                           └────────────────────┘
```

## 目录

| 路径 | 说明 |
|---|---|
| `reader/` | 墨水屏端 App（`com.ebike.hud.reader`） |
| `phone/` | 手机端 App（`com.ebike.hud.phone`） |
| `tools/build.ps1` | 免 Gradle 打包脚本（aapt2 + javac + d8 + apksigner） |
| `tools/send-json.ps1` | 电脑通过蓝牙推 JSON 帧（调试用） |
| `tools/send-frame.ps1` | 电脑通过蓝牙推单帧 JPEG + 旋转指令 |
| `tools/mktest.ps1` | 生成带方向标记的测试图 |
| `docs/PROTOCOL.md` | 帧协议说明 |

## 编译

依赖：**JDK 17** + **Android SDK**（platform 30 与任一 build-tools，无需 Gradle）。
脚本同时兼容 SDK Manager 目录结构（`platforms/android-30`）和官方 zip 解包结构（`android-11`）。

```powershell
# SDK 路径可通过参数或环境变量 ANDROID_SDK_ROOT 指定
powershell -ExecutionPolicy Bypass -File tools\build.ps1 -App all -Sdk C:\Android\Sdk
```

产物在 `.build\<app>\<app>.apk`。默认会在 `.build\debug.keystore` 生成调试密钥；
若需沿用已有签名（例如升级已安装的版本），可指定自己的密钥：

```powershell
powershell -ExecutionPolicy Bypass -File tools\build.ps1 -App all `
    -KeyStore C:\keys\my.keystore -KeyAlias myalias -StorePass 你的密码
```

> 注意：APK 签名不一致无法覆盖安装（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`），
> 升级旧版本时必须用与旧版本相同的密钥签名。

## 安装与使用

### 墨水屏端
```powershell
adb -s <墨水屏序列号> install -r .build\reader\reader.apk
```
打开 App，屏幕会显示蓝牙适配器名字与自身 MAC（部分系统不允许应用读取 MAC 时会提示在手机端填写）；设备会自动改名进入可发现模式。

### 手机端
```powershell
adb -s <手机序列号> install -r .build\phone\phone.apk
```

1. 打开手机的蓝牙（`BLUETOOTH_CONNECT/SCAN` 为运行时权限，首次会弹窗）
2. 填电纸书蓝牙 MAC（可在墨水屏设备上执行 `adb shell settings get secure bluetooth_address` 获取）
3. 任选一种模式：
   - **★ 开启截图模式** → 系统投屏授权里选「**单个应用**」→ 目标应用（如高德地图）
   - **★ 发送骑行数据** → 授予定位权限，户外等搜星（室内无信号是正常的）

### 常用参数

| 参数 | 推荐 | 说明 |
|---|---|---|
| 分辨率 | 原生 | 让应用按虚拟屏尺寸排版，而不是缩放 |
| 虚拟屏倍数 | 1.5× | 720×1200 超采样后再降到 480×800，字更锐利 |
| 原生 DPI | 200 | 越高字越大，界面越紧凑 |
| 白边 | 自动裁掉并铺满 | 按行/列亮度起伏识别边框，纯白/纯黑都能裁 |
| 横屏 | 电纸书切横版显示 | 内容为横向时让墨水屏旋转画布；画面倒置就切 90°/270° |

## 兼容性与已知问题

- 在 **XTEINK S4**（Android 11 / RK3326 / 480×800 单色）上验证通过；其他 Android 墨水屏设备理论可用，需自行调整输出分辨率
- 部分国产 ROM（如 ColorOS）额外强制检查 `BLUETOOTH_SCAN`、要求 `MediaProjection` 必须运行在
  `foregroundServiceType="mediaProjection"` 的前台服务里，代码里已处理
- 单应用捕获时：应用不在前台会暂停捕获（画面冻结在最后一帧）；手机息屏仍可继续
- 墨水屏全刷约 0.3–0.5 s，帧间隔建议 ≥1.5 s
- 每次重新安装或被系统回收后需重新授权投屏
- 无障碍/通知监听通道已移除：高德等应用的导航通知只含「正在导航」，无法提供转向信息，
  也正是本项目最终选择**截图镜像**的原因

## 协议

见 [`docs/PROTOCOL.md`](docs/PROTOCOL.md)。

## 许可

GNU General Public License v3.0，见 [LICENSE](LICENSE)。

## 声明

本项目与高德（AutoNavi）、XTEINK 及任何设备厂商均无关联；相关名称仅为说明用途。
抓屏内容仅在本机与配对的墨水屏设备之间传输，不经网络上传。
