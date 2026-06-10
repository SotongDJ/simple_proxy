#!/bin/bash
set -e

# Colors for log output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0;37m' # No Color

echo -e "${BLUE}[1/3] Cleaning up old build artifacts...${NC}"
rm -rf bin
rm -f cors_proxy.jar
mkdir -p bin

echo -e "${BLUE}[2/3] Compiling Java source files...${NC}"
javac -d bin src/com/corsproxy/*.java

echo -e "${BLUE}[3/3] Creating executable JAR (cors_proxy.jar)...${NC}"
# jar cfe <jar-file> <main-class> -C <dir> <files>
jar cfe cors_proxy.jar com.corsproxy.CorsProxyApp -C bin com/
chmod +x cors_proxy.jar

if [ -f cors_proxy.jar ]; then
    echo -e "${GREEN}===========================================${NC}"
    echo -e "${GREEN}Build successful! Generated: cors_proxy.jar${NC}"
    echo -e "${GREEN}To run the app, execute: ./cors_proxy.jar or java -jar cors_proxy.jar${NC}"
    echo -e "${GREEN}===========================================${NC}"
else
    echo -e "${RED}Error: Failed to generate cors_proxy.jar${NC}"
    exit 1
fi
