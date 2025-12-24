#!/bin/bash
# Build Genie DMG installer with drag-to-Applications experience
# Creates a beautiful DMG that users can double-click to install

set -e

echo "🧞 Building Genie DMG Installer..."

# First, build the .app if it doesn't exist
if [ ! -d "target/Genie.app" ]; then
    echo "📦 Building Genie.app first..."
    ./build-macos-app.sh
fi

# Configuration
APP_NAME="Genie"
DMG_NAME="Genie-1.0.0"
DMG_DIR="target/dmg"
DMG_PATH="target/${DMG_NAME}.dmg"
APP_PATH="target/Genie.app"

# Clean up previous DMG build
rm -rf "$DMG_DIR"
rm -f "$DMG_PATH"
mkdir -p "$DMG_DIR"

echo "📁 Preparing DMG contents..."

# Copy the app
cp -r "$APP_PATH" "$DMG_DIR/"

# Create Applications symlink
ln -s /Applications "$DMG_DIR/Applications"

# Create a simple background image with instructions
BACKGROUND_DIR="$DMG_DIR/.background"
mkdir -p "$BACKGROUND_DIR"

# Create background using sips/ImageMagick or a simple approach
# For now, create a placeholder - in production, use a designed image
cat > "/tmp/dmg_background.svg" << 'EOF'
<svg xmlns="http://www.w3.org/2000/svg" width="600" height="400">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" style="stop-color:#1a1a2e;stop-opacity:1" />
      <stop offset="100%" style="stop-color:#16213e;stop-opacity:1" />
    </linearGradient>
  </defs>
  <rect width="600" height="400" fill="url(#bg)"/>
  <text x="300" y="350" font-family="SF Pro Display, Helvetica" font-size="16" fill="#888" text-anchor="middle">
    Drag Genie to Applications to install
  </text>
  <!-- Arrow -->
  <path d="M 250 200 L 350 200 L 330 180 M 350 200 L 330 220" 
        stroke="#8B5CF6" stroke-width="4" fill="none" stroke-linecap="round"/>
</svg>
EOF

# Try to convert SVG to PNG for background
if command -v convert &> /dev/null; then
    convert /tmp/dmg_background.svg "$BACKGROUND_DIR/background.png"
elif command -v rsvg-convert &> /dev/null; then
    rsvg-convert /tmp/dmg_background.svg -o "$BACKGROUND_DIR/background.png"
else
    echo "⚠️  No image converter found - DMG will have no background image"
fi

echo "💿 Creating DMG..."

# Create temporary DMG
TEMP_DMG="target/${DMG_NAME}-temp.dmg"

# Calculate size (app size + 20MB buffer)
APP_SIZE=$(du -sm "$APP_PATH" | cut -f1)
DMG_SIZE=$((APP_SIZE + 50))

# Create DMG
hdiutil create -srcfolder "$DMG_DIR" -volname "$APP_NAME" -fs HFS+ \
    -fsargs "-c c=64,a=16,e=16" -format UDRW -size ${DMG_SIZE}m "$TEMP_DMG"

echo "🎨 Customizing DMG appearance..."

# Mount the DMG
MOUNT_DIR=$(hdiutil attach -readwrite -noverify -noautoopen "$TEMP_DMG" | \
    grep -E '^/dev/' | tail -1 | awk '{print $3}')

if [ -n "$MOUNT_DIR" ]; then
    # Use AppleScript to set DMG window properties
    osascript << APPLESCRIPT
    tell application "Finder"
        tell disk "$APP_NAME"
            open
            set current view of container window to icon view
            set toolbar visible of container window to false
            set statusbar visible of container window to false
            set bounds of container window to {400, 200, 1000, 600}
            set theViewOptions to the icon view options of container window
            set arrangement of theViewOptions to not arranged
            set icon size of theViewOptions to 100
            
            -- Position the icons
            set position of item "Genie.app" of container window to {150, 180}
            set position of item "Applications" of container window to {450, 180}
            
            -- Set background if it exists
            try
                set background picture of theViewOptions to file ".background:background.png"
            end try
            
            close
            open
            update without registering applications
            delay 2
            close
        end tell
    end tell
APPLESCRIPT

    # Sync and unmount
    sync
    hdiutil detach "$MOUNT_DIR"
else
    echo "⚠️  Could not mount DMG for customization"
fi

echo "📀 Converting to compressed DMG..."

# Convert to compressed read-only DMG
hdiutil convert "$TEMP_DMG" -format UDZO -imagekey zlib-level=9 -o "$DMG_PATH"

# Clean up
rm -f "$TEMP_DMG"
rm -rf "$DMG_DIR"

echo ""
echo "✅ DMG created: $DMG_PATH"
echo ""
echo "File size: $(du -h "$DMG_PATH" | cut -f1)"
echo ""
echo "To install:"
echo "  1. Double-click ${DMG_NAME}.dmg"
echo "  2. Drag Genie to Applications folder"
echo "  3. Eject the disk image"
echo "  4. Launch Genie from Applications or Spotlight"
echo ""

