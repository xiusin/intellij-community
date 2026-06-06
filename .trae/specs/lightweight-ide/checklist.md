# Checklist

## 构建系统精简
- [x] `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 不再包含任何 `java/` 路径的模块
- [x] `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 不再包含 Java 相关插件（junit、testng、maven、gradle、groovy、javaFX、ant、ByteCodeViewer、coverage、java-decompiler、java-i18n、ui-designer、eclipse、stream-debugger、jshell、lombok、rareJavaRefactorings、gradle-maven、java-features-trainer）
- [x] `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 不再包含 Android 模块（android/*）
- [x] `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 不再包含 Python 模块（python/*）
- [x] `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 不再包含 Kotlin 插件（plugins/kotlin）
- [x] `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 不再包含不必要的 ML 插件（completion-ml-ranking、completion-ml-ranking-models、search-everywhere-ml、marketplace-ml、ml-local-models、findUsagesMl、turboComplete、filePrediction）
- [x] `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 不再包含性能测试插件（performanceTesting）
- [x] `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 不再包含不必要的平台插件（ide-features-trainer、stats-collector、evaluation-plugin、remote-control、compilation-charts、configuration-script、commander、copyright、emojipicker、webp、xpath）
- [x] Git 相关模块保留（git4idea、git-features-trainer、git-modal-commit、github、gitlab、vcs-*、dvcs-*）
- [x] 核心平台模块保留（platform/boot、platform/bootstrap、platform/platform-api、platform/platform-impl、platform/core-api、platform/core-impl、platform/editor-ui-api）
- [x] 核心插件保留（terminal、agent-workbench、markdown、yaml、toml、json、editorconfig、textmate）

## Light 快速启动模式
- [x] `LightModeService` 接口已创建在 `platform/core-api/src/com/intellij/openapi/application/`
- [x] `LightModeServiceImpl` 实现已创建在 `platform/core-impl/src/com/intellij/ide/`
- [x] 支持通过 `--light` 命令行参数启动 Light 模式
- [x] Light 模式启动时跳过项目模型加载
- [x] Light 模式启动时跳过索引初始化
- [x] `LightEditorFrame` 组件已创建，提供简洁编辑器界面
- [x] Light 编辑器支持文件打开/新建/保存/另存为操作
- [x] Light 编辑器支持多标签页编辑
- [x] 提供"切换到完整模式"按钮
- [x] `ConvertToFullModeAction` 已实现（内联在 LightEditorFrame.switchToFullMode() 中）
- [x] 从 Light 切换到完整模式时触发 IDE 重启（Light 模式为纯 Swing 界面，需重启切换）

## AI Agent 主界面改造
- [x] Agent Sessions Tool Window 采用 Air 风格左侧垂直列表布局
- [x] 会话列表支持搜索过滤
- [x] 会话列表包含"新建会话"按钮
- [x] 会话项支持右键菜单操作
- [x] Chat 面板采用对话气泡风格（用户靠右，AI 靠左）
- [x] Chat 面板支持代码块语法高亮渲染
- [x] Chat 面板支持 Markdown 渲染
- [x] 消息底部有操作按钮（复制、重试、点赞/踩）
- [x] 输入区域固定在底部，支持多行输入
- [x] Prompt Palette 采用居中弹出风格，半透明遮罩
- [x] Prompt Palette 输入框样式优化（圆角、阴影）
- [x] 上下文标签以 chips 形式显示

## 内存与性能优化
- [x] Light 模式默认堆内存上限为 512MB（-Xmx512m）
- [x] 非必要插件标记为 `load="lazy"` 延迟加载
- [x] AI Agent 模块标记为 `load="lazy"` 首次使用时加载
- [x] Git 模块标记为 `load="lazy"` 首次使用时加载
- [x] Light 模式下禁用 `FileBasedIndex` 初始化（FileBasedIndexImpl.loadIndexes 添加检查）
- [x] Light 模式下禁用 `InspectionEngine` 后台检查（InspectionEngine.inspectEx 添加检查）
- [x] Light 模式下禁用分析服务（DaemonCodeAnalyzerImpl.restart 添加检查）

## UI 精简美化
- [x] 主工具栏只保留核心操作按钮
- [x] 主工具栏不包含 Java 开发相关按钮
- [x] 默认工具窗口只显示 Project、Git、Terminal、AI Sessions
- [x] Java 相关工具窗口已移除

## Linux 安装脚本
- [x] 安装脚本 `installers/linux/install.sh` 已创建
- [x] 支持从 `.tar.gz` 解压安装
- [x] 创建 `.desktop` 桌面快捷方式
- [x] 创建命令行列名 `light-ide`
- [x] 支持 `light-ide --light` Light 模式启动
- [x] 支持 `light-ide` 完整模式启动