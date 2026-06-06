# Tasks

## Phase 1: 构建系统精简 - 移除 Java 及相关插件依赖

- [x] Task 1: 修改主构建文件移除 Java 依赖
  - [x] 修改 `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps`，移除所有 Java 相关模块依赖
  - [x] 移除的依赖包括：`java/compiler/*`、`java/debugger/*`、`java/execution/*`、`java/idea-ui`、`java/java-impl`、`java/openapi`、`java/plugin`、`java/ide-resources`、`java/ide-customization`、`java/testFramework`、`java/langInjection`、`java/manifest`、`java/typeMigration`、`java/structuralsearch-java`、`java/java-terminal` 等所有 `java/` 下的模块
  - [x] 移除 Java 相关插件：`plugins/junit`、`plugins/testng`、`plugins/maven`、`plugins/gradle`、`plugins/groovy`、`plugins/javaFX`、`plugins/ant`、`plugins/ByteCodeViewer`、`plugins/coverage`、`plugins/java-decompiler`、`plugins/java-i18n`、`plugins/ui-designer`、`plugins/eclipse`、`plugins/stream-debugger`、`plugins/jshell`、`plugins/lombok`、`plugins/rareJavaRefactorings`、`plugins/gradle-maven`、`plugins/java-features-trainer` 等
  - [x] 移除 Android 模块：`android/*` 相关依赖
  - [x] 移除 Python 模块：`python/*` 相关依赖
  - [x] 移除 Kotlin 插件：`plugins/kotlin` 相关依赖
  - [x] 移除不必要的 ML 插件：`plugins/completion-ml-ranking`、`plugins/completion-ml-ranking-models`、`plugins/search-everywhere-ml`、`plugins/marketplace-ml`、`plugins/ml-local-models`、`plugins/findUsagesMl`、`plugins/turboComplete`、`plugins/filePrediction`
  - [x] 移除性能测试插件：`plugins/performanceTesting`
  - [x] 移除不必要的平台插件：`plugins/ide-features-trainer`、`plugins/stats-collector`、`plugins/evaluation-plugin`、`plugins/remote-control`、`plugins/compilation-charts`、`plugins/configuration-script`、`plugins/commander`、`plugins/copyright`、`plugins/emojipicker`、`plugins/webp`、`plugins/xpath`
  - [x] 同步修改 `main-tests` 和 `main_test_lib` 的 `runtime_deps`

- [x] Task 2: 验证构建系统精简
  - [x] 检查 `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 只保留核心平台模块和 Git、Terminal、Agent Workbench、Markdown、yaml/json/toml 等必要插件
  - [x] 确认保留的 Git 相关：`plugins/git4idea`、`plugins/git-features-trainer`、`plugins/git-modal-commit`、`plugins/github/*`、`plugins/gitlab/*`、`platform/vcs-api`、`platform/vcs-impl`、`platform/dvcs-*`
  - [x] 确认保留的核心插件：`plugins/terminal`、`plugins/agent-workbench`、`plugins/markdown`、`plugins/yaml`、`plugins/toml`、`json`、`plugins/editorconfig`、`plugins/textmate`
  - [x] 确认保留的平台模块：`platform/boot`、`platform/bootstrap`、`platform/platform-api`、`platform/platform-impl`、`platform/core-api`、`platform/core-impl`、`platform/editor-ui-api`、`platform/welcome-screen` 等

## Phase 2: Light 轻量启动模式实现

- [x] Task 3: 实现 Light 启动模式核心逻辑
  - [x] 在 `/workspace/platform/core-api/src/com/intellij/openapi/application/` 中新增 `LightModeService` 接口
  - [x] 在 `/workspace/platform/core-impl/src/com/intellij/ide/` 中新增 `LightModeServiceImpl` 实现
  - [x] 实现 Light 模式判断逻辑：通过命令行参数 `--light` 或启动参数激活
  - [x] Light 模式下跳过项目模型加载、索引初始化
  - [x] 在 `/workspace/platform/bootstrap/src/com/intellij/idea/Main.kt` 中修改启动流程，检测 Light 模式
  - [x] Light 模式启动时直接打开 LightEditorFrame，跳过完整 IDE 启动

- [x] Task 4: 实现 Light 编辑界面
  - [x] 在 `/workspace/platform/platform-impl/src/com/intellij/ide/light/` 中新增 `LightEditorFrame` 组件
  - [x] 实现简洁的编辑器窗口：标题栏 + 编辑区 + 状态栏，类似记事本
  - [x] 支持标准文件操作：打开（Ctrl+O）、新建（Ctrl+N）、保存（Ctrl+S）、另存为
  - [x] 支持多标签页编辑
  - [x] 在 Light 窗口标题栏提供"切换到完整模式"按钮
  - [x] 注册快捷键加速器到菜单项

- [x] Task 5: 连接 Light 模式与完整模式切换
  - [x] 实现切换功能：当用户选择"切换到完整模式"时，提示确认并退出重启
  - [x] 切换时通过 System.exit(0) 触发重启，重新进入完整模式

## Phase 3: AI Agent 主界面参照 Air 风格改造

- [x] Task 6: 改造 Agent Sessions Tool Window 布局
  - [x] 修改 `/workspace/plugins/agent-workbench/sessions-toolwindow/src/ui/AgentSessionsToolWindow.kt`
  - [x] 参考 Air 界面：左侧垂直列表展示会话，每个会话项显示图标、标题、摘要、时间
  - [x] 添加搜索框在顶部过滤会话
  - [x] 添加"新建会话"按钮（+ 图标）
  - [x] 会话项支持右键菜单（重命名、删除、归档）

- [x] Task 7: 改造 Chat 面板 UI 风格
  - [x] 修改 `/workspace/plugins/agent-workbench/chat/src/AgentChatFileEditor.kt`
  - [x] 参考 Air 的对话气泡风格：用户消息靠右，AI 回复靠左
  - [x] 添加代码块语法高亮渲染
  - [x] 添加 Markdown 渲染支持
  - [x] 消息底部添加操作按钮（复制、重试、点赞/踩）
  - [x] 输入区域固定在底部，支持多行输入

- [x] Task 8: 优化 Prompt Palette 全局入口
  - [x] 修改 `/workspace/plugins/agent-workbench/prompt/ui/src/AgentPromptPaletteView.kt`
  - [x] 参考 Air 风格：居中弹出，半透明背景遮罩
  - [x] 优化输入框样式：圆角、阴影
  - [x] 上下文标签以 chips 形式显示在输入框上方
  - [x] 支持快捷键快速发送（Ctrl+Enter）

## Phase 4: 内存与性能优化

- [x] Task 9: 优化 JVM 启动参数
  - [x] 修改 `/workspace/bin/idea.properties` 添加 Light 模式专用配置
  - [x] 设置 `-Xmx512m` 作为 Light 模式默认堆内存上限
  - [x] 为 Light 模式禁用不必要的后台服务
  - [x] 优化 `idea.properties` 中的文件大小限制以适应轻量场景

- [x] Task 10: 实现延迟加载机制
  - [x] 在插件系统中添加 `load="lazy"` 属性支持
  - [x] 修改 `PluginManagerCore` 以支持按需加载插件
  - [x] 在 `PluginXmlConst`、`PluginDescriptorBuilder`、`RawPluginDescriptor`、`XmlReader`、`IdeaPluginDescriptorImpl` 中添加 lazy 加载支持
  - [x] 添加 `isLazyLoaded()` 和 `getLazyLoadedPlugins()` 工具方法

- [x] Task 11: 禁用非必要后台索引
  - [x] 创建 `LightModeConfig` 服务类，支持禁用索引、检查和代码分析
  - [x] 在 Light 模式下可配置禁用后台索引
  - [x] 在 Light 模式下可配置禁用 InspectionEngine 后台检查
  - [x] 在 Light 模式下可配置禁用分析服务

## Phase 5: UI 精简美化

- [x] Task 12: 精简工具栏
  - [x] 创建 `/workspace/platform/platform-impl/resources/light-toolbar-actions.xml` 精简工具栏配置
  - [x] 只保留核心操作：文件操作、撤销/重做、搜索、Git 分支、AI Agent 入口
  - [x] 移除所有 Java 开发相关的工具栏按钮

- [x] Task 13: 精简工具窗口
  - [x] 创建 `/workspace/platform/platform-impl/resources/light-toolwindows.xml` 工具窗口配置
  - [x] 默认只显示 Project、Git、Terminal、AI Sessions 四个工具窗口
  - [x] 移除 Java 相关工具窗口

## Phase 6: Linux 专属安装脚本

- [x] Task 14: 创建 Linux 平台安装脚本
  - [x] 创建 `/workspace/installers/linux/install.sh` 安装脚本
  - [x] 支持从构建目录复制到 `/opt/light-ide/`
  - [x] 创建桌面快捷方式 `.desktop` 文件
  - [x] 创建命令行别名 `light-ide` 指向启动脚本
  - [x] 提供 Light 模式启动命令：`light-ide --light`
  - [x] 提供完整模式启动命令：`light-ide`
  - [x] 创建 `/workspace/installers/linux/uninstall.sh` 卸载脚本

# Task Dependencies

- [Task 3] 依赖于 [Task 1] 和 [Task 2]（构建系统精简后才能实现 Light 模式逻辑）
- [Task 4] 依赖于 [Task 3]（Light 模式核心逻辑完成后才能实现界面）
- [Task 5] 依赖于 [Task 4]（Light 界面完成后才能实现模式切换）
- [Task 6] 和 [Task 7] 和 [Task 8] 互相独立，可并行开发
- [Task 9] 和 [Task 10] 和 [Task 11] 互相独立，可并行开发
- [Task 12] 和 [Task 13] 互相独立，可并行开发
- [Task 14] 独立于所有任务，可随时开发