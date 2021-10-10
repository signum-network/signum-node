#!/bin/bash

set -e

echo "🛰 Updating Phoenix Wallet to current release..."

# prepare tmp folder
TMPDIR=/app/tmp
if [[ -e $TMPDIR ]]; then
	rm -rf $TMPDIR
fi
mkdir $TMPDIR
pushd $TMPDIR > /dev/null

echo "⬇️ Downloading latest release..."

# Download the latest phoenix release
curl -s "https://api.github.com/repos/signum-network/phoenix/releases/latest" \
    | grep "web-phoenix-signum-wallet.*.zip" \
    | cut -d : -f 2,3 \
    | tr -d \" \
    | grep "https" \
    | wget -qi -

echo "📦 Extracting files..."
# Unzip it
unzip -qq web-phoenix-signum-wallet.*.zip

echo "🏗 Patching base href..."

# Modify the base href in the index file
sed -i 's;<base href="/">;<base href="/phoenix/">;g' dist/index.html

echo "📝 Copying Phoenix Wallet to node..."

rm -rf /app/html/ui/phoenix/*
cp -R $TMPDIR/dist/* /app/html/ui/phoenix/

echo "🛀 Cleaning up..."
# Go back to original directory
popd > /dev/null

rm -rf $TEMPDIR

echo "✅ Phoenix Wallet has been updated."

echo "🚀 Launching the node..."

java -jar /app/signum-node.jar
