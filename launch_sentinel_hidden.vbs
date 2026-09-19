' ==============================================================================
' DPCLOCKER :: WINDOWLESS REAL-TIME SENTINEL LAUNCHER (TRIAD MESH)
' ==============================================================================
' Launches windows_proxy_sentinel.py, windows_sentinel_watchdog.py,
' and windows_sentinel_guard.py with WindowStyle 0
' (Completely hidden, zero taskbar presence, no console).
' ==============================================================================

Option Explicit
Dim WshShell, fso, scriptDir, pywPath, userName, cleanUser, userSignalFile, globalSignalFile, cmdLine

Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
WshShell.CurrentDirectory = scriptDir

' 1. Locate pythonw.exe dynamically across multi-user environments
pywPath = ""
If fso.FileExists("C:\Users\ThinkPad\AppData\Local\Programs\Python\Python311\pythonw.exe") Then
    pywPath = "C:\Users\ThinkPad\AppData\Local\Programs\Python\Python311\pythonw.exe"
ElseIf fso.FileExists("C:\Program Files\Python311\pythonw.exe") Then
    pywPath = "C:\Program Files\Python311\pythonw.exe"
ElseIf fso.FileExists("C:\Windows\pyw.exe") Then
    pywPath = "C:\Windows\pyw.exe"
Else
    pywPath = "pythonw.exe"
End If

' 2. Determine per-user stop signal file
On Error Resume Next
userName = WshShell.ExpandEnvironmentStrings("%USERNAME%")
cleanUser = LCase(userName)
cleanUser = Replace(cleanUser, "'", "_")
cleanUser = Replace(cleanUser, " ", "_")
On Error GoTo 0

userSignalFile = scriptDir & "\.sentinel_stop_signal_" & cleanUser
globalSignalFile = scriptDir & "\.sentinel_stop_signal"

' Ensure any leftover stop signal is cleared before starting
If fso.FileExists(userSignalFile) Then
    On Error Resume Next
    fso.DeleteFile userSignalFile, True
    On Error GoTo 0
End If
If fso.FileExists(globalSignalFile) Then
    On Error Resume Next
    fso.DeleteFile globalSignalFile, True
    On Error GoTo 0
End If

' 3. Launch the primary Sentinel process with WindowStyle 0 (Completely hidden, no console, no taskbar button)
' Sentinel automatically spawns and synchronizes Watchdog Alpha and Guard Beta.
cmdLine = Chr(34) & pywPath & Chr(34) & " " & Chr(34) & scriptDir & "\windows_proxy_sentinel.py" & Chr(34)

On Error Resume Next
WshShell.Run cmdLine, 0, False

If Err.Number <> 0 Then
    Dim logFile
    Set logFile = fso.OpenTextFile(scriptDir & "\sentinel_launcher_error.log", 8, True)
    logFile.WriteLine "[" & Now & "] Launcher error for user " & userName & ": " & Err.Description & " (" & Hex(Err.Number) & ")"
    logFile.Close
End If
On Error GoTo 0
