$ErrorActionPreference = "Stop"
$url = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_x64_windows_hotspot_17.0.13_11.zip"
$output = "jdk-17.zip"
$dest = "jdk-17"

Write-Host "Downloading JDK 17..."
Invoke-WebRequest -Uri $url -OutFile $output

Write-Host "Extracting JDK 17..."
Expand-Archive -Path $output -DestinationPath $dest -Force

Write-Host "Done."
