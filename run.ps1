param([switch]$NoBuild)
$ErrorActionPreference = 'Stop'
if (-not $NoBuild) { & (Join-Path $PSScriptRoot 'build.ps1') }
& java -jar (Join-Path $PSScriptRoot 'build/traffic-sandbox.jar')
