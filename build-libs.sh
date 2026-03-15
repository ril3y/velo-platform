#!/bin/bash
# Build standalone JARs and copy to consumer apps
# Outputs: ucblib/build/libs/ucblib.jar, velofit/build/libs/velofit.jar
set -e

echo "=== Building ucblib + velofit JARs ==="
./gradlew :ucblib:buildClassesJar :velofit:buildClassesJar

echo ""
echo "=== Copying JARs to velogames ==="
cp ucblib/build/libs/ucblib.jar  ../velogames/metrics/libs/
cp velofit/build/libs/velofit.jar ../velogames/metrics/libs/
echo "  ucblib.jar  → velogames/metrics/libs/"
echo "  velofit.jar → velogames/metrics/libs/"
