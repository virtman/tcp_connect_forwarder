@taskkill /IM java.exe /T /F
@set path=C:\Program Files (x86)\Java\jdk1.6.0_45\bin;%path%
@rem set path=C:\Program Files\Java\jdk1.8.0_45\bin;%path%

@set JAVA_HOME=C:\Program Files (x86)\Java\jdk1.6.0_45
@rem set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_45

@FOR %%F IN (juwagn\*.class) DO del %%F>nul
@FOR %%F IN (juwagn\*.~ava) DO del %%F>nul
@FOR %%F IN (localwebs.jar) DO del %%F>nul

@FOR %%F IN (juwagn\ServerListener.java) DO javac.exe %%F -source 1.2 -target 1.1 -O -deprecation -g 2>err.txt

@jar.exe -cfvm localwebs.jar MANIFEST.MF juwagn\*.class
@FOR %%F IN (juwagn\*.class) DO del %%F>nul
@FOR %%F IN (juwagn\*.~ava) DO del %%F>nul

pause