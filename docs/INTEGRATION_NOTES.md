# File Safety Layer - Integration Notes

## What these two files do
- `FileOperationSafety.kt` - the only place renameTo()/delete() should ever
  be called. Every action is pre-checked (source exists, inside the library,
  destination doesn't already exist), logged before AND after it runs, and
  wrapped so it returns a result instead of throwing. Deletes never actually
  delete on the first action - they move to a quarantine folder, so a bad
  tap is recoverable. dryRun defaults to true.
- `ConfirmFileActionDialog.kt` - a Compose dialog that has to sit between any
  button and the executor above. HIGH risk actions require typing CONFIRM.

## Wiring it in (example)
```kotlin
val executor = SafeFileExecutor(
    libraryRoots = listOf(File("/storage/emulated/0/Movies")),
    quarantineDir = File(context.filesDir, "quarantine"),
    log = FileOperationLog(context.filesDir),
    dryRun = false // flip only when you're ready for real writes
)

// in the ViewModel, behind a button:
fun onRenameRequested(file: File, newName: String) {
    _pendingConfirmation.value = PendingUiConfirmation(
        title = "Rename file?",
        description = "${file.name} -> $newName",
        risk = RiskLevel.LOW,
        onConfirm = {
            val result = executor.rename(file, newName, confirmed = true)
            _lastOpResult.value = result // surface Success/Failure/Blocked in the UI, don't swallow it
        }
    )
}
```
The key rule: nothing calls `executor.rename/move/delete(confirmed = true)`
except the dialog's onConfirm. Buttons only ever request a confirmation.

## Next step - the actual audit
I can't see the real VLM codebase from here, so I can't yet tell you which
buttons are wired to real file operations versus a mocked/no-op path, or
where the crash risks actually are. To do that audit for real, send me the
source. Easiest way, run this in Termux:

```bash
cd $HOME/VideoLibraryManager
zip -r $HOME/storage/downloads/VLM_TXT/vlm_audit_source.zip \
  app/src/main/java app/src/main/AndroidManifest.xml app/build.gradle.kts \
  -x "*/build/*"
```

Then upload `vlm_audit_source.zip` from your Downloads/VLM_TXT folder here.
With that I can check, file by file: which button handlers actually touch
the filesystem vs. just update UI state, where exceptions are swallowed or
unhandled, what's likely causing crashes, and where this safety layer
needs to be inserted.
