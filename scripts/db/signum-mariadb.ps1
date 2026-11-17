<#
.SYNOPSIS
Sets up MariaDB for Signum Node on Windows: sync tuning, database and user.

.DESCRIPTION
Writes the tuning settings from DB_SETUP_MARIADB.md into the my.ini of the MariaDB
Windows service (a backup is kept next to it), restarts the service, then creates
the database and the node's user. Asks for the MariaDB root password.
Run it in a PowerShell started as administrator. Safe to run again.

.EXAMPLE
.\scripts\db\signum-mariadb.ps1

.EXAMPLE
.\scripts\db\signum-mariadb.ps1 -Database signum_testnet -SkipTuning
#>
[CmdletBinding()]
param(
    # Password for the node's database user; asked for if not given.
    # On the command line it is visible in the process list and the shell history.
    [string]$Password,
    # Database name, e.g. signum_testnet
    [ValidatePattern('^[A-Za-z0-9_]+$')][string]$Database = 'signum',
    # Database user
    [ValidatePattern('^[A-Za-z0-9_]+$')][string]$User = 'signumnode',
    # Host the node connects from; '%' for any
    [ValidatePattern('^[A-Za-z0-9_.%-]+$')][string]$UserHost = 'localhost',
    # innodb_buffer_pool_size, up to about half the RAM
    [ValidatePattern('^[0-9]+[KMG]?$')][string]$BufferPool = '1G',
    # Name of the MariaDB Windows service (default: detected)
    [string]$ServiceName,
    # Only create database and user
    [switch]$SkipTuning,
    # Only write the tuning settings
    [switch]$SkipSetup,
    # Print what would be done, change nothing
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$BeginMarker = '# BEGIN Signum Node'
$EndMarker = '# END Signum Node'

function Get-TuningBlock([string]$BufferPool) {
    @(
        $BeginMarker
        '# Written by scripts\db\signum-mariadb.ps1, see DB_SETUP_MARIADB.md.'
        '# A power loss can lose about the last second of commits; the node syncs those blocks again.'
        '[mariadb]'
        'innodb_flush_log_at_trx_commit = 2'
        'innodb_log_file_size = 1G'
        "innodb_buffer_pool_size = $BufferPool"
        $EndMarker
    ) -join "`r`n"
}

# Replaces an earlier Signum Node block, or appends one, keeping the rest of my.ini as it is
function ConvertTo-TunedIni([string]$IniText, [string]$Block) {
    $pattern = '(?s)\r?\n?' + [regex]::Escape($BeginMarker) + '.*?' + [regex]::Escape($EndMarker) + '\r?\n?'
    $rest = [regex]::Replace($IniText, $pattern, "`r`n").TrimEnd()
    if ($rest) { "$rest`r`n`r`n$Block`r`n" } else { "$Block`r`n" }
}

# The service command line looks like: "C:\...\bin\mysqld.exe" "--defaults-file=C:\...\data\my.ini" "MariaDB"
function Get-ServicePath([string]$PathName) {
    if ($PathName -notmatch '^\s*"?(?<exe>[^"]+?\.exe)"?') { throw "Cannot read the server path from: $PathName" }
    $exe = $Matches.exe
    if ($PathName -notmatch '"--defaults-file=(?<ini>[^"]+)"' -and $PathName -notmatch '--defaults-file=(?<ini>\S+)') {
        throw "The service does not use --defaults-file: $PathName"
    }
    [pscustomobject]@{ Bin = $exe.Substring(0, $exe.LastIndexOfAny([char[]]'\/')); Ini = $Matches.ini }
}

# Get-SetupSql turns off backslash escapes, so only ' needs doubling
function ConvertTo-SqlString([string]$Value) {
    "'" + $Value.Replace("'", "''") + "'"
}

function Get-SetupSql([string]$Database, [string]$User, [string]$UserHost, [string]$Password) {
    $account = "'$User'@'$UserHost'"
    $pw = ConvertTo-SqlString $Password
    @(
        # Backslashes in the password stay literal, whatever sql_mode the server has
        "SET SESSION sql_mode = CONCAT_WS(',', NULLIF(@@sql_mode, ''), 'NO_BACKSLASH_ESCAPES');"
        "CREATE DATABASE IF NOT EXISTS ``$Database``;"
        "CREATE USER IF NOT EXISTS $account IDENTIFIED BY $pw;"
        "ALTER USER $account IDENTIFIED BY $pw;"
        "GRANT ALL PRIVILEGES ON ``$Database``.* TO $account;"
        'FLUSH PRIVILEGES;'
    ) -join "`n"
}

function Find-MariaDbService([string]$ServiceName) {
    $services = @(Get-CimInstance Win32_Service | Where-Object { $_.PathName -match '\\(mysqld|mariadbd)\.exe' })
    if ($ServiceName) { $services = @($services | Where-Object Name -eq $ServiceName) }
    if ($services.Count -eq 0) { throw 'No MariaDB service found. Is MariaDB installed as a Windows service?' }
    if ($services.Count -gt 1) { throw "Several MariaDB services found ($($services.Name -join ', ')). Pick one with -ServiceName." }
    $services[0]
}

function Invoke-MariaDb([string]$Client, [string]$Sql, [string[]]$Options = @()) {
    # The SQL goes through stdin, so the password is not on the process command line.
    # -p without a value asks for the root password on the console.
    $OutputEncoding = New-Object Text.UTF8Encoding $false
    $Sql | & $Client -u root -p --default-character-set=utf8mb4 @Options
    if ($LASTEXITCODE -ne 0) { throw "mariadb failed with exit code $LASTEXITCODE" }
}

function Write-Tuning($Paths, [string]$BufferPool) {
    Write-Host "==> Writing the tuning settings to $($Paths.Ini)"
    $old = [IO.File]::ReadAllText($Paths.Ini)
    $new = ConvertTo-TunedIni $old (Get-TuningBlock $BufferPool)
    if ($DryRun) { Write-Host (Get-TuningBlock $BufferPool); return }
    $backup = "$($Paths.Ini).bak-$(Get-Date -Format yyyyMMdd-HHmmss)"
    Copy-Item $Paths.Ini $backup
    Write-Host "    Backup: $backup"
    # UTF-8 without BOM, which MariaDB reads
    [IO.File]::WriteAllText($Paths.Ini, $new, (New-Object Text.UTF8Encoding $false))
}

function Main {
    if (-not $SkipSetup -and -not $Password) {
        $secure = Read-Host -AsSecureString "Password for the database user $User"
        $Password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
        if (-not $Password) { throw 'The password must not be empty.' }
    }
    $admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole('Administrators')
    if (-not $admin -and -not $DryRun) { throw 'Run this script in a PowerShell started as administrator.' }

    $service = Find-MariaDbService $ServiceName
    $paths = Get-ServicePath $service.PathName
    $client = @('mariadb.exe', 'mysql.exe') | ForEach-Object { Join-Path $paths.Bin $_ } | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $client) { throw "No mariadb.exe found in $($paths.Bin)" }
    Write-Host "==> Service $($service.Name), config $($paths.Ini)"

    if (-not $SkipTuning) {
        Write-Tuning $paths $BufferPool
        Write-Host "==> Restarting $($service.Name)"
        if (-not $DryRun) { Restart-Service -Name $service.Name }
    }
    # One client call, so the root password is asked only once
    $sql = "SHOW VARIABLES WHERE Variable_name IN ('innodb_flush_log_at_trx_commit', 'innodb_log_file_size', 'innodb_buffer_pool_size');"
    if (-not $SkipSetup) {
        Write-Host "==> Creating database $Database and user '$User'@'$UserHost'"
        $sql = (Get-SetupSql $Database $User $UserHost $Password) + "`n" + $sql
    }
    Write-Host '==> Active settings'
    if (-not $DryRun) { Invoke-MariaDb $client $sql @('--table') }
    Write-Host ''
    Write-Host 'Done. Add to conf\node.properties:'
    Write-Host "DB.Url=jdbc:mariadb://localhost:3306/$Database"
    Write-Host "DB.Username=$User"
    Write-Host 'DB.Password=<the password you chose>'
}

# Dot-sourcing the script (". .\signum-mariadb.ps1") only loads the functions, for testing
if ($MyInvocation.InvocationName -ne '.') { Main }
