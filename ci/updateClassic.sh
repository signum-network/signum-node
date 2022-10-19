#!/bin/bash

set -e

echo "========================================"
echo "🛰 Updating to latest Classic Version..."
echo "----------------------------------------"
BASE_CI_DIR=$(pwd)

# prepare tmp folder
TMPDIR=$(mktemp -d)
cd $TMPDIR > /dev/null

# download
echo
echo "======================================="
echo "⬇️ Cloning Classic wallet main branch..."
echo "---------------------------------------"
git clone https://github.com/signum-network/signum-classic-wallet.git
# gh repo clone signum-network/signum-classic-wallet
cd signum-classic-wallet/src > /dev/null

# cleanup old version
rm -rf ${BASE_CI_DIR}/../html/ui/classic/*
cp -R * ${BASE_CI_DIR}/../html/ui/classic/
echo "✅ Copied wallet sources"

echo
echo "======================================="
echo "🛀 Cleaning up..."
echo "---------------------------------------"
cd ${BASE_CI_DIR}
rm -rf ${TMPDIR}
echo "✅ Removed temp data"
echo
echo "🎉 Yay. Successfully updated Classic Wallet"
