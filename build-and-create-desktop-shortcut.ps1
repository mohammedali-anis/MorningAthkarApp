$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot
$inputDirectory = Join-Path $projectRoot 'target\jpackage-input'
$libraryDirectory = Join-Path $inputDirectory 'lib'
$javafxDirectory = Join-Path $inputDirectory 'javafx'
$iconPath = Join-Path $projectRoot 'assets\morning-athkar.ico'
$appName = "MorningAthkar-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
$appImageDirectory = Join-Path (Join-Path $projectRoot 'dist') $appName
$mainJar = 'morning-athkar-1.0.0.jar'

$maven = (Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue).Source
if (-not $maven) {
    $knownMaven = 'C:\Data\apache-maven-3.8.8\bin\mvn.cmd'
    if (Test-Path -LiteralPath $knownMaven) {
        $maven = $knownMaven
    }
}
if (-not $maven) {
    throw 'Maven wurde nicht gefunden. Installiere Maven oder trage den Pfad zu mvn.cmd in dieses Skript ein.'
}

$javaCommand = Get-Command 'java.exe' -ErrorAction Stop
$javaHome = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
$jpackage = Join-Path $javaHome 'bin\jpackage.exe'
if (-not (Test-Path -LiteralPath $jpackage)) {
    throw "jpackage wurde unter '$jpackage' nicht gefunden. Installiere ein JDK 21."
}

Push-Location $projectRoot
try {
    Write-Host 'Erstelle das App-Icon ...'
    & $javaCommand.Source (Join-Path $projectRoot 'tools\GenerateAppIcon.java') $projectRoot
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $iconPath)) {
        throw 'Das App-Icon konnte nicht erstellt werden.'
    }

    Write-Host 'Baue die JavaFX-Anwendung und sammle Laufzeitbibliotheken ...'
    & $maven '-f' 'pom.xml' 'clean' 'compile' 'jar:jar' 'dependency:copy-dependencies' `
        '-DincludeScope=runtime' "-DoutputDirectory=$libraryDirectory"
    if ($LASTEXITCODE -ne 0) {
        throw "Maven ist mit Exit-Code $LASTEXITCODE fehlgeschlagen."
    }

    $builtJar = Join-Path $projectRoot "target\$mainJar"
    if (-not (Test-Path -LiteralPath $builtJar)) {
        throw "Das gebaute Programm wurde nicht gefunden: $builtJar"
    }
    New-Item -ItemType Directory -Force -Path $libraryDirectory | Out-Null
    Copy-Item -LiteralPath $builtJar -Destination (Join-Path $inputDirectory $mainJar) -Force
    New-Item -ItemType Directory -Force -Path $javafxDirectory | Out-Null
    Get-ChildItem -LiteralPath $libraryDirectory -Filter 'javafx-*-win.jar' |
        Copy-Item -Destination $javafxDirectory -Force

    Write-Host 'Erstelle die Windows-App ...'
    & $jpackage '--type' 'app-image' '--dest' (Join-Path $projectRoot 'dist') '--name' $appName `
        '--input' $inputDirectory '--main-jar' $mainJar '--main-class' 'MorningAthkarFxApp' `
        '--icon' $iconPath `
        '--java-options' '--module-path=$APPDIR/javafx --add-modules=javafx.controls,java.desktop -Djava.library.path=$APPDIR'
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage ist mit Exit-Code $LASTEXITCODE fehlgeschlagen."
    }

    $appExecutable = Join-Path $appImageDirectory "$appName.exe"
    if (-not (Test-Path -LiteralPath $appExecutable)) {
        throw "Der Programmstarter wurde nicht erstellt: $appExecutable"
    }

    $shortcutScriptPath = Join-Path $projectRoot 'target\create-desktop-shortcut.vbs'
    $shortcutScript = @"
Set shell = CreateObject("WScript.Shell")
linkPath = shell.SpecialFolders("Desktop") & "\MorningAthkar.lnk"
Set shortcut = shell.CreateShortcut(linkPath)
shortcut.TargetPath = "$appExecutable"
shortcut.WorkingDirectory = "$(Join-Path $appImageDirectory 'app')"
shortcut.IconLocation = "$appExecutable,0"
On Error Resume Next
shortcut.Save
If Err.Number <> 0 Then
    WScript.Echo "Die Desktop-Verknuepfung konnte nicht gespeichert werden: " & Err.Description
    WScript.Quit 1
End If
On Error GoTo 0
"@
    Set-Content -LiteralPath $shortcutScriptPath -Value $shortcutScript -Encoding ASCII
    $cscript = Join-Path $env:WINDIR 'System32\cscript.exe'
    & $cscript '//nologo' $shortcutScriptPath
    if ($LASTEXITCODE -ne 0) {
        throw "Windows konnte die Desktop-Verknüpfung nicht anlegen (Exit-Code $LASTEXITCODE)."
    }

    Write-Host 'Fertig. Die Desktop-Verknüpfung MorningAthkar.lnk wurde angelegt.'
}
finally {
    Pop-Location
}
