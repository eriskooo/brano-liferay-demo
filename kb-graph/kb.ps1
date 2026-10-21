# Znalostná báza v grafe: up | extract | check | load | guide | down (bash dvojča: kb.sh).
param([Parameter(Position = 0)][string]$Command)
$ErrorActionPreference = 'Stop'

Set-Location (Join-Path $PSScriptRoot '..')
$composeFile = 'kb-graph/docker-compose.yml'
$jar = 'kb-graph/extractor/target/kb-graph-extractor.jar'

function Show-Usage {
    Write-Host 'Použitie: .\kb-graph\kb.ps1 up|extract|check|load|guide|down'
    exit 64
}

# JAR sa prebuilduje, len keď chýba alebo sú zdrojáky novšie – opakovaný load je potom rýchly.
function Build-Jar {
    $stale = -not (Test-Path $jar)
    if (-not $stale) {
        $jarTime = (Get-Item $jar).LastWriteTime
        $newer = Get-ChildItem -Recurse -File 'kb-graph/extractor/src', 'kb-graph/extractor/pom.xml' |
            Where-Object { $_.LastWriteTime -gt $jarTime } | Select-Object -First 1
        $stale = $null -ne $newer
    }
    if ($stale) {
        mvn -q -B -f kb-graph/extractor/pom.xml package -DskipTests
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
}

function Invoke-Extractor([string]$Name) {
    Build-Jar
    java '-Dstdout.encoding=UTF-8' -jar $jar $Name --repo .
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

switch ($Command) {
    'up' {
        docker compose -f $composeFile up -d --wait
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Host 'Neo4j Browser: http://localhost:7474 (neo4j / kbdemo123), guide: :play http://localhost:7475/kb.html'
    }
    { $_ -in 'extract', 'check', 'guide' } { Invoke-Extractor $Command }
    'load' { Invoke-Extractor 'load'; Invoke-Extractor 'guide' }
    'down' { docker compose -f $composeFile down }
    default { Show-Usage }
}
