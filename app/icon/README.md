# Иконка приложения

Пиксельный посох, 32×32 точки, 16 цветов. Рисунок задан кодом в `generate.py`, а не нарисован в редакторе: так его можно править по одной точке и держать в git текстом.

| Файл | Что это |
|---|---|
| `generate.py` | сам рисунок: палитра и расстановка точек |
| `icon-32.png` | исходный размер, одна точка рисунка — один пиксель |
| `icon-128.png`, `icon-512.png` | увеличенные копии для просмотра и для сборки значков |

## Как перерисовать

Нужен Python с библиотекой Pillow:

```powershell
pip install Pillow
python app\icon\generate.py
```

Скрипт перезапишет три PNG рядом с собой и напечатает, сколько цветов из шестнадцати задействовано.

## Как пересобрать значки для Android

Готовые значки лежат в `app/app/src/main/res/` и в репозиторий входят, так что для обычной сборки этот шаг не нужен. Он нужен, только если изменился сам рисунок.

Команда для PowerShell (Windows), ничего ставить не нужно:

```powershell
Add-Type -AssemblyName System.Drawing
$root = "app\app\src\main\res"
$src  = [System.Drawing.Image]::FromFile("app\icon\icon-512.png")

$d = @(
  @{n='mdpi';    icon=48;  layer=108},
  @{n='hdpi';    icon=72;  layer=162},
  @{n='xhdpi';   icon=96;  layer=216},
  @{n='xxhdpi';  icon=144; layer=324},
  @{n='xxxhdpi'; icon=192; layer=432}
)

foreach ($e in $d) {
  $mip = Join-Path $root "mipmap-$($e.n)"
  $drw = Join-Path $root "drawable-$($e.n)"
  New-Item -ItemType Directory -Force -Path $mip, $drw | Out-Null

  foreach ($job in @(
      @{path=(Join-Path $mip 'ic_launcher.png');            size=$e.icon;  art=$e.icon;              round=$false},
      @{path=(Join-Path $mip 'ic_launcher_round.png');       size=$e.icon;  art=$e.icon;              round=$true},
      @{path=(Join-Path $drw 'ic_launcher_foreground.png');  size=$e.layer; art=[int]($e.layer*2/3);  round=$false}
  )) {
    $bmp = New-Object System.Drawing.Bitmap $job.size, $job.size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::None
    if ($job.round) {
      $path = New-Object System.Drawing.Drawing2D.GraphicsPath
      $path.AddEllipse(0, 0, $job.size, $job.size)
      $g.SetClip($path)
    }
    $off = [int](($job.size - $job.art) / 2)
    $g.DrawImage($src, $off, $off, $job.art, $job.art)
    $bmp.Save($job.path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
  }
}
$src.Dispose()
```

## Почему именно так

**Только «ближайший сосед».** Обычное сглаживание при уменьшении размывает границы точек, и пиксель-арт превращается в кашу. Во всех пересчётах стоит `NearestNeighbor`.

**Передний слой занимает 72 dp из 108.** Android показывает иконку через маску своей формы — круг, квадрат со скруглением, каплю, — и из слоя размером 108 dp гарантированно видны только центральные 72. Рисунок положен ровно в эту зону, поэтому посох не обрежется ни при какой форме значка.

**Фон — сплошной `#070C14`.** Это цвет рамки самого рисунка, поэтому стык между картинкой и фоном не виден, а маска обрезает только пустое поле.

**Монохромного слоя нет.** Он нужен для «тематических значков» Android 13+, но цветной пиксель-арт в силуэте превращается в сплошное пятно. Без этого слоя система показывает обычную иконку, и это выглядит лучше.
