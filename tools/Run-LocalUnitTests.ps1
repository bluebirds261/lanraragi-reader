param([string]$JavaHome = 'C:\Users\辰宿列张\.jdks\jbr-21.0.11')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    $env:JAVA_HOME = $JavaHome
    New-Item -ItemType Directory -Force .verify | Out-Null
    # Some Windows Gradle worker launchers misdecode non-ASCII user-cache paths.
    # Export the actual Gradle runtime and run the same JUnit classes via a UTF-8 manifest.
    @'
allprojects {
    afterEvaluate { p ->
        if (p.path == ':app') {
            p.tasks.register('exportLocalTestClasspath') {
                dependsOn p.tasks.named('compileDebugUnitTestKotlin'), p.tasks.named('bundleDebugClassesToRuntimeJar')
                doLast {
                    def test = p.tasks.named('testDebugUnitTest').get()
                    new File(p.rootDir, '.verify/local-test-classpath.txt').text = test.classpath.asPath
                    new File(p.rootDir, '.verify/local-test-dirs.txt').text = test.testClassesDirs.asPath
                }
            }
        }
    }
}
'@ | Set-Content .verify/local-test-init.gradle -Encoding utf8
    & .\gradlew.bat -I .verify/local-test-init.gradle :app:exportLocalTestClasspath --no-configuration-cache --max-workers=2
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
    @'
from pathlib import Path
import os, subprocess, sys, zipfile
v = Path('.verify')
paths = (v / 'local-test-classpath.txt').read_text(encoding='utf-8-sig').strip().split(';')
urls = [Path(p).as_uri() + ('/' if Path(p).is_dir() else '') for p in paths]
line = 'Class-Path: ' + ' '.join(urls)
manifest = 'Manifest-Version: 1.0\r\n' + line[:70] + '\r\n'
line = line[70:]
while line:
    manifest += ' ' + line[:69] + '\r\n'
    line = line[69:]
manifest += '\r\n'
jar = v / 'local-test-classpath.jar'
with zipfile.ZipFile(jar, 'w') as z:
    z.writestr('META-INF/MANIFEST.MF', manifest)
classes = []
for directory in (v / 'local-test-dirs.txt').read_text(encoding='utf-8-sig').strip().split(';'):
    root = Path(directory)
    if root.exists():
        classes += ['.'.join(f.relative_to(root).with_suffix('').parts)
                    for f in root.rglob('*Test.class') if '$' not in f.name]
result = subprocess.run([str(Path(os.environ['JAVA_HOME']) / 'bin/java.exe'), '-Dfile.encoding=UTF-8',
                         '-cp', str(jar.resolve()), 'org.junit.runner.JUnitCore', *sorted(set(classes))],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
(v / 'local-junit.log').write_bytes(result.stdout)
print(result.stdout.decode('utf-8', errors='replace'))
sys.exit(result.returncode)
'@ | python -
    if ($LASTEXITCODE -ne 0) { throw 'JUnit tests failed; see .verify/local-junit.log' }
} finally {
    Pop-Location
}
