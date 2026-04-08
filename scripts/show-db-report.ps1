param(
    [string]$EnvPath = "c:\Users\HP\Downloads\prescripto-full-stack\prescripto-full-stack\backend\.env"
)

$ErrorActionPreference = "Stop"

if (!(Test-Path $EnvPath)) {
    throw "Env file not found: $EnvPath"
}

$vars = @{}
Get-Content $EnvPath | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -match '^\s*$') {
        return
    }

    $parts = $_ -split '=', 2
    if ($parts.Count -ne 2) {
        return
    }

    $key = $parts[0].Trim()
    $value = $parts[1].Trim()

    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    $vars[$key] = $value
}

$dbHost = $vars['MYSQL_HOST']
$dbPort = $vars['MYSQL_PORT']
$dbName = $vars['MYSQL_DATABASE']
$dbUser = $vars['MYSQL_USERNAME']
$dbPass = $vars['MYSQL_PASSWORD']

if ([string]::IsNullOrWhiteSpace($dbHost) -or [string]::IsNullOrWhiteSpace($dbPort) -or [string]::IsNullOrWhiteSpace($dbName) -or [string]::IsNullOrWhiteSpace($dbUser)) {
    throw "Missing one or more MYSQL_* values in .env"
}

$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if (!(Test-Path $mysql)) {
    $mysql = "mysql.exe"
}

$env:MYSQL_PWD = $dbPass

$sql = @"
SELECT 'DATABASE' AS section, DATABASE() AS value;
SELECT 'TABLE_COUNT' AS section, COUNT(*) AS value FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type='BASE TABLE';
SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type='BASE TABLE' ORDER BY table_name;
SET @db = DATABASE();
SELECT GROUP_CONCAT(CONCAT('SELECT ''', table_name, ''' AS table_name, COUNT(*) AS row_count FROM `', table_name, '`') SEPARATOR ' UNION ALL ') INTO @q
FROM information_schema.tables
WHERE table_schema = @db AND table_type='BASE TABLE';
PREPARE s FROM @q; EXECUTE s; DEALLOCATE PREPARE s;
SELECT id, name, email FROM users ORDER BY id LIMIT 5;
SELECT id, name, email, speciality, available FROM doctors ORDER BY id LIMIT 5;
SELECT id, user_id, doc_id, slot_date, slot_time, cancelled, payment, is_completed FROM appointments ORDER BY date DESC LIMIT 5;
SELECT id, appointment_id, user_id, doc_id, diagnosis, issued_at FROM prescriptions ORDER BY issued_at DESC LIMIT 5;
SELECT id, name, email, is_approved, is_active FROM pharmacies ORDER BY created_at DESC LIMIT 5;
SELECT id, order_number, user_id, pharmacy_id, status, payment_status, created_at FROM pharmacy_orders ORDER BY created_at DESC LIMIT 5;
"@

try {
    & $mysql -h $dbHost -P $dbPort -u $dbUser -D $dbName -e $sql
}
finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
