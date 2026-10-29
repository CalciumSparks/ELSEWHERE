# Architecture diagram

Generated with **Archify 3.2.0** from the repository at revision
`5f367ce883203ff5af15645f8b970d186378cf10`.

- Interactive artifact: [`elsewhere-architecture.html`](elsewhere-architecture.html)
- README image: [`elsewhere-architecture.png`](elsewhere-architecture.png)
- Source specification: [`elsewhere-architecture.spec.json`](elsewhere-architecture.spec.json)
- Repository origin: `git@github.com:CalciumSparks/ELSEWHERE.git` (SSH; Archify source links are local-only)

Regenerate from the repository root:

```bash
node <archify-package>/bin/archify.mjs finalize architecture \
  docs/architecture/elsewhere-architecture.spec.json \
  docs/architecture/elsewhere-architecture.html \
  --repo-root . --quality showcase
```

The spec is the source of truth for nodes, sources, connections and the external
injection boundary. The HTML is self-contained; the PNG is cropped from an
embed-mode screenshot of that HTML.

The diagram deliberately separates:

- the no-root location path (`MockLocationService` → test providers);
- route recording (`RouteRecorder`);
- the Shizuku shell/AppOps channel;
- the external Frida runtime, with three distinct paths:
  - **JDWP** through `frida-jdwp-loader` (no root, debuggable targets only);
  - a **root frida-server** (any target);
  - a **Frida Gadget** repackaged into the target.
- the **LSPatch / VirtualXposed** route: LSPatch patches the target and embeds
  the `Sensor + Wireless Module` (API 82), or VirtualXposed runs both in its
  virtual space. This is a separate patch/virtualization route, not JDWP.
- the module hooks motion sensors plus Java Wi-Fi (`WifiInfo`/scan),
  `BluetoothAdapter`, and telephony (`TelephonyManager`/`CellIdentityLte`).
- `Settings.Global.elsewhere_sensor_config` supplies the module config; the app
  exports the module APK and writes/copies sensors + environment groups.
