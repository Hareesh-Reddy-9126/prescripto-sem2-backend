param(
    [string]$EnvPath = "",
    [string]$BackupFile = "",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

function Read-EnvMap {
    param([string]$Path)

    if (!(Test-Path $Path)) {
        throw "Env file not found: $Path"
    }

    $map = @{}
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -match '^\s*#' -or $line -match '^\s*$') {
            return
        }

        $parts = $line -split '=', 2
        if ($parts.Count -ne 2) {
            return
        }

        $key = $parts[0].Trim()
        $value = $parts[1].Trim()

        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        $map[$key] = $value
    }

    return $map
}

function Resolve-MySqlClient {
    $candidates = @(
        "mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
    )

    foreach ($candidate in $candidates) {
        if ($candidate -eq "mysql.exe") {
            $cmd = Get-Command $candidate -ErrorAction SilentlyContinue
            if ($cmd) {
                return $cmd.Source
            }
        }
        elseif (Test-Path $candidate) {
            return $candidate
        }
    }

    throw "mysql client not found. Install MySQL client tools or add mysql.exe to PATH."
}

function Parse-DbFromJdbcUrl {
    param([string]$JdbcUrl)

    $result = @{}
    if ([string]::IsNullOrWhiteSpace($JdbcUrl)) {
        return $result
    }

    if ($JdbcUrl -match '^jdbc:mysql://([^:/?]+)(?::(\d+))?/([^?]+)') {
        $result["host"] = $matches[1]
        $result["port"] = if ($matches[2]) { $matches[2] } else { "3306" }
        $result["database"] = $matches[3]
    }

    return $result
}

$scriptDir = $PSScriptRoot
$backendDir = Split-Path -Parent $scriptDir

if ([string]::IsNullOrWhiteSpace($EnvPath)) {
    $EnvPath = Join-Path $backendDir ".env"
}

$envMap = Read-EnvMap -Path $EnvPath
$jdbcParts = Parse-DbFromJdbcUrl -JdbcUrl $envMap["DB_URL"]

$dbHost = if ($jdbcParts.ContainsKey("host")) { $jdbcParts["host"] } else { $envMap["MYSQL_HOST"] }
$dbPort = if ($jdbcParts.ContainsKey("port")) { $jdbcParts["port"] } else { $envMap["MYSQL_PORT"] }
$dbName = if ($jdbcParts.ContainsKey("database")) { $jdbcParts["database"] } else { $envMap["MYSQL_DATABASE"] }
$dbUser = if ($envMap.ContainsKey("DB_USER")) { $envMap["DB_USER"] } else { $envMap["MYSQL_USERNAME"] }
$dbPass = if ($envMap.ContainsKey("DB_PASSWORD")) { $envMap["DB_PASSWORD"] } else { $envMap["MYSQL_PASSWORD"] }

if ([string]::IsNullOrWhiteSpace($dbHost) -or [string]::IsNullOrWhiteSpace($dbPort) -or [string]::IsNullOrWhiteSpace($dbName) -or [string]::IsNullOrWhiteSpace($dbUser)) {
    throw "Missing DB settings. Expected DB_URL/DB_USER/DB_PASSWORD (or MYSQL_HOST/MYSQL_PORT/MYSQL_DATABASE/MYSQL_USERNAME) in .env"
}

if ([string]::IsNullOrWhiteSpace($BackupFile)) {
    $defaultBackupRoot = Join-Path $backendDir "migration-backup"
    $latest = Get-ChildItem -Path $defaultBackupRoot -Filter "mysql_*.sql" -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $latest) {
        throw "No mysql_*.sql backup found in $defaultBackupRoot. Pass -BackupFile explicitly."
    }
    $BackupFile = $latest.FullName
}

if (!(Test-Path $BackupFile)) {
    throw "Backup file not found: $BackupFile"
}

if (-not $Force) {
    Write-Warning "This will overwrite data in database '$dbName' on $dbHost:$dbPort"
    $confirm = Read-Host "Type RESTORE to continue"
    if ($confirm -ne "RESTORE") {
        throw "Restore cancelled by user"
    }
}

$mysql = Resolve-MySqlClient
$sourcePath = (Resolve-Path $BackupFile).Path -replace "\\", "/"

$env:MYSQL_PWD = $dbPass
try {
    & $mysql -h $dbHost -P $dbPort -u $dbUser $dbName --execute="source $sourcePath"
    if ($LASTEXITCODE -ne 0) {
        throw "mysql restore failed with exit code $LASTEXITCODE"
    }
}
finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

Write-Output "Restore completed from: $BackupFile"
