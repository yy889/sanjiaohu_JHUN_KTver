# MainActivity 重构方案（第 3 步）

**状态：阶段 1 已实施完成并验证；阶段 2、3 仍为待办方案。**
分析对象：`app/src/main/java/cn/jhun/sanjiaohu/`，v1.0.1（versionCode 31）。
已确认的搬运策略（用户选择）：**MainActivity 保留同名方法，内部委托给 `Ui`**。

---

## 阶段 1 实施记录（已完成）

**新增** `app/src/main/java/cn/jhun/sanjiaohu/Ui.java`（48 行），装载 7 个无状态方法：
`dp` / `column` / `row` / `weighted` / `space` / `place` / `shape`。

**改造 7 个类**，删除各自的重复实现，改为委托 `Ui`：

| 类 | 原重复定义 | 现状 |
|---|---|---|
| `MainActivity` | dp, column, row, weighted, space, place, shape | 全部委托 |
| `AboutActivity` | dp, column, shape, gap | 全部委托 |
| `IdentityActivity` | dp, column, row, shape, gap | 全部委托 |
| `LoginActivity` | dp, column, shape | 全部委托 |
| `MoreMenu` | dp, shape（静态，Activity 首参） | 全部委托 |
| `CampusMapActivity` | dp | 委托 |
| `AcademicCalendarActivity` | dp | 委托 |

共 **7 份 `dp()`、5 份 `shape()`、4 份 `column()`、3 份 `row()`** 的重复被消除。
`git diff --stat`：8 个文件，+49 / −32。

### 陷阱处理结果（对照第二节）

- **陷阱 1（LoginActivity radius 16）已正确处理**：保留单参数 `shape(int color)`，
  内部委托为 `Ui.shape(this,color,16)`，圆角语义不变。
- **陷阱 4（dp 浮点语义）已保留**：`Ui.dp` 用 `density*value+.5f` 后 `f2i` 截断。
  已用 `javap -c` 核验字节码为 `fmul → ldc 0.5f → fadd → f2i`，与原实现一致；
  未使用 `Math.round`。
- 各类**无遗留未使用 import**（已逐类检查）。

### 验证结果

| 项 | 结果 |
|---|---|
| 构建 | ✅ 成功，APK label `三角狐`，签名 v2/v3 |
| package / versionCode / versionName | `cn.jhun.sanjiaohu` / 31 / 1.0.1（不变） |
| Java 套件 8 个 | ✅ 全绿（20 / 12544 / 10 / 13152 / 6060 / 1007 / 116 / 60） |
| Node 套件 6 个 | ✅ 全绿（20 / 156 / 9 / login / login-state / 50） |

### 局限（必须保留的声明）

如上文第六节所述：**现有测试没有任何布局像素断言**，因此上述"全绿"**不能证明界面视觉未变**。
阶段 1 的准确结论是：**"消除了 7 份重复实现，且逐字保留了原有数值语义"**，
而非"已验证界面一致"。真机视觉验收**仍未进行**。

---

---

## 〇、重要更正：问题描述需要修正

我在前几轮里说"`MainActivity` 是 508 行上帝对象，应该拆它"。**深入分析耦合后，这个描述不准确，据此制定的顺序也是错的。**

真实情况是：**`MainActivity` 同时扮演三个角色**——

1. **`MainActivity` 本来的职责**（Activity 生命周期、三页面渲染、课表网格）
2. **全 App 的 UI 工具库**（`dp`/`shape`/`label`/`row`/`column`/`space`）
3. **全局可变状态容器**（6 个主题色 + 11 个认证字段）

角色 2、3 才是它臃肿的根因。**直接拆角色 1（Activity 职责）会失败**，因为角色 2、3 是它的地基。
正确顺序是**先拆角色 2（UI 工具库）→ 再拆角色 3（状态）→ 最后才动角色 1**。

---

## 一、现状量化

### 1.1 其他类直接依赖 MainActivity

由于 Java 包级私有（package-private）可见性，同包类可以调用 `MainActivity` 的私有方法：

| 类 | 持有方式 | 实际调用 |
|---|---|---|
| `CalibrationSheet` | `final MainActivity a;` | `a.dp()` `a.label()` `a.row()` `a.themedButton()` … |
| `CourseEditor` | `final MainActivity a;` | `a.label()` `a.INK` `a.PRIMARY` `a.shape()` `a.activeSchedule()` … |
| `ThemeColorSheet` | `final MainActivity a;` | 同上 |
| `CourseDetailSheet` | `static void show(MainActivity a, …)` | `a.dp()` `a.themedButton()` |
| `UiSheet` | `show(MainActivity a)` / `actions(MainActivity a, …)` | `a.dp()` `a.themedButton()` |

**`MainActivity` 是全 App 的共享基类**，而非一个独立页面。这是拆分必须先解决的结构性事实。

### 1.2 UI 工厂方法被重复实现（最大的一块重复代码）

| 方法 | 定义它的类 |
|---|---|
| `dp` | **MainActivity、AboutActivity、IdentityActivity、LoginActivity、MoreMenu、CampusMapActivity、AcademicCalendarActivity（7 处）** |
| `shape` | MainActivity、AboutActivity、IdentityActivity、LoginActivity、MoreMenu（5 处） |
| `column` | MainActivity、AboutActivity、IdentityActivity、LoginActivity（4 处） |
| `row` | MainActivity、IdentityActivity、MoreMenu（3 处） |
| `label`/`text` | MainActivity、MoreMenu（2 处） |

跨类调用次数：`dp` 出现在 **12 个类**、`shape` **10 个类**、`column`/`row` **8 个类**、`label` **8 个类**、`space` **5 个类**。

### 1.3 主题色字段是穿透性的

| 字段 | 使用处数（约） |
|---|---|
| `MUTED` | 26 |
| `INK` | 24 |
| `PRIMARY` | 21 |
| `ON_PRIMARY` | 4 |
| `ACCENT_TEXT` | 4 |
| `BG` | 6 |

约 **90 处**。它们同时服务于渲染、卡片样式、底部面板、导航栏。**任何"把渲染拆出去"的方案都必须先决定这 6 个字段归谁。**

### 1.4 认证状态机与 UI 的双向耦合

认证簇（`sync` / `cancelSync` / `tryAutoLogin` / `pollLogin` / `startPoll` / `poll` / `fail` / `showLogin` / `clearCredentials`）共触及 **11 个字段**：

```
web, busy, generation, deadline, autoTried, autoSubmitted,
autoRunning, mainLoaded, loadError, automaticCredentials, inBackground
```

**耦合点在此**：
- `setState()` 既写状态字符串，又直接调用 `stateText.setText()` 和 `refreshSummary()`（操作 View）
- `authStatus()` 读 `busy` / `autoRunning` / `credentialBusy` 生成展示文本
- `refreshSummary()` 读 `busy` 渲染"正在获取汇总数据"

即：**状态机需要通知 UI，UI 需要读状态机**。这正是 `SyncController` 拆分必须先定义回调接口的原因。

---

## 二、发现的三个"陷阱"（必须在编码前知晓）

### 陷阱 1：`LoginActivity.shape()` 签名与行为都不同 ⚠️

```java
// LoginActivity L127 —— 单参数，圆角硬编码 16
GradientDrawable shape(int color){...setCornerRadius(dp(16));}

// MainActivity L509 —— 双参数
GradientDrawable shape(int c,int radius){...setCornerRadius(dp(radius));}
```

**不能简单合并**。统一 API 后，`LoginActivity` 的 3 个调用点（L52、L125）必须显式传 `16`：

```java
shape(theme.primary)                    →  Ui.shape(ctx, theme.primary, 16)
shape(ThemePalette.mix(...))            →  Ui.shape(ctx, ThemePalette.mix(...), 16)
```

若漏改，输入框和登录按钮会变成**直角**（radius 0）。这是**静默的视觉回归**，编译和测试都不会报错。

### 陷阱 2：`AboutActivity` 的 `column()` 写法有细微差异

```java
// MainActivity:  setOrientation(1)
// AboutActivity / IdentityActivity:  setOrientation(LinearLayout.VERTICAL)
```

数值等价（`VERTICAL == 1`），但说明这些是**各自独立复制的**，不能假设处处一致。

### 陷阱 3：`MoreMenu` 用的是 `Activity` 而非 `MainActivity`，且参数顺序不同

```java
// MainActivity 风格（实例方法，隐式 this）
int dp(float x)
GradientDrawable shape(int c, int radius)

// MoreMenu 风格（静态，Activity 作为首参）
static int dp(Activity a, float value)
static GradientDrawable shape(Activity a, int color, int radius)
```

合并到 `Ui` 时，**`MoreMenu` 的 18 处 `dp(a,…)` 调用与 5 处 `shape(a,…)` 需保持可用**。
建议 `Ui` 采用 MoreMenu 的"Context/Activity 首参"静态风格（因为 `Ui` 是静态类，无隐式 `this`），
再让各 Activity 用**实例转发方法**保持调用点不变。

### 陷阱 4：`dp()` 的浮点细节必须逐字保留

```java
MainActivity:              (int)(x*getResources().getDisplayMetrics().density+0.5f)
AboutActivity:             (int)(getResources().getDisplayMetrics().density*value+.5f)
LoginActivity:             (int)(getResources().getDisplayMetrics().density*n+.5f)
```

三者写法不同（`x*d` vs `d*x`、`+0.5f` vs `+.5f`），**数值完全等价**。
统一为一处实现是安全的，但**不要"顺手优化"成 `Math.round()`** —— `Math.round` 对负数舍入方向与 `+0.5f` 截断不同，可能改变极端布局。

---

## 三、目标 API 设计（阶段 1）

新建 `app/src/main/java/cn/jhun/sanjiaohu/Ui.java`：

```java
/** Shared view factory. Stateless: every helper derives from the Context passed in. */
static final class Ui {
    // --- geometry ---
    static int dp(Context c, float value)                 // 保留 +0.5f 截断语义
    static LinearLayout column(Context c)
    static LinearLayout row(Context c)
    static LinearLayout.LayoutParams weighted(Context c)
    static void space(Context c, LinearLayout parent, int size)
    static void place(FrameLayout parent, View v, int x, int y, int w, int h)
    static GradientDrawable shape(Context c, int color, int radius)
    static GradientDrawable shape(Context c, int color)   // 可选：radius 16，供 LoginActivity
}
```

**关键设计决策（依用户选择）**：`Ui` 只做**无状态**部分（`dp`/`shape`/`column`/`row`/`space`/`place`/`weighted`）。

`label` / `button` / `themedButton` / `panel` / `iconButton` / `homeEntry` **依赖主题色与状态**，本阶段**不迁移**，仍留在 `MainActivity`（它们依赖 `INK`/`PRIMARY`/`palette`，属于阶段 2 的范畴）。

`MainActivity` 保留同名实例方法，内部委托：

```java
int dp(float x){return Ui.dp(this,x);}
GradientDrawable shape(int c,int radius){return Ui.shape(this,c,radius);}
LinearLayout column(){return Ui.column(this);}
LinearLayout row(){return Ui.row(this);}
void space(LinearLayout p,int s){Ui.space(this,p,s);}
void place(FrameLayout p,View v,int x,int y,int w,int h){Ui.place(p,v,x,y,w,h);}
```

→ **其他 5 个类的调用点（`a.dp()` `a.shape()` …）一行都不用改。**

---

## 四、分阶段执行计划

### 阶段 1：提取 `Ui`（本次建议执行）

| 步骤 | 内容 | 涉及文件 |
|---|---|---|
| 1.1 | 新建 `Ui.java`，搬入 7 个无状态方法 | 新增 |
| 1.2 | `MainActivity` 改为委托 | MainActivity |
| 1.3 | `AboutActivity` / `IdentityActivity` / `MoreMenu` / `CampusMapActivity` / `AcademicCalendarActivity` 删除各自重复定义，改为调 `Ui` | 5 个 |
| 1.4 | `LoginActivity` 特殊处理：`shape(color)` → `Ui.shape(this,color,16)` | LoginActivity |

**预期收益**：消灭 **7 份 `dp()`、5 份 `shape()`、4 份 `column()`、3 份 `row()`** 的重复。

**每步验收**：
```
.\build-local.ps1 -Work "$PWD\.buildwork" -Apk "$PWD\Sanjiaohu-1.0.1.apk"
```
+ 8 个 Java 套件 + 6 个 Node 套件 **必须全绿**（当前基线：全部通过）。

⚠️ **本阶段无法验证视觉**：无真机验收。必须靠"逐字搬运、不改语义"来保证等价，而非靠测试（测试覆盖不到布局像素）。

### 阶段 2：收敛主题色（后续）

把 6 个字段收进一个 `Theme` 对象。`ThemePalette palette` 已存在，可扩展为持有 `surface`/`text`/`muted`/`primary`/`accent`/`onPrimary`。
涉及 **约 90 处**调用点。**风险显著高于阶段 1**，建议单独一个任务、单独验收。

### 阶段 3：拆 `MainActivity`（最后）

在 1、2 完成后，把认证状态机独立为 `SyncController`。需先定义回调接口解决双向耦合：

```java
interface SyncView {          // 状态机 → UI
    void onState(String text);
    void onAuthStatus(String text);
}
```
耦合点：`setState()`（写 View）、`authStatus()`（读 flags）、`refreshSummary()`（读 `busy`）。

⚠️ **这是 1.6.1~1.6.7 反复修了 7 个版本才收敛的核心代码**（重复跳转、回调竞态、会话复用）。拆它的收益必须与重开这 7 轮修复的风险相权衡。

---

## 五、风险登记

| # | 风险 | 严重度 | 缓解 |
|---|---|---|---|
| R1 | `LoginActivity.shape()` 漏改 → 输入框/按钮变直角 | **中**（静默视觉回归） | 阶段 1.4 单独核对 3 个调用点，传 `16` |
| R2 | `dp()` 浮点语义被"优化" → 全 App 布局偏移 | **高** | 逐字保留 `+0.5f`；禁用 `Math.round` |
| R3 | 搬到一半的中间态无法编译 | 低 | 分步提交，每步跑构建 |
| R4 | 测试覆盖不到像素 → 等价性无法自动证明 | **高** | 承认局限；写进文档，不声称"已验证视觉一致" |
| R5 | 与用户正在进行的工作冲突 | 中 | 动手前确认工作树干净 |

---

## 六、必须诚实声明的局限

按项目 README 一贯的"不声称未验证结论"原则：

- 本次**未改任何代码**，因此**未做任何验证**。
- 阶段 1 完成后，**"编译通过 + 14 个测试套件全绿"不等于"界面与之前完全一致"** —— 现有测试是纯 Java 逻辑测试 + Node 脚本测试，**没有任何布局像素断言**，覆盖不到 `dp`/`shape` 的视觉结果。
- 本项目**从未做过真机验收**。阶段 1 之后若要声称"视觉无变化"，需要真机对比截图，而非测试结论。
- 因此阶段 1 的正确描述是：**"消除了重复代码，且未改变任何语义"**，而**不是**"已验证界面一致"。

---

## 七、给执行者的第一步命令

工作树确认干净后：

```powershell
git status                      # 确认无未提交改动
.\build-local.ps1 -Work "$PWD\.buildwork" -Apk "$PWD\Sanjiaohu-1.0.1.apk"   # 先建立基线
```

基线（本次实测，全部通过）：
- 构建成功，APK label `三角狐`，签名 v2/v3
- Java：CourseTest(20)、RevisionTest(12544)、TermTest(10)、MealGridTest(13152)、AdaptiveTextTest(6060)、ThemeTest(1007)、CalendarViewportTest(116)、CustomCoursesTest(60)
- Node：identity-document(20)、identity-login(156)、identity-session(9)、login、login-state、portal(50)

**先跑通基线，再动手。**
