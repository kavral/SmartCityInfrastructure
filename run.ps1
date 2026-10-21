param(
    [ValidateSet('demo', 'test', 'benchmark', 'plots', 'verify', 'file', 'compile')]
    [string]$Task = 'demo',
    [string]$GraphFile = '',
    [int]$Source = 0
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Path build -Force | Out-Null
    $sources = Get-ChildItem -Path src, tests -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }
    & javac -encoding UTF-8 -Xlint:all -d build $sources
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
    switch ($Task) {
        'compile' { return }
        'demo' { & java -cp build app.Main demo }
        'file' { & java -cp build app.Main file $GraphFile $Source }
        'test' { & java -cp build tests.AlgorithmTests }
        'benchmark' { & java -Xms256m -Xmx1g -cp build benchmark.Benchmark }
        'plots' { & java -cp build benchmark.PlotResults }
        'verify' { & java -cp build benchmark.VerifyResults }
    }
    if ($LASTEXITCODE -ne 0) { throw "Task failed: $Task" }
} finally { Pop-Location }
