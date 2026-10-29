# Xposed API 82 (compile-only)

`xposed-sensor` compiles against the classic Xposed API 82 JAR from
https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar

The JAR is used with `compileOnly`; no Xposed API classes are bundled into the
module APK. At runtime LSPatch/Vector provides the implementation.

The module source itself is MIT-licensed with the rest of ELSEWHERE.
