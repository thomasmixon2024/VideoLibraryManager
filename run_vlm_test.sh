#!/usr/bin/env bash
TRANSFORMS_DIR="/data/data/com.termux/files/home/.gradle/caches/8.9/transforms"
NATIVE_AAPT2="/data/data/com.termux/files/home/android-sdk/build-tools/35.0.0/aapt2"

# Fast-scan and intercept any atomized directory drops residing in cache lines
if [ -d "$TRANSFORMS_DIR" ]; then
    find "$TRANSFORMS_DIR" -type f -name "aapt2" 2>/dev/null | while read -r target_bin; do
        if [ ! -L "$target_bin" ]; then
            rm -f "$target_bin"
            cat << 'SHIM' > "$target_bin"
#!/usr/bin/env bash
exec "/data/data/com.termux/files/home/android-sdk/build-tools/35.0.0/aapt2" "$@"
SHIM
            chmod +x "$target_bin"
        fi
    done
fi

# Run the target execution with a hard process safety timeout guard
timeout --kill-after=5s 90s ./gradlew testDebugUnitTest --no-daemon "$@"
GRADLE_STATUS=$?

# Instantly drop background JVM threads to keep Termux fully responsive
killall -9 java aapt2 app_process 2>/dev/null
exit $GRADLE_STATUS
