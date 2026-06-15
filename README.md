# HikariVehicle

<div align="center">

**A ground vehicle plugin for Minecraft Paper servers**

Turn minecarts into drivable ground vehicles.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-green.svg)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**English** | [简体中文](README_CN.md)

</div>

---

## Features

- **Ground Driving** — Drive minecarts on any solid surface, not just rails.
- **Fuel System** — Configurable fuel consumption; fuel is only burned while accelerating.
- **Durability** — Vehicles wear down over distance and are scrapped when worn out (cargo is still returned).
- **Speed-Scaled Collision** — Running into entities deals damage that scales with speed up to a configurable cap, with knockback.
- **Accurate Death Messages** — A custom "run over" message is shown only when the vehicle was the actual killing blow.
- **Hazard Handling** — Water ejects the driver; lava destroys the vehicle.
- **Light Exhaust Trail** — Subtle exhaust particles while driving.
- **Rail Hand-off** — Driving onto a rail hands the cart back to vanilla / rail-transit plugins with the rider still aboard.
- **Multi-language** — Simplified Chinese (`zh_cn`) and English (`en_us`).
- **Bedrock Compatible** — Works with Geyser/Floodgate for Bedrock players.

---

## Installation

1. Download the latest `HikariVehicle-x.x.x.jar` from [Releases](../../releases).
2. Place it in your server's `plugins` folder.
3. Start the server.
4. Adjust `plugins/HikariVehicle/config.yml`, then run `/hv reload`.

**Requirements**

- Paper (or a fork) 1.21.4 or later
- Java 21 or later

---

## Usage

### Placing a vehicle

Right-click any solid block with a minecart to place it on the ground (not just on rails).

### Driving

1. Right-click the minecart to enter.
2. **W** — Accelerate forward
3. **S** / **Sneak** — Brake
4. **A / D** — Turn assist
5. **Mouse** — Steering direction
6. **Sneak while stopped** — Exit the vehicle

> Bedrock players (via Geyser) auto-accelerate while not sneaking and brake by sneaking, since precise key input isn't reliably available.

### Fuel

Vehicles consume fuel items from your inventory **only while accelerating**. Defaults:

| Item | Burn Time |
|------|-----------|
| Coal / Charcoal | 120s |
| Coal Block | 1200s |
| Blaze Rod | 180s |
| Lava Bucket | 600s |
| Other burnables | 60s |

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/hv` | Show plugin version | — |
| `/hv reload` | Reload configuration | `hikarivehicle.admin` |

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `hikarivehicle.drive` | Drive vehicles | `true` |
| `hikarivehicle.place` | Place minecarts on the ground | `true` |
| `hikarivehicle.admin` | Admin commands (`/hv reload`) | `op` |

---

## Configuration

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

# Fuel system (only burns while accelerating)
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

# Collision settings (damage scales with speed)
collision:
  enabled: true
  damage: 1.0              # Base damage at min-speed (HP; 2 HP = 1 heart)
  max-damage: 4.0          # Cap reached at movement.max-speed (HP)
  min-speed: 3.0           # Minimum speed (m/s) to deal damage
  knockback: 0.5
  damage-cooldown: 20      # Ticks between damage to the same entity (20 = 1s)
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
    type: SMOKE            # Light trail; CAMPFIRE_SIGNAL_SMOKE is the heavy column
    count: 1
    only-when-accelerating: true
```

---

## Rail Compatibility

HikariVehicle is designed to coexist with rail-transit plugins:

- Minecarts on rails use vanilla / rail-plugin behavior.
- Minecarts on the ground use HikariVehicle driving.
- Driving onto a rail **hands the cart back to rail behavior with the rider still aboard** (you are no longer ejected). Re-mount on the ground to resume driving.

---

## Building

```bash
git clone https://github.com/HyacinthHaru/HikariVehicle.git
cd HikariVehicle
mvn clean package
```

The compiled JAR will be in `target/HikariVehicle-x.x.x.jar`.

---

## License

Licensed under the MIT License — see [LICENSE](LICENSE).

---

## Credits

Developed for the HikariCraft server.
