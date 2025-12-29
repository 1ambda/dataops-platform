# -*- mode: python ; coding: utf-8 -*-

import os

# Get the directory where the spec file is located
spec_dir = os.path.dirname(os.path.abspath(SPEC))

a = Analysis(
    [os.path.join(spec_dir, 'src', 'dli', 'main.py')],
    pathex=[os.path.join(spec_dir, 'src')],
    binaries=[],
    datas=[],
    hiddenimports=['typer', 'rich', 'click'],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='dli',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
