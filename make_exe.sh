#!/usr/bin/env bash
# run on windows using git bash. sorry, its shit i know
# delete old exe
rm -rf dist

# clean and rebuild
mvn clean package

# make new exe
jpackage \
  --input target/ \
  --name CubeArray \
  --main-jar CubeArray.jar \
  --main-class org.ironsight.CubeArray.CubeArrayMain \
  --type app-image \
  --dest dist/

mkdir dist

# zip the app-image
cd dist && zip -r CubeArray.zip CubeArray && cd ..

# open containing folder (best effort)
start dist\\CubeArray\\
