# Checklist

## 构建系统精简
- [x] `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 不再包含任何 `java/` 路径的模块
- [x] `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 不再包含 Java 相关插件
- [x] `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 不再包含 Android、Python、Kotlin 模块
- [x] `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 不再包含 ML 插件
- [x] `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 不再包含性能测试插件
- [x] `/workspace/BUILD.bazel` 中 `main` 的 `runtime_deps` 不再包含 GitHub、GitLab、Grazie、TextMate、Settings Sync、Smart Update、LAF、spellchecker、testRunner、usageView、images、structuralsearch、sh、git-features-trainer、git-modal-commit
- [x] Git 核心模块保留（git4idea、vcs-*、lvcs-*、diff-*）
- [x] 核心平台模块保留（platform/boot、platform/bootstrap、platform/platform-api、platform/platform-impl、platform/core-api、platform/core-impl、platform/editor-ui-api）
- [x] 核心插件保留（terminal、agent-workbench、markdown、yaml、toml、json、editorconfig、mcp-server、properties、RegExpSupport）
- [x] 最终 `main` runtime_deps 从 172 个精简到 45 个（74% 缩减）

## Linux 平台代码移除
- [x] `installers/linux/` 目录已删除
- [x] `bin/linux/` 目录已删除
- [x] `native/fsNotifier/linux/` 目录已删除
- [x] `LinuxInstaller.kt` 已删除
- [x] `LinuxUiUtil.kt` 已删除
- [x] `X11UiUtil.java` 已删除
- [x] `LinuxFrameButton.kt` 已删除
- [x] 测试依赖中的 Kotlin/SVN/Tasks/DOM 引用已清理

## Light 快速启动模式
- [x] `LightModeService` 接口已创建
- [x] `LightModeServiceImpl` 实现已创建
- [x] 支持通过 `--light` 命令行参数启动 Light 模式
- [x] Light 模式启动时跳过项目模型加载、索引初始化
- [x] `LightEditorFrame` + `LightEditorPanel` 组件已创建
- [x] Light 编辑器支持文件打开/新建/保存/另存为、多标签页
- [x] 提供"切换到完整模式"按钮

## AI Agent 主界面改造
- [x] Sessions Tool Window: Air 风格 + 搜索 + 新建按钮 + 右键菜单
- [x] Chat Panel: 对话气泡 + 代码高亮 + Markdown + 操作按钮
- [x] Prompt Palette: 居中弹出 + 遮罩 + 圆角 + Ctrl+Enter

## 内存与性能优化
- [x] Light 模式 -Xmx512m 堆内存上限
- [x] 4 个插件添加 `load="lazy"` 延迟加载
- [x] FileBasedIndex、InspectionEngine、DaemonCodeAnalyzer 在 Light 模式禁用