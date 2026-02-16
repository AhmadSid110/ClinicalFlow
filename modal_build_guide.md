# Modal Build Guide for ClinicalFlow

This guide covers how to build the ClinicalFlow Android APK using Modal containers.

## Quick Start

```bash
# Install Modal CLI
pip install modal

# Run the build
modal run modal_build.py
```

## Build Output

- **APK Location**: `./dist/clinicalflow-debug.apk`
- **Volume Storage**: Modal volume `clinicalflow-apk`

## Build Optimizations

The build script includes several optimizations for faster builds:

### 1. Gradle Cache (Volume-mounted)

The first build downloads all dependencies. Subsequent builds reuse the cached Gradle user home (`/root/.gradle`) mounted from Modal volume `clinicalflow-gradle-cache`.

**Benefit**: ~50-70% faster dependency resolution on subsequent builds.

### 2. Dependency Pre-resolution

Before compiling, we run `gradle dependencies --no-daemon -q` to download all dependencies first.

**Benefit**: Prevents mid-build downloads which can timeout.

### 3. Gradle Optimizations

`gradle.properties` is injected with:
```properties
org.gradle.parallel=true          # Parallel task execution
org.gradle.workers.max=2           # Limit worker threads
org.gradle.caching=true            # Enable build cache
kotlin.incremental=true           # Kotlin incremental compile
kotlin.compiler.execution.strategy=in-process  # Reduce JVM spawns
org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m -XX:+UseG1GC
```

**Benefit**: Faster compilation and reduced memory pressure.

### 4. Gradle Arguments

- `--no-daemon` - Avoids daemon startup overhead (Modal is ephemeral)
- `--stacktrace` - Useful for debugging failures
- `--info` - Detailed logs (can reduce to `--warn` for faster runs)

## Troubleshooting

### Build Timeout

If builds timeout, increase in `modal_build.py`:
```python
@app.function(..., timeout=900)  # 15 minutes
```

### Out of Memory

Adjust JVM args in the build script:
```python
os.environ["GRADLE_OPTS"] = "-Xmx4g -XX:MaxMetaspaceSize=1g"
```

### Clean Cache

To force a fresh build without cache:

```python
# In modal_build.py, comment out the volume mount temporarily:
# volumes={"/vol": volume}  # Remove gradle cache
```

Or delete the Modal volume:
```bash
modal volume delete clinicalflow-gradle-cache
```



## First-time Setup

On first run, Modal will:
1. Create volume `clinicalflow-apk` for APK storage
2. Create volume `clinicalflow-gradle-cache` for Gradle cache
3. Download Android SDK (~500MB)
4. Download Gradle (~100MB)
5. Download all Maven dependencies (~200MB)

This first run takes ~10-15 minutes. Subsequent runs are much faster.

## Manual Commands

If you need to run Gradle manually inside Modal:

```python
run(["gradle", "assembleDebug", "--no-daemon", "--stacktrace", "--info"], project_root)
```

## Performance Tips

1. **Keep the container warm** - Run builds within a few days of each other
2. **Don't exclude too many files** - Some cache is good
3. **Use debug builds for testing** - Release builds take longer
4. **Monitor Modal dashboard** - Check resource usage at modal.com
