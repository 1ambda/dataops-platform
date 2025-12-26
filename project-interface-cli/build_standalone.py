#!/usr/bin/env python3
"""
Standalone executable builder for dli CLI using PyInstaller.
This script creates a single executable file that includes all dependencies.
"""

from pathlib import Path
import subprocess
import sys


def run_command(command, description):
    """Run a command and print the description."""
    print(f"🔧 {description}...")
    try:
        result = subprocess.run(
            command, shell=True, check=True, capture_output=True, text=True
        )
        print(f"✅ {description} completed successfully")
        if result.stdout:
            print(f"   Output: {result.stdout.strip()}")
        return True
    except subprocess.CalledProcessError as e:
        print(f"❌ {description} failed")
        print(f"   Error: {e.stderr}")
        return False


def main():
    """Build standalone executable."""
    print("🚀 Building dli standalone executable with PyInstaller")

    # Check if PyInstaller is installed
    try:
        import PyInstaller

        print(f"✅ PyInstaller found: {PyInstaller.__version__}")
    except ImportError:
        print("❌ PyInstaller not found. Installing...")
        if not run_command("pip install pyinstaller", "Installing PyInstaller"):
            return False

    # Build paths
    project_root = Path(__file__).parent
    main_script = project_root / "src" / "dli" / "main.py"
    dist_dir = project_root / "dist_standalone"

    if not main_script.exists():
        print(f"❌ Main script not found: {main_script}")
        return False

    # PyInstaller command
    pyinstaller_cmd = [
        "pyinstaller",
        "--onefile",  # Create single executable
        "--name=dli",  # Output name
        f"--distpath={dist_dir}",  # Output directory
        "--clean",  # Clean build
        "--noconfirm",  # Overwrite without confirmation
        "--console",  # Console application
        str(main_script),
    ]

    # Run PyInstaller
    cmd_str = " ".join(pyinstaller_cmd)
    if not run_command(cmd_str, "Building standalone executable"):
        return False

    # Check if executable was created
    executable_path = dist_dir / "dli"
    if sys.platform == "win32":
        executable_path = dist_dir / "dli.exe"

    if executable_path.exists():
        size_mb = executable_path.stat().st_size / (1024 * 1024)
        print("🎉 Standalone executable created successfully!")
        print(f"   Location: {executable_path}")
        print(f"   Size: {size_mb:.1f} MB")

        # Test the executable
        print("🔍 Testing standalone executable...")
        if not run_command(f'"{executable_path}" --help', "Testing --help command"):
            return False

        if not run_command(f'"{executable_path}" version', "Testing version command"):
            return False

        print("✅ Standalone executable works correctly!")
        print(f"\n📦 You can now distribute: {executable_path}")
        return True
    print(f"❌ Executable not found at: {executable_path}")
    return False


if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
