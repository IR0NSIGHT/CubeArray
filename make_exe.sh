#!/usr/bin/env bash
# run on windows using git bash. sorry, its shit i know
# delete old exe
rm -rf dist

# clean and rebuild
mvn clean package

# stage only the fat jar — jpackage copies everything in --input
rm -rf staging
mkdir staging
cp target/CubeArray.jar staging/CubeArray.jar

# make new exe
jpackage \
  --input staging/ \
  --name CubeArray \
  --main-jar CubeArray.jar \
  --main-class org.ironsight.CubeArray.CubeArrayMain \
  --type app-image \
  --dest dist/

# copy the fat jar and readme alongside the exe inside the app-image folder
cp staging/CubeArray.jar dist/CubeArray/CubeArray.jar
cp release/README.txt dist/CubeArray/README.txt

# zip the full app-image (exe + bundled JRE)
cd dist && zip -r CubeArray.zip CubeArray && cd ..

# zip jar-only (lightweight, requires Java 17+)
mkdir -p dist/jar-only
cp staging/CubeArray.jar dist/jar-only/CubeArray.jar
cp release/README.txt dist/jar-only/README.txt
cd dist && zip -j CubeArray-win-jar.zip jar-only/CubeArray.jar jar-only/README.txt && cd ..

# open containing folder (best effort)
start dist\\CubeArray\\
