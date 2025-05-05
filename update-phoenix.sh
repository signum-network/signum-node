#!/bin/bash

# Add these checks at the beginning of the script
echo "Testing internet connectivity..."
curl -v --connect-timeout 5 https://google.com

echo "Testing DNS resolution..."
ping -c 1 api.github.com


set -e

echo "🛰 Updating Signum Phoenix Wallet to current release..."

PHOENIX_DIR=./html/ui/phoenix/
if [[ ! -e $PHOENIX_DIR ]]; then
  echo "Cannot find $PHOENIX_DIR"
  echo "🚫 Please run this script in your signum-node root dir (aside signum-node executable)"
  exit 1
fi

# prepare tmp folder
TMPDIR=./tmp
if [[ -e $TMPDIR ]]; then
        rm -rf $TMPDIR
fi
mkdir $TMPDIR
pushd $TMPDIR > /dev/null

echo "⬇️  Downloading latest release..."

# First, let's see the raw API response
echo "Getting GitHub API response..."
GITHUB_RESPONSE=$(curl -k -s "https://api.github.com/repos/signum-network/phoenix/releases/latest")
echo "Raw API response:"
echo "$GITHUB_RESPONSE"


# Download the latest phoenix release
DOWNLOAD_URL=$(curl -s "https://api.github.com/repos/signum-network/phoenix/releases/latest" \
    | grep "browser_download_url" \
    | grep "web-phoenix-signum-wallet.*.zip" \
    | cut -d : -f 2,3 \
    | tr -d \" \
    | tr -d ' ')

echo "Found: $DOWNLOAD_URL"

curl -L -o phoenix.zip "$DOWNLOAD_URL"

echo "📦 Extracting files..."
# Unzip it
unzip phoenix.zip

echo "🏗  Patching base href..."

# Modify the base href in the index file
if [[ "$(uname)" == "Darwin" ]]; then
  # macOS (BSD sed)
  sed -i '' 's;<base href="/">;<base href="/phoenix/">;g' dist/index.html
else
  # GNU/Linux (GNU sed)
  sed -i 's;<base href="/">;<base href="/phoenix/">;g' dist/index.html
fi
echo "📝 Copying Phoenix Wallet to node..."

rm -rf ../$PHOENIX_DIR/*
cp -R dist/* ../$PHOENIX_DIR

echo "🛀 Cleaning up..."
# Go back to original directory
popd > /dev/null

rm -rf $TMPDIR

echo "✅ Phoenix Wallet has been updated."
