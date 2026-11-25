"""Check 64-bit ELF LOAD/RELRO alignment in an Android APK. ZIP alignment is checked separately with zipalign."""
import struct
import sys
import zipfile

failures = []
count = 0
with zipfile.ZipFile(sys.argv[1]) as apk:
    for name in apk.namelist():
        if not name.startswith(("lib/arm64-v8a/", "lib/x86_64/")) or not name.endswith(".so"):
            continue
        count += 1
        data = apk.read(name)
        assert data[:6] == b"\x7fELF\x02\x01", name
        phoff = struct.unpack_from("<Q", data, 32)[0]
        phsize, phnum = struct.unpack_from("<HH", data, 54)
        headers = [struct.unpack_from("<IIQQQQQQ", data, phoff + i * phsize) for i in range(phnum)]
        loads = [p for p in headers if p[0] == 1]
        errors = []
        for p in loads:
            if p[7] < 16384 or (p[3] - p[2]) % 16384:
                errors.append("LOAD not aligned to 16 KB")
        for p in headers:
            if p[0] == 0x6474E552:
                end = p[3] + p[6]
                if end % 16384 and not any(q[3] <= p[3] and q[3] + q[6] == end for q in loads):
                    errors.append("RELRO shares a trailing page with writable data")
        print(f"{'FAIL' if errors else 'PASS'} {name}: {', '.join(errors) if errors else 'LOAD and RELRO compatible'}")
        failures.extend(errors)
print(f"Checked {count} native 64-bit libraries; {len(failures)} failures")
sys.exit(1 if failures else 0)
