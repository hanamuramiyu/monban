# Third-party notices

monban bundles and relocates the following third-party libraries inside its distribution
JARs (`monban-bukkit-spigot`, `monban-paper-folia`, `monban-velocity`). The original
package namespaces are relocated under `hanamuramiyu.monban.internal.libs.*`.

This file is included in every distribution JAR as `META-INF/THIRD_PARTY_NOTICES.md`.

## bStats (MIT)

Bundled as `org.bstats:bstats-bukkit` and `org.bstats:bstats-velocity` (and their
`bstats-base` dependency), relocated to `hanamuramiyu.monban.internal.libs.bstats`.

Project: <https://bstats.org> — source: <https://github.com/Bastian/bStats-metrics>

```text
MIT License

Copyright (c) 2021 Bastian Oppermann

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Apache-2.0 libraries

The libraries below are licensed under the Apache License, Version 2.0.
Full license text: <https://www.apache.org/licenses/LICENSE-2.0>.

| Library | Version | Bundled as | License |
| --- | --- | --- | --- |
| SnakeYAML | 2.6 | `org.yaml:snakeyaml`, relocated to `hanamuramiyu.monban.internal.libs.snakeyaml` | Apache-2.0 (<https://bitbucket.org/snakeyaml/snakeyaml> / <https://www.apache.org/licenses/LICENSE-2.0>) |
| Adventure | 4.17.0 | `net.kyori:adventure-api`, `net.kyori:adventure-text-serializer-gson`, relocated to `hanamuramiyu.monban.internal.libs.adventure` | Apache-2.0 (<https://github.com/KyoriPowered/adventure> / <https://www.apache.org/licenses/LICENSE-2.0>) |
| Gson | 2.8.0 (transitive) | `com.google.code.gson:gson`, bundled via Adventure, relocated to `hanamuramiyu.monban.internal.libs.gson` | Apache-2.0 (<https://github.com/google/gson> / <https://www.apache.org/licenses/LICENSE-2.0>) |
