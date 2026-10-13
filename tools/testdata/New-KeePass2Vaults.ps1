#requires -Version 5.1
<#
Genera las bóvedas ficticias de testdata/ con la biblioteca oficial de KeePass 2.x
(KeePassLib, dentro de KeePass.exe). Solo datos inventados; las contraseñas son públicas.

Uso:  powershell -File tools\testdata\New-KeePass2Vaults.ps1 [-OutDir testdata] [-KeePassExe <ruta>]
#>
param(
    [string]$OutDir = (Join-Path $PSScriptRoot '..\..\testdata'),
    [string]$KeePassExe = 'C:\Program Files (x86)\KeePass2x\KeePass.exe'
)
$ErrorActionPreference = 'Stop'
[void][Reflection.Assembly]::LoadFrom($KeePassExe)
Add-Type -AssemblyName System.Drawing
[void](New-Item -ItemType Directory -Force $OutDir)
$OutDir = (Resolve-Path $OutDir).Path

$Password = 'test-password-1'
$UnicodeText = "$([char]0x00D1)and$([char]0x00FA) caf$([char]0x00E9) $([char]0x65E5)$([char]0x672C)$([char]0x8A9E) $([char]::ConvertFromUtf32(0x1F600))"

function New-Binary([byte[]]$bytes) { New-Object KeePassLib.Security.ProtectedBinary($false, $bytes) }

function New-CustomIconPng {
    $bmp = New-Object System.Drawing.Bitmap 16, 16
    for ($x = 0; $x -lt 16; $x++) { for ($y = 0; $y -lt 16; $y++) {
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $x * 15, $y * 15, 128)) } }
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    ,$ms.ToArray()
}

function Add-Entry($group, $title, $user, $pass, $url, $notes) {
    $e = New-Object KeePassLib.PwEntry($true, $true)
    $e.Strings.Set('Title', (New-Object KeePassLib.Security.ProtectedString($false, $title)))
    $e.Strings.Set('UserName', (New-Object KeePassLib.Security.ProtectedString($false, $user)))
    $e.Strings.Set('Password', (New-Object KeePassLib.Security.ProtectedString($true, $pass)))
    $e.Strings.Set('URL', (New-Object KeePassLib.Security.ProtectedString($false, $url)))
    $e.Strings.Set('Notes', (New-Object KeePassLib.Security.ProtectedString($false, $notes)))
    $group.AddEntry($e, $true)
    $e
}

function New-RichDatabase([bool]$tags, [bool]$publicData = $true) {
    $db = New-Object KeePassLib.PwDatabase
    $key = New-Object KeePassLib.Keys.CompositeKey
    $key.AddUserKey((New-Object KeePassLib.Keys.KcpPassword($Password)))
    $db.New((New-Object KeePassLib.Serialization.IOConnectionInfo), $key)
    $db.Name = 'KeyNexa test vault'
    $db.RootGroup.Name = 'Root'

    $internet = New-Object KeePassLib.PwGroup($true, $true, 'Internet', [KeePassLib.PwIcon]::World)
    $shopping = New-Object KeePassLib.PwGroup($true, $true, 'Shopping', [KeePassLib.PwIcon]::Folder)
    $work = New-Object KeePassLib.PwGroup($true, $true, 'Work', [KeePassLib.PwIcon]::Homebanking)
    $db.RootGroup.AddGroup($internet, $true)
    $internet.AddGroup($shopping, $true)
    $db.RootGroup.AddGroup($work, $true)

    $png = New-CustomIconPng
    $iconId = New-Object KeePassLib.PwUuid($true)
    $db.CustomIcons.Add((New-Object KeePassLib.PwCustomIcon($iconId, $png)))

    $e1 = Add-Entry $internet 'Example mail' 'alice@example.org' 'correct-horse-battery-staple' 'https://mail.example.org' 'Ficticious mail account.'
    $e1.IconId = [KeePassLib.PwIcon]::EMail
    $e2 = Add-Entry $shopping 'Example shop' 'alice' 's3cret-Shop!' 'https://shop.example.org' 'Order notes line 1'
    $e2.CustomIconUuid = $iconId
    $e3 = Add-Entry $work 'Intranet' 'a.test' 'work-pass-123' 'https://intranet.example.org' ''
    $e3.Expires = $true
    $e3.ExpiryTime = [datetime]::SpecifyKind([datetime]'2020-01-01', 'Utc')
    $e4 = Add-Entry $db.RootGroup "T$UnicodeText" "u$UnicodeText" "p$UnicodeText" 'https://unicode.example.org' "Notes $UnicodeText"
    $e5 = Add-Entry $db.RootGroup 'Server' 'root' 'srv-pass-456' 'ssh://server.example.org' "multi`nline`nnotes"
    $e5.Strings.Set('Environment', (New-Object KeePassLib.Security.ProtectedString($false, 'production')))
    $e5.Strings.Set('ApiKey', (New-Object KeePassLib.Security.ProtectedString($true, 'FAKE-KEY-0123456789')))
    $e5.Binaries.Set('readme.txt', (New-Binary ([Text.Encoding]::UTF8.GetBytes("Ficticious attachment.`n"))))
    $random = New-Object byte[] (64KB); (New-Object Random 42).NextBytes($random)
    $e5.Binaries.Set('random.bin', (New-Binary $random))
    if ($tags) { [void]$e1.AddTag('personal'); [void]$e2.AddTag('shopping'); [void]$internet.Tags.Add('web') }

    $hist = Add-Entry $internet 'Changing password' 'bob' 'v1-password' 'https://history.example.org' 'v1'
    foreach ($v in 2..4) {
        $hist.CreateBackup($db)
        $hist.Strings.Set('Password', (New-Object KeePassLib.Security.ProtectedString($true, "v$v-password")))
        $hist.Strings.Set('Notes', (New-Object KeePassLib.Security.ProtectedString($false, "v$v")))
        $hist.Touch($true)
    }

    $bin = New-Object KeePassLib.PwGroup($true, $true, 'Recycle Bin', [KeePassLib.PwIcon]::TrashBin)
    $db.RootGroup.AddGroup($bin, $true)
    $db.RecycleBinUuid = $bin.Uuid
    $db.RecycleBinEnabled = $true
    $del = Add-Entry $bin 'Deleted entry' 'old' 'old-pass' 'https://old.example.org' 'in the recycle bin'

    if ($publicData) { [void]$db.PublicCustomData.SetString('keynexa-test', 'value') }
    $db
}

function Save-Database($db, [string]$cipher, [string]$kdf, [uint64]$memMiB, [uint64]$iter, [string]$file, $keys, [uint32]$forceVersion = 0) {
    if ($keys) { $db.MasterKey = $keys }
    $db.DataCipherUuid = if ($cipher -eq 'chacha20') { (New-Object KeePassLib.Cryptography.Cipher.ChaCha20Engine).CipherUuid } else { [KeePassLib.Cryptography.Cipher.StandardAesEngine]::AesUuid }
    $engine = [KeePassLib.Cryptography.KeyDerivation.KdfPool]::Engines | Where-Object { $_.Name -eq $kdf }
    $p = $engine.GetDefaultParameters()
    if ($kdf -eq 'AES-KDF') {
        $p.SetUInt64([KeePassLib.Cryptography.KeyDerivation.AesKdf]::ParamRounds, $iter)
    } else {
        $p.SetUInt64([KeePassLib.Cryptography.KeyDerivation.Argon2Kdf]::ParamMemory, $memMiB * 1MB)
        $p.SetUInt64([KeePassLib.Cryptography.KeyDerivation.Argon2Kdf]::ParamIterations, $iter)
        $p.SetUInt32([KeePassLib.Cryptography.KeyDerivation.Argon2Kdf]::ParamParallelism, 2)
    }
    $db.KdfParameters = $p
    $path = Join-Path $OutDir $file
    $fs = [IO.File]::Create($path)
    try {
        $kdbx = New-Object KeePassLib.Serialization.KdbxFile($db)
        if ($forceVersion -ne 0) {
            $flags = [Reflection.BindingFlags]'Instance,Public,NonPublic'
            $kdbx.GetType().GetProperty('ForceVersion', $flags).SetValue($kdbx, [uint32]$forceVersion, $null)
        }
        $kdbx.Save($fs, $null, [KeePassLib.Serialization.KdbxFormat]::Default, $null)
    } finally { $fs.Dispose() }
    "wrote $file"
}

$rich = @(
    @('rich-aes-argon2d.kdbx', 'aes', 'Argon2d', 64, 10, $false),
    @('rich-aes-argon2id.kdbx', 'aes', 'Argon2id', 64, 10, $false),
    @('rich-chacha20-argon2d.kdbx', 'chacha20', 'Argon2d', 64, 10, $false),
    @('rich-chacha20-argon2id.kdbx', 'chacha20', 'Argon2id', 64, 10, $false),
    @('rich-chacha20-aeskdf.kdbx', 'chacha20', 'AES-KDF', 0, 600000, $false),
    @('rich-aes-aeskdf.kdbx', 'aes', 'AES-KDF', 0, 600000, $false),
    @('rich-aes-argon2d-tags.kdbx', 'aes', 'Argon2d', 64, 10, $true)
)
foreach ($r in $rich) {
    $db = New-RichDatabase $r[5]
    Save-Database $db $r[1] $r[2] $r[3] $r[4] $r[0] $null
}

# KDBX 3.1 forzado (solo AES + AES-KDF, sin datos personalizados públicos ni etiquetas).
Save-Database (New-RichDatabase $false $false) 'aes' 'AES-KDF' 0 600000 'rich-kdbx31-aes-aeskdf.kdbx' $null 0x00030001

# Clave de archivo (32 bytes aleatorios fijos) con y sin contraseña.
$keyFile = Join-Path $OutDir 'keyfile-only.key'
$kb = New-Object byte[] 32; (New-Object Random 7).NextBytes($kb)
[IO.File]::WriteAllBytes($keyFile, $kb)

$db = New-RichDatabase $false
$k = New-Object KeePassLib.Keys.CompositeKey
$k.AddUserKey((New-Object KeePassLib.Keys.KcpKeyFile($keyFile)))
Save-Database $db 'aes' 'AES-KDF' 0 600000 'keyfile-only.kdbx' $k

$db = New-RichDatabase $false
$k = New-Object KeePassLib.Keys.CompositeKey
$k.AddUserKey((New-Object KeePassLib.Keys.KcpPassword($Password)))
$k.AddUserKey((New-Object KeePassLib.Keys.KcpKeyFile($keyFile)))
Save-Database $db 'aes' 'Argon2id' 64 10 'keyfile-password-kp2.kdbx' $k

$db = New-RichDatabase $false
$k = New-Object KeePassLib.Keys.CompositeKey
$k.AddUserKey((New-Object KeePassLib.Keys.KcpPassword("p$([char]0x00E4)ssw$([char]0x00F6)rd-$([char]0x65E5)$([char]0x672C)$([char]0x8A9E)-$([char]::ConvertFromUtf32(0x1F600))")))
Save-Database $db 'aes' 'Argon2id' 64 10 'unicode-password.kdbx' $k

# Argon2id con 1 GiB para medir memoria y tiempo en Android.
$db = New-RichDatabase $false
Save-Database $db 'aes' 'Argon2id' 1024 2 'argon2-1gib.kdbx' $null
