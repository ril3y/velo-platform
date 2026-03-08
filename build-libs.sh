#!/bin/bash
# Build standalone JARs for consumer apps (BikeArcade, FreeRide)
# Outputs: ucblib/build/libs/ucblib.jar, velofit/build/libs/velofit.jar
set -e

echo "=== Building ucblib + velofit JARs ==="
./gradlew :ucblib:buildClassesJar :velofit:buildClassesJar

echo ""
echo "=== JARs built ==="
echo "  ucblib:  ucblib/build/libs/ucblib.jar"
echo "  velofit: velofit/build/libs/velofit.jar"
echo ""
echo "Copy to consumer apps:"
echo "  cp ucblib/build/libs/ucblib.jar  ../velogames/metrics/libs/"
echo "  cp velofit/build/libs/velofit.jar ../freeride/app/libs/"
