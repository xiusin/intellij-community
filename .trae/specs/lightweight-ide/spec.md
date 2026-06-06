# 轻量级 AI IDE 改造方案

## Why

当前 IntelliJ IDEA Community Edition 包含大量内置功能和插件，启动慢、内存占用大。需要改造为一个**轻量级、AI优先、快速启动**的编辑器，类似 Sublime Text 的内存占用，同时保留 Git 功能和 AI Agent 能力，适合日常编辑和AI辅助开发。

## What Changes

### 精简功能与插件
- **BREAKING**: 移除所有 Java 相关模块和插件（整个 `/workspace/java/` 目录下的所有模块）
- **BREAKING**: 移除所有 Java 相关内置插件（Maven、Gradle、JUnit、TestNG、Groovy、JavaFX 等）
- **BREAKING**: 移除 Android 相关模块和插件
- **BREAKING**: 移除 Python、Kotlin 等语言插件
- **BREAKING**: 移除不必要的ML/AI相关插件（completion-ml-ranking、search-everywhere-ml 等）
- **BREAKING**: 移除性能测试相关插件（performanceTesting）
- **保留**: Git 完整功能模块（git4idea、github、gitlab 保留）
- **保留**: 插件扩展机制（保留完整插件体系，但不内置多余插件）
- **保留**: 完整键盘快捷键支持

### 轻量级快速启动模式
- **新增**: 实现 Light 快速启动模式（类似记事本）
  - Light 模式下跳过完整项目加载
  - 直接进入编辑模式，可快速打开单个或多个文件
  - 延迟加载非核心功能
  - 保持低内存占用

### AI Agent 主界面开发
- **新增**: 基于现有 `agent-workbench` 插件，参考 Air/Codex 界面风格，实现现代化 AI Agent 主界面
  - 参考 Air 的简洁布局，左侧 Agent 会话列表，右侧聊天面板
  - 支持全局快捷键唤起（Ctrl+\ 或 Ctrl+Ctrl）
  - 支持多会话管理，持久化保存
  - 支持分屏/独立窗口模式
  - 自动上下文收集（编辑器选区、VCS变更等）

### 内存与性能优化
- **优化**: 严格内存管理，做到 Sublime Text 级别小巧
  - 延迟加载非核心模块
  - 禁用不必要的后台索引
  - Light 模式下关闭增量后台索引
  - 优化启动类路径，减少初始加载类

### UI 改造
- **改造**: 保持现有 UI 架构，参考 Air 界面风格做轻量化美化
  - 精简工具栏，减少视觉干扰
  - 优化工具窗口布局
  - 保持 IntelliJ 平台原生 UI，不引入新框架

## Impact

- Affected specs: 产品规格改造，从全功能 IDE 变为轻量级 AI 编辑器
- Affected code:
  - `/workspace/BUILD.bazel` - 主构建文件，移除 Java 相关依赖
  - `/workspace/platform/` - 核心平台，添加 Light 启动模式
  - `/workspace/plugins/agent-workbench/` - AI Agent 插件，参考 Air 重构主界面
  - `/workspace/plugins/` - 移除大量内置插件
  - `/workspace/java/` - 整个 Java 模块移除

## ADDED Requirements

### Requirement: Light 快速启动模式

系统 **SHALL** 提供 Light 快速启动模式：

#### Scenario: 用户选择快速启动
- **WHEN** 用户以 Light 模式启动 IDE
- **THEN** 系统在 2 秒内完成启动
- **THEN** 显示简洁编辑器界面，类似记事本
- **THEN** 用户可以快速打开文件进行编辑
- **THEN** 内存占用控制在 500MB 以内

#### Scenario: 从 Light 切换到完整模式
- **WHEN** 用户打开项目文件夹
- **THEN** 系统询问是否切换到完整项目模式
- **THEN** 用户确认后加载完整项目功能

### Requirement: AI Agent 集成

系统 **SHALL** 集成 AI Agent 功能，参考 Air/Codex 界面：

#### Scenario: 全局唤起 AI
- **WHEN** 用户按下全局快捷键（Ctrl+\）
- **THEN** 在屏幕中央显示 Prompt Palette
- **THEN** 自动收集当前编辑器上下文（选区、文件信息）
- **THEN** 用户输入提示后创建新的 AI 会话

#### Scenario: AI 会话管理
- **WHEN** 用户打开 AI Sessions 工具窗口
- **THEN** 按项目分组显示所有 AI 会话
- **THEN** 用户可以点击恢复历史会话
- **THEN** 会话持久化保存，重启后恢复

#### Scenario: 独立窗口模式
- **WHEN** 用户选择分离窗口模式
- **THEN** AI 聊天在独立窗口中打开
- **THEN** 用户可以并排工作，同时看代码和 AI 对话

## MODIFIED Requirements

### Requirement: 插件系统

**修改为**: 保留完整插件加载机制，但默认只内置最少插件：
- Git（git4idea）
- Agent Workbench（AI Agent）
- Terminal
- Markdown
- yaml/toml/json 基础语法支持
- 所有其他语言插件需用户手动安装

### Requirement: 内存管理

**修改为**: 严格的分阶段加载：
1. 启动阶段：只加载核心平台、编辑器、UI 框架
2. 打开项目后：加载 Git、索引
3. 首次使用 AI 时：加载 AI Agent 模块

## REMOVED Requirements

### Requirement: 所有 Java 相关功能

**Reason**: 用户需求明确要求删除所有 Java 相关插件，目标是轻量级通用编辑器，不是 Java IDE。

**Migration**:
- Java 支持完全移除，不再内置
- 用户如果需要 Java 支持，可以通过插件市场手动安装 Java 插件
- 构建配置中从 `runtime_deps` 移除所有 Java 相关模块
