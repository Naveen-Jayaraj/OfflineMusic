# Render the production vector geometry for a convenient PNG preview.
Add-Type -AssemblyName System.Drawing
$bitmap = New-Object System.Drawing.Bitmap 512,512
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#191C19'))
$graphics.ScaleTransform(5.12,5.12)
$disc = New-Object System.Drawing.Drawing2D.GraphicsPath
$disc.AddEllipse(8,8,84,84)
$graphics.SetClip($disc)
$mark = New-Object System.Drawing.Drawing2D.GraphicsPath
$mark.FillMode = [System.Drawing.Drawing2D.FillMode]::Alternate
$mark.AddEllipse(8,8,84,84)
$mark.AddEllipse(42,42,16,16)
$mark.StartFigure()
$mark.AddBezier(0,72,20,58,30,63,50,72)
$mark.AddBezier(50,72,70,81,80,60,100,68)
$mark.AddLine(100,68,100,72)
$mark.AddBezier(100,72,80,64,70,85,50,76)
$mark.AddBezier(50,76,30,67,20,62,0,76)
$mark.CloseFigure()
$brush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#A8B89A'))
$graphics.FillPath($brush,$mark)
$bitmap.Save((Join-Path $PSScriptRoot 'bittune-mark.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$brush.Dispose()
$mark.Dispose()
$disc.Dispose()
$graphics.Dispose()
$bitmap.Dispose()
