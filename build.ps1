$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$localJdk = Get-ChildItem (Join-Path $projectRoot '.tools\jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
if (-not $env:JAVA_HOME) { throw 'Configure JAVA_HOME para um JDK 17.' }
$sdkLocation = Join-Path $projectRoot '.tools\android-sdk'
if (Test-Path $sdkLocation) {
    $escapedSdk = $sdkLocation.Replace('\','/').Replace(':','\:')
    Set-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Value "sdk.dir=$escapedSdk"
}
Push-Location $projectRoot
try {
    & .\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Compilação ou verificação falhou.' }
    New-Item -ItemType Directory -Force entrega | Out-Null
    Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-arm64-v8a-debug.apk' -Destination 'entrega\LangerCamera-0.1.14-arm64.apk'
    Get-FileHash -Algorithm SHA256 'entrega\LangerCamera-0.1.14-arm64.apk'
} finally { Pop-Location }
