# Kotlin 等效写法验证结果

**状态：已实测验证。核心结论见下。**
验证对象：`Course.java` → `Course.kt`、`Ui.java` → `Ui.kt`。

---

## 〇、先纠正我之前的一个错误判断

我在 `kotlin-migration-analysis.md` 中把"测试网失效"列为 **P0 阻断项**，并写道
"上千条回归断言将无法运行"。**这个说法过于悲观，已被本次实验推翻。**

实测结论：**Java 测试代码可以一字不改地测试 Kotlin 类。** 需要改的只是**测试的调用方式**
（在 `javac` 之前插入一次 `kotlinc`），而**测试代码本身完全不用动**。

证据：项目原有的 `tests/CourseTest.java` **未做任何修改**，编译并运行在 Kotlin 版
`Course` 上，输出：

```
PASS: 20 parser assertions
```

与 Java 版基线**完全一致**。所以安全网是**保得住**的。

（`build-local.ps1` 需要加 kotlinc 步骤这一点仍然成立，但那是**一个步骤**，不是"安全网崩塌"。）

---

## 一、Kotlin 编译器可用（无需下载）

本机原本没有 `kotlinc`，但 **Gradle 缓存里已有完整的 Kotlin 2.0.21 编译器**，
拼出可用的编译命令后可直接调用。已封装为可复用脚本：
`design/kotlin-examples/kotlinc-embed.ps1`（已验证端到端可用）。

### 为什么需要一个脚本

`kotlin-compiler-embeddable` 需要**另外 6 个 jar** 在它自己的 classpath 上，缺一个就报错：

| jar | 缺失时的报错 |
|---|---|
| `kotlin-stdlib` | `NoClassDefFoundError: kotlin/jvm/internal/Intrinsics` |
| `kotlin-daemon-embeddable` | `NoClassDefFoundError: kotlinx/coroutines/CoroutineScope` |
| `kotlinx-coroutines-core-jvm` | 同上 |
| `trove4j` | `NoClassDefFoundError: gnu/trove/TObjectHashingStrategy` |
| `kotlin-script-runtime` | 编译器初始化相关报错 |
| `annotations` | `NoClassDefFoundError: org/jetbrains/annotations/NotNull` |

### ⚠️ 必须用 JDK 22，不能用 JDK 25

Kotlin 2.0.21 内嵌的 IntelliJ 工具**无法解析 JDK 25 的版本字符串**：

```
exception: java.lang.IllegalArgumentException: 25.0.2
    at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse
```

本机 `D:\jdk22` 可用；`D:\jdk25`（PATH 默认）会失败。脚本默认 `-Java D:\jdk22`。

---

## 二、验证 1：`Course.java` → `Course.kt`（纯逻辑类）

见 `design/kotlin-examples/Course.kt`。

**验证方式**：用 kotlinc 编译 `Course.kt`，再用 `javac` 编译**原封不动的**
`tests/CourseTest.java`（classpath 指向 Kotlin 输出），运行。

**结果**：`PASS: 20 parser assertions` ✅

### 关键：必须加 `@JvmField` 和 `@JvmStatic`

这是**本项目迁移时最重要的一条**。现有 Java 代码是**直接读字段**的：

```java
Course a = Course.parse("...", 2, 1, 25);
a.name.equals("概率论与数理统计（理）");   // 直接读字段，不是 getName()
a.weeks.contains(5)
```

而 Kotlin 属性**默认只生成 getter/setter，不生成 public 字段**。若不加注解：

| 注解 | 不加会怎样 |
|---|---|
| `@JvmField` | Java 的 `c.name`、`c.weeks` 全部编译失败（Kotlin 生成的是 `getName()`） |
| `@JvmStatic` | Java 的 `Course.parse(...)`、`Ui.dp(...)` 编译失败（Kotlin 生成的是 `Course.Companion.parse()`） |

加了之后，**所有现有 Java 调用点零改动**。

### 语义等价的细节

| 项 | 处理 |
|---|---|
| `throw new IllegalArgumentException` | Kotlin `require {}` —— 抛的正是 `IllegalArgumentException`，测试捕获逻辑不变 |
| `s.split(",", -1)`（保留尾部空串） | Kotlin `s.split(",")` 默认同样保留尾部空串，行为一致 |
| `"-15"` / `"10-"` 开区间解析 | 实测通过（`parseWeeks("-15",25).size()==15`） |
| `Comparator.comparing(...).thenComparingInt(...)` | Kotlin `compareBy({ it.key() }, { it.start })`，排序键与次序一致 |

---

## 三、验证 2：`Ui.java` → `Ui.kt`（Android 类）

见 `design/kotlin-examples/Ui.kt`。用 `android.jar` 作为 classpath 编译。

**结果**：编译通过 ✅，且**Java 调用方零改动**——Java 仍写 `Ui.dp(c, 24f)`、
`Ui.column(c)`、`Ui.space(c, col, 12)`，而不是 `Ui.INSTANCE.dp(...)`（靠 `@JvmStatic`）。

### 发现一个真实的 Kotlin 陷阱：没有 int → float 隐式提升

这是本次唯一一处**真的编译报错**的地方：

```java
// Java：合法。第三参数是 float weight，Java 自动把 1 提升为 1.0f
new LinearLayout.LayoutParams(0, dp(44), 1)
```
```kotlin
// Kotlin：编译错误。Kotlin 不做隐式数值提升
LinearLayout.LayoutParams(0, dp(c, 44f), 1)
//                                        ^ argument type mismatch: actual Int, expected Float
LinearLayout.LayoutParams(0, dp(c, 44f), 1f)   // 正确
```

**影响面**：本项目大量使用 `LinearLayout.LayoutParams(w, h, weight)`。
迁移时必须逐处检查 weight 参数是否需要写 `1f`。
好消息：**这是编译期错误，不会静默产生错误布局。**

---

## 四、验证 3：双向互操作（你提的"在 kt 里 import java"方案）

**完全可行，且已跑通。**

```
java -> kotlin -> java: Math A|Teacher|weeks=10
```

即：Java 主程序 → 调用 Kotlin 顶层函数 → 该函数调用 **未改动的 Java `Course`**。

| 方向 | 结果 |
|---|---|
| Kotlin 文件 `import` 现有 Java 类 | ✅ 可行 |
| Java 文件调用 Kotlin 类/函数 | ✅ 可行 |
| 双向混合后正常运行 | ✅ 可行 |

**这意味着渐进式迁移是可行的**：不必一次性转换，可以新代码用 Kotlin、旧代码留 Java。

---

## 五、修正后的迁移判断

| 原判断 | 修正后 |
|---|---|
| P0-2 "测试网失效，断言无法运行" | ❌ **不成立**。测试代码零改动即可测 Kotlin 类，只需在调用前加 kotlinc 步骤 |
| P0-1 "build-local.ps1 只收 *.java" | ✅ **仍成立**，但只是需要加一个编译步骤（已提供可用脚本） |
| "Java/Kotlin 联合编译需解决顺序问题" | ⚠️ 需注意，但实测 `javac -cp <kotlin output>` 的顺序是可行的 |

### 仍然成立的成本

1. **编译器依赖 JDK 22**（不能用 JDK 25）。CI 里要固定 JDK。
2. **每个测试调用都需要 kotlinc 步骤**，CI 与 `build-local.ps1` 都要改。
3. **`@JvmField`/`@JvmStatic` 必须逐类仔细加**，漏了就是编译失败（好在是显式错误）。
4. **认证状态机（`MainActivity` 的 WebView 轮询）仍不建议动**——那是 1.6.1~1.6.7
   修了 7 版才收敛的代码，与语言无关的风险依然存在。

### 建议路径（修正版）

若要迁移，顺序：**纯逻辑层**（`Course`/`Term`/`GridGeometry`/`CourseColors`/
`CourseAppearance`/`ThemePalette`/`CachePolicy`）→ **存储层** → **UI 工具层**（`Ui`）→
**认证状态机最后或不动**。

以及：**先加 kotlinc 步骤并让现有全部断言保持全绿，再动任何源文件。**

---

## 六、诚实声明

- 本次验证**只在 `Course` 和 `Ui` 两个类上做过**，不代表其余 35 个类同样顺利。
  尤其 `MainActivity`（508 行、含 WebView 状态机）**未做任何 Kotlin 验证**。
- 验证方式是**命令行编译 + 运行原测试**，**未做真机、未做 APK 打包**。
  完整 APK 构建还需在 Gradle 中加入 Kotlin 插件并重新验证，**本次未做**。
- `CodeTest` 通过的 20 项断言只覆盖 `Course` 的解析逻辑，**不覆盖 UI 层**。
  `Ui.kt` 本次只验证"能编译"，**未验证视觉与行为等价**。
