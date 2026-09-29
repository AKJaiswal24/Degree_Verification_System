@echo off
setlocal
set JBR=C:\Program Files\Android\Android Studio\jbr\bin
set OUT=tools\build

for /f "delims=" %%i in ('dir /b /s "%USERPROFILE%\.gradle\caches\modules-2\files-2.1\com.google.zxing\core\3.5.3\*.jar"') do set ZX=%%i

if not exist "%OUT%" mkdir "%OUT%"

"%JBR%\javac.exe" -d "%OUT%" -cp "%ZX%" ^
  tools\java\tools\SampleRecords.java ^
  tools\java\tools\Base64Codec.java ^
  tools\java\tools/CryptoSelfTest.java ^
  tools\java\tools/QrFixtureGenerator.java ^
  app\src\main\java\com\example\ibu_verification\security\CertificatePayloadCodec.java || exit /b 1

echo Running crypto self test...
"%JBR%\java.exe" -cp "%OUT%;%ZX%" tools.CryptoSelfTest || exit /b 1

echo.
echo Generating QR fixtures...
"%JBR%\java.exe" -cp "%OUT%;%ZX%" tools.QrFixtureGenerator sample_qr_codes || exit /b 1

endlocal
