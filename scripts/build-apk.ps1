# Downloads Gradle 8.9, generates the wrapper, and builds the debug APK.
$ErrorActionPreference = "Stop"

$JavaHome = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"
$env:ANDROID_HOME = "C:\Android\Sdk"
$env:ANDROID_SDK_ROOT = "C:\Android\Sdk"

$gradleBat = "C:\Gradle\gradle-8.9\bin\gradle.bat"
if (-not (Test-Path $gradleBat)) {
    $gradleZip = "$env:TEMP\gradle-8.9-bin.zip"
    if (-not (Test-Path $gradleZip)) {
        Write-Host "Downloading Gradle 8.9..."
        $wc = New-Object System.Net.WebClient
        $wc.DownloadFile("https://services.gradle.org/distributions/gradle-8.9-bin.zip", $gradleZip)
    }
    Write-Host "Extracting Gradle..."
    New-Item -ItemType Directory -Force -Path "C:\Gradle" | Out-Null
    Expand-Archive -Path $gradleZip -DestinationPath "C:\Gradle" -Force
}

Set-Location "C:\Users\moron\AndroidProjects\ControleNotas"

Write-Host "Generating Gradle wrapper..."
& $gradleBat wrapper --gradle-version 8.9 --no-daemon

Write-Host "Building debug APK..."
& $gradleBat assembleDebug --no-daemon --stacktrace

Write-Host "APK output:"
Get-ChildItem -Recurse -Filter *.apk app\build\outputs 2>$null | Select-Object -ExpandProperty FullName
