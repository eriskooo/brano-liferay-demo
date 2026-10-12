# Build legacy app and SSO mock, start the Docker stack and hot-deploy the WARs (PowerShell 5.1).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

mvn -q -B -f "$root\legacy\pom.xml" verify
if ($LASTEXITCODE -ne 0) { throw 'Build of legacy failed' }
mvn -q -B -f "$root\sso-mock\pom.xml" verify
if ($LASTEXITCODE -ne 0) { throw 'Build of sso-mock failed' }
Copy-Item "$root\legacy\sso-portal-api\target\sso-portal-api.jar" "$PSScriptRoot\liferay\sso-portal-api.jar" -Force
New-Item -ItemType Directory -Force "$PSScriptRoot\deploy" | Out-Null

Push-Location $PSScriptRoot
try {
    docker compose up -d --build
    if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed' }
} finally {
    Pop-Location
}

# Theme first: the portlets list it in required-deployment-contexts.
Copy-Item "$root\legacy\customercare-theme\target\customercare-theme.war" "$PSScriptRoot\deploy\" -Force
Copy-Item "$root\legacy\customercare-portlets\target\customercare-portlet.war" "$PSScriptRoot\deploy\" -Force

# RedisInsight 3.x ignores connection env vars - register the shared-session database through its API.
$api = 'http://localhost:5540/api/databases'
$existing = $null
for ($i = 0; $i -lt 30; $i++) {
    try { $existing = @(Invoke-RestMethod $api); break } catch { Start-Sleep -Seconds 2 }
}
if (-not ($existing | Where-Object { $_.name -eq 'crm-shared-session' })) {
    $body = '{"name":"crm-shared-session","host":"redis","port":6379}'
    Invoke-RestMethod -Method Post -Uri $api -ContentType 'application/json' -Body $body | Out-Null
}
Write-Output 'Done. Liferay needs 1-3 min: docker compose -f docker\docker-compose.yml logs -f liferay'
