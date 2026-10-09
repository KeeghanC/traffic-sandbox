$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$classes = Join-Path $projectRoot 'build/classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Filter '*.java' -Recurse | ForEach-Object FullName)
& javac --release 11 -encoding UTF-8 -d $classes @sourceFiles
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
& jar ('-J-Djava.io.tmpdir=' + (Join-Path $projectRoot 'build')) --create --file (Join-Path $projectRoot 'build/traffic-sandbox.jar') --main-class traffic.App -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed' }
Write-Output 'Built build/traffic-sandbox.jar'
