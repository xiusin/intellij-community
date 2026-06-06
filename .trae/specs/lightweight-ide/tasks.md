# Tasks

## Phase 1: 构建系统精简 - 移除 Java 及相关插件依赖

- [x] Task 1: 修改主构建文件移除 Java 依赖
  - [x] 修改 `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps`，移除所有 Java 相关模块依赖
  - [x] 移除 Java 相关插件、Android、Python、Kotlin、ML 插件、性能测试插件等
  - [x] 同步修改 `main-tests` 和 `main_test_lib` 的 `runtime_deps`

- [x] Task 2: 验证构建系统精简
  - [x] 最终 `main` runtime_deps 从 172 个精简到 45 个（74% 缩减）

## Phase 2: Light 轻量启动模式实现

- [x] Task 3-5: Light 启动模式完整实现
  - [x] LightModeService 接口 + LightModeServiceImpl 实现
  - [x] LightEditorFrame + LightEditorPanel 记事本式编辑器
  - [x] Main.kt 支持 `--light` 参数
  - [x] 模式切换功能

## Phase 3: AI Agent 主界面参照 Air 风格改造

- [x] Task 6-8: AI Agent UI 改造
  - [x] Sessions Tool Window: 搜索框 + 新建按钮 + 右键菜单
  - [x] Chat Panel: 对话气泡 + 代码高亮 + Markdown + 操作按钮
  - [x] Prompt Palette: 居中弹出 + 遮罩 + 圆角 + Ctrl+Enter

## Phase 4: 内存与性能优化

- [x] Task 9-11: 内存优化
  - [x] idea.properties Light 模式配置（-Xmx512m）
  - [x] 4 个插件添加 `load="lazy"` 延迟加载
  - [x] FileBasedIndex、InspectionEngine、DaemonCodeAnalyzer 在 Light 模式禁用

## Phase 5: UI 精简美化

- [x] Task 12-13: UI 精简
  - [x] 精简工具栏配置（6 个核心按钮）
  - [x] 精简工具窗口（4 个）

## Phase 6: 进一步精简 + Linux 代码移除

- [x] Task 15: 进一步精简 BUILD.bazel
  - [x] 移除 GitHub、GitLab、Grazie、TextMate、Settings Sync、Smart Update、LAF、spellchecker、testRunner、usageView、images、structuralsearch、sh、terminal/sh、git-features-trainer、git-modal-commit、ide-startup/importSettings、testFramework

- [x] Task 16: 移除 Linux 平台代码
  - [x] 删除 installers/linux/、bin/linux/、native/fsNotifier/linux/
  - [x] 删除 LinuxInstaller.kt、LinuxUiUtil.kt、X11UiUtil.java、LinuxFrameButton.kt

- [x] Task 17: 清理测试依赖中的 Java/Kotlin/SVN/Tasks 引用