#!/bin/bash

# This script will be executed inside the docker container
# The paths are all relative to docker root folder '/signum'

set -e

BOOT_DIR=./bootstrap
CONF_DIR=/conf # symbolic link
CONF_DEFAULT=$CONF_DIR/node-default.properties
CONF_LOGGING=$CONF_DIR/logging-default.properties
CONF_CUSTOM=$CONF_DIR/node.properties

echo "👩‍⚕Checking for configuration files..."
if [[ ! -e $CONF_DEFAULT ]]; then
  echo "🆕Creating $CONF_DEFAULT"
  cp $BOOT_DIR/node-default.properties $CONF_DEFAULT
fi

if [[ ! -e $CONF_CUSTOM ]]; then
  echo "🆕Creating $CONF_CUSTOM"
  cp $BOOT_DIR/node.properties $CONF_CUSTOM
fi

if [[ ! -e $CONF_LOGGING ]]; then
  echo "🆕Creating $CONF_LOGGING"
  cp $BOOT_DIR/logging-default.properties $CONF_LOGGING
fi

# Run update scripts with debug output
echo "🔄 Running update-phoenix.sh..."
./update-phoenix.sh

echo "🔄 Running update-classic.sh..."
./update-classic.sh


echo "🚀Starting Signum Node"
exec java -XX:MaxRAMPercentage=90.0 -jar signum-node.jar --headless -c /conf/
