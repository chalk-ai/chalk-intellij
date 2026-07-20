# Chalk for JetBrains IDEs

Chalk language support for IntelliJ IDEA, PyCharm, DataSpell, and other commercial IntelliJ-based IDEs using JetBrains' native Language Server Protocol API.

## Compatibility

The plugin requires a 2025.3 or newer IDE with `com.intellij.modules.lsp`. Unified PyCharm is supported. Android Studio and open-source IntelliJ Platform builds do not expose JetBrains' LSP API.

## Features

- Python, `.chalk.sql`, and Chalk configuration language intelligence through the downloaded `chalk-lsp` server
- One server per root containing `chalk.yml` or `chalk.yaml`
- Managed, checksum-verified language-server installation
- Root-scoped suppression of JetBrains Python type inspections

`chalk-lsp` performs Chalk's semantic analysis and typechecking, SQL resolver support, and configuration diagnostics. Those features and their fixes ship independently of plugin releases, so keeping the server current matters. The latest server installs on the first Chalk file open. After that, the plugin does not look up releases, update automatically, or prompt you; the plugin and server have independent versions.

The installed `chalk-lsp` version is always shown under **Settings → Languages & Frameworks → Chalk**. To update manually, choose **Tools → Update Chalk Language Server**, or open that settings page and click **Update language server**. The update downloads and verifies the latest release, replaces the server, and restarts it. A failed lookup, download, checksum, extraction, metadata write, or replacement preserves the existing server and its installed-version metadata.

`chalk-lsp` replaces other Python semantic/typechecking servers in Chalk projects. New Chalk projects include `pyproject.toml` rules for Pyright, basedpyright, mypy, and ty. Third-party plugins that expose no per-root disable API must be disabled using that plugin's project settings.

## Development

The build requires JDK 21 and Gradle 8.14.5:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
gradle wrapper --gradle-version 8.14.5
./gradlew buildPlugin
```

The project intentionally uses IntelliJ Platform Gradle Plugin 2.11.0, which supports IntelliJ 2025.3 without the Gradle 9 minimum introduced in plugin 2.12. The first command regenerates the checked-in wrapper before the plugin build.
The build resolves the latest `PythonCore` plugin compatible with the target IDE from JetBrains Marketplace because Python support is no longer bundled with IntelliJ IDEA.

The package is written to `build/distributions/`. `just publish <version>` runs the same Gradle publication task locally with an explicit non-placeholder version.

## Marketplace publication

The distribution embeds the Chalk plugin logo, homepage, vendor contact, description, and release notes required for its IDE and Marketplace listing. For the first JetBrains Marketplace upload, select Chalk's vendor profile and provide the Marketplace-only fields: the MIT license, the required developer EULA, and the public source URL `https://github.com/chalk-ai/chalk-intellij`.

Run `./release.sh` (or `just release`) and select a semantic-version bump. The shared Chalk release script creates the next GitHub release and bare semantic-version tag; the tag triggers the publication workflow, which uses that tag as the plugin version. It builds, signs, and publishes the plugin to JetBrains Marketplace. Configure the `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, and `PRIVATE_KEY_PASSWORD` repository secrets before releasing. The certificate chain and private key may be stored as raw multiline secrets or Base64-encoded values accepted by the IntelliJ Platform Gradle plugin.

For protocol logs, enable `#com.intellij.platform.lsp` under **Help → Diagnostic Tools → Debug Log Settings** or use the Chalk settings page to open `idea.log`.
