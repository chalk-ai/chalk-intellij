# Chalk IntelliJ Plugin

IntelliJ plugin for [Chalk](https://chalk.ai) development.

## Features

### SQL Resolver Navigation
- **Go-to-definition**: Cmd/Ctrl+Click on class names in `-- resolves: ClassName` comments to jump to the Python feature class definition
- **Syntax highlighting**: Class names in resolves comments are highlighted as clickable references
- **Validation**: Shows errors for `.chalk.sql` files missing required `-- source:` or `-- resolves:` comments
- **Quick fixes**: Automatically add missing comments with Alt+Enter

## Building

```bash
./gradlew buildPlugin
```

The plugin zip will be at `build/distributions/chalk-intellij-1.0.0.zip`

## Installation

### From JetBrains Marketplace

Search for "Chalk" in Settings → Plugins → Marketplace

### From Disk

1. Build: `./gradlew buildPlugin`
2. Settings → Plugins → ⚙️ → "Install Plugin from Disk..."
3. Select the zip from `build/distributions/`

## Development

```bash
./gradlew runIde
```

This launches a sandboxed IntelliJ instance with the plugin loaded.

## Usage

In any `.chalk.sql` file:

```sql
-- source: my_snowflake_source
-- resolves: UserRetailerAffinityScore
SELECT * FROM some_table
```

- Cmd/Ctrl+Click on `UserRetailerAffinityScore` to navigate to the Python class
- Missing comments show as errors with quick-fix support
- Only searches within Chalk project root (defined by `chalk.yaml`/`chalk.yml`)

## Requirements

- IntelliJ IDEA 2023.3+ (Community or Ultimate)
- JDK 17+ (for building)

## License

Apache 2.0 - see [LICENSE](LICENSE)
