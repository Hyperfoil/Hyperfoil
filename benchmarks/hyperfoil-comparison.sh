#!/bin/bash
set -eo pipefail

# ==========================================
# Default Configuration (States)
# ==========================================
SERVER_THINK_TIME=10
SERVER_THREADS=10
CLIENT_CONNECTIONS=10
BENCHMARK_DURATION="30s"
BENCHMARK_ENDPOINT="http://localhost:8080/fruits"
BUILD_DIR="../"

# CPU and JVM Defaults
SERVER_CPUS="0,1,2"
CLIENT_CPUS="3,4,5"
SERVER_JVM_ARGS="-Xms512m -Xmx512m -XX:+UseParallelGC"
CLIENT_JVM_ARGS="-Xms512m -Xmx512m -XX:+UseParallelGC"

# wrk2 only
BENCHMARK_RATE="1000"

# Build Cache
CACHE_BUILD="false"

# ==========================================
# Argument Parsing
# ==========================================
usage() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  --server-think-time <ms>     Server think time in ms (default: $SERVER_THINK_TIME)"
    echo "  --server-threads <num>       Number of server threads (default: $SERVER_THREADS)"
    echo "  --client-connections <num>   Number of client connections (default: $CLIENT_CONNECTIONS)"
    echo "  --benchmark-duration <time>  Duration of the benchmark (default: $BENCHMARK_DURATION)"
    echo "  --benchmark-endpoint <url>   Target endpoint (default: $BENCHMARK_ENDPOINT)"
    echo "  --build-dir <path>           Hyperfoil source directory to build (default: $BUILD_DIR)"
    echo "  --server-cpus <cpulist>      CPUs for the server taskset (default: $SERVER_CPUS)"
    echo "  --client-cpus <cpulist>      CPUs for the client taskset (default: $CLIENT_CPUS)"
    echo "  --server-jvm-args <args>     JVM parameters for the server (default: $SERVER_JVM_ARGS)"
    echo "  --client-jvm-args <args>     JVM parameters for the client (default: $CLIENT_JVM_ARGS)"
    echo "  --cache-build <true|false>   Skip build if wrk.jar already exists (default: $CACHE_BUILD)"
    echo "  --benchmark-rate <req/s>     Request rate for the wrk2 benchmark (default: $BENCHMARK_RATE)"
    echo "  -h, --help                   Display this help message"
    exit 1
}

while [[ "$#" -gt 0 ]]; do
    case $1 in
        --server-think-time) SERVER_THINK_TIME="$2"; shift ;;
        --server-threads) SERVER_THREADS="$2"; shift ;;
        --client-connections) CLIENT_CONNECTIONS="$2"; shift ;;
        --benchmark-duration) BENCHMARK_DURATION="$2"; shift ;;
        --benchmark-endpoint) BENCHMARK_ENDPOINT="$2"; shift ;;
        --build-dir) BUILD_DIR="$2"; shift ;;
        --server-cpus) SERVER_CPUS="$2"; shift ;;
        --client-cpus) CLIENT_CPUS="$2"; shift ;;
        --server-jvm-args) SERVER_JVM_ARGS="$2"; shift ;;
        --client-jvm-args) CLIENT_JVM_ARGS="$2"; shift ;;
        --cache-build) CACHE_BUILD="$2"; shift ;;
        --benchmark-rate) BENCHMARK_RATE="$2"; shift ;;
        -h|--help) usage ;;
        *) echo "Unknown parameter passed: $1"; usage ;;
    esac
    shift
done

# ==========================================
# Functions (Scripts/Roles)
# ==========================================

setup_server() {
    echo "[INFO] Setting up the Mock Server..."
    mkdir -p "$HOME/benchmarks_output"

    # Prepend the jbang dependency and download the source code
    echo "//DEPS io.netty:netty-all:4.1.136.Final" > /tmp/MockHttpServer.java
    cat src/main/java/io/hyperfoil/mock/MockHttpServer.java >> /tmp/MockHttpServer.java

    # Start the server in the background using taskset and custom JVM args
    echo "[INFO] Starting MockHttpServer on CPUs [$SERVER_CPUS] with JVM args [$SERVER_JVM_ARGS]..."

    JAVA_OPTS="$SERVER_JVM_ARGS" taskset -c "$SERVER_CPUS" jbang /tmp/MockHttpServer.java --port 8080 --think-time "$SERVER_THINK_TIME" --threads "$SERVER_THREADS" > "$HOME/benchmarks_output/server.log" 2>&1 &
    echo $! > server.pid

    # Wait for the server to initialize
    sleep 5
}

stop_server() {
    echo "[INFO] Cleaning up and stopping the Mock Server..."
    if [ -f server.pid ]; then
        kill $(cat server.pid) || true
        rm -f server.pid
    fi
}

build_hyperfoil() {
    # Check if cache is enabled and the target jar already exists
    if [[ "$CACHE_BUILD" == "true" ]] && [[ -f "$BUILD_DIR/clustering/target/wrk.jar" ]]; then
        echo "[INFO] Build cache enabled and $BUILD_DIR/clustering/target/wrk.jar found. Skipping build step."
        return 0
    fi

    echo "[INFO] Building local Hyperfoil source tree..."
    pwd
    pushd "$BUILD_DIR" > /dev/null
    mvn clean install -DskipTests
    popd > /dev/null
}

benchmark_hyperfoil() {
    echo "[INFO] Starting $BENCHMARK_DURATION warm-up burst via jbang wrk on CPUs [$CLIENT_CPUS]..."
    JAVA_OPTS="$CLIENT_JVM_ARGS" taskset -c "$CLIENT_CPUS" jbang wrk@hyperfoil -c "$CLIENT_CONNECTIONS" -d "$BENCHMARK_DURATION" "$BENCHMARK_ENDPOINT" > "$HOME/benchmarks_output/wrk_jbang_warmup.log" 2>&1
    sleep 3

    echo "[INFO] Running wrk $BENCHMARK_DURATION measurement for public release on CPUs [$CLIENT_CPUS]..."
    JAVA_OPTS="$CLIENT_JVM_ARGS" taskset -c "$CLIENT_CPUS" jbang wrk@hyperfoil -c "$CLIENT_CONNECTIONS" -d "$BENCHMARK_DURATION" "$BENCHMARK_ENDPOINT" > "$HOME/benchmarks_output/wrk_jbang_measurement.log" 2>&1
    sleep 3

    echo "[INFO] Running wrk $BENCHMARK_DURATION measurement for local built jar on CPUs [$CLIENT_CPUS]..."
    taskset -c "$CLIENT_CPUS" java $CLIENT_JVM_ARGS -jar "$BUILD_DIR/clustering/target/wrk.jar" -c "$CLIENT_CONNECTIONS" -d "$BENCHMARK_DURATION" "$BENCHMARK_ENDPOINT" > "$HOME/benchmarks_output/wrk_local_measurement.log" 2>&1
    sleep 3

    echo "[INFO] Running wrk2 $BENCHMARK_DURATION measurement for public release on CPUs [$CLIENT_CPUS]..."
    JAVA_OPTS="$CLIENT_JVM_ARGS" taskset -c "$CLIENT_CPUS" jbang wrk2@hyperfoil -c "$CLIENT_CONNECTIONS" -d "$BENCHMARK_DURATION" --rate $BENCHMARK_RATE "$BENCHMARK_ENDPOINT" > "$HOME/benchmarks_output/wrk2_jbang_measurement.log" 2>&1
    sleep 3

    echo "[INFO] Running wrk2 $BENCHMARK_DURATION measurement for local built jar on CPUs [$CLIENT_CPUS]..."
    taskset -c "$CLIENT_CPUS" java $CLIENT_JVM_ARGS -jar "$BUILD_DIR/clustering/target/wrk2.jar" -c "$CLIENT_CONNECTIONS" -d "$BENCHMARK_DURATION" --rate $BENCHMARK_RATE "$BENCHMARK_ENDPOINT" > "$HOME/benchmarks_output/wrk2_local_measurement.log" 2>&1
    sleep 3
}

# ==========================================
# Main Execution (Roles Setup)
# ==========================================

# Register the cleanup function to run on exit or interruption
trap stop_server EXIT

setup_server
build_hyperfoil
benchmark_hyperfoil
