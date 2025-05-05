# Use the Debian-based node image with OpenJDK 21 for better compatibility
ARG NODE_VERSION=20
FROM node:${NODE_VERSION}-bullseye AS builder

# Install dependencies for adding new repository
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    apt-transport-https \
    gnupg \
    software-properties-common \
    && rm -rf /var/lib/apt/lists/*

# Add Adoptium repository for OpenJDK 21
RUN wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | apt-key add - \
    && echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list

# Install required tools, including Temurin (Adoptium) JDK 21
RUN apt-get update && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends \
        coreutils \
        bind9-dnsutils \
        git \
        unzip \
        wget \
        curl \
        ca-certificates \
        openssl \
        binutils \
        temurin-21-jdk \
    && mkdir -p /etc/ssl/certs \
    && chmod 755 /etc/ssl/certs \
    && update-ca-certificates --fresh \
    && chmod -R 644 /etc/ssl/certs/* \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN ls -la /etc/ssl/certs && \
    curl -V
# Set Java version (21) and configure the environment variables
ENV JAVA_HOME="/usr/lib/jvm/temurin-21-jdk-arm64"
ENV PATH="${JAVA_HOME}/bin:${PATH}"
# Set the working directory
WORKDIR /signum-node

# Copy all project files
COPY . .

# Verify Node.js and npm versions
RUN node -v
RUN npm -v

# Disable the Gradle wrapper from downloading itself
RUN sed -i 's/download = true/download = false/g' /signum-node/build.gradle

# Build Signum Node Jar using Gradle wrapper
RUN chmod +x /signum-node/gradlew \
    && ./gradlew clean dist jdeps \
        --no-daemon \
        -Dorg.gradle.jvmargs="-Xmx1024m -Xms512m" \
        -Dorg.gradle.vfs.watch=false \
        -Pjdeps.recursive=true \
        -Pjdeps.ignore.missing.deps=true \
        -Pjdeps.print.module.deps=true

# Unpack the build into the /signum directory
RUN unzip -o build/distributions/signum-node.zip -d /signum \
    && rm -rf build/distributions/signum-node.zip

# Provide update scripts
COPY update-phoenix.sh /signum/update-phoenix.sh
COPY update-classic.sh /signum/update-classic.sh
RUN chmod +x /signum/update-phoenix.sh /signum/update-classic.sh

# Create a custom JRE using jlink
RUN ${JAVA_HOME}/bin/jlink \
    --add-modules $(cat /signum-node/build/reports/jdeps/print-module-deps-main.txt) \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=1 \
    --output /jre

# Copy required native libraries for Java dependencies
RUN mkdir -p /requirements \
    && ldd /jre/bin/java | awk 'NF == 4 { system("cp --parents " $3 " /requirements") }'

# Prepare the final slim runtime image
FROM debian:bullseye-slim
LABEL name="Signum Node"
LABEL description="This is the official Signum Node image"
LABEL credits="gittrekt,damccull,ohager"

# Set environment variables
ENV JAVA_HOME=/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Install runtime dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    coreutils \
    bind9-dnsutils \
    git \
    unzip \
    curl \
    ca-certificates \
    openssl \
    && mkdir -p /etc/ssl/certs \
    && chmod 755 /etc/ssl/certs \
    && update-ca-certificates --fresh \
    && chmod -R 644 /etc/ssl/certs/* \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Copy the custom Java runtime and application from the builder stage
COPY --from=builder /jre $JAVA_HOME
COPY --from=builder /signum /signum
COPY --from=builder /requirements/ /

# Set the working directory
WORKDIR /signum

# Add volumes for configuration and database
VOLUME ["/conf", "/db"]
RUN ln -s /conf /signum/conf && ln -s /db /signum/db

# Use one of [sqlite,mariadb,postgres]
ARG database=sqlite
ARG network=mainnet

# Provide the default configuration files
RUN mkdir ./bootstrap
COPY conf/logging-default.properties ./bootstrap/logging-default.properties
COPY conf/node-default.properties ./bootstrap/node-default.properties
COPY conf/${network}/node.${database}.properties ./bootstrap/node.properties

# Copy and make the start script executable
COPY docker/scripts/start-node.sh ./start-node.sh
RUN chmod +x start-node.sh

# Injectable ports defaulting to mainnet
ARG port_p2p=8123
ARG port_http=8125
ARG port_ws=8126
EXPOSE $port_p2p $port_http $port_ws

# Run the application
ENTRYPOINT [ "/bin/bash", "./start-node.sh" ]
