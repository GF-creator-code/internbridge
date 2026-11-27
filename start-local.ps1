# Build and start the local management UI.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/java.exe")) {
    $jdk = Get-ChildItem "$env:USERPROFILE/.gradle/jdks" -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path "$($_.FullName)/bin/java.exe" } | Select-Object -First 1
    if (-not $jdk) { throw 'Please install JDK 21 and configure JAVA_HOME.' }
    $env:JAVA_HOME = $jdk.FullName
}
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
$env:SERVER_ADDRESS = '127.0.0.1'
if ($env:JAVA_TOOL_OPTIONS -notmatch 'jdk.net.unixdomain.tmpdir') {
    $env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Djdk.net.unixdomain.tmpdir=$env:SystemRoot\Temp".Trim()
}

if (-not (Test-Path 'src/main/resources/dist/index.html') -and -not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw 'Install Node.js 22.13+ and pnpm or Corepack to build the management UI, then run this script again.'
}
& ./gradlew.bat bootRun @args
exit $LASTEXITCODE
