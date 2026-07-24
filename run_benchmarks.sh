#!/bin/bash
set -e

cd "$(dirname "$0")"

FILTER="${1:-.*}"

echo "Building project..."
mvn -pl cubearray-core test-compile -Dspotless.skip=true -q

echo "Building classpath..."
mvn -pl cubearray-core dependency:build-classpath -Dmdep.outputFile=target/cp.txt -DincludeScope=test -q

cd cubearray-core

echo "Running benchmarks (filter: ${FILTER})..."
CP=$(cat target/cp.txt):target/classes:target/test-classes

java -cp "$CP" \
    -Dbenchmark="${FILTER}" \
    -Djava.awt.headless=true \
    org.ironsight.cubearray.benchmark.BenchmarkRunner

echo ""
echo "Results:"
echo "  CSV:  cubearray-core/target/benchmark-results.csv"
echo "  JSON: cubearray-core/target/jmh-results.json"