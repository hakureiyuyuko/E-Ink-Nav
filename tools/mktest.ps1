param(
    [string]$OutDir = "."
)

# 生成两张带方向标记的测试图（竖版 480x800 / 横版 800x480），
# 用来验证墨水屏端的铺满、白边裁剪和旋转是否正确。

Add-Type -AssemblyName System.Drawing

function New-TestImage([int]$w, [int]$h, [string]$path) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::White)
    $b = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::Black)

    $t = 12
    $g.FillRectangle($b, 0, 0, $w, $t)                 # 上
    $g.FillRectangle($b, 0, ($h - $t), $w, $t)         # 下
    $g.FillRectangle($b, 0, 0, $t, $h)                 # 左
    $g.FillRectangle($b, ($w - $t), 0, $t, $h)         # 右
    $g.FillRectangle($b, $t, $t, [int]($w / 4), [int]($h / 4))              # 左上角标记
    $g.FillRectangle($b, [int]($w / 2), [int]($h * 0.8), [int]($w / 3), [int]($h * 0.1))  # 底部横条

    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "生成 $path (${w}x${h})"
}

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Force -Path $OutDir | Out-Null }
New-TestImage 480 800 (Join-Path $OutDir "test-portrait.png")
New-TestImage 800 480 (Join-Path $OutDir "test-landscape.png")
