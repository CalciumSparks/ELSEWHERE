# Architecture diagram

Generated with **Archify 3.2.0** from the repository at revision
`ad891b93fa808c32b4c4f129e7d61a793e4bf471`.

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
- LSPatch / VirtualXposed are patch-or-virtualize routes, not the JDWP path;
  they are noted in the interactive cards and are not integrated as a runtime.
