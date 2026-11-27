param([string]$OutRoot = ".")

# 按电纸书官方应用图标风格（白底 + 黑色细线稿 + 少量实心块）生成 launcher 图标
# 坐标系统一按官方 adaptive-icon 的 108x108 视口，最后缩放到各密度

Add-Type -AssemblyName System.Drawing

$dens = @(
    @{ dir = "mipmap-mdpi";    px = 48 },
    @{ dir = "mipmap-hdpi";    px = 72 },
    @{ dir = "mipmap-xhdpi";   px = 96 },
    @{ dir = "mipmap-xxhdpi";  px = 144 },
    @{ dir = "mipmap-xxxhdpi"; px = 192 }
)

function New-Canvas([int]$px) {
    $bmp = New-Object System.Drawing.Bitmap($px, $px)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::White)
    return @($bmp, $g)
}

function P([double]$v, [int]$px) { return [single]($v * $px / 108.0) }

# ---------------- 导航图标：路牌 + 右转箭头 + 立柱 ----------------
function Draw-Nav($g, [int]$px) {
    $white = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::Black), (P 2.6 $px)
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round

    # 牌面（圆角矩形，描边 + 白底）
    $board = New-Object System.Drawing.RectangleF((P 22 $px), (P 26 $px), (P 64 $px), (P 42 $px))
    $r = P 8 $px
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc($board.X, $board.Y, $r * 2, $r * 2, 180, 90)
    $path.AddArc($board.Right - $r * 2, $board.Y, $r * 2, $r * 2, 270, 90)
    $path.AddArc($board.Right - $r * 2, $board.Bottom - $r * 2, $r * 2, $r * 2, 0, 90)
    $path.AddArc($board.X, $board.Bottom - $r * 2, $r * 2, $r * 2, 90, 90)
    $path.CloseFigure()
    $g.FillPath($white, $path)
    $g.DrawPath($pen, $path)

    # 右转箭头：竖→横 折线 + 开口箭头（全描边，不做大块实心）
    $g.DrawLines($pen, @(
        (New-Object System.Drawing.PointF((P 38 $px), (P 58 $px))),
        (New-Object System.Drawing.PointF((P 38 $px), (P 44 $px))),
        (New-Object System.Drawing.PointF((P 50 $px), (P 44 $px)))
    ))
    $g.DrawLines($pen, @(
        (New-Object System.Drawing.PointF((P 45 $px), (P 37 $px))),
        (New-Object System.Drawing.PointF((P 53 $px), (P 44 $px))),
        (New-Object System.Drawing.PointF((P 45 $px), (P 51 $px)))
    ))

    # 立柱
    $g.DrawLine($pen, (P 54 $px), (P 68 $px), (P 54 $px), (P 86 $px))

    $pen.Dispose(); $white.Dispose(); $path.Dispose()
}

# ---------------- 相册图标：照片框 + 山 + 太阳 + 地面线 ----------------
function Draw-Gallery($g, [int]$px) {
    $black = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::Black)
    $white = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::Black), (P 2.6 $px)
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round

    # 照片框
    $fr = New-Object System.Drawing.RectangleF((P 20 $px), (P 30 $px), (P 68 $px), (P 52 $px))
    $r = P 6 $px
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc($fr.X, $fr.Y, $r * 2, $r * 2, 180, 90)
    $path.AddArc($fr.Right - $r * 2, $fr.Y, $r * 2, $r * 2, 270, 90)
    $path.AddArc($fr.Right - $r * 2, $fr.Bottom - $r * 2, $r * 2, $r * 2, 0, 90)
    $path.AddArc($fr.X, $fr.Bottom - $r * 2, $r * 2, $r * 2, 90, 90)
    $path.CloseFigure()
    $g.FillPath($white, $path)
    $g.DrawPath($pen, $path)

    # 太阳（小圆，描边）
    $g.DrawEllipse($pen, (P 64 $px), (P 39 $px), (P 11 $px), (P 11 $px))

    # 山峰（折线，描边，两个峰）
    $g.DrawLines($pen, @(
        (New-Object System.Drawing.PointF((P 25 $px), (P 73 $px))),
        (New-Object System.Drawing.PointF((P 44 $px), (P 50 $px))),
        (New-Object System.Drawing.PointF((P 54 $px), (P 63 $px))),
        (New-Object System.Drawing.PointF((P 61 $px), (P 54 $px))),
        (New-Object System.Drawing.PointF((P 84 $px), (P 73 $px)))
    ))

    $pen.Dispose(); $black.Dispose(); $white.Dispose(); $path.Dispose()
}

function Write-Icon([string]$appPath, [scriptblock]$draw, [string]$label) {
    foreach ($d in $dens) {
        $dir = Join-Path $appPath ("res\" + $d.dir)
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        $pair = New-Canvas $d.px
        $bmp = $pair[0]; $g = $pair[1]
        & $draw $g $d.px
        $g.Dispose()
        $out = Join-Path $dir "ic_launcher.png"
        $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
    }
    # 512 母版，便于查看/发布
    $pair = New-Canvas 512
    $bmp = $pair[0]; $g = $pair[1]
    & $draw $g 512
    $g.Dispose()
    $prev = Join-Path $PSScriptRoot ("preview-" + $label + ".png")
    $bmp.Save($prev, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "icons -> $appPath ($($dens.Count) densities)"
}

$RepoRoot = Split-Path -Parent $PSScriptRoot
$navReader = Join-Path $RepoRoot "reader"
$navPhone  = Join-Path $RepoRoot "phone"
$galDirs = $env:EINK_GALLERY_PATH

Write-Icon $navReader { param($g, $px) Draw-Nav $g $px } "nav"
Write-Icon $navPhone  { param($g, $px) Draw-Nav $g $px } "nav"
if ($galDirs -and (Test-Path $galDirs)) {
    Write-Icon $galDirs { param($g, $px) Draw-Gallery $g $px } "gallery"
}
