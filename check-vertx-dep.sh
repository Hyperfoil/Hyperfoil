#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

echo "=== Step 1: Extracting Vert.x version from local POM ==="
VERT_X_TAG=$(mvn help:evaluate -Dexpression=version.vertx -q -DforceStdout)

if [ -z "$VERT_X_TAG" ] || [ "$VERT_X_TAG" == "null object or invalid expression" ]; then
    echo "Error: Could not extract version.vertx from local pom.xml"
    exit 1
fi

echo "Extracted Vert.x version: $VERT_X_TAG"

TARGET_DIR_VERTX="/tmp/infinispan-vertx-$VERT_X_TAG"

echo ""
echo "=== Step 2: Setting up vertx-infinispan repository ==="
if [ ! -d "$TARGET_DIR_VERTX" ]; then
    echo "Directory not found. Cloning vertx-infinispan ($VERT_X_TAG) to $TARGET_DIR_VERTX..."
    # Silenced advice and redirected stderr to hide tag warnings
    git -c advice.detachedHead=false clone -q --depth 1 --branch "$VERT_X_TAG" https://github.com/vert-x3/vertx-infinispan.git "$TARGET_DIR_VERTX" 2>/dev/null
else
    echo "Directory $TARGET_DIR_VERTX already exists. Skipping clone."
fi

echo ""
echo "=== Step 3: Generating Dependency List ==="
# Navigate directly into the implementation submodule
cd "$TARGET_DIR_VERTX/vertx-infinispan"

mvn dependency:list -DoutputFile=resolved-deps.txt -q
echo "Successfully generated resolved-deps.txt with fully resolved dependencies."

echo ""
echo "=== Step 4: Extracting Vert.x Dependencies ==="

ARTIFACTS_TO_CHECK=(
    "infinispan-core"
    "caffeine"
    "rxjava"
    "reactive-streams"
    "jakarta.transaction-api"
    "wildfly-common"
)

# Declare an associative array to store the final resolved versions
declare -A VERTX_VERSIONS

for ARTIFACT in "${ARTIFACTS_TO_CHECK[@]}"; do
    # Extract the version from the dependency list
    RESOLVED_VERSION=$(grep ":$ARTIFACT:" resolved-deps.txt | head -n 1 | tr -d ' ' | awk -F':' '{print $(NF-1)}')
    [ -z "$RESOLVED_VERSION" ] && RESOLVED_VERSION="NOT_FOUND"
    VERTX_VERSIONS["$ARTIFACT"]=$RESOLVED_VERSION
    echo "- $ARTIFACT: $RESOLVED_VERSION"
done

# Return to original directory
cd - > /dev/null
