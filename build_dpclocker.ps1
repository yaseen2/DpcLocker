$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

$sdkDir = "C:\Users\ThinkPad\AppData\Local\Android\Sdk"
$buildToolsDir = "$sdkDir\build-tools\34.0.0"
$androidJar = "$sdkDir\platforms\android-34\android.jar"
$javac = "$env:JAVA_HOME\bin\javac.exe"
$keytool = "$env:JAVA_HOME\bin\keytool.exe"

$projectDir = "d:\Ai studio\windows operation\DpcLocker"
$buildDir = "$projectDir\build"
$objDir = "$buildDir\obj"

Write-Host "1. Compiling resources with AAPT2..."
Set-Location $projectDir
if (Test-Path "$buildDir\unaligned.apk") { Remove-Item "$buildDir\unaligned.apk" -Force }
if (Test-Path "$buildDir\DpcLocker.apk") { Remove-Item "$buildDir\DpcLocker.apk" -Force }
if (Test-Path "$buildDir\classes.dex") { Remove-Item "$buildDir\classes.dex" -Force }

& "$buildToolsDir\aapt2.exe" compile --dir res -o "$buildDir\resources.zip"
& "$buildToolsDir\aapt2.exe" link -o "$buildDir\unaligned.apk" -I $androidJar --manifest AndroidManifest.xml "$buildDir\resources.zip" --java src

Write-Host "2. Compiling Java source files..."
$javaFiles = Get-ChildItem -Path "$projectDir\src" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
& $javac -source 1.8 -target 1.8 -cp $androidJar -d $objDir $javaFiles

Write-Host "3. Converting class files to DEX..."
$classFiles = Get-ChildItem -Path $objDir -Filter "*.class" -Recurse | Select-Object -ExpandProperty FullName
& "$buildToolsDir\d8.bat" --min-api 26 --output "$buildDir" --lib $androidJar $classFiles

Write-Host "4. Adding classes.dex to APK..."
Set-Location $buildDir
& "$buildToolsDir\aapt.exe" add unaligned.apk classes.dex

Write-Host "5. Aligning APK..."
& "$buildToolsDir\zipalign.exe" -v -f 4 "$buildDir\unaligned.apk" "$buildDir\DpcLocker.apk"

Write-Host "6. Generating Debug Keystore if missing..."
$keystore = "$buildDir\debug.keystore"
if (-not (Test-Path $keystore)) {
    & $keytool -genkeypair -v -keystore $keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" -storepass android -keypass android
}

Write-Host "7. Signing APK..."
& "$buildToolsDir\apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android "$buildDir\DpcLocker.apk"

Write-Host "BUILD SUCCESSFUL! Created DpcLocker.apk at $buildDir\DpcLocker.apk"
