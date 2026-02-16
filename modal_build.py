"""
ClinicalFlow Android APK Builder via Modal

Usage:
    pip install modal
    modal run modal_build.py
"""

import os
import pathlib
import shutil
import subprocess
import tarfile
import tempfile
from typing import List

import modal

# Create or get the volume for APK storage
volume = modal.Volume.from_name("clinicalflow-apk", create_if_missing=True)

# Create or get volume for Gradle cache (speeds up subsequent builds)
gradle_cache_volume = modal.Volume.from_name("clinicalflow-gradle-cache", create_if_missing=True)

def run(cmd: List[str], cwd: pathlib.Path) -> None:
    print(f"[build] {' '.join(cmd)}")
    process = subprocess.Popen(
        cmd,
        cwd=str(cwd),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1
    )
    output_lines = []
    for line in process.stdout:
        print(line, end='')
        output_lines.append(line)
    process.wait()
    if process.returncode != 0:
        output = ''.join(output_lines)
        print(f"[build] Build failed with exit code {process.returncode}. Full output captured ({len(output)} chars).")
        raise subprocess.CalledProcessError(process.returncode, cmd, output, '')

# Android SDK image with all build tools
android_image = (
    modal.Image.debian_slim()
    .apt_install("openjdk-17-jdk", "wget", "unzip", "git", "curl")
    .run_commands(
        # Download Android command-line tools
        "mkdir -p /sdk/cmdline-tools",
        "wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /sdk/tools.zip",
        "unzip -q /sdk/tools.zip -d /sdk/cmdline-tools",
        "mv /sdk/cmdline-tools/cmdline-tools /sdk/cmdline-tools/latest",
        "rm /sdk/tools.zip",
    )
    .env({
        "JAVA_HOME": "/usr/lib/jvm/java-17-openjdk-amd64",
        "ANDROID_HOME": "/sdk",
        "ANDROID_SDK_ROOT": "/sdk",
        "PATH": "/usr/lib/jvm/java-17-openjdk-amd64/bin:/sdk/platform-tools:/sdk/cmdline-tools/latest/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
    })
    .run_commands(
        # Accept licenses and install SDK components
        "yes | sdkmanager --licenses > /dev/null 2>&1",
        "sdkmanager 'platform-tools' 'platforms;android-34' 'build-tools;34.0.0'",
    )
    .run_commands(
        # Install Gradle for wrapper generation
        "wget -q https://services.gradle.org/distributions/gradle-8.5-bin.zip -O /tmp/gradle.zip",
        "unzip -q /tmp/gradle.zip -d /opt",
        "rm /tmp/gradle.zip",
    )
    .env({
        "JAVA_HOME": "/usr/lib/jvm/java-17-openjdk-amd64",
        "ANDROID_HOME": "/sdk",
        "ANDROID_SDK_ROOT": "/sdk",
        "GRADLE_HOME": "/opt/gradle-8.5",
        "PATH": "/opt/gradle-8.5/bin:/usr/lib/jvm/java-17-openjdk-amd64/bin:/sdk/platform-tools:/sdk/cmdline-tools/latest/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
    })
)

app = modal.App("clinicalflow-builder")


@app.function(image=android_image, volumes={"/vol": volume, "/root/.gradle": gradle_cache_volume}, cpu=4.0, memory=32768, timeout=600)
def build_apk_remote(archive_bytes: bytes) -> str:
    """Build APK from uploaded project archive."""
    workdir = pathlib.Path(tempfile.mkdtemp(prefix="clinicalflow-modal-"))
    
    # Set JVM memory limits
    os.environ["JAVA_OPTS"] = "-Xmx2g -XX:MaxMetaspaceSize=512m"
    os.environ["GRADLE_OPTS"] = "-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m"
    
    try:
        # Extract uploaded archive
        archive_path = workdir / "project.tar.gz"
        archive_path.write_bytes(archive_bytes)
        
        with tarfile.open(archive_path, mode="r:gz") as tar:
            tar.extractall(path=workdir)
        
        # Find project root
        project_root = workdir / "ClinicalFlow"
        if not project_root.exists():
            # Check if extracted at root
            if (workdir / "app").exists():
                project_root = workdir
            else:
                raise RuntimeError(f"Cannot find ClinicalFlow project in {list(workdir.iterdir())}")
        
        print(f"[build] Project root: {project_root}")
        
        # Create local.properties with SDK path
        local_props = project_root / "local.properties"
        local_props.write_text("sdk.dir=/sdk\n")
        
        # Create gradle.properties with optimizations
        gradle_props = project_root / "gradle.properties"
        gradle_props.write_text("""android.useAndroidX=true
android.enableJetifier=true
android.nonTransitiveRClass=true
org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m -XX:+UseG1GC
org.gradle.daemon=false
org.gradle.parallel=true
org.gradle.workers.max=2
org.gradle.caching=true
kotlin.incremental=true
kotlin.compiler.execution.strategy=in-process
""")
        
        # Pre-download dependencies (uses cached .gradle from volume)
        print("[build] Pre-downloading dependencies...")
        run(["gradle", "dependencies", "--no-daemon", "-q"], project_root)
        
        # Build debug APK using installed Gradle (skip wrapper)
        print("[build] Running Gradle assembleDebug...")
        run(["gradle", ":app:assembleDebug", "--no-daemon", "--stacktrace", "--info"], project_root)
        
        # Find the generated APK
        apk_paths = list(project_root.glob("**/build/outputs/apk/**/*.apk"))
        if not apk_paths:
            raise RuntimeError("APK not found after build")
        
        apk_src = apk_paths[0]
        print(f"[build] APK generated: {apk_src}")
        
        # Copy to volume
        dest = pathlib.Path("/vol/clinicalflow-debug.apk")
        shutil.copy(apk_src, dest)
        volume.commit()
        
        return f"APK built successfully: {apk_src.name}"
        
    finally:
        shutil.rmtree(workdir)


@app.local_entrypoint()
def main():
    """Upload project, build APK, download result."""
    import time
    
    # Create tarball of ClinicalFlow project
    project_dir = pathlib.Path(__file__).parent
    if not (project_dir / "app").exists():
        raise FileNotFoundError(f"ClinicalFlow project not found at {project_dir}")
    
    print(f"[local] Creating archive from {project_dir}...")
    
    # Create tarball in memory
    import io
    tar_buffer = io.BytesIO()
    
    with tarfile.open(fileobj=tar_buffer, mode="w:gz") as tar:
        for file_path in project_dir.rglob("*"):
            if file_path.is_file():
                # Skip build outputs and cache
                if any(p in file_path.parts for p in ["build", ".gradle", ".idea", "node_modules", "__pycache__"]):
                    continue
                arcname = pathlib.Path("ClinicalFlow") / file_path.relative_to(project_dir)
                tar.add(file_path, arcname=arcname)
    
    archive_bytes = tar_buffer.getvalue()
    print(f"[local] Archive size: {len(archive_bytes) / 1024:.1f} KB")
    
    # Build on Modal
    print("[local] Starting Modal build...")
    start = time.time()
    result = build_apk_remote.remote(archive_bytes)
    elapsed = time.time() - start
    print(f"[local] Build completed in {elapsed:.1f}s: {result}")
    
    # Download APK
    output_dir = pathlib.Path(__file__).parent / "dist"
    output_dir.mkdir(exist_ok=True)
    
    # Read from volume
    apk_data = b"".join(volume.read_file("clinicalflow-debug.apk"))
    output_path = output_dir / "clinicalflow-debug.apk"
    output_path.write_bytes(apk_data)
    
    print(f"[local] APK downloaded to: {output_path}")
    print(f"[local] APK size: {output_path.stat().st_size / 1024:.1f} KB")
