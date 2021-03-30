#!/bin/bash

set -e

echo "======================================="
echo "🛰 Fetching latest Phoenix Release..."
echo "---------------------------------------"
RELEASE=$(node getLatestPhoenixVersion.js)
echo $RELEASE

# prepare tmp folder
TMPDIR=./tmp
if [[ -e $TMPDIR ]]; then
	rm -rf $TMPDIR
fi
mkdir $TMPDIR
pushd $TMPDIR > /dev/null

# download
echo
echo "======================================="
echo "⬇️ Downloading latest Phoenix Web Release..."
echo "---------------------------------------"
DOWNLOAD_URL=$(echo $RELEASE | cut -d ',' -f 2 | cut -d ':' -f 2,3)
wget $DOWNLOAD_URL
echo
echo "======================================="
echo "📦 Unpacking..."
echo "---------------------------------------"
unzip -q web-phoenix-burst-wallet.\*.zip
echo "✅ Extracted newest wallet sources successfully"
echo
echo "======================================="
echo "🏗 Updating..."
echo "---------------------------------------"
pushd ./dist > /dev/null
# set new base ref in index.html
sed -i 's;<base href="/">;<base href="/phoenix/">;g' index.html
echo "✅ Written base href"

# cleanup old version
rm -rf ../../../html/ui/phoenix/*
cp -R * ../../../html/ui/phoenix
echo "✅ Copied wallet sources"

#./dist
popd > /dev/null

#./tmp
popd > /dev/null
echo
echo "======================================="
echo "🛀 Cleaning up..."
echo "---------------------------------------"
rm -rf ./tmp
echo "✅ Removed temp data"
echo
echo "🎉 Yay. Successfully updated Phoenix Web Wallet"




