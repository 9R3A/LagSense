# LagSense

[![GitHub release](https://img.shields.io/github/v/release/9r3a/LagSense?style=for-the-badge)](https://github.com/9r3a/LagSense/releases)
[![Spigot](https://img.shields.io/badge/Spigot-1.20.4-FFA500?style=for-the-badge&logo=java)](https://www.spigotmc.org/)
[![License](https://img.shields.io/github/license/9r3a/LagSense?style=for-the-badge)](LICENSE)

LagSense is a powerful Minecraft plugin that helps players identify and understand the source of their lag. It provides detailed analysis of both client and server performance metrics, helping to determine if lag is caused by the player's connection, their computer, or the server.

![LagSense in Action](https://i.imgur.com/placeholder.png)

## Features

- **Real-time Performance Metrics**:
  - Player ping and connection quality
  - Server TPS (Ticks Per Second)
  - Chunk load times
  - CPU and memory usage

- **Smart Analysis**:
  - Identifies if lag is client or server-side
  - Provides specific suggestions to improve performance
  - Color-coded metrics for easy understanding

- **User-Friendly**:
  - Simple `/lagsense` command
  - Clean, easy-to-read output
  - No configuration needed

## Commands

| Command | Description | Permission | Aliases |
|---------|-------------|------------|---------|
| `/lagsense` | Check your lag information | `lagsense.use` | `/ls`, `/lag` |

## Installation

1. Download the latest version from [Releases](https://github.com/9r3a/LagSense/releases)
2. Place the JAR file in your server's `plugins` folder
3. Restart your server

## Building from Source

1. Clone the repository
2. Run `mvn clean package`
3. Find the compiled JAR in the `target` directory

## Requirements

- Java 17 or higher
- Spigot/Paper 1.20.4 (other versions may work but are untested)

## Contributing

Contributions are welcome! Feel free to submit issues or pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For support, please open an issue or contact me on Discord: `itz_intellij` / `9R3A_`
