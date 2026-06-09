# =============================================================================
# AI IDE Build System - Makefile
# =============================================================================
# Usage:
#   make build                    # Compile only (dependency graph validation)
#   make build OS=mac             # Compile for macOS
#   make dist OS=mac              # Build macOS installer
#   make dist OS=windows          # Build Windows installer
#   make dist OS=mac ARCH=aarch64 # Build macOS ARM64 installer
#   make run                      # Run IDE locally (dev mode)
#   make clean                    # Clean build cache
#   make check                    # Verify dependency graph (no compilation)
#
# With extensions:
#   make build EXTENSIONS="git4idea,terminal"
#   make dist OS=mac EXTENSIONS="git4idea,terminal,markdown"
#
# Available extensions (plugins included in //:main runtime_deps):
#   git4idea      - Git version control
#   terminal      - Built-in terminal
#   agent-workbench - AI Agent workbench
#   editorconfig  - EditorConfig support
#   markdown      - Markdown support
#   toml          - TOML support
#   yaml          - YAML support
#   json          - JSON support
#   mcp-server    - MCP server
#   properties    - Properties file support
# =============================================================================

# ---- Configuration ----
BAZEL := ./bazel.cmd

# Target OS: mac, windows, linux, all, current, none
OS ?= current

# Target architecture: x64, aarch64, current
ARCH ?=

# Comma-separated list of plugin directory names to include
# If empty, all bundled plugins are included
EXTENSIONS ?=

# Build output directory
OUTPUT_DIR ?= out

# ---- Bazel target definitions ----
COMPILE_TARGET := //:main
RUN_TARGET := //:main_run
DIST_TARGET := //build:i_build_target
DEV_TARGET := //build:idea_community

# ---- Available extensions map ----
# Maps short name -> Bazel target
EXT_GIT4IDEA := //plugins/git4idea:vcs-git //plugins/git4idea/shared //plugins/git4idea/frontend
EXT_TERMINAL := //plugins/terminal //plugins/terminal/frontend //plugins/terminal/backend
EXT_AGENT_WORKBENCH := //plugins/agent-workbench/plugin:plugin
EXT_EDITORCONFIG := //plugins/editorconfig:editorconfig-plugin-main
EXT_MARKDOWN := //plugins/markdown/core:markdown //plugins/markdown/plugin:plugin-main
EXT_TOML := //plugins/toml
EXT_YAML := //plugins/yaml //plugins/yaml/backend
EXT_JSON := //json //json/backend
EXT_MCP_SERVER := //plugins/mcp-server:mcpserver
EXT_PROPERTIES := //plugins/properties:properties-backend

# ---- Helper functions ----
define resolve_extensions
$(if $(EXTENSIONS),\
  $(foreach ext,$(subst $(comma), ,$(EXTENSIONS)),\
    $(if $(filter $(ext),git4idea terminal agent-workbench editorconfig markdown toml yaml json mcp-server properties),\
      $(EXT_$(shell echo $(ext) | tr 'a-z-' 'A-Z_')),\
      $(error Unknown extension: $(ext). Available: git4idea, terminal, agent-workbench, editorconfig, markdown, toml, yaml, json, mcp-server, properties)\
    )\
  )\
)
endef

# Build JVM flags for target OS and architecture
define os_flags
$(if $(filter $(OS),mac windows linux all none current),\
  --jvm_flag=-Dintellij.build.target.os=$(OS),\
  $(error Invalid OS: $(OS). Use: mac, windows, linux, all, current, none)\
)
$(if $(ARCH),--jvm_flag=-Dintellij.build.target.arch=$(ARCH),)
endef

# ---- Targets ----

.PHONY: build dist run dev check clean query help \
        dist-mac dist-mac-arm64 dist-windows dist-linux

# Default target
help:
	@echo "AI IDE Build System"
	@echo ""
	@echo "Targets:"
	@echo "  build          Compile the project"
	@echo "  dist           Build distributable installer"
	@echo "  run            Run IDE (dev mode, current platform)"
	@echo "  dev            Run IDE via dev-build target"
	@echo "  check          Verify dependency graph only"
	@echo "  clean          Clean Bazel cache"
	@echo "  query          Show dependency graph"
	@echo ""
	@echo "Shortcuts:"
	@echo "  dist-mac          Build macOS (x64) installer"
	@echo "  dist-mac-arm64    Build macOS (ARM64) installer"
	@echo "  dist-windows      Build Windows installer"
	@echo ""
	@echo "Variables:"
	@echo "  OS=mac|windows|linux|all|current|none  (default: current)"
	@echo "  ARCH=x64|aarch64|current               (default: empty = all)"
	@echo "  EXTENSIONS=git4idea,terminal,markdown   (default: empty = all)"

# Compile only (validates dependency graph)
build:
	$(BAZEL) build $(COMPILE_TARGET)

# Verify dependency graph without compilation
check:
	$(BAZEL) build $(COMPILE_TARGET) --nobuild

# Build distributable installer
dist:
	@echo "=== Building installer for OS=$(OS)$(if $(ARCH), ARCH=$(ARCH),) ==="
	$(BAZEL) run $(DIST_TARGET) -- \
		--jvm_flag=-Dintellij.build.target.os=$(OS) \
		$(if $(ARCH),--jvm_flag=-Dintellij.build.target.arch=$(ARCH),) \
		$(if $(EXTENSIONS),--jvm_flag=-Dintellij.build.bundled.plugin.dirs.to.skip=$(EXTENSIONS),)

# Platform shortcuts for dist
dist-mac:
	$(MAKE) dist OS=mac ARCH=x64

dist-mac-arm64:
	$(MAKE) dist OS=mac ARCH=aarch64

dist-windows:
	$(MAKE) dist OS=windows ARCH=x64

dist-linux:
	$(MAKE) dist OS=linux ARCH=x64

# Run IDE locally (dev mode)
run:
	$(BAZEL) run $(RUN_TARGET)

# Run IDE via dev-build target (faster iteration)
dev:
	$(BAZEL) run $(DEV_TARGET)

# Clean Bazel cache
clean:
	$(BAZEL) clean

# Deep clean (removes all cached artifacts)
clean-all:
	$(BAZEL) clean --expunge

# Show dependency graph for //:main
query:
	$(BAZEL) query 'deps(//:main)' --output=package

# Show what depends on a specific plugin
query-plugin:
	@if [ -z "$(PLUGIN)" ]; then echo "Usage: make query-plugin PLUGIN=git4idea"; exit 1; fi
	$(BAZEL) query "rdeps('//:main', '//plugins/$(PLUGIN)/...')"

# Build with specific extensions only (compile target)
build-ext:
	@echo "=== Building with extensions: $(EXTENSIONS) ==="
	$(BAZEL) build $(COMPILE_TARGET)

# Build with custom Bazel flags
build-custom:
	$(BAZEL) build $(COMPILE_TARGET) $(BAZEL_FLAGS)
