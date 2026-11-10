# libs/ — compile-time dependencies (not shipped)

Xaero's mods are **not open source**, so their jars are deliberately not committed to this repository.
`build.gradle` simply puts *everything* in this folder on the compile classpath:

```groovy
compileOnly fileTree(dir: 'libs', include: ['*.jar'])
```

To build from source, drop these two jars here (any file name, as long as it ends with `.jar`):

1. **Xaero's World Map** for 1.20.1 / Forge — download it from CurseForge or Modrinth,
   e.g. `xaeroworldmap-forge-1.20.1-1.47.0.jar`.
2. **XaeroLib** — it is embedded inside the world map jar as a JarJar dependency, extract it:

```bash
# Linux / macOS / Git Bash
unzip -p libs/xaeroworldmap-forge-1.20.1-1.47.0.jar \
      META-INF/jarjar/xaerolib-forge-1.20.1-1.7.3.jar > libs/xaerolib.jar
```

```powershell
# Windows PowerShell (no need to unzip the whole jar)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = [System.IO.Compression.ZipFile]::OpenRead("libs\xaeroworldmap-forge-1.20.1-1.47.0.jar")
$entry = $jar.Entries | Where-Object { $_.FullName -like 'META-INF/jarjar/xaerolib-*.jar' }
[System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, "libs\xaerolib.jar", $true)
$jar.Dispose()
```

XaeroLib is only needed because 1.44+ world map builds its GUI on top of it and `GuiMap`'s superclass
lives there. At runtime both are provided by the world map mod itself, so neither jar ends up in the built mod.

The `build` workflow in `.github/workflows/build.yml` does exactly the above automatically
(it downloads the world map from Modrinth's CDN, extracts XaeroLib, then builds).
