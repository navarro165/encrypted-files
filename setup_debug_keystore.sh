#!/bin/bash

# Setup Debug Keystore for Consistent Signing
# This script creates the same debug keystore that GitHub Actions uses
# Run this once to ensure your local builds can update GitHub Actions builds

echo "🔧 Setting up consistent debug keystore..."

# Create .android directory if it doesn't exist
mkdir -p ~/.android

# Check if debug keystore already exists
if [ -f ~/.android/debug.keystore ]; then
    echo "⚠️  Debug keystore already exists at ~/.android/debug.keystore"
    echo "   This script will overwrite it to match GitHub Actions."
    read -p "   Continue? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "❌ Setup cancelled."
        exit 1
    fi
fi

# Generate debug keystore with standard Android debug credentials
echo "📝 Generating debug keystore..."
keytool -genkey -v -keystore ~/.android/debug.keystore \
  -storepass android -alias androiddebugkey \
  -keypass android -keyalg RSA -keysize 2048 \
  -validity 10000 -dname "CN=Android Debug,O=Android,C=US"

if [ $? -eq 0 ]; then
    echo "✅ Debug keystore created successfully!"
    echo "📍 Location: ~/.android/debug.keystore"
    echo "🔑 Store Password: android"
    echo "🔑 Key Password: android"
    echo "🔑 Key Alias: androiddebugkey"
    echo ""
    echo "🎉 Now your local debug builds will use the same signing key as GitHub Actions!"
    echo "   You should be able to update the app without uninstalling."
else
    echo "❌ Failed to create debug keystore."
    exit 1
fi 