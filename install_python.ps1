$ErrorActionPreference = "Stop"

$pythonVersion = "3.10.11"
$installerUrl = "https://www.python.org/ftp/python/$pythonVersion/python-$pythonVersion-amd64.exe"
$installerPath = "python_installer.exe"
$installDir = "$PSScriptRoot\python-3.10"

Write-Host "Downloading Python $pythonVersion installer..."
Invoke-WebRequest -Uri $installerUrl -OutFile $installerPath

Write-Host "Installing Python to $installDir..."
# Arguments for silent install to local directory with pip
$args = "/quiet InstallAllUsers=0 TargetDir=`"$installDir`" PrependPath=0 Include_test=0 Include_pip=1 Include_tcltk=0 Include_doc=0"
Start-Process -FilePath $installerPath -ArgumentList $args -Wait

Write-Host "Cleaning up installer..."
Remove-Item $installerPath

Write-Host "Verifying installation..."
& "$installDir\python.exe" --version

Write-Host "Python $pythonVersion installed successfully!"
