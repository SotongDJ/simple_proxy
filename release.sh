#!/bin/bash
set -e

# Colors for log output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0;37m' # No Color

# Ensure version argument is provided
if [ -z "$1" ]; then
    echo -e "${RED}Error: Version tag argument is required. (e.g., ./release.sh v1.0.1)${NC}"
    exit 1
fi

VERSION=$1

# Validate version format (must start with v followed by numbers)
if [[ ! "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo -e "${RED}Error: Version must follow semantic versioning format vX.Y.Z (e.g., v1.0.1)${NC}"
    exit 1
fi

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo -e "${RED}Error: GitHub CLI (gh) is not installed or not in PATH.${NC}"
    exit 1
fi

# Ensure working directory is clean
if [ -n "$(git status --porcelain)" ]; then
    echo -e "${RED}Error: Working directory is not clean. Please commit or stash changes first.${NC}"
    exit 1
fi

echo -e "${BLUE}[1/5] Building executable JAR...${NC}"
./build.sh

echo -e "${BLUE}[2/5] Creating signed Git tag ${VERSION}...${NC}"
git tag -s "$VERSION" -m "Release $VERSION"

echo -e "${BLUE}[3/5] Pushing commits and tag to origin...${NC}"
git push origin main
git push origin "$VERSION"

echo -e "${BLUE}[4/5] Creating GitHub Release and uploading JAR via GitHub CLI...${NC}"
gh release create "$VERSION" cors_proxy.jar --title "Release $VERSION" --generate-notes

echo -e "${GREEN}===========================================${NC}"
echo -e "${GREEN}Successfully released ${VERSION} to GitHub!${NC}"
echo -e "${GREEN}===========================================${NC}"
