param(
    [Parameter(Mandatory = $true)][string]$Mac,
    [Parameter(Mandatory = $true)][string]$File,
    [Parameter(Mandatory = $true)][string]$BtDll,
    [int]$DelayMs = 1000
)

# 通过蓝牙 RFCOMM(SPP) 逐行推送 JSON 文本帧（GPS 数据、旋转指令、文字导航帧等）。
# -BtDll 指向 32feet.NET 的 InTheHand.Net.Bluetooth.dll：
#   https://www.nuget.org/packages/InTheHand.Net.Bluetooth
# 例：powershell -File tools\send-json.ps1 -Mac AA:BB:CC:DD:EE:FF `
#        -File frames.jsonl -BtDll C:\libs\InTheHand.Net.Bluetooth.dll

Add-Type -Path $BtDll

if (-not (Test-Path $File)) { Write-Host "找不到文件: $File"; exit 1 }
$lines = @(Get-Content -Path $File -Encoding UTF8 | Where-Object { $_.Trim().Length -gt 0 })
if ($lines.Count -eq 0) { Write-Host "文件为空"; exit 1 }

$addr = [InTheHand.Net.BluetoothAddress]::Parse(($Mac -replace ':', ''))
$svc = [Guid]"00001101-0000-1000-8000-00805F9B34FB"
$ep = New-Object InTheHand.Net.BluetoothEndPoint($addr, $svc)
$cli = New-Object InTheHand.Net.Sockets.BluetoothClient

try { $cli.Connect($ep) } catch {
    Write-Host "连接失败: $($_.Exception.InnerException.Message)"
    exit 1
}

$st = $cli.GetStream()
for ($i = 0; $i -lt $lines.Count; $i++) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($lines[$i].Trim() + "`n")
    $st.Write($bytes, 0, $bytes.Length)
    $st.Flush()
    Write-Host "SENT $i : $($lines[$i].Trim())"
    if ($i -lt $lines.Count - 1) { Start-Sleep -Milliseconds $DelayMs }
}
$st.Close()
$cli.Close()
