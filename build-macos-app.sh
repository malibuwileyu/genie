#!/bin/bash
# Build Genie as a native macOS application
# This creates a proper .app bundle with bundled JavaFX

set -e

echo "🧞 Building Genie macOS App..."

# Clean and package the fat JAR
echo "📦 Creating fat JAR..."
mvn clean package -DskipTests

# Find the shaded JAR
JAR_FILE="target/genie-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR file not found: $JAR_FILE"
    exit 1
fi

# Create app output directory
APP_DIR="target/Genie.app"
CONTENTS_DIR="$APP_DIR/Contents"
MACOS_DIR="$CONTENTS_DIR/MacOS"
RESOURCES_DIR="$CONTENTS_DIR/Resources"
JAVAFX_DIR="$RESOURCES_DIR/javafx"

rm -rf "$APP_DIR"
mkdir -p "$MACOS_DIR" "$RESOURCES_DIR" "$JAVAFX_DIR"

# Copy JAR
cp "$JAR_FILE" "$RESOURCES_DIR/genie.jar"

# Bundle JavaFX JARs from Maven repository
echo "📚 Bundling JavaFX modules..."
JAVAFX_VERSION="21"
M2_REPO="$HOME/.m2/repository"
JAVAFX_MODS="$M2_REPO/org/openjfx"

# Detect architecture
ARCH=$(uname -m)
if [ "$ARCH" = "arm64" ]; then
    PLATFORM="mac-aarch64"
else
    PLATFORM="mac"
fi

# Copy all required JavaFX modules including javafx-web
MODULES="javafx-base javafx-controls javafx-graphics javafx-fxml javafx-web javafx-media"
for mod in $MODULES; do
    JAR="$JAVAFX_MODS/$mod/$JAVAFX_VERSION/$mod-$JAVAFX_VERSION-$PLATFORM.jar"
    if [ -f "$JAR" ]; then
        cp "$JAR" "$JAVAFX_DIR/"
        echo "  ✓ Bundled $mod"
    else
        echo "  ⚠️ Warning: $JAR not found"
    fi
done

# Create launcher script that uses bundled JavaFX
cat > "$MACOS_DIR/Genie" << 'EOF'
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
RESOURCES_DIR="$DIR/../Resources"
JAVAFX_DIR="$RESOURCES_DIR/javafx"

# Find Java
if [ -n "$JAVA_HOME" ]; then
    JAVA="$JAVA_HOME/bin/java"
elif command -v java &> /dev/null; then
    JAVA="java"
else
    osascript -e 'display dialog "Java is required to run Genie. Please install Java 17 or later." buttons {"OK"} default button "OK" with icon stop'
    exit 1
fi

# Build module path from bundled JavaFX
MODULE_PATH=""
for jar in "$JAVAFX_DIR"/*.jar; do
    if [ -f "$jar" ]; then
        if [ -n "$MODULE_PATH" ]; then
            MODULE_PATH="$MODULE_PATH:$jar"
        else
            MODULE_PATH="$jar"
        fi
    fi
done

if [ -z "$MODULE_PATH" ]; then
    osascript -e 'display dialog "JavaFX modules not found in app bundle." buttons {"OK"} default button "OK" with icon stop'
    exit 1
fi

# Run the app with bundled JavaFX modules
exec "$JAVA" \
    --module-path "$MODULE_PATH" \
    --add-modules javafx.controls,javafx.fxml,javafx.web \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    --add-opens java.desktop/java.awt=ALL-UNNAMED \
    --enable-native-access=ALL-UNNAMED \
    -cp "$RESOURCES_DIR/genie.jar" \
    com.genie.Genie
EOF

chmod +x "$MACOS_DIR/Genie"

# Create Info.plist with privacy descriptions
cat > "$CONTENTS_DIR/Info.plist" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>Genie</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon</string>
    <key>CFBundleIdentifier</key>
    <string>com.genie.app</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>Genie</string>
    <key>CFBundleDisplayName</key>
    <string>Genie</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSMinimumSystemVersion</key>
    <string>10.15</string>
    <key>LSUIElement</key>
    <true/>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSSupportsAutomaticGraphicsSwitching</key>
    <true/>
    
    <!-- Privacy Permission Descriptions -->
    <key>NSMicrophoneUsageDescription</key>
    <string>Genie needs microphone access to listen for voice commands like "Hey Genie".</string>
    <key>NSSpeechRecognitionUsageDescription</key>
    <string>Genie uses speech recognition to understand your voice commands and wishes.</string>
    <key>NSAppleEventsUsageDescription</key>
    <string>Genie uses AppleScript to detect active windows and browser tabs for context saving.</string>
</dict>
</plist>
EOF

# Copy app icon if it exists
if [ -f "AppIcon.icns" ]; then
    cp "AppIcon.icns" "$RESOURCES_DIR/AppIcon.icns"
    echo "🎨 Copied AppIcon.icns"
elif [ -d "Genie.iconset" ]; then
    # Try to generate icns from iconset
    if iconutil -c icns Genie.iconset -o "$RESOURCES_DIR/AppIcon.icns" 2>/dev/null; then
        echo "🎨 Generated AppIcon.icns from iconset"
    else
        echo "⚠️  Could not generate icon. Using placeholder."
    fi
else
    echo "⚠️  No AppIcon.icns found. Using placeholder icon."
fi

echo ""
echo "✅ Genie.app created at: $APP_DIR"
echo ""
echo "To install:"
echo "  1. Copy Genie.app to /Applications:"
echo "     cp -r target/Genie.app /Applications/"
echo ""
echo "  2. First launch will request permissions:"
echo "     - Microphone (for voice activation)"
echo "     - Accessibility (for global hotkeys)"  
echo "     - Automation (for window/tab detection)"
echo ""
echo "  3. Or run directly:"
echo "     open target/Genie.app"
echo ""
