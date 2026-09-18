# 三角狐 项目工作交接（可直接粘贴到新对话继续）

## 0. 一句话现状

江汉大学课表 App「三角狐」，代码已从 1.0.1 升到 **y7c_0.1 (versionCode 32)**，做了
**UI 工具类提取重构（阶段 1）**，已通过 **PR #2 合并进 `main`**，**CI 已在 GitHub runner 上跑通**。
当前**无阻塞性代码问题**；剩下的都是收尾、验证与后续重构。

---

## 1. 项目基本信息

| 项 | 值 |
|---|---|
| 名称 | 三角狐（Sanjiaohu），江汉大学个人课表 |
| 本地路径 | `D:\dsh_prj\sanjiaohu_JHUN_KTver` |
| 远端 | `https://github.com/yy889/sanjiaohu_JHUN_KTver` |
| 语言 | Java（Android），37 个类、约 2058 行 |
| 包名 | `cn.jhun.sanjiaohu` |
| 版本 | versionName `y7c_0.1`，versionCode **32** |
| SDK | minSdk 26 / targetSdk 35 / compileSdk 36 |
| 构建 | **无 Gradle Wrapper**（项目刻意不带），靠 `build-local.ps1` 手写链路 |
| 测试 | 根目录 `tests/`：Java main 方法 + Node `.test.cjs`，**未接进 Gradle** |
| 布局 | **无 `res/layout`**，界面全部用 Java 代码手写构建 |

---

## 2. 环境事实（已实测，务必遵守）

| 资源 | 路径 / 状态 |
|---|---|
| Android SDK | `%LOCALAPPDATA%\Android\Sdk`（**无** `ANDROID_HOME` 环境变量，但 `build-local.ps1` 默认路径正好指向它） |
| 已装 platform | `android-36.1`、`android-37.0` |
| 已装 build-tools | `36.0.0`、`36.1.0`（脚本硬要求 **`36.1.0`**） |
| JDK（脚本默认） | `C:\Program Files\Android\Android Studio\jbr`，**JDK 25.0.2** |
| JDK 22 | `D:\jdk22`（**Kotlin 编译器必须用它**，见第 7 节） |
| Gradle CLI | `D:\gradle-9.7.0`，**已损坏**（`native-platform.dll` 加载失败），**别用** |
| adb | `<Sdk>\platform-tools\adb.exe` 可用；**但无连接设备、无 AVD、无 system-image** |
| GitHub 凭据 | **完全没有**。无 token、无 `gh` CLI、无 `.git-credentials`、凭据管理器为空 |

### ⚠️ 两个必须记住的环境坑

**坑 1：schannel TLS 损坏。** 一切 git 远端操作必须加 `-c http.sslBackend=openssl`：

```powershell
git -c http.sslBackend=openssl fetch origin
git -c http.sslBackend=openssl push -u origin <branch>
```

不加会报 `schannel: AcquireCredentialsHandle failed: SEC_E_NO_CREDENTIALS`。
（PowerShell 的 `Invoke-WebRequest`、`curl.exe` 同样受影响；**Node 自带 TLS，可用**，
所以查 GitHub API 用 `node -e "https.get(...)"` 可行。）

**坑 2：文件沙箱。** 工作区是 `D:\dsh_prj\sanjiaohu_JHUN_KTver`，只能写工作区内。
`build-local.ps1` 默认 `-Work` 会解析到工作区**外**的 `D:\work` → 被拒绝。
**解决：显式传 `-Work` 到工作区内**（脚本本身没毛病，不要改它的默认值）：

```powershell
.\build-local.ps1 -Work "$PWD\.buildwork" -Apk "$PWD\.buildwork\out.apk"
```

---

## 3. 构建与测试命令（照抄可用）

### 构建

```powershell
cd D:\dsh_prj\sanjiaohu_JHUN_KTver
.\build-local.ps1 -Work "$PWD\.buildwork" -Apk "$PWD\.buildwork\Sanjiaohu-y7c_0.1.apk"
```

成功标志：`Verified using v2 scheme: true` / `v3 scheme: true`，末尾打印 SHA-256。
产出 APK 的 `application-label` 应为 **`三角狐`**（若变成乱码说明清单编码又被破坏，见第 5 节）。

### 测试（Java，14 个套件里的 8 个 + 4 个额外）

```powershell
$jbr = "C:\Program Files\Android\Android Studio\jbr"
$src = "app\src\main\java\cn\jhun\sanjiaohu"
New-Item -ItemType Directory -Force -Path .testclasses | Out-Null

javac -encoding UTF-8 -d .testclasses "$src\Course.java" "$src\CachePolicy.java" `
  "$src\GridGeometry.java" "$src\Term.java" "$src\CourseAppearance.java" `
  "$src\CourseColors.java" "$src\ThemePalette.java" "$src\CalendarViewport.java" `
  "$src\CustomCourses.java" tests\CourseTest.java tests\RevisionTest.java `
  tests\TermTest.java tests\MealGridTest.java tests\AdaptiveTextTest.java `
  tests\ThemeTest.java tests\CalendarViewportTest.java tests\CustomCoursesTest.java

foreach ($t in 'CourseTest','RevisionTest','TermTest','MealGridTest','AdaptiveTextTest',
               'ThemeTest','CalendarViewportTest','CustomCoursesTest') {
  & "$jbr\bin\java.exe" -cp .testclasses "cn.jhun.sanjiaohu.$t"
}
```

**另 4 个套件（CI 里漏了，但能跑，实测通过）**：

```powershell
# 3 个 identity 套件
javac -encoding UTF-8 -d .testclasses "$src\IdentityPolicy.java" `
  "$src\IdentityNavigation.java" "$src\IdentityDiagnostics.java" `
  tests\IdentityPolicyTest.java tests\IdentityNavigationTest.java tests\IdentityDiagnosticsTest.java
java -cp .testclasses cn.jhun.sanjiaohu.IdentityPolicyTest        # 47 checks
java -cp .testclasses cn.jhun.sanjiaohu.IdentityNavigationTest    # 59 checks
java -cp .testclasses cn.jhun.sanjiaohu.IdentityDiagnosticsTest   # 31 checks

# SessionCookiesTest 需要 cookie-stubs
javac -encoding UTF-8 -d .testclasses "$src\SessionCookies.java" `
  tests\cookie-stubs\android\webkit\CookieManager.java `
  tests\cookie-stubs\android\webkit\WebStorage.java `
  tests\cookie-stubs\SessionCookiesTest.java
java -cp .testclasses cn.jhun.sanjiaohu.SessionCookiesTest        # 202 checks
```

（`tests/android/GridInstrumentation.java` 需要真机，合理排除。）

### Node 测试

```powershell
foreach ($f in Get-ChildItem tests\*.test.cjs) { node $f.FullName }
```
6 个：`identity-document`(20)、`identity-login`(156)、`identity-session`(9)、
`login`、`login-state`、`portal`(50)。

### 基线（改动前先跑一遍对照）

| 套件 | 断言数 |
|---|---|
| CourseTest | 20 |
| RevisionTest | 12544 |
| TermTest | 10 |
| MealGridTest | 13152 |
| AdaptiveTextTest | 6060 |
| ThemeTest | 1007 |
| CalendarViewportTest | 116 |
| CustomCoursesTest | 60 |

---

## 4. 已完成的工作

### ① 修好了 `build-local.ps1` 的两个真实缺陷（此前项目在本机根本无法构建）

**缺陷 1 — 清单编码损坏（致命）。** 原脚本用 `Get-Content` / `Set-Content -Encoding utf8`
处理 `AndroidManifest.xml`，在 **Windows PowerShell 5.1** 下会经过 ANSI 代码页往返，
把应用名 `三角狐` 变成乱码 `涓夎鐙?`，其中 `?` 吃掉闭合引号 → `aapt2` 报
`not well-formed (invalid token)`。**源文件本身是正确的，损坏只发生在中间产物。**
已改为 `[System.IO.File]::ReadAllText/WriteAllText` + 显式 `UTF8Encoding($false)`。

**缺陷 2 — stderr 被误判为失败。** 脚本开头 `$ErrorActionPreference='Stop'`，
PS 5.1 会把原生程序写到 stderr 的**任何提示**当致命错误终止脚本（`javac` 的
deprecation 提示、`keytool` 的正常提示、`apksigner` 在 JDK 25 下的警告）。
已新增 `Invoke-Tool` 辅助函数降级透传输出，**成败仍以退出码为准**。

### ② 阶段 1 重构：提取共享 UI 工具类（已合并）

**问题**：7 个类各自复制了同一套视图工具方法 —— `dp()` 重复 **7 份**、`shape()` **5 份**、
`column()` **4 份**、`row()` **3 份**。

**做法**：新建 `Ui.java`（48 行）承载 7 个**无状态**方法
`dp / column / row / weighted / space / place / shape`；
各调用类**保留同名方法、内部委托**给 `Ui`：

```java
int dp(float x){return Ui.dp(this,x);}
GradientDrawable shape(int c,int radius){return Ui.shape(this,c,radius);}
```

**这样做的价值**：`MainActivity` 被另外 5 个类持有（`CalibrationSheet`、`CourseEditor`、
`ThemeColorSheet`、`CourseDetailSheet`、`UiSheet`），它们直接调 `a.dp()` / `a.shape()`；
保留同名方法后**这些调用点一行都不用改**。

**两个刻意保留的行为（关键，别改坏）**：
1. `LoginActivity.shape(int color)` 保留**单参数**形式，内部固定传 `16`
   （原本圆角硬编码 16，与其他类的双参数 `shape(color, radius)` 不同）。
   若误统一，登录页输入框和「登 录」按钮会**静默变成直角**——编译和测试都发现不了。
2. `Ui.dp()` 保留原始舍入 `density*value + 0.5f` 后截断（`f2i`）。
   **不要改成 `Math.round()`** —— 它对负数舍入方向相反，会导致全局布局偏移。
   已用 `javap -c` 核验字节码为 `fmul / ldc 0.5f / fadd / f2i`。

**验证**：构建成功 + 8 个 Java 套件 + 6 个 Node 套件**全绿**。

### ③ 版本号改为 `y7c_0.1`

改了 4 处：`AndroidManifest.xml`、`app/build.gradle`（都设为 versionCode **32** /
versionName `y7c_0.1`）、`AboutActivity.java` 的回退默认值、`build-local.ps1` 的输出文件名。
（versionCode 必须递增，沿用 31 会被 Android 拒绝覆盖升级。）

### ④ 新增 `.gitignore` + GitHub Actions CI

`.gitignore` 覆盖 `*.apk`、`*.idsig`、`*.class`、`*.dex`、`*.jar`、`*.keystore`、
`.buildwork/`、`.testclasses/`、`build/`、`.gradle/`。
**已按用户要求，APK 不进仓库。**

CI 文件 `.github/workflows/gradle.yml`，**已在 runner 上跑通（全部 12 步 PASS）**：

```
RUN: Java CI with Gradle | 6bf3ba8 | success
```

两个关键设计（因为项目特殊性）：
- **无 Gradle Wrapper** → 用 `gradle/actions/setup-gradle@v4` 提供 Gradle **8.13**
  （AGP 8.13.0 的最低要求）；不能用 `./gradlew`
- **测试没接进 Gradle** → 用 `javac`/`java` 直接跑（`gradle test` 什么都跑不到）
- JDK 17（AGP 8.13 要求 17+）、`platforms;android-36`、`build-tools;36.0.0`

### ⑤ 文档产出（都在 `design/`）

| 文件 | 内容 |
|---|---|
| `design/build-environment.md` | 环境与构建链修复记录 |
| `design/mainactivity-refactor-plan.md` | 三阶段重构方案（含阶段 1 实施记录） |
| `design/kotlin-migration-analysis.md` | Kotlin 迁移可行性分析（**结论已被后续实验部分修正**） |
| `design/kotlin-equivalents-verified.md` | Kotlin 等效写法**实测**结果（较新，优先看） |
| `design/kotlin-examples/` | `Course.kt`、`Ui.kt`、`kotlinc-embed.ps1` |
| `design/device-verification-y7c_0.1.md` | 真机验证清单（**尚未执行**） |

---

## 5. 代码结构要点（重构时必知）

**`MainActivity` 不是普通的"上帝对象"，它是全 App 的共享基类。** 它同时扮演三个角色：

1. Activity 本身（生命周期、三页面渲染、课表网格）
2. **全 App 的 UI 工具库**（被 5 个类通过 `MainActivity a` 引用调用）
3. **全局可变状态容器**（6 个主题色字段 + 11 个认证字段）

**角色 2、3 才是它臃肿的根因，也是角色 1 的地基 → 直接拆角色 1 必然失败。**

其他量化事实：
- 单文件 `MainActivity` 508 行 = 全部代码 25%；三个 Activity 合计 43%
- 主题色字段（`BG/INK/MUTED/PRIMARY/ACCENT_TEXT/ON_PRIMARY`）被用在**约 90 处**
- 仅 `MainActivity` 就有 **13 个 boolean 旗标**
- 认证状态机（`sync`/`cancelSync`/`tryAutoLogin`/`pollLogin`/`startPoll`/`poll`/`fail`/
  `showLogin`/`clearCredentials`）共触及 **11 个字段**，且与 UI **双向耦合**
  （`setState()` 写 View、`authStatus()` 读 flags）

**纯逻辑类（不依赖 Android，最容易迁移）**：`Course`、`Term`、`GridGeometry`、
`CourseColors`、`CourseAppearance`、`ThemePalette`、`CachePolicy`。

---

## 6. Git / 仓库现状

| 项 | 状态 |
|---|---|
| `origin/main` | `6bf3ba8`（Create gradle.yml）—— **无新变动** |
| 已合并 PR | #1、#2（#2 = 我的 Ui 重构 + 版本号） |
| CI 运行 | 仅 1 次，success |
| 我的提交 `c3bc4d3` | ✅ 已在 `origin/main` |
| 我的提交 `28c6e0d` | ⚠️ **冗余，未推送，建议丢弃**（见下） |
| 本地 `main` | 落后远端 **9 个提交** |
| 本地分支 | `main`、`KTver0.1`、`PRktv` 都停在 `121b816`；`feat/ui-shared-helper-y7c_0.1` 在 `28c6e0d` |
| 远端 tag | `Dev` → `6bf3ba8` |

### ⚠️ `28c6e0d` 为什么该丢

远端 `main` 已有 gradle.yml（`6bf3ba8`，你在 GitHub 网页创建，**比我本地版本多了 7 行
GitHub 模板注释头**）。我本地 commit 没有那 7 行，**若推送会删掉它们** —— 无意义改动。

### ⚠️ "51 个文件改动"是假象

`git diff origin/main` 会显示 51 个文件、1344 增 / 1344 删。**这是 CRLF vs LF 噪音**，
不是真实改动。系统级 `core.autocrlf = true`，仓库存 LF、检出为 CRLF。
用 `git diff origin/main --ignore-cr-at-eol` 才能看到真实差异（**只有 gradle.yml 那 1 个文件**）。

### ⚠️ 上游仍跟踪 11 个构建垃圾文件

`.gitignore` 只防**新增**，**不能移除已跟踪的**。上游 `main` 仍跟踪：

```
.gradle/9.2.0/checksums/checksums.lock
.gradle/9.2.0/checksums/md5-checksums.bin
.gradle/9.2.0/checksums/sha1-checksums.bin
.gradle/9.2.0/fileChanges/last-build.bin
.gradle/9.2.0/fileHashes/fileHashes.bin
.gradle/9.2.0/fileHashes/fileHashes.lock
.gradle/9.2.0/gc.properties
.gradle/buildOutputCleanup/buildOutputCleanup.lock
.gradle/buildOutputCleanup/cache.properties
.gradle/vcs-1/gc.properties
build/reports/problems/problems-report.html
```

需 `git rm --cached` 才能真正移除。**上游已无 APK（删除成功）✅。**

---

## 7. Kotlin 迁移：实测结论（前人分析曾被修正，以此节为准）

### 可用工具链

本机**没有** `kotlinc` 命令，但 **Gradle 缓存里就有 Kotlin 2.0.21 编译器**。
已封装脚本：`design/kotlin-examples/kotlinc-embed.ps1`（已验证端到端可用）。

**必须额外挂 7 个 jar**，缺一个就报错：

| jar | 缺失报错 |
|---|---|
| `kotlin-stdlib` | `NoClassDefFoundError: kotlin/jvm/internal/Intrinsics` |
| `kotlin-daemon-embeddable` | `NoClassDefFoundError: kotlinx/coroutines/CoroutineScope` |
| `kotlinx-coroutines-core-jvm` | 同上 |
| `trove4j` | `NoClassDefFoundError: gnu/trove/TObjectHashingStrategy` |
| `kotlin-script-runtime` | 编译器初始化报错 |
| `annotations` | `NoClassDefFoundError: org/jetbrains/annotations/NotNull` |

**⚠️ 必须用 JDK 22 运行编译器**，不能用 JDK 25 —— Kotlin 2.0.21 解析不了 JDK 25 的版本串：
`IllegalArgumentException: 25.0.2`。

### 实测验证结果

| 验证 | 结果 |
|---|---|
| `Course.java` → `Course.kt`，用**原封不动的** `tests/CourseTest.java` 跑 | ✅ `PASS: 20 parser assertions` |
| `Ui.java` → `Ui.kt`（用 android.jar 编译） | ✅ 编译通过，Java 调用方零改动 |
| Kotlin ↔ Java 双向 import | ✅ 跑通（Java→Kotlin→Java round trip 正常） |

**重要修正**：此前的分析把"测试网失效"列为 P0 阻断项，说"上千条断言将无法运行"——
**该判断已被推翻**。Java 测试代码**一字不改**即可测 Kotlin 类，只需在 `javac` 前加一次
`kotlinc`。安全网保得住。

### 迁移时必加的两个注解

现有 Java **直接读字段**（`a.name`、`a.weeks`），而 Kotlin 属性默认只生成 getter：

| 注解 | 不加的后果 |
|---|---|
| `@JvmField` | Java 的 `c.name`、`c.weeks` 全部编译失败 |
| `@JvmStatic` | Java 的 `Course.parse(...)`、`Ui.dp(...)` 全部失败（会变成 `Companion.parse()`） |

### Kotlin 陷阱（实测撞到）

**没有 int → float 隐式提升**（Java 有）：

```java
new LinearLayout.LayoutParams(0, dp(44), 1)      // Java 合法
LinearLayout.LayoutParams(0, dp(c, 44f), 1)      // Kotlin 编译错误
LinearLayout.LayoutParams(0, dp(c, 44f), 1f)     // Kotlin 正确
```

本项目大量使用带 weight 的 `LayoutParams`，需逐处检查。好消息：**是编译期错误，不会静默出错。**

### 仍未验证 / 不建议动

- 只验证了 `Course` 和 `Ui` **两个类**，其余 35 个类**未验证**
- `MainActivity`（508 行 + WebView 状态机）**完全未做 Kotlin 验证**
- 完整 APK 打包（Gradle 加 Kotlin 插件）**未做**
- **认证状态机不建议迁移** —— 那是 README 记载 1.6.1~1.6.7 **修了 7 个版本**才收敛的代码

---

## 8. 待办清单（按推荐顺序）

### A. 收尾修复（低风险、价值高）

1. **补 CI 覆盖**：CI 现在漏跑 4 个套件（**339 项断言**）——
   `IdentityPolicyTest`(47)、`IdentityNavigationTest`(59)、`IdentityDiagnosticsTest`(31)、
   `SessionCookiesTest`(202)。**这四个正是统一认证 URL 策略、导航防循环、诊断隐私等最敏感的逻辑。**
   已实测这 4 个都能跑通，补进 workflow 即可。
2. **修 README**：上游第一行仍是 `# 三角狐 v1.0.1`（实际 `y7c_0.1`）；
   第 305 行仍是 `- APK 版本号 23 / 版本名 1.6.0，...`（与顶部自相矛盾）。
3. **清理上游 11 个已跟踪的垃圾文件**（`git rm --cached`）。
4. **丢弃冗余提交 `28c6e0d`**，把 `feat/ui-shared-helper-y7c_0.1` 重置到 `origin/main`。

### B. 待用户决策

5. **关于页版本号显示问题**：`AboutActivity` L40 逻辑是"版本名不以 `v` 开头就补 `v`"，
   而 `y7c_0.1` 不以 v 开头 → 界面显示 **`vy7c_0.1`**（多一个 v）。
   需决定：保持 `vy7c_0.1`，还是改逻辑显示 `y7c_0.1`。

### C. 需要设备

6. **真机验证**：阶段 1 重构**尚未做任何真机视觉确认**。
   重点检查 `design/device-verification-y7c_0.1.md` 第 4.2 节的**圆角项**
   （尤其登录页，见第 4·② 节的陷阱 1）。
   ⚠️ **本机无设备、无 AVD、无 system-image**，需装 system-image（约 1.5GB）或接入手机。
   ⚠️ 当前 APK 用**新生成的密钥**签名，**无法覆盖安装**旧版；
   强行安装需先卸载 → **会清除课表/成绩/自定义课程/背景/登录凭证**。

### D. 后续重构（阶段 2、3）

7. **阶段 2**：收敛 6 个主题色字段（约 90 处调用点）—— 风险明显高于阶段 1。
8. **阶段 3**：拆 `SyncController` —— **风险最高**，见第 7 节末。

---

## 9. 工作纪律（这个项目的要求）

这个项目的 README 有很强的"**不声称未验证结论**"传统：几乎每条版本记录都明确列出
"因为没连真机所以未验证"的内容，绝不把代码回归测试等同于真机验收。**继续遵守。**

具体到当前：
- 阶段 1 的准确表述是"**消除了 7 份重复实现且逐字保留原有数值语义**"，
  **不是**"已验证界面一致"——现有测试**没有任何布局像素断言**，覆盖不到视觉结果。
- CI 已跑通是**事实**；但真机行为、WebView 登录、Keystore 读写**仍未验证**。
- 推送前先跑基线（第 3 节），改完再跑一次对照。

---

## 10. 立刻可执行的第一条命令

```powershell
cd D:\dsh_prj\sanjiaohu_JHUN_KTver
git -c http.sslBackend=openssl fetch origin
git status
.\build-local.ps1 -Work "$PWD\.buildwork" -Apk "$PWD\.buildwork\base.apk"   # 建立基线
```
