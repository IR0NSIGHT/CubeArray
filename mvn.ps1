#!/usr/bin/env pwsh
# Drop-in Maven wrapper for CubeArray.
#
# Resolves all dependencies directly from Maven Central (via settings-central.xml)
# instead of the company Artifactory, and trusts the corporate proxy CA
# (Zscaler / Vector) through the Windows root certificate store so TLS
# interception doesn't cause PKIX errors.
#
# Because the dependencies are cached in the local repo under the "central"
# repository id, every Maven command for this project must run in this same
# context -- a bare "mvn ..." would fail. Use this wrapper instead.
#
# Usage (from the project root):
#   ./mvn compile
#   ./mvn clean install
#   ./mvn test

Set-Location -Path $PSScriptRoot

# Build with JDK 25.
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"

# Trust the Windows root store (contains the corporate proxy CA) at JVM startup.
$env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT -Djavax.net.ssl.trustStore=NUL"

# Forward all arguments to Maven, forcing the Maven Central settings.
mvn -s settings-central.xml @args

exit $LASTEXITCODE
