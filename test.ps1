$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'build.ps1')
$testClasses = Join-Path $PSScriptRoot 'build/test-classes'
$classes = Join-Path $PSScriptRoot 'build/classes'
New-Item -ItemType Directory -Path $testClasses -Force | Out-Null
$testFiles = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'test') -Filter '*.java' -Recurse | ForEach-Object FullName)
& javac --release 11 -encoding UTF-8 -cp $classes -d $testClasses @testFiles
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
$classPath = $classes + [IO.Path]::PathSeparator + $testClasses
Push-Location -LiteralPath $PSScriptRoot
try {
    & java '-Djava.awt.headless=true' -ea -cp $classPath traffic.ArchitectureTest
    if ($LASTEXITCODE -ne 0) { throw 'Architecture tests failed' }
    & java '-Djava.awt.headless=true' -ea -cp $classPath traffic.signals.ActuatedControllerTest
    if ($LASTEXITCODE -ne 0) { throw 'Signal controller tests failed' }
    & java '-Djava.awt.headless=true' -ea -cp $classPath traffic.engine.SimulationTest
    if ($LASTEXITCODE -ne 0) { throw 'Simulation tests failed' }
    & java '-Djava.awt.headless=true' -ea -cp $classPath traffic.desktop.InterfaceTest
    if ($LASTEXITCODE -ne 0) { throw 'Interface tests failed' }
} finally {
    Pop-Location
}
