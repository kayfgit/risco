# Builds the optimized APK, installs it on the connected phone and opens it.
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\gradlew.bat" -p $PSScriptRoot installRelease
if ($LASTEXITCODE) { exit $LASTEXITCODE }
adb shell am start -n com.kayf.dedo/.MainActivity
