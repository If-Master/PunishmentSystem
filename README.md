# PunishmentSystem

A comprehensive Minecraft punishment management plugin with GUI interface, Discord integration, and ban evasion detection.

## Features

- **Intuitive GUI Interface** - Easy-to-use graphical interface for managing punishments
- **Multiple Punishment Types** - Ban, tempban, mute, kick, and unban capabilities
- **Discord Integration** - Real-time punishment notifications sent to Discord channels
- **Ban Evasion Detection** - Automatically detects and alerts staff to potential ban evaders
- **Punishment History** - Track and view complete punishment history for any player
- **Essentials Integration** - Seamless integration with EssentialsX
- **Folia Support** - Compatible with Folia server software
- **Encrypted Data Storage** - Secure storage of punishment data with AES encryption
- **Customizable Reasons** - Pre-configured punishment reasons with custom durations
- **Bedrock Player Support** - Handles Bedrock Edition players with custom prefixes
- **Auto-Update System** - Built-in update checker and downloader

## Requirements

- **Minecraft Version**: 1.21+
- **Dependencies**: EssentialsX
- **Java Version**: 11+
- **Server Software**: Paper, Spigot, or Folia

## Installation

1. Download the latest release from the [Releases](../../releases) page
2. Place the `.jar` file in your server's `plugins` folder
3. Ensure EssentialsX is installed and running
4. Start/restart your server
5. Configure the plugin using the generated `config.yml`

## Configuration

### Basic Setup

```yaml
bedrock-prefix: "." # Prefix for Bedrock Edition players
Server-name: "My Server" # Server name displayed in Discord notifications

debug:
  active: false # Enable debug information
```

### Discord Integration

To enable Discord notifications:

1. Create a Discord bot at [Discord Developer Portal](https://discord.com/developers/applications)
2. Get your bot token, channel ID, and bot ID
3. Configure the discord section in `config.yml`:

```yaml
discord:
  bot-token: "YOUR_BOT_TOKEN_HERE"
  channel-id: "YOUR_CHANNEL_ID_HERE"
  bot-id: "YOUR_BOT_ID_HERE"
  active: true  # Set to true to enable Discord integration
  custom-status: "Punishment Logger 3000"
  debug-logging: false # Enable for debugging
```
### Customizing Punishment Reasons

Edit the `Punishment-Reasons` list to add or remove available punishment reasons:

```yaml
Punishment-Reasons:
  - "Fly Hacks"
  - "Speed Hacks"
  - "Griefing"
  # Add your custom reasons here
```

### Duration Configuration

Set custom durations for each punishment reason:

```yaml
ban-reason-durations:
  Fly_Hacks: 7d
  Speed_Hacks: 5d
  Griefing: 7d

mute-reason-durations:
  Spam: 30m
  Inappropriate_Behavior: 6h
  Advertising: 1h
```

**Duration Format:**
- `m` = minutes
- `h` = hours  
- `d` = days
- `w` = weeks
- `months` = months
- `y` = years

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/punish` | Opens the main punishment GUI | `punishmentsystem.use` |
| `/punish history <player>` | View punishment history | `punishmentsystem.history` |
| `/punish reload` | Reload configuration | `punishmentsystem.reload` |
| `/punish reloaddata` | Reload punishment data | `punishmentsystem.reload` |
| `/punish debug` | Show debug information | `punishmentsystem.debug` |
| `/punish discord test` | Test Discord connection | `punishmentsystem.debug` |
| `/punish discord status` | Check Discord status | `punishmentsystem.debug` |
| `/punish version` | Check plugin version | `punishmentsystem.admin` |
| `/punish download` | Download plugin updates | `punishmentsystem.admin` |
| `/punish help` | Show help menu | `punishmentsystem.use` |

**Aliases:** `bgui`, `banguio`
### Discord Usage
```
• `history <player>` - View punishment history
• `lookup <player>` - Quick player lookup
• `check <player>` - Check if player is online
• `debug` - Show debug information

**Example:** `history Notch`
```
## Permissions

### Basic Permissions
- `punishmentsystem.use` - Access to punishment GUI
- `punishmentsystem.ban` - Permission to ban players
- `punishmentsystem.tempban` - Permission to temporarily ban players
- `punishmentsystem.mute` - Permission to mute players
- `punishmentsystem.kick` - Permission to kick players
- `punishmentsystem.unban` - Permission to unban players

### Administrative Permissions
- `punishmentsystem.admin` - Full access (includes all permissions)
- `punishmentsystem.reload` - Reload configuration
- `punishmentsystem.debug` - Debug commands
- `punishmentsystem.history` - View punishment history
- `punishmentsystem.warn` - Receive ban evasion alerts
- `punishmentsystem.warnbypass` - Bypass ban evasion detection

## Features in Detail

### Ban Evasion Detection
The plugin automatically detects potential ban evaders by:
- Tracking player IP addresses
- Comparing IPs of banned players with new joiners
- Alerting staff members with appropriate permissions
- Logging evasion attempts for review

### Discord Integration
When enabled, the plugin sends rich embeds to Discord containing:
- Punishment type and reason
- Staff member who issued the punishment
- Duration and timestamp
- Player information
- Server name

### Punishment History
- Complete punishment history for all players
- Paginated GUI for easy browsing
- Search and filter capabilities
- Export options for administrative review

### Data Security
- All punishment data is encrypted using AES-256
- Secure key generation and storage
- Protection against data tampering
- Automatic backup creation

## Support

For support, bug reports, or feature requests:
- Create an issue on this repository
- Contact the developer: Kanuunankuula [If_Master] | Discord: @If_master | https://discord.gg/ZhYuUtbRNp

## License

This project is licensed under the GNU General Public License V3 - see the LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Changelog

### Version 1.0.2
- Basic punishment GUI functionality
- Discord integration
- Ban evasion detection
- Essentials integration
- Folia support

---

**Author:** Kanuunankuula [If_Master]  
**Version:** 1.0.2  
**Minecraft Version:** 1.21+
