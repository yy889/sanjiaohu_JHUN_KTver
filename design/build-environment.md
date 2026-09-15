# 构建环境记录（已刷新）

刷新时间：本次会话。状态：**环境已就绪，项目可完整构建，全部回归测试通过。**

## 一、环境已从"不可构建"变为"完全就绪"

上一次扫描时以下组件全部缺失，本次刷新后**均已安装**：

| 组件 | 上次 | 本次 | 路径 |
|---|---|---|---|
| Android SDK | ❌ 不存在 | ✅ | `C:\Users\shaoh\AppData\Local\Android\Sdk` |
| Android Studio | ❌ 不存在 | ✅ | `C:\Program Files\Android\Android Studio` |
| `adb` | ❌ NOT FOUND | ✅ | `<Sdk>\platform-tools\adb.exe`（v1.0.41） |

`ANDROID_HOME` / `ANDROID_SDK_ROOT` 仍为空，但 `build-local.ps1` 的默认参数直接指向
`$env:LOCALAPPDATA/Android/Sdk`，因此**无需设置环境变量即可构建**。

## 二、build-local.ps1 所需的精确组件（全部已就位）

| 需求 | 路径 | 状态 |
|---|---|---|
| Platform `android-36.1` | `<Sdk>\platforms\android-36.1\android.jar` | ✅ |
| `aapt2.exe` | `<Sdk>\build-tools\36.1.0\aapt2.exe` | ✅ |
| `core-lambda-stubs.jar` | `<Sdk>\build-tools\36.1.0\core-lambda-stubs.jar` | ✅ |
| `lib\d8.jar` | `<Sdk>\build-tools\36.1.0\lib\d8.jar` | ✅ |
| `lib\apksigner.jar` | `<Sdk>\build-tools\36.1.0\lib\apksigner.jar` | ✅ |
| `zipalign.exe` | `<Sdk>\build-tools\36.1.0\zipalign.exe` | ✅ |
| JDK（脚本默认） | `C:\Program Files\Android\Android Studio\jbr` | ✅ JBR **25.0.2** |

已安装 platform：`android-36.1`、`android-37.0`。已安装 build-tools：`36.0.0`、`36.1.0`。
**注意**：脚本硬要求 `36.1` 平台与 `36.1.0` 构建工具，二者不可用 `36.0.0` 或 `android-37.0` 替代。

### 更正上一条结论

上次我警告"JDK 21+ 已移除 `-source 8`/-target 8 支持，用 JDK 25 必然失败"——**该判断不成立**。
已实测 JBR 25.0.2 的 `javac` 接受 `-source 8 -target 8`，退出码 0，编译正常。
该警告作废，无需另装 JDK 17/21。

## 三、本次修复的两个真实缺陷

刷新后发现项目**在这台机器上原本就无法构建**，根因是 `build-local.ps1` 在
Windows PowerShell 5.1 下的两个缺陷（与 SDK 无关，属脚本自身问题）：

### 缺陷 1：清单文件编码损坏（致命）

原第 23-24 行：

```powershell
$manifestText = Get-Content -LiteralPath "$app/AndroidManifest.xml" -Raw
$manifestText.Replace(...) | Set-Content -LiteralPath "$Work/AndroidManifest.xml" -Encoding utf8
```

在 **PowerShell 5.1** 下，`Get-Content`/`Set-Content` 会经过 ANSI 代码页往返，
把非 ASCII 的应用名 `三角狐` 转成乱码 `涓夎鐙?` —— 其中的 `?` 替换掉一个字节，
**吃掉了闭合引号**，导致 `aapt2` 报 `not well-formed (invalid token)`：

```
<application android:label="涓夎鐙? android:icon="@mipmap/ic_launcher" ...
                                    ↑ 引号丢失，XML 结构被破坏
```

注意 **`app/src/main/AndroidManifest.xml` 源文件本身是正确的**（UTF-8，无 BOM，`三角狐` 完好）；
损坏只发生在构建中间产物 `.buildwork/AndroidManifest.xml`。

修法：改用 `[System.IO.File]::ReadAllText/WriteAllText` + 显式 `UTF8Encoding($false)`，
不经过 PowerShell 的编码转换。

### 缺陷 2：stderr 输出被误判为构建失败（阻断后续所有步骤）

脚本开头设 `$ErrorActionPreference = 'Stop'`。PowerShell 5.1 把**原生程序写到 stderr 的
任何内容**（即使只是提示信息）包装成 `NativeCommandError` 并立即终止脚本，导致：

- `javac` 的 deprecation 提示（"某些输入文件使用或覆盖了已过时的 API"）→ 脚本中断；
  但实测 `javac` **退出码为 0，类文件正常产出**。
- `keytool` 生成密钥库时的正常提示 → 脚本中断。
- `apksigner` 在 JDK 25 下的 `restricted method` 警告 → 脚本中断。

修法：新增 `Invoke-Tool` 辅助函数，在调用原生程序时**临时**把
`$ErrorActionPreference` 降为 `Continue`，把输出逐行透传，再恢复原值；
**成败仍以 `Check` 检查退出码为准**，不依赖 stderr。同时保留一个已设的全局 `Continue`。

## 四、构建结果（已验证）

```
.\build-local.ps1 -Work "$PWD\.buildwork" -Apk "$PWD\Sanjiaohu-1.0.1.apk"
```

> 说明：脚本默认 `-Work "$PSScriptRoot/../../work/android-build"` 会解析到工作区之外的
> `D:\work`，在当前文件沙箱下被拒绝。用脚本自带的 `-Work` 参数改到工作区内即可，
> **无需提权，脚本本身也没有问题**。

产出 `Sanjiaohu-1.0.1.apk`（2,467,501 字节）核验：

| 项 | 值 |
|---|---|
| package | `cn.jhun.sanjiaohu` |
| versionCode / versionName | 31 / 1.0.1 |
| compileSdk / minSdk / targetSdk | 36 / 26 / 35 |
| application-label | **`三角狐`** ✅（编码修复生效） |
| launchable-activity | `cn.jhun.sanjiaohu.MainActivity` |
| 签名 | v2 ✅ / v3 ✅（v1、v3.1、v4 为 false，与项目一贯行为一致） |
| SHA-256 | `170F777F2A202E8E9E03C793C9D3F16A3923E75092B3661460B57A5D91B4E133` |

**签名密钥**：`.buildwork/development.keystore`（本次新建，因为工作目录换到了工作区内）。
⚠️ README 指明"签名密钥沿用原构建中间目录的 development.keystore，不放在源码包内"。
本次用的是**新生成的密钥**，因此这个 APK **无法覆盖安装**此前用旧密钥签名的版本。
若要发布，必须用原有密钥，或把它放到 `-Work` 指定的目录中。

## 五、回归测试（全部通过）

Java（裸 `javac` + `java`，无 JUnit）：

| 套件 | 结果 |
|---|---|
| CourseTest | PASS: 20 parser assertions |
| RevisionTest | PASS: 12544 upgrade and viewport checks (60 viewport sizes) |
| TermTest | 10 semester normalization checks passed |
| MealGridTest | 13152 meal separator and time gutter checks passed |
| AdaptiveTextTest | 6060 sampled-background text checks passed |
| ThemeTest | PASS: 1007 palettes, ... |
| CalendarViewportTest | 116 checks passed |
| CustomCoursesTest | PASS: 60 custom course isolation, ... |

Node：`identity-document`(20)、`identity-login`(156)、`identity-session`(9)、
`login`、`login-state`、`portal`(50) —— **6/6 通过**。

## 六、仍未验证 / 待办

- **未连接安卓设备**（`adb` 已可用，但 `adb devices` 未检查，本次未做真机验收）。
  按项目一贯原则：编译通过 + 回归测试通过 **不等于** 真机行为正确。
- 未做真机安装、登录、WebView 端到端验收。
- `-Work` 默认路径在沙箱外，属环境限制，非脚本缺陷；如长期在本机开发，
  可考虑把默认 `-Work` 改到工作区内。
- README 第 305 行仍写"版本号 23 / 版本名 1.6.0"，与顶部的 31 / 1.0.1 不一致（遗留文字）。

## 七、对 Kotlin 迁移结论的影响

环境就绪**不改变**此前的 Kotlin 迁移判断。两个 P0 阻断项依然存在，且现在已被实测确认：

1. `build-local.ps1` 用 `javac` 且只收集 `-Filter '*.java'`（第 41 行）——
   `.kt` 文件不会被编译，需引入 `kotlinc` 并解决 Java/Kotlin 联合编译顺序。
2. 测试网是**裸 `javac` 直接编译源码文件**再 `java` 运行（本次即以此方式跑通 8 个套件）。
   一旦 `Course.java` 等改为 `.kt`，这些命令立即失效，上千条回归断言将无法运行——
   而这套安全网是项目在无真机条件下持续迭代的唯一依据。

结论不变：**不建议整体迁移**；优先重构 `MainActivity`（508 行上帝对象 + 13 个
boolean 旗标），那才是真正的技术债，且与语言无关。
