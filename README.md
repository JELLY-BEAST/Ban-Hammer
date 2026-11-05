# Ban Hammer — Minecraft Java 26.2 / Fabric

## Build requirements
- JDK 25 (not just a JRE)
- Gradle wrapper 9.5.1
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric Loom 1.17 (`net.fabricmc.fabric-loom`)
- Fabric API 0.161.0+26.2

Build with:
- Windows: `gradlew.bat build`
- Linux/macOS: `./gradlew build`

The jar is created in `build/libs/`.

## Install
Put the built `banhammer-1.0.0.jar` into the server's `mods` folder alongside Fabric API.
Restart the server. The mod creates:
- `config/banhammer-whitelist.json`
- `config/banhammer-bans.json`

Edit `banhammer-whitelist.json` and replace the example UUID with your Minecraft account UUID. Restart the server after editing.

## Commands
- `/BanHammer` — issue the marked mace (whitelisted admins only)
- `/BanHammer Discard` — remove marked Ban Hammers from your inventory
- `/BanHammer reason <reason>`
- `/BanHammer duration <seconds|minutes|hours|days|weeks|years> <amount>`
- `/BanHammer duration permanent`

Then sneak-right-click the hammer to see the setup instructions, configure the reason and duration with commands, and hit the target player.

## Important limitations in this initial implementation
This is a server-side first implementation: ordinary players do not need the mod installed.
A truly custom graphical form with free-text input is not available to an unmodified vanilla client through a server-only Fabric mod. This build uses commands/chat instructions instead.
The item is marked with server-side custom data. A server-only mod cannot prevent every possible client-side inventory manipulation or guarantee that the item can never be dropped; the attack and command authorization checks remain server-side.
The ban database is persistent and temporary bans expire automatically on the next join check. The current implementation stores bans in its own JSON file rather than Minecraft's vanilla ban list.
