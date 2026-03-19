# HikariVehicle

<div align="center">

**A ground vehicle plugin for Minecraft Paper servers**

将矿车变为可驾驶的地面载具

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-green.svg)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

---

## Features | 特性

- **Ground Driving** - Drive minecarts on any solid surface, not just rails
- **Fuel System** - Configurable fuel consumption with multiple fuel types
- **Durability** - Vehicles degrade over distance and can be destroyed
- **Collision Damage** - Running into entities deals damage with knockback
- **Death Messages** - Custom death messages when players are killed by vehicles
- **Hazard Handling** - Water ejection and lava destruction
- **Visual Effects** - Exhaust particles while driving
- **Multi-language** - Support for Chinese (zh_cn) and English (en_us)
- **Bedrock Compatible** - Works with Geyser/Floodgate for Bedrock players

---

## Installation | 安装

1. Download the latest release from [Releases](../../releases)
2. Place `HikariVehicle-x.x.x.jar` in your server's `plugins` folder
3. Start the server
4. Configure `config.yml` as needed

**Requirements:**
- Paper 1.21.4 or later
- Java 21 or later

---

## Usage | 使用方法

### Placing a Vehicle | 放置载具

Right-click on any solid block with a minecart to place it on the ground (not just rails).

右键点击任意固体方块放置矿车（不再局限于铁轨）。

### Driving | 驾驶

1. Right-click the minecart to enter
2. **W** - Accelerate forward
3. **S** / **Sneak** - Brake
4. **A/D** - Turn assist
5. **Mouse** - Steering direction
6. **Sneak while stopped** - Exit vehicle

### Fuel | 燃料

Vehicles consume fuel items from your inventory. Default fuels:

| Item | Burn Time |
|------|-----------|
| Coal / Charcoal | 120s |
| Coal Block | 1200s |
| Blaze Rod | 180s |
| Lava Bucket | 600s |
| Other burnables | 60s |

---

## Commands | 命令

| Command | Description | Permission |
|---------|-------------|------------|
| `/hv` | Show plugin version | - |
| `/hv reload` | Reload configuration | `hikarivehicle.admin` |

---

## Permissions | 权限

| Permission | Description | Default |
|------------|-------------|---------|
| `hikarivehicle.drive` | Drive vehicles | `true` |
| `hikarivehicle.admin` | Admin commands | `op` |

---

## Configuration | 配置

```yaml
# Language: zh_cn (简体中文) or en_us (English)
language: zh_cn

# Movement settings
movement:
  max-speed: 10.0          # Maximum speed (blocks/second)
  acceleration: 2.0        # Acceleration (blocks/second²)
  coast-friction: 0.92     # Friction when coasting
  brake-friction: 0.80     # Friction when braking
  min-speed: 0.1           # Minimum speed threshold

# Steering settings
steering:
  max-turn-rate: 15.0      # Max turn rate (degrees/tick)
  turn-damping: 0.3        # Speed-dependent turn damping
  key-turn-rate: 5.0       # A/D key turning boost

# Terrain settings
terrain:
  step-height: 0.5         # Max climbable height (0.5 = slabs)

# Fuel system
fuel:
  enabled: true
  items:
    COAL: 120
    CHARCOAL: 120
    COAL_BLOCK: 1200
    BLAZE_ROD: 180
    LAVA_BUCKET: 600
  default-burn-time: 60

# Durability system
durability:
  enabled: true
  max-durability: 1000
  distance-per-durability: 10.0

# Collision settings
collision:
  enabled: true
  damage: 1.0
  min-speed: 3.0
  knockback: 0.5
  damage-cooldown: 20      # Ticks between damage (20 = 1 second)
  death-track-window: 100  # Death attribution window (ticks)

# Hazard settings
hazards:
  water:
    eject-delay: 20        # Ticks before water ejection
  lava:
    instant-destroy: true

# Visual effects
effects:
  exhaust:
    enabled: true
    type: CAMPFIRE_SIGNAL_SMOKE
    count: 1
    only-when-accelerating: true
```

---

## Rail Compatibility | 铁轨兼容性

HikariVehicle is designed to coexist with rail transit plugins:

- Minecarts placed on rails use vanilla/plugin rail behavior
- Minecarts placed on ground use HikariVehicle driving
- If a driving vehicle enters a rail, it automatically exits driving mode

---

## Building | 构建

```bash
git clone https://github.com/your-repo/HikariVehicle.git
cd HikariVehicle
mvn clean package
```

The compiled JAR will be in `target/HikariVehicle-x.x.x.jar`.

---

## License | 许可证

This project is licensed under the MIT License.

---

## Credits | 致谢

Developed for HikariCraft server.
