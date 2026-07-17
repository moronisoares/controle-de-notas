# Sets up Android SDK command-line tools and installs required packages.
$ErrorActionPreference = "Stop"

$JavaHome = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"

$sdk = "C:\Android\Sdk"
$zip = "$env:TEMP\cmdline-tools.zip"

Write-Host "Zip size (MB): $([math]::Round((Get-Item $zip).Length / 1MB, 1))"

$tmp = "$env:TEMP\cmdline-extract"
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
Write-Host "Extracting command-line tools..."
Expand-Archive -Path $zip -DestinationPath $tmp -Force

$latest = "$sdk\cmdline-tools\latest"
if (Test-Path $latest) { Remove-Item $latest -Recurse -Force }
New-Item -ItemType Directory -Force -Path $latest | Out-Null
Move-Item -Path "$tmp\cmdline-tools\*" -Destination $latest -Force

$sdkmanager = "$latest\bin\sdkmanager.bat"
Write-Host "sdkmanager present: $(Test-Path $sdkmanager)"
