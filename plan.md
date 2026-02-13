

我打算开发一个 安卓app(我手机目前是安卓14 并且有root权限，似乎可以考虑弄些有趣的功能? 当然，也或许没有必要)，用于连接我的 memos 然后方便我记录笔记 (需要带有本地缓存)，并且可以离线也可以记录(当然，需要显示)，然后在线的时候立马上传 (目前来说，离线只需要记录就行，不需要修改 (可以修改还没上传的数据))

用户登录方式 使用apiurl+token

可以写文字，也可以 上传图片， ui给我设计的好看些，非常好看(最好是中国风格(国漫的感觉))
同时需要适配白天和夜晚模式

需要有设置，可以配置 服务器url和 token，主题配置(你需要多弄点好看的主题)

框架你需要用的好一些(不考虑适配低版本安卓(我手机最低都是安卓14))
框架和结构都需要好用，新颖，美观

---

或许考虑添加一个系统快捷开关，下滑打开就可以方便我进行记录

---

## 简易plan

编程语言: Kotlin (必须，利用协程和 Flow 处理异步)。
UI 框架: Jetpack Compose (必须)。
这是 Android 原生声明式 UI，做动画、复杂的自定义绘制（实现国漫风特效）比传统 XML 强太多。
配合 Material 3 (M3) 设计规范，能完美适配系统级动态取色。
架构模式: MVI (Model-View-Intent) 或 MVVM。
推荐 MVI (如使用 Airbnb 的 Mavericks 或 Unidirectional Data Flow)，状态管理更清晰，特别适合“离线/在线”状态切换的逻辑。
本地数据库: Room (SQLite)。
这是实现“离线缓存”的核心。所有数据优先写入 Room，UI 观察 Room 的数据变化。
网络与同步: Retrofit + OkHttp。
WorkManager: 用于后台静默上传。即使 App 被杀掉，WorkManager 也能在网络恢复时把离线笔记传上去。
图片加载: Coil。
专为 Kotlin 和 Compose 打造，轻量且支持 GIF/SVG。
依赖注入: Hilt 或 Koin。

## ui相关

2. UI/UX 设计优化：国漫/中国风 (重点)
“好看”是主观的，但“国漫风”是有具体设计语言的。不要做成老气的“唐装风”，要做成新中式赛博 (New Chinese Style) 或 水墨留白 风格。
配色方案 (主题配置):
白天模式: 推荐使用中国传统色。
主色: 这里的“国漫”可以理解为高饱和度的点缀。比如 朱砂 (Vermilion)、黛蓝 (Indigo)、竹青 (Cyan)。
背景: 米宣纸色 (Off-white)，带有淡淡的纸张纹理。
夜间模式:
背景: 墨黑 (Ink Black)，不是纯黑，是深灰色带有墨韵。
强调色: 荧光青或赤红，营造一种“灯笼”或“符咒”发光的感觉。
字体:
标题使用免费商用的书法字体（如鸿雷板书简体、演示悠然小楷），正文保持系统字体以确保易读性。
视觉元素:
卡片设计: 不要用简单的圆角矩形。可以尝试带有“云纹”边框，或者类似古代“奏折”的折叠效果。
加载动画: 毛笔书写的动画，或者水墨晕开的效果。
点击反馈: 点击按钮时，出现水墨溅射的波纹，而不是普通的灰色波纹。
图片排版: 参考“小红书”或“Instagram”的瀑布流，但在图片圆角处理上可以使用“海棠窗”或“花窗”的形状遮罩（Compose 的 Clip 功能很容易实现）。

## 3.功能逻辑与体验优化建议
针对你提出的计划，我补充以下几点优化：
A. 极速记录 (Quick Settings Tile & Shortcut)
你提到的“下滑打开”非常实用。
QS Tile (下拉快捷开关):
点击快捷开关，不要打开整个 App。
弹出一个悬浮窗 (Floating Window) 或 透明 Activity。
输入框直接弹起键盘，写完点发送，窗口消失。
痛点解决: 很多时候我只想记一句话，不想等待 App 冷启动的 Loading 动画。
B. 离线/同步逻辑 (关键)
你提到“离线只记录，不修改已上传的”。这个逻辑是合理的 MVP (最小可行性产品) 逻辑，但要注意：
UUID 生成: 离线创建笔记时，务必在本地生成一个临时的 UUID。
状态标记: 数据库里加一个字段 status (Synced, Pending, Failed)。
冲突处理: 如果你在线，但是网络很差，用户狂点发送怎么办？要有防抖动处理。
C. Memos 特性适配
Markdown 支持: Memos 核心是 Markdown。你的输入框和展示页需要支持 Markdown 渲染（加粗、代码块、列表）。Compose 有现成的 Markdown 渲染库。
标签系统: 输入 # 时，最好能联想弹出已有的标签（从本地缓存读取），方便归类。
资源压缩: 图片上传前，建议在本地进行压缩。现在的手机照片一张 10MB，直接传会把 Memos 服务器（特别是自建的小鸡）撑爆。


## 有趣的功能

功能扩展：
1. 视觉与交互的“国漫化” (强化设计灵魂)
你提到要“国漫风”，这不应该只是换个背景图，而应该是交互上的体验。
“电子印章”上传反馈 (强烈推荐)
功能: 当你点击发送（上传）笔记时，不要用普通的 Loading 转圈。
设计: 屏幕上盖下一个红色的**“印章”**（可以是“已阅”、“记录”、“存档”或者你自定义的篆刻字）。
效果: 配合震动反馈（Haptic Feedback）和水墨晕开的特效。这会让每一次记录都有一种“签署奏折”或者“盖章定论”的仪式感。
天气与节气联动
功能: 获取本地天气和中国传统二十四节气。
设计:
如果是雨天，App 背景有淡淡的水墨雨丝飘落效果。
如果是清明或立秋，UI 的强调色自动变成对应的传统色（如“柳黄”、“素鼠”），并在角落显示对应的水墨插画。
卷轴式回顾
功能: 查看历史记录时。
设计: 也就是 Infinite Scroll，但做成横向或纵向展开的卷轴。旧的笔记像是写在泛黄的长卷上，划动时有纸张摩擦的声音（可选）。
2. 利用 Root 的“降维打击”功能 (极客实用派)
利用 Root 权限，你可以绕过安卓繁琐的权限申请和限制，实现“无感”记录。
全局“灵感爆炸” OCR (屏幕文字提取)
痛点: 你在看B站、小红书、PDF或者无法复制文字的App时，想把那段话存进 Memos。
Root 方案: 不需要截图再去扫。
操作: 设定一个侧边栏手势或系统快捷键。触发后，App 在后台直接Dump 当前屏幕的 View 树或者进行静默截图+本地OCR。
结果: 直接提取屏幕上所有文字，你点选一下，直接存入 Memos。比“导出聊天记录”更通用，且不侵犯隐私。
剪贴板的“时光机”
功能: 利用 Root 权限（如 Riru/Zygisk 模块或系统级监听），记录你过去 1小时内的剪贴板历史。
场景: 有时候复制了好几段东西，忘了粘贴。打开你的 App，直接列出最近 10 条剪贴板历史，点击直接转存 Memos。
静默安装/更新
如果你打算把这个 App 分享给朋友或者自己多设备用，利用 Root 权限实现 App 自身的静默更新，体验会很棒。
3. 内容增强与 AI (紧跟潮流)
Memos 通常是碎片化的，需要整理。
本地 AI 标签建议 (On-device AI)
功能: 利用 Android 自带的 ML Kit 或者接入 Gemini Nano (如果是 Pixel 等支持的机型)。
效果: 当你输入一段话，比如“今天买了茄子和土豆”，AI 自动在下方推荐标签 #购物 #食材 #生活。点击即可添加，不需要手打。
“那年今日” (回顾机制)
功能: 每次打开 App 或者通过 Widget，显示一张卡片：“去年的今天，你记录了……”。
设计: 用一种“旧时光”的滤镜展示旧笔记，唤醒记忆。这是笔记软件粘性最强的功能之一。
4. 桌面组件 (Widget) 的极致美学
安卓的灵魂在于 Widget，这更是展示“国漫风”的最佳窗口。
诗词/格言 随机组件:
不只显示 Memos，你可以预埋一些中国古诗词。如果你今天没写 Memos，Widget 就显示一句诗词（如“欲买桂花同载酒，终不似，少年游”），字体要用竖排书法。
热力图窗棂 (Github Contribution Graph):
把 Memos 的贡献热力图（一年里哪天写的勤快），做成**中国古代园林窗棂（花窗）**的样式。
写得多的格子亮起暖光，没写的格子是暗的。放在桌面上非常雅致。
5. 数据与隐私
本地生物识别锁:
虽然是 Memos，但可能记了私密日记。利用 BiometricPrompt 加入指纹/人脸解锁。解锁动画可以做一个“解开鲁班锁”或者“推开古门”的动画。
总结建议
既然你要做独立开发，我的建议是：功能做减法，视觉做加法。
核心: 极速启动 -> 记录 -> 盖章(印章特效) -> 退出。
Root: 用来实现OCR 识屏，这个功能最实用且最能体现 Root 的便捷性。
视觉: 抓住**“留白”、“书法字体”、“传统色”、“印章”**这四个关键词，就能做出非常高级的国漫风。


开发测试 demo 

serverurl: https://me.pscly.cc/
apitoken: 

# plan1

> 这些大概已经完成，只是之前放在另一个plan文件中，现在放过来了而已

这是一个非常敏锐的问题。Memos 的开发非常活跃，API 经常发生“断崖式”更新（比如从 v0.15 到 v0.22 再到现在的 v0.x，很多字段名、路径甚至认证方式都变过）。
如果不想每次 Memos 更新，你的 App 就崩溃或者需要重写一半代码，你需要采用 Clean Architecture (整洁架构) 配合 防腐层 (Anti-Corruption Layer) 的设计思想。
下面是为您量身定制的高可维护性、高颜值、Root 增强型 Android App 开发详细计划。
一、 核心架构设计 (应对 API 变动)
核心原则：本地数据库 (Room) 是 UI 的唯一真理来源。网络 API 只是数据同步的管道。
1. 分层架构 (Layered Architecture)
数据层 (Data Layer):
Network (易变): 负责连接 Memos API。这里定义 DTO (Data Transfer Object)，完全对应 API 的 JSON 结构。
Database (稳定): 使用 Room 定义本地 Entity。这是你自己设计的表结构，永远不要直接把 API 返回的数据存进数据库，中间必须转换。
Repository (协调): 负责判断是从本地拿数据，还是去网络拉取。
Mapper (适配器): 这是关键！ 写一个转换器，把 NetworkDTO 转换为 LocalEntity。
如果 Memos API 变了：你只需要修改 Network DTO 和 Mapper，UI 层和数据库层一行代码都不用动。
领域层 (Domain Layer):
Model: 纯粹的业务对象 (比如 MemoNote)，不含任何 Gson/Room 注解。
UseCase: 业务逻辑 (如 SyncMemosUseCase, UploadImageUseCase)。
表现层 (UI Layer):
ViewModel: 持有 StateFlow，由 UseCase 驱动。
Compose: 即使底层天翻地覆，只要 ViewModel 吐出的 State 没变，UI 就不会崩。
二、 详细开发计划 (Roadmap)
我们将开发分为 5 个阶段。
阶段 1： 地基与防腐层 (The Foundation)
目标：跑通登录和数据同步，无视 UI 美丑，确保 API 变动不影响核心。
项目搭建:
Kotlin + KTS (Gradle)
Hilt (依赖注入)
Room (数据库)
Retrofit + OkHttp (网络)
API 客户端封装 (关键步骤):
创建一个 MemosApiStrategy 接口。
目前 Memos 是 REST API，实现一个 RestMemosClient。
在 OkHttp 中配置 Interceptor，处理 Token 自动注入。如果 API 变了（比如 header key 从 Authorization 变成 X-API-Key），只改这里。
数据映射 (Mapper):
定义 MemoDto (对应服务器 JSON)。
定义 MemoEntity (本地数据库表，包含字段：uuid, content, resourceList, status(SYNCED/PENDING), updateTime)。
编写 fun MemoDto.toEntity(): MemoEntity。
仓库层实现:
实现“离线优先”逻辑：getMemos() 直接返回 Room 的 Flow。同时触发后台 refresh() 从网络拉取并更新 Room。
阶段 2： 国漫风 UI 系统构建 (The Aesthetics)
目标：建立你的“新中式”设计语言，不依赖 Material 默认组件。
Design System 搭建:
在 ui/theme 下定义 ChineseColorPalette (朱砂、黛蓝、宣纸白)。
引入书法字体 (放入 res/font)。
核心组件开发:
InkCard: 自定义 Card，带宣纸纹理背景 + 淡墨描边。
SealButton: 仿印章的 FAB (悬浮按钮)。
ScrollInput: 仿奏折/信纸的输入框。
主页实现:
使用 LazyColumn 展示笔记列表。
实现 Markdown 渲染（推荐使用 com.halilibo.compose-richtext 并魔改样式以适配字体）。
实现图片瀑布流展示 (Coil)。
阶段 3： 离线与同步引擎 (The Engine)
目标：断网也能用，联网自动传，图片高效处理。
WorkManager 集成:
创建 SyncWorker：后台定期拉取最新 Memos。
创建 UploadWorker：监听数据库中 status = PENDING 的数据，自动重试上传。
图片处理:
引入 Luban 或 Compressor 进行图片压缩（Memos 往往对大图支持一般，且浪费流量）。
实现先传图拿到 resourceId，再发 Memo 的逻辑链。
冲突解决 (简单版):
遵循“最后写入优先”或“本地未同步优先”。
阶段 4： Android 14 & Root 特性 (The Cyberpunk)
目标：极客功能，利用 Root 权限。
Shizuku / Root 适配:
引入 libsu (顶级 Root 库) 或 Shizuku API。
封装一个 SystemController，如果没 Root 也能优雅降级。
Quick Settings Tile (下拉开关):
实现 TileService。点击后启动一个 Theme.Translucent 的 Activity。
实现极速弹窗记录。
屏幕识屏 (OCR) (Root/Shizuku):
方案: 使用 adb shell screencap (Root) 或者 AccessibilityService 获取当前屏幕节点。
接入 Google ML Kit (离线 OCR SDK)，识别截图中的文字。
流程：下拉 Tile -> 点击“识屏记录” -> 截图 -> 提取文字 -> 填入输入框 -> 发送。
阶段 5： 细节打磨 (Polish)
设置页: 配置服务器 URL、Token、切换白天/黑夜模式。
动画: 添加盖章动画、入场水墨晕开动画。
Shortcut: 添加 Launcher App Shortcuts (长按图标直接进入“记笔记”)。
三、 项目结构参考 (Package Structure)
保持结构清晰是应对变化的最好方法：
code
Text
com.yourname.guomanmemos
├── core
│   ├── network          // Retrofit, DTOs (只有这里知道 Memos API 长啥样)
│   │   ├── model        // MemoDto.kt
│   │   └── MemosService.kt
│   ├── database         // Room, Entities (本地数据结构)
│   │   ├── dao
│   │   └── entity       // MemoEntity.kt
│   └── common           // Resource, Result, Dispatchers
├── domain               // 纯净的业务逻辑
│   ├── model            // Memo.kt (UI用的最终对象)
│   ├── repository       // MemosRepository Interface
│   └── usecase          // SyncMemosUseCase, CreateMemoUseCase
├── data                 // 数据层实现
│   ├── repository       // MemosRepositoryImpl (调用 core.network 和 core.database)
│   └── mapper           // Mapper.kt (DTO -> Entity -> Domain Model)
├── ui                   // Jetpack Compose
│   ├── theme            // Color, Type, Theme (国漫风配置)
│   ├── component        // InkCard, SealButton (复用组件)
│   ├── feature
│   │   ├── home         // 首页
│   │   ├── editor       // 编辑页
│   │   └── settings     // 设置页
│   └── root             // Root/Shizuku 相关功能
└── worker               // WorkManager 后台任务
四、 针对 Memos API 变动的特别策略代码示例
Entity (本地表 - 你说了算):
code
Kotlin
@Entity(tableName = "memos")
data class MemoEntity(
    @PrimaryKey val localId: Long = 0, // 本地自增ID，防冲突
    val serverId: Int?, // 对应 Memos 的 ID，没上传成功前是 null
    val content: String,
    val createTime: Long,
    val syncStatus: SyncStatus // ENUM: SYNCED, PENDING, FAILED
)
Mapper (防腐层 - 脏活累活在这里做):
code
Kotlin
// 假设 Memos v0.15 返回的是 content
// 假设 Memos v0.22 改成了 payload.content
// 你只需要改这个函数，其他文件不用动

fun MemoDto.toEntity(): MemoEntity {
    return MemoEntity(
        serverId = this.id,
        // 这里处理 API 变动
        content = this.content ?: this.payload?.content ?: "", 
        createTime = this.createdTs * 1000,
        syncStatus = SyncStatus.SYNCED
    )
}
总结
这个计划的核心在于：
UI 极致美学：用 Compose 画出中国风，而不是用贴图。
架构防御：用 Mapper 隔离 Memos API 的神经刀改动。
Root 赋能：用 OCR 和 快速磁贴 解决“想记笔记还要打开App”的痛点。

## plan1ui相关


### 1. 核心设计语言 (Design Token)

#### A. 色彩系统 (Color Palette)

抛弃 Material 默认的紫色系，使用**中国传统色**。

- **白天模式 (宣纸与墨)** :

  - **背景色 (Background)** : 草白 #F7F8F4 (模拟宣纸质感，不要纯白)
  - **表面色 (Surface)** : 米色 #FDFBF3
  - **主色/强调色 (Primary)** : 朱砂 #FF4C39 (用于按钮、印章) 或 黛蓝 #305169 (用于图标)
  - **文字色 (OnSurface)** : 焦茶 #383431 (比纯黑更柔和，像干掉的墨迹)
  - **次要文字**: 雅灰 #878885
- **黑夜模式 (漆器与流光)** :

  - **背景色**: 墨灰 #18191B (深沉的墨色，非纯黑)
  - **表面色**: 玄青 #22252A
  - **主色**: 赤金 #F2BE45 (暗夜里的灯火感) 或 荧光青 #00E5BC (赛博国漫感)
  - **文字色**: 银灰 #E0E0E0

#### B. 字体排印 (Typography)

- **标题 (Display/Headline)** : 使用**书法字体**（如：鸿雷板书简体、演示悠然小楷、江西拙楷）。

  - 注意: 只用在 Toolbar 标题、卡片大标题、印章文字。
- **正文 (Body)** : 保持系统默认的 Roboto 或 Noto Sans CJK，保证长时间阅读不累眼。但可以稍微调整 LineHeight (行高) 为 1.5 倍，增加呼吸感。

#### C. 形状与材质 (Shapes & Textures)

- **圆角**: 即使是现代风格，也建议使用较小的圆角 (e.g., 8.dp) 或者特殊的切角。
- **噪点 (Noise)** : 在背景中叠加一层极淡的噪点图片，模拟纸张的粗糙纹理。

---

### 2. 关键组件设计 (Component Library)

这是你需要封装的 Compose 组件库：

#### 1. InkSurfaceCard (水墨/宣纸卡片)

这是承载 Memos 内容的容器。

- **外观**:

  - 不要用阴影 (Elevation)，用**描边 (Border)**  或 **底色**。
  - 背景带有一点点宣纸纹理。
- **特效**:

  - **点击涟漪 (Ripple)** : 改写 RippleTheme，点击时不是扩散圆圈，而是一个**不规则的水墨晕开**效果 (可以用 Shader 或 Gif 实现，或者简单点用灰色的圆形但边缘模糊)。
- **代码思路**:

#### 2. SealButton (印章浮动按钮 - FAB)

这是发送/记录的入口。

- **形态**: 方形或圆形，红色底，白色字（篆体或楷体）。
- **文案**: 不用 "+"，用汉字  **"记"** 、 **"撰"** 、 **"书"** 。
- **交互**:

  - **按下**: 缩小并有类似盖章的顿挫感 (Scale Animation)。
  - **松开**: 播放一个粒子散开的动画（像印泥把纸压下去的感觉）。

#### 3. ScrollTextField (奏折/卷轴输入框)

- **外观**:

  - 去掉底部横线。
  - 添加背景：可以是**竖向的格线**（信纸模式）或者**空白留白**。
  - 光标 (Cursor) 颜色设为朱红色。
- **Compose 实现**: 使用 BasicTextField 进行完全自定义，在 decorationBox 里用 Canvas 画出信纸的横线或竖线。

#### 4. Dialog (窗棂弹窗)

- **边框**: 借鉴中国园林的花窗（海棠窗、六角窗）。
- **遮罩**: 弹窗背后的半透明遮罩，可以加一点**模糊 (Blur)**  效果，营造“隔窗观景”的朦胧感。

#### 5. QuickSettingsTile (下拉开关)

- **图标**: 设计一个简笔画的**毛笔**或**砚台**图标。
- **状态**:

  - 关：淡墨色。
  - 开：朱砂红。

---

### 3. 特效与动画 (Animation)

让 App 活起来的关键。

- **加载动画 (Loading)** :

  - 不要转圈圈。
  - 做一个**笔走龙蛇**的动画，或者一滴墨水滴入水中慢慢扩散的 Lottie 动画。
- **页面切换**:

  - **入场**: 页面像卷轴一样从右向左展开 (slideInHorizontally)，或者从下往上浮现配合透明度变化。
- **图片加载**:

  - 使用 Coil 加载图片时，Placeholder 不要用灰色方块。用一张**淡色的山水简笔画**作为占位图。

---

### 4. 具体的 Compose UI 架构建议

为了方便开发，建议你这样组织你的 UI 库代码结构：

### 5. 一个简单的视觉示例描述

想象一下你的 App 首页：

- ### 一、 关于 Memos API 的“暗坑” (最容易让人头秃的地方)

  *. **资源 (Resource) 链接的相对路径问题**

  - **坑**: Memos 里的图片（Resource），API 返回的 URL 往往是相对路径（比如 /o/r/123），而不是完整的 https://demo.memos/o/r/123。
  - **解**: 你必须在防腐层写一个 UrlResolver。
  - **注意**: Memos 有时候会开启“外部存储（S3/R2）”，这时候返回的又是绝对路径。你的代码必须能同时处理**相对路径**和**绝对路径**，否则图片会裂开。
    *. **Markdown 解析的复杂性**
  - **坑**: Memos 支持 Task List [ ]，支持 #Tag，支持 ![](image)。直接用普通的 Markdown 库可能渲染得很丑，或者图片显示过大。
  - **解**: 在 Android Compose 中，推荐使用 halilibo/compose-richtext。你需要自定义 Image 渲染器，确保图片在你的“国漫风卡片”里是圆角且裁切得当的，不要让一张巨图撑破布局。
    *. **分页与“无限加载”**
  - **坑**: Memos API 的分页参数在不同版本里变过（limit/offset vs cursor）。
  - **解**: 一定要仔细看你对接的 Memos 版本文档。如果数据量大，**不要一次性全拉下来**，会把手机内存撑爆。使用 Room 的 PagingSource 配合 Compose 的 LazyColumn 做分页。

  ---

  ### 二、 关于 Android 14 开发的“紧箍咒”

  *. **Edge-to-Edge (边到边) 强制适配**

  - **提醒**: Android 14/15 强制推行“边到边”设计（状态栏和导航栏透明）。
  - **国漫风要点**: 你的“水墨背景图”必须**铺满整个屏幕**，包括状态栏后面。
  - **代码**: 在 Activity 的 onCreate 里必须写：
  - **坑**: 如果不处理 WindowInsets，你的“设置”按钮可能会被状态栏的摄像头挖孔挡住，或者被底部的导航横条遮住。记得给 TopBar 加 statusBarsPadding()。
    *. **后台上传的限制**
  - **坑**: Android 14 对后台进程杀得很凶。用户写了笔记，切出去回微信，你的 App 可能马上就被冻结了，导致上传失败。
  - **解**: **不要**只依赖 Kotlin Coroutines 的 scope.launch。上传任务**必须**交给 WorkManager (设置为 Expedited 任务)。只有 WorkManager 才能保证即使 App 死了，系统也会找时间把笔记发出去。

  ---

  ### 三、 关于 Root 功能的“安全与体验”

  *. **不要阻塞主线程 (ANR 警告)**

  - **坑**: Root 命令（Shell 命令）执行速度是不稳定的。如果你在 UI 线程直接调 su，界面会瞬间卡死（ANR）。
  - **解**: 所有 Root 操作必须在 Dispatchers.IO 中执行。
  - **库推荐**: 强烈建议使用 **libsu** (TopJohnWu 开发，Magisk 作者)。它能帮你维护一个全局的 Root Shell 会话，不用每次执行命令都重新申请 Root，速度快很多。

  ---

  ### 四、 架构与逻辑层面的 3 个建议

  *. **UUID 是你的救命稻草**

  - **场景**: 离线状态下，用户创建了一个笔记。此时服务器还没给它分配 ID (id\=null)。
  - **建议**: 在本地数据库中，除了 id (自增主键)，一定要加一个 **uuid (字符串)** 。
  - **逻辑**: 创建时生成 UUID -\> 存本地 -\> 上传时带上 UUID (如果 API 支持) 或者上传成功后，把服务器返回的 ID 更新回本地。**永远用 UUID 做本地 UI 的唯一标识**，防止同步时 ID 错乱。
    *. **图片保持原图，不丢元数据 ，不考虑流量和速度 **
  - **心态**: 你现在的计划很宏大（Root、国漫、离线）。

  ### 最后的彩蛋：关于“国漫风”的资源获取

  既然你要做UI，素材哪里来？

  - **中国传统色**: 推荐网站 nipponcolors.com (虽是日本色，但很多源自中国，且配色极佳) 或 http://zhongguose.com/。
  - **纹理**: 搜索关键词 "Rice Paper Texture Seamless" (宣纸无缝纹理)。
  - **图标**: 哪怕是 SVG 图标，也可以在 Compose 里通过 Modifier.alpha(0.8f) 配合 ColorFilter 做成淡淡的墨色，不一定非要找水墨画的 PNG。

  **背景**: 米白色，有隐约的纸张纤维感。
- **顶栏**: 没有巨大的色块，只有黑色的书法字“**随笔**”二字在左上角，右侧是极简的设置齿轮。
- **列表**: 瀑布流布局。每一条笔记是一个卡片，卡片边缘有一圈极细的淡墨色描边。
- **FAB**: 右下角悬浮着一个**朱红色的方形印章**，中间白色的“**记**”字。
- **操作**: 当你向下滑动查看旧笔记时，右侧的滚动条不是普通的灰条，而是一根**细细的红绳**或是**玉佩**在移动。

这种设计既保留了 Android 的流畅性，又极具中国美学辨识度。

## plan1ui相关2

设计一套\*\*“国漫风/新中式”**的 UI 库，核心在于**“神似而形不繁”\*\*。不要堆砌大红大绿的灯笼剪纸，要提取中国美学的核心元素：**水墨、留白、印章、窗棂、宣纸**，并结合现代 Android 的 **Material 3 (M3)**  规范进行重构。

既然你是 Android 14 + Jetpack Compose 开发，我们可以完全自定义组件。以下是这套 UI 库的设计指南：

---

### 1. 核心设计语言 (Design Token)

#### A. 色彩系统 (Color Palette)

抛弃 Material 默认的紫色系，使用**中国传统色**。

- **白天模式 (宣纸与墨)** :

  - **背景色 (Background)** : 草白 #F7F8F4 (模拟宣纸质感，不要纯白)
  - **表面色 (Surface)** : 米色 #FDFBF3
  - **主色/强调色 (Primary)** : 朱砂 #FF4C39 (用于按钮、印章) 或 黛蓝 #305169 (用于图标)
  - **文字色 (OnSurface)** : 焦茶 #383431 (比纯黑更柔和，像干掉的墨迹)
  - **次要文字**: 雅灰 #878885
- **黑夜模式 (漆器与流光)** :

  - **背景色**: 墨灰 #18191B (深沉的墨色，非纯黑)
  - **表面色**: 玄青 #22252A
  - **主色**: 赤金 #F2BE45 (暗夜里的灯火感) 或 荧光青 #00E5BC (赛博国漫感)
  - **文字色**: 银灰 #E0E0E0

#### B. 字体排印 (Typography)

- **标题 (Display/Headline)** : 使用**书法字体**（如：鸿雷板书简体、演示悠然小楷、江西拙楷）。

  - 注意: 只用在 Toolbar 标题、卡片大标题、印章文字。
- **正文 (Body)** : 保持系统默认的 Roboto 或 Noto Sans CJK，保证长时间阅读不累眼。但可以稍微调整 LineHeight (行高) 为 1.5 倍，增加呼吸感。

#### C. 形状与材质 (Shapes & Textures)

- **圆角**: 即使是现代风格，也建议使用较小的圆角 (e.g., 8.dp) 或者特殊的切角。
- **噪点 (Noise)** : 在背景中叠加一层极淡的噪点图片，模拟纸张的粗糙纹理。

---

### 2. 关键组件设计 (Component Library)

这是你需要封装的 Compose 组件库：

#### 1. InkSurfaceCard (水墨/宣纸卡片)

这是承载 Memos 内容的容器。

- **外观**:

  - 不要用阴影 (Elevation)，用**描边 (Border)**  或 **底色**。
  - 背景带有一点点宣纸纹理。
- **特效**:

  - **点击涟漪 (Ripple)** : 改写 RippleTheme，点击时不是扩散圆圈，而是一个**不规则的水墨晕开**效果 (可以用 Shader 或 Gif 实现，或者简单点用灰色的圆形但边缘模糊)。
- **代码思路**:

#### 2. SealButton (印章浮动按钮 - FAB)

这是发送/记录的入口。

- **形态**: 方形或圆形，红色底，白色字（篆体或楷体）。
- **文案**: 不用 "+"，用汉字  **"记"** 、 **"撰"** 、 **"书"** 。
- **交互**:

  - **按下**: 缩小并有类似盖章的顿挫感 (Scale Animation)。
  - **松开**: 播放一个粒子散开的动画（像印泥把纸压下去的感觉）。

#### 3. ScrollTextField (奏折/卷轴输入框)

- **外观**:

  - 去掉底部横线。
  - 添加背景：可以是**竖向的格线**（信纸模式）或者**空白留白**。
  - 光标 (Cursor) 颜色设为朱红色。
- **Compose 实现**: 使用 BasicTextField 进行完全自定义，在 decorationBox 里用 Canvas 画出信纸的横线或竖线。

#### 4. Dialog (窗棂弹窗)

- **边框**: 借鉴中国园林的花窗（海棠窗、六角窗）。
- **遮罩**: 弹窗背后的半透明遮罩，可以加一点**模糊 (Blur)**  效果，营造“隔窗观景”的朦胧感。

#### 5. QuickSettingsTile (下拉开关)

- **图标**: 设计一个简笔画的**毛笔**或**砚台**图标。
- **状态**:

  - 关：淡墨色。
  - 开：朱砂红。

---

### 3. 特效与动画 (Animation)

让 App 活起来的关键。

- **加载动画 (Loading)** :

  - 不要转圈圈。
  - 做一个**笔走龙蛇**的动画，或者一滴墨水滴入水中慢慢扩散的 Lottie 动画。
- **页面切换**:

  - **入场**: 页面像卷轴一样从右向左展开 (slideInHorizontally)，或者从下往上浮现配合透明度变化。
- **图片加载**:

  - 使用 Coil 加载图片时，Placeholder 不要用灰色方块。用一张**淡色的山水简笔画**作为占位图。

---

### 4. 具体的 Compose UI 架构建议

为了方便开发，建议你这样组织你的 UI 库代码结构：

### 5. 一个简单的视觉示例描述

想象一下你的 App 首页：

- **背景**: 米白色，有隐约的纸张纤维感。
- **顶栏**: 没有巨大的色块，只有黑色的书法字“**随笔**”二字在左上角，右侧是极简的设置齿轮。
- **列表**: 瀑布流布局。每一条笔记是一个卡片，卡片边缘有一圈极细的淡墨色描边。
- **FAB**: 右下角悬浮着一个**朱红色的方形印章**，中间白色的“**记**”字。
- **操作**: 当你向下滑动查看旧笔记时，右侧的滚动条不是普通的灰条，而是一根**细细的红绳**或是**玉佩**在移动。

这种设计既保留了 Android 的流畅性，又极具中国美学辨识度。



# plan2

 1. 输入效率（Input Efficiency）
系统分享意图（Share Intent）接入：
痛点：现在必须打开 App 或悬浮窗才能记录。但我如果在浏览器看到一段好文，或者在小红书看到一张好图，想存入 Memos？
方案：注册 android.intent.action.SEND。允许用户在其他 App 中选中文字/图片 -> 分享 -> 选择“心流” -> 直接弹起悬浮窗并自动填入内容 -> 发送。
价值：这是“信息收集”最核心的路径，比下拉 Tile 更高频。

2 语音转文字（Whisper/系统级）：
场景：走路、开车时产生灵感。
方案：在悬浮窗记录时增加录音按钮，调用 Android 系统语音识别，转为文字存入，并且同时存入录音文件

3  内容呈现（Content Presentation）
Markdown 渲染增强：
现状：文档提到“预览/标签处理”。
缺口：Memos 核心是 Markdown。检查是否支持 Checklist (任务列表)、代码块高亮、引用块。特别是 Checklist，对于“待办事项”场景非常重要。

4 视频/音频支持：
虽然你强调图片，但 Memos 支持视频。由于视频体积大，可以考虑仅做“占位符显示”或“调用系统播放器播放”，不一定内置复杂播放器，但不能让用户感觉文件丢失了。

5. 数据安全（Security）
生物识别锁（Biometric Auth） （这个可以在设置中打开）：
场景：日记（Diary）性质极强，内容私密。
方案：进入 App 时要求指纹/面容解锁，或者是输入软件的密码(Pin)

6. 热力图 
场景：Memos 网页版最标志性的就是“绿格子”热力图。
方案：在“归档”或“个人中心”页面，用“朱砂红”或“墨色”的深浅绘制一个 GitHub 风格的年度记录热力图
价值：直观反馈“坚持记录”的成就感，强化“心流”的持续性。
在菜单那边弄一个个人中心 （随笔的下边，已归档的上面，然后把设置放到最下面去））
个人中心里面可以有一个热力图，而且可以 点击热力图的某天查看某天的记录(按照时间正向排序（先显示 8点 再是9点， 10点这样）)
也可以长按某个日期，然后 拖动选着好几天，可以一起查看好几天的记录 (这里每拖动一天，都需要一个震动)

7 触感反馈 
(默认用户的手机是很好的那种线性马达，你把触感震动弄的细腻一点)
场景：下拉刷新、极速记录发送成功、长按排序。
建议：加入轻微的 Haptic Feedback（震动）。
发送成功：轻快的一顿（CONFIRM）。
归档：沉重的一顿（HEAVY_CLICK）。
这能增加 App 的“实体感”。

# plan3

主要是为了方便可以不登录 完全离线的用户使用


# 工程实施计划：Project Standalone (离线优先重构)

**项目名称**：心流 (1memos)  
**更新日期**：2026-01-19  
**核心目标**：解耦服务端依赖，实现“开箱即用”的本地化体验，并构建“先离线使用，后账号绑定”的数据迁移能力。

---

## 1. 核心架构重构 (Architecture & Data Layer)

此阶段是地基，必须在修改任何 UI 之前完成。核心在于建立“双主键”体系。

### 1.1 数据库 Schema 变更 (Room)
* **Entity调整 (`MemoEntity`)**
    * **主键变更**：确保 `@PrimaryKey` 为本地自增 ID (`localId/uid`)，而非服务端 ID。
    * **字段扩展**：
        * `remoteId` (Long, Nullable): 存储服务端 ID。`NULL` 代表仅本地存在。
        * `syncState` (Enum): 扩充状态机，需包含 `LOCAL_ONLY` (纯本地), `SYNCING` (同步中), `SYNCED` (已同步), `DIRTY` (本地修改待上传)。
    * **索引优化**：为 `remoteId` 建立索引，加快同步时的查找速度。

### 1.2 仓库层解耦 (Repository Pattern)
* **移除强制鉴权**：
    * 审查 `MemoRepository`，移除所有方法入口处的 `checkToken()` 或 `isLoggedIn()` 阻断检查。
    * 所有 CRUD 操作（增删改查）直接对 Room 数据库生效。
* **虚拟用户上下文 (Local Context)**：
    * 在 `DataStore` 或 `MemoryCache` 中维护一个当前会话状态。
    * 若未登录，注入 `MockUser` (ID=-1, Name="本地访客") 供领域层调用，防止 NPE。

---

## 2. 业务流程改造 (User Flow & Interaction)

此阶段关注用户进入 App 的“第一印象”和路径。

### 2.1 启动与引导 (Onboarding)
* **启动页逻辑分流**：
    * *原逻辑*：无 Token -> 跳转 LoginActivity。
    * *新逻辑*：无 Token -> 跳转 **WelcomeActivity** (新页面)。
* **WelcomeActivity 设计**：
    * 提供 [立即体验] 按钮：初始化本地环境，直接进入 MainActivity。
    * 提供 [登录账号] 按钮：跳转原 LoginActivity。

### 2.2 登录入口后置
* **设置页改造**：
    * 顶部新增“账号状态卡片”。
    * 离线状态下显示：“当前为离线模式，数据仅保存在本机”，并提供 [登录/绑定服务器] 按钮。

### 2.3 功能降级 (Feature Gating)
* **不可用功能屏蔽**：
    * 在离线模式下，隐藏“修改密码”、“服务器资源统计”等仅服务端有意义的功能。
    * 搜索、标签、归档、编辑等核心功能保持全量开放。

---

## 3. 同步与迁移机制 (Synchronization & Migration)

这是风险最高的环节。当一个记了 50 条本地笔记的用户决定登录服务器时，必须保证数据不丢、不乱。

### 3.1 账号绑定 Worker (`AccountBindingWorker`)
* **触发时机**：用户在离线模式下使用了 App，并在设置页成功登录后。
* **执行逻辑**：
    1.  **上行合并 (Merge Upstream)**：
        * 查询所有 `remoteId IS NULL` 的记录。
        * 将这些记录视为 `CREATE` 请求，逐条上传至服务器。
        * 获取服务器返回的新 ID，回写到本地 `remoteId`。
    2.  **下行拉取 (Fetch Downstream)**：
        * 执行标准的 `FullSync`，拉取服务器已有的历史数据。
        * 写入本地数据库（注意避免与刚上传的数据重复，需依靠去重逻辑）。

### 3.2 冲突预防策略
* **乐观锁**：上传修改时携带时间戳，若服务端时间更新，则提示冲突（V1.1 可简化为“最后写入优先”，但需记录日志）。
* **附件处理**：
    * 本地附件在绑定前路径为 `file:///data/...`。
    * 上传后，需更新为资源 ID，但本地缓存文件不应立即删除，而是通过 `cacheUri` 关联，避免重新下载。

---

## 4. 风险评估与测试清单 (QA Checklist)

### 4.1 关键风险点
* **ID 碰撞**：本地生成的 ID 和服务器 ID 混淆（必须严格区分 `localId` 和 `remoteId`）。
* **网络切换**：在绑定过程中（BindingWorker 运行中）断网。
    * *对策*：WorkManager 自带重试机制，需确保上传操作的幂等性（即重复上传同一条笔记不会导致服务器创建两条）。

### 4.2 验收标准
1.  **冷启动**：全新安装 -> 点击“立即体验” -> 进入主页 -> 能够写笔记、插图片 -> 重启 App 数据不丢。
2.  **后登录**：在上述基础上 -> 去设置页登录 -> 观察到本地笔记状态变为“已同步” -> 网页端能看到刚才写的笔记。
3.  **混合数据**：网页端已有 10 条，本地新写 5 条 -> 登录后 -> 列表应显示 15 条数据。

# plan4功能设计方案：心流 · 墨迹卡片 (Share Card)

## 1. 核心设计理念

- **克制的美感**：用户不需要调整几十个参数（如色值、边距），我们提供\*\*“策展级”\*\*的预设。怎么选都好看，避免用户配出“丑图”。
- **内容优先**：无论背景如何，文字的可读性永远第一。
- **品牌印记**：通过独特的排版、印章、字体，让别人一眼就能看出“这是用心流 App 生成的”。

---

## 2. 交互入口 (Entry Points)

为了保证“极速”与“易用”，建议设置两个入口：

1. **详情页顶部栏**：在笔记详情页的右上角菜单中，加入“生成卡片”或“分享”。
2. **长按列表项**：在首页列表中，长按某条笔记 -\> 弹出菜单 -\> 选择“分享图片”。

---

## 3. 卡片工坊界面 (The Studio UI)

点击入口后，不直接生成图片，而是进入一个**全屏预览编辑页**。

- **上部（70%区域）** ：实时预览区。显示当前的卡片效果。支持双指缩放查看细节。
- **下部（30%区域）** ：控制面板。采用**Tab切换**结构，分为 `模板`、`样式`、`更多`。

---

## 4. 核心功能模块设计

### 4.1 模板主题系统 (Themes) —— “好看”的关键

我们需要预设 4-5 款符合 App 调性的核心主题，让开发人员写死样式，用户点选即可。

**主题 A：【素履】（默认·极简）**

- **背景**：纯白或微微带暖的米色（#FDFBF7）。
- **文字**：深灰（非纯黑），衬线体（霞鹜文楷/思源宋体）。
- **特色**：极大的留白（Padding），文字居中或居左。底部居中放置一个小小的 Logo 和二维码。
- **适用**：短句、灵感、诗词。

**主题 B：【墨染】（夜间/深色）**

- **背景**：深岩灰或墨色（#1A1A1A）。
- **文字**：银灰或哑光金。
- **特色**：文字像是在黑夜中发光。非常有质感。

**主题 C：【宣纸】（拟物·国风）**

- **背景**：带淡淡纹理的宣纸素材图。
- **文字**：竖排版（如果技术允许，竖排最具国风，若太难则横排但增加行间距）。
- **装饰**：右上角或右下角自动生成一枚红色的“用户昵称印章”（仿朱砂印）。

**主题 D：【光影】（配图模式）**

- **逻辑**：如果笔记里有图片（附件），自动提取第一张图作为卡片背景。
- **处理**：图片上层必须加一层 40%-60% 透明度的蒙版（黑色或磨砂玻璃效果），确保上面的文字清晰可见。
- **适用**：旅行记录、摄影日记。

### 4.2 样式微调 (Customization)

在选定主题后，允许用户微调少量参数：

- **画布比例**：

  - `自适应`（默认，像长图）
  - `1:1`（适合 Instagram/朋友圈九宫格）
  - `9:16`（适合发 Story/抖音/全屏壁纸）
- **字体大小**：提供 `小 / 中 / 大` 三档即可，不要滑块。
- **对齐方式**：`左对齐` / `居中对齐`。

### 4.3 底部版权区 (Footer)

这是 App 传播的关键，设计要精致：

- **元素包含**：

  - **作者**：用户昵称（或生成的印章）。
  - **日期**：`2026.01.19` 或 `乙巳年 · 腊月`（国风日期转换，极加分）。
  - **来源**：`Written in 1memos` 或 `来自 心流`。
  - **二维码**：可选显示/隐藏。指向你的下载页或 GitHub。

---

## 5. 几个提升“高级感”的细节设计

告诉你的开发人员，这几个细节决定了是“工具”还是“艺术品”：

1. **智能长文截断**：

   - 如果笔记有 2000 字，卡片放不下怎么办？
   - **方案**：默认截取前 140 字，末尾加 `...`，并提示“长文模式”。如果用户选择“长文模式”，则生成长图，但要注意内存溢出问题。通常建议引导用户精简分享。
2. **动态印章（Signature Seal）** ：

   - **逻辑**：取用户昵称的最后一个字或前两个字，用“篆体”或“隶书”动态渲染成一个红色方块图片，放在落款处。
   - **效果**：这会给用户极大的归属感，仿佛这是他私人的书法作品。
3. **引用样式 (Quote)** ：

   - 如果笔记中包含 Markdown 的引用块（`> text`），在卡片中要特殊渲染。比如加上巨大的双引号 `“ ”` 水印作为背景装饰。
4. **图片圆角与阴影**：

   - 如果笔记包含多张图片，在卡片中以“拍立得”风格（白色边框+淡淡的阴影）排列展示，会比直接铺满更有生活感。

---

## 6. 给开发人员的执行简报 (Brief)

你可以直接把这段话复制给开发看：

> **[需求] 笔记图片导出 (Share Card)**
>
> **目标**：将当前的 Memo 内容（文字+图片）渲染为一张静态图片保存到相册。
>
> **技术实现建议**：
>
> 1. **View to Bitmap**：不建议用复杂的 Canvas 绘图。建议使用 Compose 自身的布局能力写好 UI，然后通过 `ComposeView` 离屏渲染或 `Picture` 类截取当前 View 生成 Bitmap。这样修改样式最快。
> 2. **模板化**：把样式参数（背景色、字体颜色、Padding、Logo 资源）抽取为 Data Class，方便后续新增主题。
> 3. **权限**：注意 Android 13+ 的相册写入权限处理。
> 4. **二维码**：集成 `ZXing` 或类似库动态生成包含下载链接的二维码。
> 5. **字体**：不仅 UI 要用霞鹜文楷，生成的图片里也必须确保存入该字体（需要把字体文件打包进 assets 并在绘图时加载）。

---

### 总结

你现在的 App 已经有了很好的“骨架”（离线、同步），这个图片导出功能就是它的“皮囊”和“华服”。

**建议第一版先做两个最经典的主题：**

1.  **【素履】** （纯白底+宋体字+二维码）—— 只有文字时用。
2.  **【光影】** （图片高斯模糊背景+白字）—— 有图时自动切换。

这足以覆盖 90% 的场景，且开发成本可控。你觉得这个“印章”和“国风日期”的点子怎么样？



# plan5 优化


非常精彩。我已经仔细审阅了这份 1memos v1.4.5 的项目总结。
总评：
这是一份成熟度极高的工程文档。你不仅仅是在写代码，更是在打磨一个“产品”。从架构选型（Jetpack Compose + Clean Architecture）到核心难点攻克（离线优先的 ID 映射策略、IME 键盘适配、长图渲染内存管理），都显示出你不仅具备深厚的 Android 开发功底，还拥有优秀的产品思维（如“国风克制 UI”、“录与记的呼应”）。
特别是你对 “离线优先 (Offline-first)” 数据模型的处理，以及 墨迹卡片 (Share Card) 的兜底策略，都是很多高级工程师容易踩坑的地方，而你已经有了成熟的解决方案。
为了助你将这个项目从“优秀”推向“极致”，我基于你的文档进行了深度的架构评审，以下是我的分析与建议：
Phase 1: 架构亮点评审 (Architecture Highlights)
数据一致性设计的智慧 (localId vs uuid vs serverId)
点评：这是离线优先应用最核心的护城河。很多开发者试图用一个 ID 统管所有，导致本地未同步数据与服务端数据冲突。你采用的三级 ID 策略（本地主键、业务唯一键、服务端锚点）非常经典且稳健。
优势：彻底解耦了本地数据库操作与网络同步逻辑，INSERT IGNORE + UPDATE 策略有效防止了因同步机制导致的级联删除（如附件丢失）。
用户体验的微观打磨
输入法适配：在 Android Compose 中，WindowInsets 和键盘动画的同步一直是个痛点。你能针对 Android 13+ 做 imePadding 和 ScrollTextField 优化，说明你非常在意“手感”。
Markdown 解析策略：将解析移至后台线程 + 占位符，这是保持列表滚动 60fps 的关键。
墨迹卡片 (Share Card) 的工程化
OOM 防护：对长图导出做“分页”和“单张上限”的降级处理，体现了极好的容错思维。离屏渲染（ComposeView to Bitmap）是目前 Compose 生态下生成分享图的最佳实践。
Phase 2: 潜在风险与优化建议 (Critical Analysis)
尽管现状很棒，但作为架构师，我看到了几个值得深思的扩展性与健壮性问题：
1. 同步冲突解决策略 (Conflict Resolution)
现状：文档提到了状态机（DIRTY, SYNCING），但未详细说明“冲突”发生时怎么办。
场景：用户离线修改了 Note A，同时服务器上的 Note A 也被另一个设备修改了。现在的逻辑是“最后写入优先（LWW）”还是“本地覆盖服务端”？
建议：
如果逻辑简单，Last Write Wins (基于时间戳) 是最经济的选择。
如果需要更强的数据安全性，建议引入Mermaid 流程图来明确定义冲突时的“分支逻辑”（例如：保留本地副本作为 conflict copy）。
2. 大数据量下的列表性能 (Scalability)
现状：目前通过后台线程解析 Markdown 优化了卡顿。
风险：如果用户有 5,000+ 条 Memos，一次性加载进 Room 或内存会造成启动慢。
建议：是否考虑引入 Paging 3？结合 Room，可以实现无限滚动且内存占用极低。虽然增加了复杂度，但对于“笔记”类应用，这是长久之计。


# plan6：主页 120Hz 掉帧治理（预计算预览/索引 + Baseline Profile）

> 你已明确：可接受“大刀阔斧”的改动，并希望把更好的设计与未来可持续的方案一起做上去。
> 已确认：采用 1B（主页列表滚动中只展示纯文本 `plainPreview`；停止滚动并停稳 ~200ms 后切换为 Markdown 样式预览）。
> 本 plan6 的目标是“治本 + 可量化 + 可维护”：把重活搬离滚动路径，并用基准测试/基线配置让 Release 体验稳定可复现。
> 补充说明（关于“是否一劳永逸”）：
> - Baseline Profile：不是一次性，但可以流程化（每次依赖/关键路径大改后重录），收益稳定可复现。
> - 预计算派生字段：更接近“一劳永逸”的架构治本，后续主要靠“写入时同步更新 + 版本号 + 回填兜底”维护一致性。

---

## 0. 背景与问题复盘（为什么主页滑动最掉帧）

### 0.1 现象
- 主页快速滑动历史记录：掉帧明显（尤其在 120Hz 设备上），CPU 占用持续偏高。
- 其它页面相对正常（或不如主页明显）。

### 0.2 根因假设（按影响力排序）
1. **滚动路径仍在做“昂贵计算”**：Markdown 解析（commonmark parse + 结构遍历）、标签提取（regex）、预览截断等，在列表项不断进入/退出屏幕时反复触发。
2. **列表项布局/排版复杂**：多段 Text、FlowRow（标签/多图）在 120Hz 下更容易超过 8.3ms/帧预算。
3. **图片解码/缓存/GC 争用**：缩略图大量出现时解码与内存压力上升，导致掉帧或 CPU 飙高。
4. **过滤/搜索在 Kotlin 层进行**：`PagingData.filter` 对每条 memo 做 `lowercase/contains/Regex` 等字符串操作，数据量上去会吃 CPU（虽不一定都发生在滑动时，但会放大整体负载）。

### 0.3 设计原则（长期收益）
- **滚动路径只做“读取 + 轻量渲染”**：不做 parse、不做 regex、不做大对象分配。
- **把可重复计算的结果存起来**：内容变更（新建/编辑/同步）时预计算并持久化（Room），主页只读预计算字段。
- **可量化**：用 Macrobenchmark/Jank 指标把“体感”变成可对比的数据，避免靠感觉回归。
- **可回滚**：新字段/新索引出问题时，有“降级到旧逻辑”的兜底开关与回填策略。

---

## 1. 目标与验收标准（可量化）

### 1.1 体验目标
- 120Hz 设备：主页快速滑动时体感“接近系统应用”，不再明显拖影/卡顿。
- CPU：滑动期间 CPU 峰值降低，且停止滑动后能快速回落（避免后台持续高占用）。
- 电量：避免后台长时间高负载（回填任务分批、可暂停）。

### 1.2 可测指标（建议在 Release + BaselineProfile 下测）
- **Jank**：Macrobenchmark `FrameTimingMetric`/`JankMetric`（滑动 5~10 秒）。
- **帧耗时分位数**：P50/P90/P95 帧时间（目标：P90 更接近 120Hz 预算）。
- **CPU 占用趋势**：Perfetto/Android Studio Profiler（滑动前/中/后对比）。

### 1.3 交付边界（本 plan6 聚焦）
- 聚焦主页列表性能（随笔/归档两套列表逻辑都覆盖）。
- 不强制改 UI 风格（国风视觉保持），但允许对主页卡片布局做“性能向”取舍（例如标签/多图展示策略）。

---

## 2. 总体方案（两条腿走路）

### 2.1 方案 A（治本）：预计算预览/索引（Room 持久化）
把以下内容从“滚动时计算”迁移到“内容变更时计算”：
- `plainPreview`：主页展示用的纯文本预览（固定策略：最多 N 字/最多 N 行）。
- `tags`：从 memo content 提取的标签集合（稳定去重、保持顺序）。
- （可选）`plainContent`/`searchTokens`：用于搜索/筛选的索引字段（或独立 FTS 表）。

收益：主页渲染不再触发 Markdown parse/regex；数据量变大也不线性拖垮 UI。

### 2.2 方案 B（稳态收益）：Baseline Profile + Macrobenchmark
- Baseline Profile：让 Release 的热点路径（启动、主页滑动、打开详情等）提前 AOT 编译，降低解释/JIT 成本，让 CPU 更稳、更省电。
- Macrobenchmark：把“主页滑动”做成可重复的基准场景，后续每次优化/重构都能对比回归。

注意：Baseline Profile 不能替代算法优化，但能让“已经做对的事情”发挥更稳定。

---

## 3. 里程碑（Milestones）

### M0：基线与可观测性（1 天）
- 明确“掉帧来源”与“滑动期间最重的工作”在哪里（主线程/后台线程/GC）。
- 确定验收指标与测试方法（Macrobenchmark/Perfetto）。

### M1：数据层预计算（2~4 天）
- Room schema 增加派生字段（预览/标签/版本号）。
- 新建派生字段生成器（纯 Kotlin、可单测、与 UI 解耦）。
- 新建回填 Worker：升级后后台批量回填派生字段（可暂停、可重试）。

### M2：主页 UI 降负（1~2 天）
- Home 列表项直接使用预计算字段，不再在 UI 内做 TagExtractor/MarkdownPreview。
- 标签/多图展示策略做性能向收敛（上限、折叠、延迟渲染）。

### M3：把“过滤/搜索”尽量下沉到数据库（可选但推荐，2~5 天）
- 把 `PagingData.filter` 的文本/标签筛选迁移到 SQL（减少 Kotlin 字符串计算）。
-（可选）引入 FTS5/索引表，搜索性能和可扩展性更强。

### M4：Baseline Profile + Macrobenchmark（需要真机/模拟器，2~4 天）
- 新增 `macrobenchmark`/`baselineprofile` 模块与脚本。
- 录制并生成基线 profile；接入到 Release 构建。
- 将“主页滑动”基准固化为自动化测试用例。

### M5：回归、灰度与交付（1~2 天）
- 全量门禁：`testDebugUnitTest + lintDebug + assembleDebug`。
- 打包 Debug APK（带时间戳）给你真机验证；必要时再给 Release/Benchmark 包验证。

---

## 4. 详细实施方案（非常细化）

### 4.1 M0：基线与定位（必须先做，不盲改）
1. 明确测试场景（至少 3 个）：
   - 场景 A：主页随笔列表，快速滑动 10 秒。
   - 场景 B：主页归档列表，快速滑动 10 秒。
   - 场景 C：在主页停止滑动后停留 5 秒（观察 CPU 是否回落/是否仍在后台忙）。
2. 打开性能观测：
   - Android Studio Profiler（CPU + Memory），关注：解析线程/GC 次数。
   - Perfetto（可选）：抓 UI thread、RenderThread、Binder、GC。
3. 产出“瓶颈报告”（写入项目总结/或 plan6 的附录）：
   - Top 3 CPU 热点方法（例如 Markdown parse/TagExtractor/图片解码）。
   - Jank 集中发生的阶段（滑动中/停止后/首帧）。

交付物：一张表格（场景 -> CPU 现象 -> 关键热点 -> 结论）。

---

### 4.2 M1：数据模型改造（预计算派生字段）

#### 4.2.1 Room Schema 设计（建议方案）
目标：让主页所需数据“直接可用”，不再依赖运行时解析。

对 `MemoEntity`（`1memos/app/src/main/java/cc/pscly/onememos/core/database/entity/MemoEntity.kt`）新增字段：
- `plainPreview: String`（非空，默认空串）
- `tagsText: String`（非空，默认空串；例如 `\nwork\nlife\n`，便于 LIKE 精确匹配）
- `derivedVersion: Int`（非空，默认 0；用于后续算法升级时回填）
- `derivedAt: Long`（非空，默认 0；记录派生字段生成时间，便于排障/调试）

可选增强（如果你确定要做“搜索下沉到 DB”）：
- 新增 FTS 表（`memos_fts`）：存储 `uuid + plainContent`（或 token），支持 MATCH/LIKE。
- 或新增 join 表（`memo_tags`）：`memoUuid + tag` 并建索引，用于高性能标签过滤与统计。

推荐决策：
- **第一阶段（M1/M2）先上 `plainPreview + tagsText`**（收益最大、改动可控）。
- **M3 再决定 FTS/join 表**（基于你的数据量与搜索使用频率）。

#### 4.2.2 派生字段生成器（与 UI 解耦）
当前 plain text/preview 的逻辑在 UI 层 `MarkdownPaper.kt`，长期不利于复用与测试。

计划新建（建议位置）：
- `1memos/app/src/main/java/cc/pscly/onememos/domain/markdown/MarkdownDeriver.kt`
  - `fun plainText(markdown: String): String`
  - `fun plainPreview(markdown: String, maxChars: Int, maxLines: Int): String`
  - 注意：这里只处理“文本”，不引入 Compose 依赖，保证 data/domain 层可直接调用。

标签提取复用现有：
- `TagExtractor.extractAll(content)`

派生字段结构（建议 DataClass）：
- `MemoDerivedFields(preview: String, tags: List<String>, version: Int)`

#### 4.2.3 何时计算（确保一致性）
必须覆盖所有“content 可能变化”的路径：
1. 本地新建 memo（createLocalMemo）
2. 本地编辑 memo（update content）
3. 同步回拉/更新 memo（服务端内容变化）
4. 归档/取消归档（不改 content，但可能影响列表；无需重算派生字段）

原则：任何写入 `memos.content` 的地方，都要同时更新派生字段（同一事务）。

---

### 4.3 M1：数据库迁移与回填（避免升级后一次性卡死）

#### 4.3.1 Room Migration
- `OneMemosDatabase` 版本从 `7 -> 8`（或更高）。
- 新增 `MIGRATION_7_8`：对 `memos` 表 `ALTER TABLE ADD COLUMN ... DEFAULT ...`。
- 如果引入新表（FTS/join），在 migration 内创建表与索引。

#### 4.3.2 回填策略（渐进、可暂停）
升级后老数据没有 `plainPreview/tagsText`，需要回填。

计划新增 Worker：
- `RebuildMemoDerivedFieldsWorker`
  - 每次处理一小批（例如 50 条），避免长时间占用 CPU。
  - 使用 `Dispatchers.Default.limitedParallelism(1~2)` 控制并行度。
  - 支持“断点续跑”：记录 lastProcessedLocalId 或分页 offset。
  - 允许用户在设置页手动触发/暂停（面向你这种 power user）。

约束建议：
- 默认不要求网络；但可设置 `requiresCharging`（可选，取决于你是否介意电量）。
- 避免在启动首帧就立刻跑满 CPU：可以延迟 30~60 秒再启动回填。

兜底：
- 如果回填未完成，主页展示仍能工作：
  - `plainPreview` 为空则临时使用 `markdown -> fastPreviewText` 的超轻量兜底（但要限制只对“当前屏幕少量 item”做）。

---

### 4.4 M2：主页 UI 降负（滚动路径彻底变轻）

#### 4.4.1 主页卡片文本策略（已选 1B，强烈推荐）
核心原则：滚动时只做“轻量渲染”，停稳后再做“富渲染”。

默认行为（1B）：
- 滚动中：只展示 `plainPreview`（纯文本），绝不渲染 Markdown（不 parse / 不 layout 富文本）。
- 停止滚动：等待 200ms（防止短暂停顿抖动），再切换为 Markdown 样式预览（复用 MarkdownPreview 的缓存与限并行）。
- 详情页：始终使用 Markdown 渲染（阅读体验不变）。

建议增加设置项（给你可控的“省电/观感”开关）：
- 主页预览模式：
  - 纯文本（最流畅、最省电）
  - 自动（滚动纯文本 + 停稳 Markdown，默认）
  - 始终 Markdown（不推荐，耗电且更易掉帧）

实现要点（Compose）：
- 以 `LazyListState.isScrollInProgress` 作为滚动信号；
- 用 `snapshotFlow { listState.isScrollInProgress }` 做状态流，`false` 后 `delay(200)` 再切到 Markdown；
- 列表 item 用 `key = memo.id/uuid` + `contentType`，减少重组与测量抖动；
- Markdown 预览必须“可被取消”：滚动开始立刻降级为纯文本，避免后台还在 parse。

#### 4.4.2 标签展示策略（减少 FlowRow 压力）
当前每个 item 都在 UI 内 `TagExtractor.extractAll(memo.content)`，且 FlowRow 排版成本高。

改造后：
- 从 domain/model 直接拿 `memo.tags`（由 `tagsText` 解析），UI 不再跑 regex。
- 展示上限：只展示前 3~5 个标签，其余用 “+N” 收起（避免 FlowRow 过长）。
-（可选）标签 Chip 改成更轻的样式（少阴影、少边框），降低 draw。

#### 4.4.3 图片缩略图策略（避免解码风暴）
- 已做：缩略图关闭 crossfade 并缓存 ImageRequest（保留）。
- plan6 可进一步增强：
  - 缩略图尺寸严格固定（避免多尺寸解码）
  - 多图时最多展示 2 张缩略图，其余显示 “+N”
  - 滑动中暂停预取（可选，Coil 支持通过 `ImageRequest` 配置）

---

### 4.5 M3：过滤/搜索下沉到 SQL（可选但推荐）

目的：减少 Kotlin 层 `PagingData.filter` 的 per-item 字符串计算，数据量越大越明显。

#### 4.5.1 标签过滤下沉
方案 1（低成本）：用 `tagsText` 做 LIKE 精确匹配
- 存储格式：`\nTAG1\nTAG2\n`
- SQL：`tagsText LIKE '%\n' || :tag || '\n%'`
- 优点：实现快；缺点：LIKE 无法走索引，超大数据量仍需扫表。

方案 2（推荐，长期）：join 表 `memo_tags`
- 表结构：`memoUuid TEXT, tag TEXT`，主键 `(memoUuid, tag)`，索引 `tag`。
- 标签过滤：通过子查询/join 快速定位 uuid 集合。
- 标签统计：直接 SQL `GROUP BY tag`，不再需要读 1000 条到内存算。

#### 4.5.2 文本搜索下沉
方案 1：LIKE（适配中文最直观）
- 把 query 拆成 token，在 SQL 层做多条件 LIKE（AND）。

方案 2：FTS5（更强，但中文分词有局限）
- 用 `unicode61` tokenizer（英文/数字效果好），中文可能仍需要 LIKE 兜底。
- 实现策略：`memos_fts(uuid, plainContent)`，写入/同步时维护。

#### 4.5.3 正则搜索策略
- 正则本质很重：
  - 继续保留为“高级模式”，但要给明确提示（耗电/可能卡顿）。
  - 执行时放到后台，并限制频率/结果数。

---

### 4.6 M4：Baseline Profile + Macrobenchmark（长期收益与可量化）

#### 4.6.1 前置条件（需要你确认环境）
- 需要一台真机/模拟器可跑 instrumentation（USB 或无线 adb 均可）。
- Release/Benchmark 需要可安装（debug keystore 或你本机签名）。

#### 4.6.2 工程改造
- 新增模块：`macrobenchmark`（或 `benchmark`）
  - 用 UIAutomator/ComposeTestRule 自动打开 App、进入主页、连续滑动、打开一条 memo 再返回。
- 新增模块：`baselineprofile`
  - 用 `androidx.baselineprofile` 插件生成 profile。
- app 侧接入 `profileinstaller`（Release 自动安装 profile）。

#### 4.6.3 基准用例设计（必须覆盖主页）
- Case 1：冷启动 -> 进入主页（StartupMetric）。
- Case 2：主页连续滑动 10 秒（FrameTiming/JankMetric）。
- Case 3：打开详情 -> 返回 -> 再滑动（模拟真实使用链路）。

产出物：
- baseline profile 文件入库（Release 受益）。
- benchmark 报告（用来对比“改造前/后”的 jank 指标）。

---

### 4.7 测试、门禁与交付

#### 4.7.1 单测（必须补）
- `MarkdownDeriver`：输入多种 Markdown，验证 `plainPreview` 截断策略稳定、无异常字符。
- `TagExtractor` + tagsText 编解码：确保中文/短横线/下划线都稳定。
-（可选）Room migration test：`7->8` 数据不丢、字段默认值正确。

#### 4.7.2 构建门禁（保持现有习惯）
- `.\\gradlew.bat testDebugUnitTest --stacktrace`
- `.\\gradlew.bat lintDebug --stacktrace`
- `.\\gradlew.bat :app:assembleDebug --stacktrace`

#### 4.7.3 APK 交付
- Debug APK 带时间戳：`yyyy-MM-ddTHH-mm-ss.apk`
- 你真机验证后再决定是否做 Release + BaselineProfile 的正式交付。

---

## 5. 风险清单与应对（成熟方案必须有）

1. **DB 迁移/回填导致的耗电**：
   - 分批处理 + 限并行 + 可暂停；默认延迟启动回填。
2. **派生字段与 content 不一致**：
   - 强制“写 content 必写派生字段”；对旧数据回填兜底。
3. **搜索下沉导致 SQL 复杂度上升**：
   - M3 做成可选里程碑；先用 tagsText/LIKE 方案验证收益，再升级 join/FTS。
4. **Baseline Profile 引入门槛**：
   - 需要设备与一次性配置；先做 Macrobenchmark（量化）再补 profile（收益）。

---

## 6. 版本策略与提交规范（建议）
- plan6 属于“大功能/大重构”：建议 minor 升级（例如 `1.8.0`）。
- commit 采用 Conventional Commits：
  - `perf:`（性能优化）
  - `refactor:`（结构重构）
  - `feat:`（新增 FTS/benchmark 等）
  - `test:`（测试补齐）
  - `docs:`（文档/plan 更新）

---

## 7. 决策确认与落地顺序（已确认/待确认）

### 7.1 已确认
1. 主页列表预览策略：**1B**
   - 滚动中纯文本 `plainPreview`
   - 停止滚动且停稳 ~200ms 后切回 Markdown 样式预览

2. 改动风格：允许大刀阔斧（以“主页 120Hz 丝滑 + CPU 降下来”为第一优先级）

### 7.2 待确认（但不阻塞 M1/M2）
1. Baseline Profile / Macrobenchmark（M4）：
   - 推荐做：它能把“Release 的启动/滚动”性能固化下来，避免“这版顺滑、下版又退化”。
   - 需要一次 instrumentation 跑分（真机或模拟器均可）。
   - 你如果暂时不方便，也不影响 M1/M2 的治本收益：我们先完成预计算 + 1B UI，后续再补录 profile。

### 7.3 最小落地顺序（建议）
- 先做 M1（派生字段入库 + 迁移 + 回填 Worker 框架）
- 再做 M2（主页 1B 渲染策略 + 标签/缩略图展示减负）
- 最后视你方便程度做 M4（Macrobenchmark + BaselineProfile）



## plan7 锦囊模式

### 接口文档（后端已实现）

- APK 对接版（最推荐）：`apidocs/collections.zh-CN.md`
- OpenAPI 快照（机器可读）：`apidocs/openapi-v1.json`
- 全量 API 文档与 sync 协议：`apidocs/api.zh-CN.md`
  - Collections 管理接口：`/api/v1/collections/*`
  - Sync：`/api/v1/sync/push|pull`，资源名 `collection_item`，pull 固定返回 `changes.collection_items`

apk 端

锦囊 (收纳箱 那种感觉) （实际上算是一种文件夹，这样子我可以快速访问到一些我可以标记的笔记 （这样的话会比置顶更方便））
而且这种锦囊里面还可以再创子文件夹  （例如  做饭相关，然后我把笔记放进去  （然后这里面我又可以放笔记，或者说在这里创个新的文件夹 例如烤肉相关  （这些地方都可以文件夹和笔记共存）  ）       ）  最好这个锦囊还可以支持文件夹和笔记都可以单独配色之类的，  而且还要支持批量管理相关
（锦囊相关的数据放在中转后端而非 memos （memos 只是负责存储笔记，而这个锦囊的结构 和数据（例如是 笔记 的 id）由 xinliuend（中转后端） 负责处理，  ）

收纳箱 (文件夹) -> “格)。
文件夹：表现为一个个精致的**“锦盒”或“竹简卷”**。
笔记：表现为展开的**“宣纸片”或“书锦盒”** 或 “抽屉”。
视觉上是一个带有精美纹样（云纹、回形纹）的盒子。

层级导航 (Breadcrumbs):
顶部不要用 `/home 或 “玉牌”。
视觉上是一张张贴在架子上的宣纸，/cooking/bbq` 这种路径。
设计一个**“连廊”**式的导航条或者挂着的木牌。

整体视图 -> “多宝阁” (博古架)。
一个错落有致的架子，格子里既可以放盒子（子文件夹），也可以直接展示玉牌（笔记）。

. UI/UX 设计方案 (前端表现)
A。例如：首页 > 膳食 > 炙烤。点击路径上的节点会有水墨晕染. 布局：错落有致的瀑布流/网格 (Staggered Grid)
**的回退动画。
配色 (Custom Coloring):
你提到了单独配色。在 UI 上，可以表现为**“系在盒子上的丝带颜色”或者“书页的边框混合排布**：
支持“盒子”和“笔记”混排。
色”**。
用户选择颜色时，给出中国传统色板（如：胭脂、群青、石绿），而不是 RGB 取色盘。
视觉交互细节
打开文件夹盒子**稍微大一点，显得厚重（带有阴影或立体感）。
笔记稍微轻盈一点（宣纸质感）。
视觉层级：
当你**: 点击“锦盒”，盒子盖子打开（简单的位移动画），然后镜头推进，进入下一层级。
点击一个“锦盒”时，不要做普通的页面跳转（Activity Slide）。
动画：做一个**“开箱”或者“镜头拉近”*的动画。背景虚化，当前盒子内容浮 拖拽归档:
长按笔记，笔记缩成一个小纸团。
拖动到“锦盒”上，锦盒微微震动（Haptic Feedback），松手后现出来。
面包屑导航：顶部显示路径，不要用 /root/folder/sub，用 印章链 的形式展示路径（例如：首页 > 烹饪 > 烤肉，每个节点都是一个红色小印章）。
B. 配色系统：扎染与流纸团飞入盒中。



#### 3. UI 交互设计 (Compose)

**布局结构**:

**具体实现细节**:

*. **无限层级实现**:

- 不需要递归渲染 View。
- 维护一个状态 var currentFolderId by remember { mutableStateOf\<String?\>(null) }。
- 点击文件夹时，更新 currentFolderId，Repository 自动通过 Flow 刷新列表数据。
  *. **批量管理与拖拽**:
- **进入收纳箱**: 从 Memos 列表长按笔记 -\> 选择“放入锦囊” -\> 弹窗选择文件夹 -\> 调用后端 POST /api/v1/collections/items（在线）或 POST /api/v1/sync/push（离线同步，resource=collection_item）。
- **箱内移动**: 长按 Item -\> 拖拽。

  - 如果是拖动排序：直接交换位置。
- 如果是拖入另一个文件夹：检测到重叠时，高亮目标文件夹，松手后调用后端 PATCH /api/v1/collections/items/move 更新 parent_id/sort_order。
    *. **颜色继承与独立配色**:
- 设计逻辑：ItemColor 优先显示 self.color。
- 如果 self.color 为空，可以设为默认色，或者继承父文件夹颜色的淡化版（做成渐变系）。

---

### 第三部分：国漫风 UI 特化设计

结合你的需求，这里是具体的视觉落地建议：

*. **面包屑 (Breadcrumb) ->**   **“竹节导航”**

- 路径不要用文字 / 分隔。
- 用**红线**串起一个个小的**玉珠** 或**印章**。
- 例如：(主页) ---红线--- [印章:做饭] ---红线--- [印章:烤肉]。
  *. **文件夹 (Folder) ->**   **“锦盒”**
- **未单独配色时**: 使用米白色/淡墨色，表面带有隐约的“云雷纹”。
- **单独配色时 (e.g. 红色)** : 盒子变成**漆器风格**（朱红底色），或者是在盒子封口处系了一根**红绳**。
- **打开动画**: 点击时，盒子盖子向上滑开，内容像烟雾一样飘出来，转场进入下一级。
  *. **空状态 ->**   **“空空如也”**
- 如果文件夹是空的，画一个**打开的空柜子**，或者一只**在睡觉的水墨猫**。

---

### 第四部分：异常处理 (Ghost Handling)

这是双后端架构最容易出问题的地方，必须处理：

- **场景**: 用户在 Memos 原生端删除了笔记 A，但你的收纳箱里还有笔记 A 的引用。
- **表现**: LEFT JOIN 结果中，memoContent 为 NULL。
- **UI 处理**:

  - 不要崩溃，不要隐藏。
  - 显示为一个\*\*“残卷”\*\*（带裂痕的图标）。
  - 标题显示：“原笔记已佚失”。
  - 点击后提示：“源笔记已被删除，是否移除此引用？”

---

### 总结开发路线

*. **后端 (Xinliuend)** : 建表 collection\_items，写好 CRUD 和 sync 接口（接口文档：`apidocs/collections.zh-CN.md`）。
*. **App 数据层**: 建立 CollectionEntity，写好 Room 的 Relation 查询（把结构和内容拼起来）。
*. **App 逻辑层**: 实现“扁平数据转树形”或“按层级查询”的 Repository。
*. **App UI**: 用 LazyVerticalGrid 做多宝阁界面，配合 currentFolderId 状态切换层级。
*. **美化**: 加上“锦盒”、“印章导航”和“开箱动画”。

