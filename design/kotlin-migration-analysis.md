# 三角狐 Kotlin 迁移可行性分析

分析对象：`D:\dsh_prj\sanjiaohu_JHUN_KTver`，版本 v1.0.1（versionCode 31）。
本报告只做代码结构与工程约束分析，**未修改任何源码，也未执行构建**。

## 结论（先说结果）

**技术上可以迁移，但不是一次"值得做"的重写，而且当前工程状态下不能直接迁移。**

- 代码规模很小（37 个文件 / 2058 行 Java），迁移的**绝对工作量不大**。
- 但**收益同样很小**：真正能从 Kotlin 获益的部分只占三分之一，而最难、最值钱的部分（WebView 认证状态机）迁移到 Kotlin 后反而更危险。
- 存在**两个硬性阻断项**（P0），在解决之前，任何 Kotlin 化都无法编译通过：构建脚本用的是 `javac`，不认 `.kt`；测试集依赖 `javac` + 纯 JVM 直接编译源码文件，Kotlin 类无法这样被测试。

建议：**不做整体迁移**。若确实想要 Kotlin，走"新文件用 Kotlin、旧文件不动"的渐进路线，并先解决 P0。

---

## 一、代码结构

### 1.1 分层实况

| 层 | 文件数 | 行数 | 是否依赖 Android SDK | Kotlin 收益 |
|---|---|---|---|---|
| 纯逻辑（可纯 JVM 测试） | 14 | ~330 | 否 | **高** |
| 存储/加密 | 5 | ~170 | 是（Context/Keystore） | 中 |
| UI 构建（全代码手写） | 12 | ~450 | 是 | 中 |
| Activity / WebView 状态机 | 6 | ~1100 | 是（重度） | **低甚至为负** |

关键数字：**`MainActivity.java` 单文件 508 行，占全部 Java 代码的 25%**；加上 `IdentityActivity`（248）和 `LoginActivity`（128），三个 Activity 合计 884 行，占 **43%**。

### 1.2 三个结构特征（决定了迁移判断）

**特征一：没有任何 XML 布局。`res/layout` 目录不存在。**

全部界面用 Java 代码手工构建，`MainActivity` 末尾集中了一组工厂方法：

```java
int dp(float x){...}
LinearLayout column(){...}
LinearLayout row(){...}
TextView label(String s,int size,int color,boolean bold){...}
TextView button(String s,Runnable action){...}
TextView themedButton(String text,Runnable action,boolean filled){...}
GradientDrawable shape(int c,int radius){...}
```

`UiSheet` 则是一个统一的底部面板构建器（圆角、标题、关闭、滚动体、footer、键盘避让、`SheetAnimation`）。

**这其实已经是"伪 DSL"了**——Java 里硬写的一套 UI 构造层。迁移到 Kotlin 后，它可以被替换为 `apply {}`、扩展函数、以及可选的 Compose/Anko 风格 DSL，这是**全项目 Kotlin 收益最高的地方**。但要注意：这属于"顺手重写 UI 层"，不是"翻译语言"。

**特征二：`MainActivity` 是上帝对象。**

单文件里同时包含：WebView 建立与导航拦截、CAS 自动登录轮询状态机（`poll` / `pollLogin` / `tryAutoLogin`，用 `generation` token + `deadline` + `handler.postDelayed` 手写）、三页面渲染（`render` 及 `homeView` / `gradesView` / `userView` / `weekView`）、课表网格与冲突分道（`assign`）、自定义课程增删改、主题与背景、透明度面板、退出登录、学期选择。

**特征三：状态用裸字段 + 布尔旗标表达。**

```java
boolean customReadError=true, todayLabel=false, verified=false, autoTried=false,
        autoSubmitted=false, autoRunning=false, credentialBusy=false;
boolean busy=false, inBackground=false, loadError=false, mainLoaded=false, identityBusy=false;
int selectedWeek=1, generation=0;
long deadline;
```

仅在 `MainActivity` 中就有 **13 个 boolean 旗标**。这是把重构风险从"语言"转移到了"状态机"——用 Kotlin 重写这些旗标（比如换成 `sealed class AuthState`）是**真正有价值的重构，但风险与语言无关**。

### 1.3 值得肯定的部分（迁移时要保护的资产）

- `Course` / `Schedule` / `Term` / `GridGeometry` / `ThemePalette` / `CourseColors` / `CourseAppearance` / `CachePolicy` 等是**不依赖 Android 的纯逻辑**，头部注释明确写着 `Pure Java parser; no dependency on the network or Android.`，因此能被 `javac` 直接编译测试。
- `CredentialStore` 用 Android Keystore + AES-GCM，密文写 `noBackupFilesDir`，注释明确 `Only ciphertext is written; the key stays in Android Keystore.`；`IdentityCredentialStore` 用**独立别名/独立文件**隔离教务与统一认证。这块边界清晰。
- 项目文档有很强的"不声称未验证结论"的传统（README 每条版本记录都列出未验证项）。

---

## 二、Kotlin 迁移的技术评估

### 2.1 语言层面：确实能获益的地方

| 位置 | 现状 | Kotlin 后 |
|---|---|---|
| `Course.java` | 8 个 public 可变字段 + `Set<Integer> weeks` | `data class` + 默认不可变 |
| `CredentialStore.Credentials` | 手写 final 字段与构造器（49 行样板） | 2 行 `data class` |
| `CourseEditor` / `UiSheet` / `ThemeColorSheet` | 匿名内部类 + `Runnable` | lambda + trailing lambda |
| `Switch` 监听、`SeekBar` 回调 | 匿名类样本代码 | SAM 转换 |
| `MainActivity` 的 `label/button/shape` 工厂 | 方法调用链 | 扩展函数 / DSL |
| 空安全 | `catch(Exception e)` 满天飞，靠约定避免 NPE | 编译期空安全 |

### 2.2 语言层面：会**变差**或持平的地方

1. **`Course.parse` 的正则与字符串切分**——L12 的超长正则、`parseWeeks` 里的 `replaceAll("[单双()（）]","")`，这些是纯字符串算法，Kotlin 写法几乎一样，**零收益**。

2. **`Schedule` / `Grades` 的 JSON 解析**——直接用 `org.json`，Kotlin 无改善（要改善得换 kotlinx.serialization / Moshi，那是**换库**而不是换语言，会改变缓存格式风险面）。

3. **`WeekGridView` 的 measure/layout 重写**——`onMeasure` / `onLayout` 的手工测量摆放，Kotlin 无改善。

4. **`MainActivity` 的 WebView 认证状态机**——这是**最危险**的部分。`generation` token 校验、`handler.postDelayed`、`evaluateJavascript` 回调、`isDestroyed()` / `inBackground` 竞态防护散落各处。README 显示这个状态机在 1.6.1~1.6.7 之间反复修了 **7 个版本**才收敛（重复跳转、回调竞态、会话复用）。**用 Kotlin 重写它，等于把这 7 轮修复的风险重新打开一次，而 Kotlin 对此毫无帮助。**

5. **`CredentialStore` 的加密读写**——`synchronized` + `AtomicFile` + `failWrite` 回滚，样本代码量小且正确。Kotlin 改写收益接近零，**动它则有破坏已保存凭证兼容性的风险**（`ALIAS="sanjiaohubian.login.v1"`、文件格式 `[1][ivLen][iv][ciphertext]`）。

### 2.3 真正的硬阻断项（P0：必须先解决，否则无法编译）

**P0-1：构建脚本用 `javac`，不支持 Kotlin。**

`build-local.ps1` 第 27-29 行：

```powershell
& "$Java/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$android;...core-lambda-stubs.jar" -d "$Work/classes" @sources
```

`$sources` 只收集 `-Filter '*.java'`（第 27 行）。要用 Kotlin，必须插入 `kotlinc` 编译步骤，并解决：Kotlin 编译需要先有 Java 类、Java 编译又需要先有 Kotlin 类时的**联合编译顺序**问题；`core-lambda-stubs.jar` 这类 Java 8 兼容技巧在 Kotlin 下不再适用。

**更关键的是环境事实**（已实测）：本机 **`C:\Program Files\Android\Android Studio\jbr` 不存在**，`%LOCALAPPDATA%\Android\Sdk` 也不存在。也就是说**当前这台机器上没有可用的 Android SDK 和 JDK**，`build-local.ps1` 现在就无法运行——你们一直是在别处构建的。所以"改造成 Kotlin 构建"这件事，本地既无法验证也无法调试。

**P0-2：测试集依赖 `javac` 直接编译源码，Kotlin 类无法这样测。**

`tests/` 是 15 个 Java 文件 + 6 个 `.cjs`，**不用 Gradle、不用 JUnit**，而是裸 `javac` + `java`：

```powershell
javac -encoding UTF-8 -d test-classes app/src/main/java/cn/jhun/sanjiaohu/Course.java \
      app/src/main/java/cn/jhun/sanjiaohu/CachePolicy.java ... tests/CourseTest.java tests/RevisionTest.java
java -cp test-classes cn.jhun.sanjiaohu.CourseTest
```

README 中"20 项解析""156 项合成表单检查""1,007 组配色"等成百上千条断言，全部靠这套机制跑。**一旦 `Course.java` 变成 `Course.kt`，`javac` 那行命令立刻失效**，几百条回归断言全部跑不起来，除非：改写全部测试命令、引入 kotlinc 编译测试、或者把测试改造成 Gradle/JUnit。

**这会直接摧毁这个项目最值得保留的东西：那套不依赖设备、随时可跑的回归安全网。** 而这套安全网正是项目在"没有真机"的条件下仍能持续迭代 20 多个版本的唯一依据。

**P0-3（次要）：Gradle 路线当前不可用。**

`app/build.gradle` 只有 7 行，`build.gradle` 只有 1 行（AGP 8.13.0），`settings.gradle` 4 行。项目**特意不使用 Gradle Wrapper**（README 写明"不附 Gradle Wrapper 二进制"），而 `.gradle/9.2.0` 缓存存在、`kotlin` 插件在 `~/.gradle/caches` 中也有——但**没有 SDK 就无法构建**。

要走标准 Kotlin 路线，需要加 `org.jetbrains.kotlin.android` 插件、`kotlinOptions`、可能还有 `kotlin-stdlib` 依赖，并重新打通 Gradle + SDK。这本身是**基础设施工程**，不是代码翻译。

---

## 三、成本估算

| 方案 | 范围 | 预估工作量 | 风险 |
|---|---|---|---|
| A. 整体迁移 | 37 文件全部转 Kotlin | 3~5 天（含重建构建链） | **高**：认证状态机回归、测试网失效 |
| B. 仅纯逻辑层 | 14 文件 / ~330 行 | 0.5~1 天 | 低，但收益也低 |
| C. 渐进混编 | 新增文件用 Kotlin，旧文件保留 | 一次性接入约 0.5 天，之后自然增长 | 中：需先解决 P0-1/P0-2 |
| D. 不迁移，只做重构 | 拆 `MainActivity`、引入状态类型 | 2~4 天 | 中，但**收益最高** |

**方案 A 的风险回报比最差。** 它同时打开"重建构建链"和"重写认证状态机"两个高风险面，换来的主要是语法糖。

---

## 四、建议

### 如果目标是"现代化 / 好维护"——推荐方案 D，不迁移语言

`MainActivity` 508 行上帝对象 + 13 个 boolean 旗标才是真正的技术债。把它拆成 `ScheduleViewModel` / `AuthController` / `UiFactory`，**用 Java 也能做，且不触碰构建链和测试网**。收益比换语言大得多。

### 如果确实要 Kotlin——按此顺序，逐项验收

1. **先修 P0-1 与 P0-2**（在没有可信构建之前不要动任何源文件）。补 `kotlinc` 步骤或切到 Gradle，并保证**现有全部回归断言在新链路下仍能全绿**。这是不可跳过的前置条件。
2. **从纯逻辑层开始**：`Course`、`Term`、`GridGeometry`、`CourseColors`、`CourseAppearance`、`ThemePalette`、`CachePolicy`。这些不依赖 Android，迁移风险最低，且**能立刻用原有 `javac`/`java` 风格测试验证等价性**。
3. **再迁存储层**：`WallpaperStore`、`AcademicStore`、`CustomCourseStore`。**`CredentialStore` / `IdentityCredentialStore` 最后迁或干脆不迁**——涉及已保存凭证与 Keystore 别名兼容性，动它可能让用户需要重新登录。
4. **UI 层可选**：`UiSheet` + `MainActivity` 的工厂方法适合做 DSL，但**这是重写不是翻译**，应单独作为一个"外观保持一致"的任务验收。
5. **`MainActivity` 的 WebView 认证状态机最后处理，或永不处理。** 1.6.1~1.6.7 七轮修复的成果都在这里，Kotlin 不提供任何保护。

### 无论选哪条路，都必须遵守项目既有的诚实原则

README 反复强调"模拟检查不代表真实网站端到端登录成功""尺寸边界测试不等同于真机截图检查"。同理：
- **Kotlin 迁移后"编译通过"不等于"行为等价"**，必须有回归断言作为证据。
- 当前**无真机、无 SDK、无 JDK**，因此本轮任何迁移都无法完成端到端验收；不要在文档里写成"已完成 Kotlin 迁移并验证"。

---

## 五、一句话回答标题的问题

> **能换，但当前工程状态下换不动（P0：`javac` 构建链 + 裸 `javac` 测试网），而且换完收益有限、风险集中在最不该动的认证状态机上。**
>
> 建议：先重构 `MainActivity`，暂不迁移语言；若坚持 Kotlin，走方案 C 渐进混编，并严格按"纯逻辑 → 存储 → UI → 认证状态机最后"的顺序推进。
