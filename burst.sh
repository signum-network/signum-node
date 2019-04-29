#!/bin/bash

MY_MAVEN_VERSION=3.5.2

MY_SELF=$0
MY_CMD=$1
MY_ARG=$2

MY_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

function usage() {
    cat << EOF
usage: $0 [command] [arguments]

INSTALLATION & UPGRADING:
  h2shell                       Opens a H2 shell for DB manipulation
  help                          Shows the help you just read
  compile                       Compile jar and create docs using maven
  upgrade                       Upgrade the config files to BRS format
  import [mariadb|h2]           DELETE current DB, then gets a new mariadb or H2
  switch <instance>             Switch config file to instance (MainNet,TestNet...)

"switch" option is for developers who need to quickly switch between various
configurations files. If you have
  conf/brs.properties.MainNet
  conf/brs.properties.TestNet
  conf/brs.properties.LocalDev
you can activate your MainNet config with "burst.sh switch MainNet" 

EOF
}

function upgrade_conf () {
    NXT_CFG_NAME="conf/$1"

    if [ -r $NXT_CFG_NAME ]
    then
        BRS_CFG_NAME="${NXT_CFG_NAME//nxt/brs}"
        BRS_CFG_NAME="${BRS_CFG_NAME}.converted"

        echo "[+] Converting $NXT_CFG_NAME -> $BRS_CFG_NAME"

        BRS=$(<$NXT_CFG_NAME)    # read in the config file content
        ### P2P-related params
        BRS="${BRS//nxt\.shareMyAddress/P2P.shareMyAddress}"
        BRS="${BRS//nxt\.myAddress/P2P.myAddress}"
        BRS="${BRS//nxt\.peerServerHost/P2P.Listen}"
        BRS="${BRS//nxt\.peerServerPort/P2P.Port}"
        BRS="${BRS//nxt\.myPlatform/P2P.myPlatform}"
        BRS="${BRS//nxt\.wellKnownPeers/P2P.BootstrapPeers}"
        BRS="${BRS//burst\.rebroadcastPeers/P2P.rebroadcastTo}"
        BRS="${BRS//burst\.connectWellKnownFirst/P2P.NumBootstrapConnections}"
        BRS="${BRS//nxt\.knownBlacklistedPeers/P2P.BlacklistedPeers}"
        BRS="${BRS//nxt\.maxNumberOfConnectedPublicPeers/P2P.MaxConnections}"
        BRS="${BRS//nxt\.connectTimeout/P2P.TimeoutConnect_ms}"
        BRS="${BRS//nxt\.readTimeout/P2P.TimeoutRead_ms}"
        BRS="${BRS//nxt\.peerServerIdleTimeout/P2P.TimeoutIdle_ms}"
        BRS="${BRS//nxt\.blacklistingPeriod/P2P.BlacklistingTime_ms}"
        BRS="${BRS//nxt\.usePeersDb/P2P.usePeersDb}"
        BRS="${BRS//nxt\.savePeers/P2P.savePeers}"
        BRS="${BRS//nxt\.getMorePeers/P2P.getMorePeers}"
        BRS="${BRS//nxt\.enableTransactionRebroadcasting/P2P.enableTxRebroadcast}"
        BRS="${BRS//burst\.rebroadcastAfter/P2P.rebroadcastTxAfter}"
        BRS="${BRS//burst\.rebroadcastEvery/P2P.rebroadcastTxEvery}"
        BRS="${BRS//nxt\.enablePeerServerGZIPFilter/JETTY.P2P.GZIPFilter}"

        ### JETTY pass-through params
        BRS="${BRS//nxt\.enablePeerServerDoSFilter/JETTY.P2P.DoSFilter}"
        BRS="${BRS//nxt\.peerServerDoSFilter.maxRequestsPerSec/JETTY.P2P.DoSFilter.maxRequestsPerSec}"
        BRS="${BRS//nxt\.peerServerDoSFilter.delayMs/JETTY.P2P.DoSFilter.delayMs}"
        BRS="${BRS//nxt\.peerServerDoSFilter.maxRequestMs/JETTY.P2P.DoSFilter.maxRequestMs}"

        ### DEVELOPMENT-related params (TestNet, Offline, Debug, Timewarp etc.)
        BRS="${BRS//nxt\.isTestnet/DEV.TestNet}"
        BRS="${BRS//nxt\.testnetPeers/DEV.TestNet.Peers}"
        BRS="${BRS//nxt\.isOffline/DEV.Offline}"
        BRS="${BRS//nxt\.time.Multiplier/DEV.TimeWarp}"
        BRS="${BRS//burst\.mockMining/DEV.mockMining}"
        BRS="${BRS//nxt\.testDbUrl/DEV.DB.Url}"
        # that bug may be in
        BRS="${BRS//nxt\.testDUsername/DEV.DB.Username}"
        BRS="${BRS//nxt\.testDbUsername/DEV.DB.Username}"
        BRS="${BRS//nxt\.testDbPassword/DEV.DB.Password}"
        BRS="${BRS//nxt\.dumpPeersVersion/DEV.dumpPeersVersion}"
        BRS="${BRS//nxt\.forceValidate/DEV.forceValidate}"
        BRS="${BRS//nxt\.forceScan/DEV.forceScan}"
        # Development/Logging/Debugging
        BRS="${BRS//nxt\.debugTraceLog/brs.debugTraceLog}"
        BRS="${BRS//nxt\.communicationLoggingMask/brs.communicationLoggingMask}"
        BRS="${BRS//nxt\.debugTraceAccounts/brs.debugTraceAccounts}"
        BRS="${BRS//nxt\.debugTraceSeparator/brs.debugTraceSeparator}"
        BRS="${BRS//nxt\.debugTraceQuote/brs.debugTraceQuote}"
        BRS="${BRS//nxt\.debugLogUnconfirmed/brs.debugLogUnconfirmed}"

        
        # API-related params
        BRS="${BRS//nxt\.enableAPIServer/API.Server}"
        BRS="${BRS//nxt\.enableDebugAPI/API.Debug}"
        BRS="${BRS//nxt\.keyStorePath/API.SSL_keyStorePath}"
        BRS="${BRS//nxt\.keyStorePassword/API.SSL_keyStorePassword}"
        BRS="${BRS//nxt\.allowedBotHosts/API.allowed}"
        BRS="${BRS//nxt\.apiServerHost/API.Listen}"
        BRS="${BRS//nxt\.apiServerPort/API.Port}"
        BRS="${BRS//nxt\.apiServerIdleTimeout/API.ServerIdleTimeout}"
        BRS="${BRS//nxt\.apiSSL/API.SSL}"
        BRS="${BRS//nxt\.apiServerEnforcePOST/API.ServerEnforcePOST}"
        BRS="${BRS//nxt\.apiServerCORS/API.CrossOriginFilter}"
        BRS="${BRS//nxt\.apiResourceBase/API.UI_Dir}"

        
        # DB-related params
        BRS="${BRS//nxt\.dbUrl/DB.Url}"
        BRS="${BRS//nxt\.dbUsername/DB.Username}"
        BRS="${BRS//nxt\.dbPassword/DB.Password}"
        BRS="${BRS//nxt\.dbMaximumPoolSize/DB.Connections}"
        # inconsistency/alias
        BRS="${BRS//nxt\.maxDbConnections/DB.Connections}"
        BRS="${BRS//nxt\.trimDerivedTables/DB.trimDerivedTables}"
        BRS="${BRS//nxt\.maxRollback/DB.maxRollback}"
        BRS="${BRS//nxt\.dbDefaultLockTimeout/DB.LockTimeout}"

        # GPU-related params
        BRS="${BRS//burst\.oclVerify/GPU.Acceleration}"
        BRS="${BRS//burst\.oclAuto/GPU.AutoDetect}"
        BRS="${BRS//burst\.oclPlatform/GPU.PlatformIdx}"
        BRS="${BRS//burst\.oclDevice/GPU.DeviceIdx}"
        BRS="${BRS//burst\.oclMemPercent/GPU.MemPercent}"
        BRS="${BRS//burst\.oclHashesPerEnqueue/GPU.HashesPerBatch}"

        # CPU-related params
        BRS="${BRS//Nxt\.cpuCores/CPU.NumCores}"
        
        echo "$BRS" > $BRS_CFG_NAME
    else
        echo "[!] $NXT_CFG_NAME not present or not readable."
        exit 1
    fi
}

function create_brs_db {
    echo "\n[+] Please enter your MariaDB connection details"
    read -rp  "     Host     (localhost) : " P_HOST
    read -rp  "     Database (brs_master): " P_DATA
    read -rp  "     Username (brs_user)  : " P_USER
    read -rsp "     Password empty       : " P_PASS
    [ -z $P_HOST ] && P_HOST="localhost"
    [ -z $P_USER ] && P_USER="brs_user"
    [ -z $P_DATA ] && P_DATA="brs_master"
    [ -z $P_PASS ] || P_PASS="$P_PASS"
    echo

    echo "[+] Creating burst wallet db ($P_DATA)..."
    mysql -uroot -h$P_HOST << EOF
CREATE DATABASE $P_DATA CHARACTER SET = 'utf8mb4' COLLATE = 'utf8mb4_unicode_ci';
CREATE USER '$P_USER'@'$P_HOST' IDENTIFIED BY '$P_PASS'; 
GRANT ALL PRIVILEGES ON $P_DATA.* TO '$P_USER'@'$P_HOST';
EOF

    # Verify mariadb setup
    if mysql -u$P_USER -p$P_PASS -h$P_HOST -e "\q" ; then
        echo "[+] $P_DATA Database created successfully."
    else
        echo "[!] Database creation failed. Exiting..."
        exit 1
    fi

    echo "DB.Url=jdbc:mariadb://$P_HOST:3306/$P_DATA" >> ./conf/brs.properties
    echo "DB.Username=$P_USER" >> ./conf/brs.properties
    echo "DB.Password=$P_PASS" >> ./conf/brs.properties
}

function macos_dependency_install {
    # Verify compatible macos version
    VERSION_MINOR=$(sw_vers -productVersion | grep -E -o '1[0-9]' | tail -n1)
    if [ "$VERSION_MINOR" -lt "10" ]; then
        echo "[!] Unsupported version of macOS, Homebrew requires 10.10 or greater"
        echo "[!] Please manually install MariaDB, and Java and run this command again."
        return
    fi
    echo "[+] Installing BRS wallet dependencies for macOS"
    
    # Install or upgrade brew
    which -s brew
    if [[ $? != 0 ]] ; then
        echo "[+] Installing Homebrew..."
        echo "[+] Press ENTER when prompted then enter sudo password if asked."
        ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
    else
        echo "[+] Homebrew found - Updating Homebrew... (might take a while)"
        brew update
    fi

    # Install or upgrade mariaDB
    echo "[+] Installing MariaDB using Homebrew..."
    if brew ls --versions mariadb > /dev/null; then
        MARIA_STATE=$(brew services list | grep mariadb | awk '{ print $2 }')
        if [[ $MARIA_STATE == "started" ]]; then
            echo "[+] MariaDB is already installed and running."
        fi
        brew upgrade mariadb
        echo "[+] MariaDB upgrade complete."
    else
        brew install mariadb
        echo "[+] MariaDB install complete."
    fi

    # mariaDB setup
    echo "[+] Starting MariaDB..."
    brew services start mariadb
    sleep 5 
    create_brs_db
    sleep 2

    # Check if java is installed
    if [ -z `which java 2>/dev/null` ]; then
        echo "[+] Java install not found. Installing Java 8 JDK using Homebrew..."
        brew tap caskroom/versions
        brew cask search java
        brew cask install java8
    else 
        echo "[+] Java dependency already met."
    fi

    echo "[+] macOS Dependency setup completed successfully."
    echo "[+] Wallet will start momentarily..."
}

function mariadb_installed {
    if which mysql >/dev/null; then
        MYSQL_VERSION=$(mysql --version)
        if [[ "$MYSQL_VERSION" == *"MariaDB"* ]]; then
            MARIADB_INSTALLED=0
        else 
            MARIADB_INSTALLED=1
        fi
    else
        MARIADB_INSTALLED=1
    fi
}

function exists_or_get {
    if [ -f $1 ]; then
        echo "[+] $1 already present - won't download"
    else
        if ! hash wget 2>/dev/null; then
            echo "[!] Please install wget"
            exit 99
        fi
        wget https://download.cryptoguru.org/burst/wallet/$1
    fi
}

if [ -z `which java 2>/dev/null` ]; then
    echo "[!] Please install java from eg. https://java.com/download/"
    exit 1
fi

if [[ $# -gt 0 ]] ; then
    case "$MY_CMD" in
        "clean")
            rm -rf dist html/ui/doc
            mkdir -p html/ui/doc
            mvn clean
            ;;
        "compile")
            if [ -d "maven/apache-maven-${MY_MAVEN_VERSION}" ]; then
                PATH=maven/apache-maven-${MY_MAVEN_VERSION}/bin:$PATH
            fi

            ## check if command exists
            if hash mvn 2>/dev/null; then
                ./burst.sh clean
                mvn -DskipTests=true package
                mvn javadoc:javadoc-no-fork
                cp -r target/site/apidocs/* html/ui/doc
                cp dist/tmp/burst.jar .
                echo a .zip file has been built for distribution in dist/, its contents are in dist/tmp
                echo Nevertheless, now you can start the wallet with ./burst.sh
            else
                echo This build method is no longer supported. Please install maven.
                echo https://maven.apache.org/install.html
                if hash wget 2>/dev/null; then
                    read -p "[?] Do you want me to install a local copy of maven in this directory? " -n 1 -r
                    echo
                    if [[ $REPLY =~ ^[Yy]$ ]]; then
                        mkdir -p maven
                        cd maven
                        ## This is an official mirror
                        wget "http://mirror.23media.de/apache/maven/maven-3/$MY_MAVEN_VERSION/binaries/apache-maven-${MY_MAVEN_VERSION}-bin.tar.gz"
                        tar -xvzf "apache-maven-${MY_MAVEN_VERSION}-bin.tar.gz"
                        rm "apache-maven-$MY_MAVEN_VERSION-bin.tar.gz"
                        echo Please try again, it should work now. You might want to check if the environment variable JAVA_HOME points to a valid JDK.
                    fi
                fi
            fi
            exit 0
            ;;
        "h2shell")
            java -cp burst.jar org.h2.tools.Shell
            ;;
        "import")
            if ! hash unzip 2>/dev/null; then
                echo "[!] Please install unzip"
                exit 99
            fi
            CONF_FILE="conf/brs.properties"
            useCurrentConfig="0"
            allFound="1"
            read -p "Do you want to remove the current databases, download and import new one? " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                if [[ $MY_ARG == "mariadb" ]]; then
                    if [[ -f $CONF_FILE ]]
                    then
                        read -p "Detected a previous database config(conf/brs.properties). Do you want to use the config? " -n 1 -r
                        echo
                        if [[ $REPLY =~ ^[Yy]$ ]]
                        then
                            useCurrentConfig="1"
                        fi
                    fi
                    if [ "${useCurrentConfig}" == "1" ]
                    then
                        BRS=$(<$CONF_FILE)
                        if [[ $BRS =~ DB.Url=jdbc:mariadb://([0-9a-zA-Z]+):[0-9]+/([0-9a-zA-Z_]+) ]]
                        then
                            P_HOST="${BASH_REMATCH[1]}"
                            P_DATA="${BASH_REMATCH[2]}"
                            if [[ $BRS =~ DB.Username=([0-9a-zA-Z_]+) ]]
                            then
                                P_USER="${BASH_REMATCH[1]}"
                            else
                                echo "Could not find Database Username"
                                allFound="0"
                            fi
                            if [[ $BRS =~ DB.Password=([0-9A-Za-z\.-_\!\"\%\&\/\(\)\\\<\>\^]+) ]]
                            then
                                P_PASS="${BASH_REMATCH[1]}"
                            else
                                echo "Could not find Database Password"
                                allFound="0"
                            fi
                        else
                            echo "Could not find Hostname and DatabaseName"
                            allFound="0"
                        fi
                    fi
                    if [ "${allFound}" != "1" ] || [ "${useCurrentConfig}" != "1" ]
                    then
                        echo
                        echo "Please enter your connection details"
                        read -rp  "Host     (localhost) : " P_HOST
                        read -rp  "Database (brs_master): " P_DATA
                        read -rp  "Username (brs_user)  : " P_USER
                        read -rsp "Password empty       : " P_PASS
                    fi
                    [ -z $P_HOST ] && P_HOST="localhost"
                    [ -z $P_USER ] && P_USER="brs_user"
                    [ -z $P_DATA ] && P_DATA="brs_master"
                    [ -z $P_PASS ] || P_PASS="-p$P_PASS"
                    echo

                    if exists_or_get brs.mariadb.zip ; then
                        if unzip brs.mariadb.zip ; then
                            if mysql -u$P_USER $P_PASS -h$P_HOST -e "DROP DATABASE if EXISTS $P_DATA; CREATE DATABASE $P_DATA CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"; then
                                if mysql -u$P_USER $P_PASS -h$P_HOST -D $P_DATA < "$MY_DIR/init-mysql.sql"; then
                                    if mysql -u$P_USER $P_PASS -h$P_HOST -D $P_DATA < brs.mariadb.sql ; then
                                        echo "[+] Import successful - please remove brs.mariadb.zip"
                                        rm brs.mariadb.sql
                                        exit
                                    fi
                                fi
                            fi
                        else
                            echo "[!] Unpacking mariadb archive failed"
                        fi
                    else
                        echo "[!] Getting mariadb archive failed"
                    fi
                elif [[ $MY_ARG == "h2" ]]; then
                    if exists_or_get brs.h2.zip ; then
                        mkdir -p "$MY_DIR/burst_db"
                        rm -f burst_db/burst.trace.db
                        if unzip brs.h2.zip ; then
                            if mv burst.mv.db "$MY_DIR/burst_db"; then
                                echo "[+] Import successful - please remove brs.h2.zip"
                                exit
                            fi
                        else
                            echo "[!] Unpacking H2 archive failed"
                        fi
                    else
                        echo "[!] Getting H2 archive failed"
                    fi
                fi
                echo "[!] DB import did not succeed"
            else
                echo "[!] Cancelling DB import by user request"
            fi
            ;;
        "switch")
            CONF_BASE=conf/brs.properties         # our symlink
            CONF_TGT=brs.properties.$MY_ARG       # target of our symlink

            if [[ (-L "$CONF_BASE" || ! -f $CONF_BASE) &&  -f "conf/$CONF_TGT" ]]
            then
                rm -f $CONF_BASE
                ln -s $CONF_TGT $CONF_BASE 
            else
                echo "$CONF_BASE exists and not a symlink or conf/$CONF_TGT nonexistant."
            fi
            ;;
        "upgrade")
            upgrade_conf nxt-default.properties
            upgrade_conf nxt.properties
            ;;
        *)
            usage
            ;;
    esac
else
    if [[ "$OSTYPE" == "darwin"* ]]; then
        mariadb_installed
        echo "[+] Darwin OS Type detected, checking dependencies..."
        if [[ $MARIADB_INSTALLED == 1 ]]; then
            macos_dependency_install
        fi
    fi
    java $BRS_DEVSTART -cp burst.jar:conf brs.Burst
fi
