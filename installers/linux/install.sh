#!/bin/bash
set -e

INSTALL_DIR="/opt/light-ide"
BIN_DIR="/usr/local/bin"
DESKTOP_DIR="/usr/share/applications"

echo "Light IDE Installer"
echo "==================="

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo "Please run as root (sudo ./install.sh)"
    exit 1
fi

# Create installation directory
echo "Creating installation directory: $INSTALL_DIR"
mkdir -p "$INSTALL_DIR"

# Copy IDE files
echo "Copying IDE files..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/../../build"

if [ -d "$BUILD_DIR" ]; then
    cp -r "$BUILD_DIR"/* "$INSTALL_DIR/"
fi

# Create launcher script
echo "Creating launcher script..."
cat > "$INSTALL_DIR/light-ide" << 'LAUNCHER'
#!/bin/bash
INSTALL_DIR="/opt/light-ide"
JAVA_BIN=$(which java 2>/dev/null || echo "")

if [ -z "$JAVA_BIN" ]; then
    echo "Error: Java not found. Please install Java 17 or later."
    exit 1
fi

if [ "$1" = "--light" ]; then
    echo "Starting Light IDE in Light mode..."
    exec "$JAVA_BIN" -Xmx512m \
        -Dsun.awt.disablegrab=true \
        -Didea.home.path="$INSTALL_DIR" \
        -cp "$INSTALL_DIR/*" \
        com.intellij.idea.Main --light "$@"
else
    echo "Starting Light IDE..."
    exec "$JAVA_BIN" -Xmx2g \
        -Dsun.awt.disablegrab=true \
        -Didea.home.path="$INSTALL_DIR" \
        -cp "$INSTALL_DIR/*" \
        com.intellij.idea.Main "$@"
fi
LAUNCHER

chmod +x "$INSTALL_DIR/light-ide"

# Create symlink
echo "Creating symlink in $BIN_DIR..."
ln -sf "$INSTALL_DIR/light-ide" "$BIN_DIR/light-ide"

# Create desktop entry
echo "Creating desktop entry..."
cat > "$DESKTOP_DIR/light-ide.desktop" << DESKTOP
[Desktop Entry]
Name=Light IDE
Comment=Lightweight AI-Powered Code Editor
Exec=light-ide
Icon=$INSTALL_DIR/icon.png
Terminal=false
Type=Application
Categories=Development;IDE;
Keywords=editor;code;ide;ai;
StartupWMClass=jetbrains-idea
DESKTOP

# Create Light mode desktop entry
cat > "$DESKTOP_DIR/light-ide-light.desktop" << DESKTOP
[Desktop Entry]
Name=Light IDE (Quick Edit)
Comment=Quick lightweight editor mode
Exec=light-ide --light
Icon=$INSTALL_DIR/icon.png
Terminal=false
Type=Application
Categories=Development;IDE;
Keywords=editor;code;light;quick;
StartupWMClass=jetbrains-idea
DESKTOP

# Update desktop database
if command -v update-desktop-database &> /dev/null; then
    update-desktop-database
fi

echo ""
echo "Installation complete!"
echo ""
echo "Usage:"
echo "  light-ide              Start in full IDE mode"
echo "  light-ide --light      Start in Light (quick edit) mode"
echo "  light-ide <file>       Open a file"
echo "  light-ide --light <file>  Quick edit a file"
echo ""
echo "Desktop shortcuts have been created for both modes."