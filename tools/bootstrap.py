"""Download a local Windows build toolchain when Android Studio is absent."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import urllib.request
import zipfile

root = Path(__file__).resolve().parents[1] / ".tools"
root.mkdir(exist_ok=True)
downloads = {
    "jdk.zip": "https://cdn.azul.com/zulu/bin/zulu17.58.21-ca-jdk17.0.15-win_x64.zip",
    "gradle.zip": "https://services.gradle.org/distributions/gradle-8.7-bin.zip",
    "cmdline.zip": "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip",
}

def download(pair):
    name, url = pair
    path = root / name
    if not path.exists() or not zipfile.is_zipfile(path):
        print("Downloading", name, flush=True)
        urllib.request.urlretrieve(url, path)
    print("Ready", name, path.stat().st_size, flush=True)
    return path

with ThreadPoolExecutor(max_workers=3) as pool:
    files = list(pool.map(download, downloads.items()))

for path in files:
    destination = root / ("sdk/cmdline-tools/latest" if path.name == "cmdline.zip" else "")
    if path.name != "cmdline.zip":
        destination = root
    marker = root / (path.stem + ".extracted")
    if not marker.exists():
        print("Extracting", path.name, flush=True)
        with zipfile.ZipFile(path) as archive:
            for member in archive.infolist():
                member.filename = member.filename.removeprefix("cmdline-tools/") if path.name == "cmdline.zip" else member.filename
                if member.filename:
                    archive.extract(member, destination)
        marker.touch()
print("Toolchain extracted", flush=True)

sdk_packages = {
    "platform-35_r02.zip": ("platforms/android-35", "android-35/"),
    "build-tools_r35_windows.zip": ("build-tools/35.0.0", "android-15/"),
    "platform-tools_r37.0.1-win.zip": ("platform-tools", "platform-tools/"),
}

def sdk_package(item):
    name, (location, prefix) = item
    url = "https://dl.google.com/android/repository/" + name
    path = download((name, url))
    destination = root / "sdk" / location
    marker = root / (name + ".extracted")
    if not marker.exists():
        destination.mkdir(parents=True, exist_ok=True)
        print("Extracting", name, flush=True)
        with zipfile.ZipFile(path) as archive:
            for member in archive.infolist():
                filename = member.filename
                if filename.startswith(prefix):
                    member.filename = filename[len(prefix):]
                    if member.filename:
                        archive.extract(member, destination)
        marker.touch()

with ThreadPoolExecutor(max_workers=3) as pool:
    list(pool.map(sdk_package, sdk_packages.items()))
print("SDK packages extracted", flush=True)
