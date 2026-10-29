# Architecture diagram

Generated with **Archify 3.2.0** from the repository at revision
`ff70aa9770ec7e9a4df93f95a602d0cc386ea78f`.

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
- the external Frida environment, which needs root + frida-server or a Gadget
  build and is not an in-app automatic injector.
