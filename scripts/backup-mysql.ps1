param(
    [string]$EnvPath = "",
    [string]$OutputDir = ""
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

function Resolve-MySqlDump {
    $candidates = @(
        "mysqldump.exe",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe"
    )

    foreach ($candidate in $candidates) {
        if ($candidate -eq "mysqldump.exe") {
            $cmd = Get-Command $candidate -ErrorAction SilentlyContinue
            if ($cmd) {
                return $cmd.Source
            }
        }
        elseif (Test-Path $candidate) {
            return $candidate
        }
    }

    throw "mysqldump not found. Install MySQL client tools or add mysqldump.exe to PATH."
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

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $backendDir "migration-backup"
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

if (!(Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFile = Join-Path $OutputDir "mysql_${stamp}.sql"
$mysqldump = Resolve-MySqlDump

$env:MYSQL_PWD = $dbPass
try {
    & $mysqldump -h $dbHost -P $dbPort -u $dbUser --single-transaction --routines --triggers --events --set-gtid-purged=OFF --no-tablespaces --default-character-set=utf8mb4 $dbName > $backupFile
    if ($LASTEXITCODE -ne 0) {
        throw "mysqldump failed with exit code $LASTEXITCODE"
    }
}
finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

$size = (Get-Item $backupFile).Length
Write-Output "Backup created: $backupFile"
Write-Output "Size bytes: $size"
