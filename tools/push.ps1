param(
    [Parameter(Mandatory = $true)][string]$Ip,
    [int]$Port = 8848,
    [Parameter(Mandatory = $true)][string]$File,
    [int]$DelayMs = 1000
)

# 通过 Wi-Fi(TCP) 逐行推送 JSON 帧，用于调试墨水屏端。
# 例：powershell -File tools\push.ps1 -Ip 192.168.1.50 -File frames.jsonl

if (-not (Test-Path $File)) { Write-Host "找不到文件: $File"; exit 1 }
$lines = @(Get-Content -Path $File -Encoding UTF8 | Where-Object { $_.Trim().Length -gt 0 })
if ($lines.Count -eq 0) { Write-Host "文件为空"; exit 1 }

$client = New-Object System.Net.Sockets.TcpClient
try {
    $client.Connect($Ip, $Port)
    $stream = $client.GetStream()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($lines[$i].Trim() + "`n")
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush()
        Write-Host "SENT $i : $($lines[$i].Trim())"
        if ($i -lt $lines.Count - 1) { Start-Sleep -Milliseconds $DelayMs }
    }
} catch {
    Write-Host "失败: $($_.Exception.Message)"
} finally {
    $client.Close()
}
