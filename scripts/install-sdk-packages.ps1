# Installs required Android SDK packages and accepts licenses.
$ErrorActionPreference = "Stop"

$JavaHome = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"

$sdk = "C:\Android\Sdk"
$sdkmanager = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"

Write-Host "Accepting SDK licenses..."
$yes = ("y`r`n" * 60)
$yes | & $sdkmanager --sdk_root=$sdk --licenses

Write-Host "Installing packages..."
& $sdkmanager --sdk_root=$sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0"

Write-Host "Installed packages:"
& $sdkmanager --sdk_root=$sdk --list_installed
