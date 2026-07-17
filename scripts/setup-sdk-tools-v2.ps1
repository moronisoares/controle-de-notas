# Downloads and sets up Android SDK command-line tools.
$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$JavaHome = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"

$sdk = "C:\Android\Sdk"
$zip = "$env:TEMP\cmdline-tools.zip"
$url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"

if (Test-Path $zip) { Remove-Item $zip -Force }
Write-Host "Downloading command-line tools (about 130 MB)..."
$wc = New-Object System.Net.WebClient
$wc.DownloadFile($url, $zip)
Write-Host "Downloaded size (MB): $([math]::Round((Get-Item $zip).Length / 1MB, 1))"

$tmp = "$env:TEMP\cmdline-extract"
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
Write-Host "Extracting..."
Expand-Archive -Path $zip -DestinationPath $tmp -Force

$latest = "$sdk\cmdline-tools\latest"
if (Test-Path $latest) { Remove-Item $latest -Recurse -Force }
New-Item -ItemType Directory -Force -Path $latest | Out-Null
Move-Item -Path "$tmp\cmdline-tools\*" -Destination $latest -Force

$sdkmanager = "$latest\bin\sdkmanager.bat"
Write-Host "sdkmanager present: $(Test-Path $sdkmanager)"
