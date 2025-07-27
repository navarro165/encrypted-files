#!/bin/bash

# Generate Android app icons from logo.png
# This script uses ImageMagick to create all required icon sizes
# Only generates if icons don't exist or if logo.png is newer

echo "Checking if app icons need to be generated..."

# Check if logo.png exists
if [ ! -f "logo.png" ]; then
    echo "❌ logo.png not found. Skipping icon generation."
    exit 0
fi

# Check if icons already exist and are newer than logo.png
ICON_DIRS=("app/src/main/res/mipmap-mdpi" "app/src/main/res/mipmap-hdpi" "app/src/main/res/mipmap-xhdpi" "app/src/main/res/mipmap-xxhdpi" "app/src/main/res/mipmap-xxxhdpi")

NEED_GENERATION=false

# Check if any icon directories are missing
for dir in "${ICON_DIRS[@]}"; do
    if [ ! -d "$dir" ]; then
        NEED_GENERATION=true
        break
    fi
done

# If directories exist, check if any icons are missing
if [ "$NEED_GENERATION" = false ]; then
    for dir in "${ICON_DIRS[@]}"; do
        if [ ! -f "$dir/ic_launcher.png" ] || [ ! -f "$dir/ic_launcher_round.png" ]; then
            NEED_GENERATION=true
            break
        fi
    done
fi

# For GitHub Actions, we'll always generate to ensure consistency
# In local development, we can be more selective
if [ "$CI" = "true" ] || [ "$GITHUB_ACTIONS" = "true" ]; then
    # In CI, always generate to ensure we have the latest icons
    NEED_GENERATION=true
fi

if [ "$NEED_GENERATION" = false ]; then
    echo "✅ App icons are up to date. Skipping generation."
    exit 0
fi

echo "🔄 Generating Android app icons from logo.png..."

# Create directories if they don't exist
mkdir -p app/src/main/res/mipmap-mdpi
mkdir -p app/src/main/res/mipmap-hdpi
mkdir -p app/src/main/res/mipmap-xhdpi
mkdir -p app/src/main/res/mipmap-xxhdpi
mkdir -p app/src/main/res/mipmap-xxxhdpi
mkdir -p app/src/main/res/mipmap-anydpi

# Remove old icon files to avoid conflicts
echo "Removing old icon files..."
find app/src/main/res/mipmap-* -name "*.webp" -delete
find app/src/main/res/mipmap-* -name "ic_launcher*.png" -delete

# Determine which ImageMagick command to use
if command -v magick >/dev/null 2>&1; then
    IMAGEMAGICK_CMD="magick"
elif command -v convert >/dev/null 2>&1; then
    IMAGEMAGICK_CMD="convert"
else
    echo "❌ Error: ImageMagick not found. Please install ImageMagick."
    exit 1
fi

# Generate icons for different densities
# MDPI (48x48)
$IMAGEMAGICK_CMD logo.png -resize 48x48 app/src/main/res/mipmap-mdpi/ic_launcher.png
$IMAGEMAGICK_CMD logo.png -resize 48x48 app/src/main/res/mipmap-mdpi/ic_launcher_round.png

# HDPI (72x72)
$IMAGEMAGICK_CMD logo.png -resize 72x72 app/src/main/res/mipmap-hdpi/ic_launcher.png
$IMAGEMAGICK_CMD logo.png -resize 72x72 app/src/main/res/mipmap-hdpi/ic_launcher_round.png

# XHDPI (96x96)
$IMAGEMAGICK_CMD logo.png -resize 96x96 app/src/main/res/mipmap-xhdpi/ic_launcher.png
$IMAGEMAGICK_CMD logo.png -resize 96x96 app/src/main/res/mipmap-xhdpi/ic_launcher_round.png

# XXHDPI (144x144)
$IMAGEMAGICK_CMD logo.png -resize 144x144 app/src/main/res/mipmap-xxhdpi/ic_launcher.png
$IMAGEMAGICK_CMD logo.png -resize 144x144 app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png

# XXXHDPI (192x192)
$IMAGEMAGICK_CMD logo.png -resize 192x192 app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
$IMAGEMAGICK_CMD logo.png -resize 192x192 app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png

# Note: Adaptive icons (foreground/background) are now generated in mipmap-anydpi folder
# This provides better compatibility across all Android versions

# Generate adaptive icons for modern Android (API 26+)
# Create foreground and background layers in hdpi folder (Android will scale automatically)
$IMAGEMAGICK_CMD logo.png -resize 162x162 -background transparent -gravity center -extent 162x162 app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png
$IMAGEMAGICK_CMD -size 162x162 xc:#FFFFFF app/src/main/res/mipmap-hdpi/ic_launcher_background.png

# Create adaptive icon XML files
cat > app/src/main/res/mipmap-anydpi/ic_launcher.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
EOF

cat > app/src/main/res/mipmap-anydpi/ic_launcher_round.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
EOF

echo "✅ App icons generated successfully!"
echo "Icons created in app/src/main/res/mipmap-*/" 