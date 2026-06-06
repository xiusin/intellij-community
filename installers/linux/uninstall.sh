#!/bin/bash
set -e

echo "Light IDE Uninstaller"
echo "====================="

if [ "$EUID" -ne 0 ]; then
    echo "Please run as root (sudo ./uninstall.sh)"
    exit 1
fi

echo "Removing installation directory..."
rm -rf /opt/light-ide

echo "Removing symlink..."
rm -f /usr/local/bin/light-ide

echo "Removing desktop entries..."
rm -f /usr/share/applications/light-ide.desktop
rm -f /usr/share/applications/light-ide-light.desktop

if command -v update-desktop-database &> /dev/null; then
    update-desktop-database
fi

echo "Uninstallation complete!"