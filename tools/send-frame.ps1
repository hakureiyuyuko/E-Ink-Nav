param(
    [Parameter(Mandatory = $true)][string]$Mac,
    [Parameter(Mandatory = $true)][string]$Image,
    [int]$Rotate = 90,
    [Parameter(Mandatory = $true)][string]$BtDll
)

# 通过蓝牙 RFCOMM(SPP) 直连墨水屏，送一帧 JPEG（可在前面附带旋转指令）。
# -BtDll 指向 32feet.NET 的 InTheHand.Net.Bluetooth.dll：
#   https://www.nuget.org/packages/InTheHand.Net.Bluetooth
# 例：powershell -File tools\send-frame.ps1 -Mac AA:BB:CC:DD:EE:FF `
#        -Image .\test.png -Rotate 90 -BtDll C:\libs\InTheHand.Net.Bluetooth.dll

Add-Type -Path $BtDll
Add-Type -AssemblyName System.Drawing

if (-not (Test-Path $Image)) { Write-Host "找不到图片: $Image"; exit 1 }

$src = [System.Drawing.Image]::FromFile((Resolve-Path $Image).Path)
$ms = New-Object System.IO.MemoryStream
$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq "image/jpeg" }
$ps = New-Object System.Drawing.Imaging.EncoderParameters(1)
$ps.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, [long]70)
$src.Save($ms, $codec, $ps)
$jpeg = $ms.ToArray()
Write-Host "图片 $($src.Width)x$($src.Height) → JPEG $($jpeg.Length) 字节"
$src.Dispose()

$addr = [InTheHand.Net.BluetoothAddress]::Parse(($Mac -replace ':', ''))
$svc = [Guid]"00001101-0000-1000-8000-00805F9B34FB"
$ep = New-Object InTheHand.Net.BluetoothEndPoint($addr, $svc)
$cli = New-Object InTheHand.Net.Sockets.BluetoothClient

try { $cli.Connect($ep) } catch {
    Write-Host "连接失败: $($_.Exception.InnerException.Message)"
    exit 1
}

$st = $cli.GetStream()
if ($Rotate -ge 0) {
    $json = '{"cmd":"rotate","deg":' + $Rotate + '}' + "`n"
    $cmd = [System.Text.Encoding]::UTF8.GetBytes($json)
    $st.Write($cmd, 0, $cmd.Length)
    $st.Flush()
    Write-Host "已发送旋转指令 deg=$Rotate"
    Start-Sleep -Milliseconds 300
}

$len = $jpeg.Length
$frame = New-Object byte[] ($len + 5)
$frame[0] = 1
$frame[1] = [byte](($len -shr 24) -band 0xFF)
$frame[2] = [byte](($len -shr 16) -band 0xFF)
$frame[3] = [byte](($len -shr 8) -band 0xFF)
$frame[4] = [byte]($len -band 0xFF)
[Array]::Copy($jpeg, 0, $frame, 5, $len)
$st.Write($frame, 0, $frame.Length)
$st.Flush()
Write-Host "已发送图片帧"
Start-Sleep -Milliseconds 1500
$st.Close()
$cli.Close()
