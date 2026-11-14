# Start the local management UI. Only new installations download the upstream database template.
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
$dbDir = Join-Path $PSScriptRoot 'db'
$dbFile = Join-Path $dbDir 'getjobs.db'
if (-not [IO.File]::Exists($dbFile)) {
    [IO.Directory]::CreateDirectory($dbDir) | Out-Null
    $download = Join-Path $dbDir ("template-" + [guid]::NewGuid().ToString('N') + ".tmp")
    $source = 'https://github.com/loks666/get_jobs/releases/download/v2.0.1/getjobs.db.template'
    $digest = '81e837df423f1dcf06c89f430493a244bdb17f46d7eab6b22afaec94675e1c97'
    try {
        Write-Host 'Preparing the local database from the upstream v2.0.1 template...'
        Invoke-WebRequest -UseBasicParsing -Uri $source -TimeoutSec 60 -OutFile $download
        if ((Get-FileHash -Algorithm SHA256 -LiteralPath $download).Hash -ne $digest) {
            throw 'The database template checksum does not match.'
        }
        try { [IO.File]::Move($download, $dbFile) }
        catch [IO.IOException] { if (-not [IO.File]::Exists($dbFile)) { throw } }
    } catch {
        throw "Database setup failed. Download getjobs.db.template from $source and place it at db/getjobs.db. $($_.Exception.Message)"
    } finally {
        if ([IO.File]::Exists($download)) { [IO.File]::Delete($download) }
    }
}
& ./gradlew.bat bootRun @args
exit $LASTEXITCODE
