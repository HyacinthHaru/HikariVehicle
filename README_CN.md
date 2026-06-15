# HikariVehicle

<div align="center">

**一个 Minecraft Paper 服务器的地面载具插件**

将矿车变为可驾驶的地面载具。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-green.svg)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[English](README.md) | **简体中文**

</div>

---

## 功能特性

- **地面驾驶** — 在任意固体方块表面驾驶矿车，不再局限于铁轨。
- **燃料系统** — 燃料种类与时长可配置；**仅在踩油门（加速）时消耗**燃料。
- **耐久系统** — 车辆随行驶距离磨损，耗尽后报废（报废时仍归还箱内货物）。
- **随速度缩放的碰撞** — 撞击实体造成的伤害随车速提升，直至可配置的上限，并带击退。
- **精准死亡提示** — 仅当车辆确实是致命一击时，才显示"开车撞死"的自定义提示。
- **危险处理** — 落水强制弹出驾驶员；进入岩浆销毁车辆。
- **轻量尾烟** — 驾驶时产生轻微的尾气粒子轨迹。
- **铁轨交接** — 驶上铁轨时，把矿车交回原版 / 轨道交通插件，并**保留乘客**（不再把人甩下车）。
- **多语言** — 支持简体中文（`zh_cn`）与英文（`en_us`）。
- **基岩版兼容** — 通过 Geyser/Floodgate 支持基岩版玩家。

---

## 安装

1. 从 [Releases](../../releases) 下载最新的 `HikariVehicle-x.x.x.jar`。
2. 放入服务器的 `plugins` 文件夹。
3. 启动服务器。
4. 按需调整 `plugins/HikariVehicle/config.yml`，然后执行 `/hv reload`。

**环境要求**

- Paper（或其分支）1.21.4 及以上
- Java 21 及以上

---

## 使用方法

### 放置载具

手持矿车右键点击任意固体方块，即可把它放在地面上（不再局限于铁轨）。

### 驾驶

1. 右键矿车进入驾驶。
2. **W** — 向前加速
3. **S** / **潜行** — 刹车
4. **A / D** — 辅助转向
5. **鼠标** — 控制方向
6. **停车时潜行** — 退出载具

> 基岩版玩家（经 Geyser）在不潜行时自动前进、潜行刹车——因为精确的按键输入无法可靠获取。

### 燃料

车辆**仅在加速时**消耗背包中的燃料物品。默认燃料：

| 物品 | 可驾驶时长 |
|------|-----------|
| 煤 / 木炭 | 120 秒 |
| 煤块 | 1200 秒 |
| 烈焰棒 | 180 秒 |
| 熔岩桶 | 600 秒 |
| 其它可燃物 | 60 秒 |

---

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/hv` | 显示插件版本 | — |
| `/hv reload` | 重新加载配置 | `hikarivehicle.admin` |

---

## 权限

| 权限节点 | 说明 | 默认 |
|----------|------|------|
| `hikarivehicle.drive` | 驾驶载具 | `true` |
| `hikarivehicle.place` | 在地面放置矿车 | `true` |
| `hikarivehicle.admin` | 管理命令（`/hv reload`） | `op` |

---

## 配置

```yaml
# 语言：zh_cn（简体中文）或 en_us（English）
language: zh_cn

# 移动设置
movement:
  max-speed: 10.0          # 最大速度（格/秒）
  acceleration: 2.0        # 加速度（格/秒²）
  coast-friction: 0.92     # 滑行时的摩擦
  brake-friction: 0.80     # 刹车时的摩擦
  min-speed: 0.1           # 最低速度阈值

# 转向设置
steering:
  max-turn-rate: 15.0      # 最大转向速率（度/tick）
  turn-damping: 0.3        # 随速度增加的转向阻尼
  key-turn-rate: 5.0       # A/D 按键转向增益

# 地形设置
terrain:
  step-height: 0.5         # 最大可爬升高度（0.5 = 台阶）

# 燃料系统（仅在加速时消耗）
fuel:
  enabled: true
  items:
    COAL: 120
    CHARCOAL: 120
    COAL_BLOCK: 1200
    BLAZE_ROD: 180
    LAVA_BUCKET: 600
  default-burn-time: 60

# 耐久系统
durability:
  enabled: true
  max-durability: 1000
  distance-per-durability: 10.0

# 碰撞设置（伤害随速度缩放）
collision:
  enabled: true
  damage: 1.0              # 最低速度时的基础伤害（HP；2 HP = 1 颗心）
  max-damage: 4.0          # 达到 movement.max-speed 时的伤害上限（HP）
  min-speed: 3.0           # 触发伤害的最低速度（米/秒）
  knockback: 0.5
  damage-cooldown: 20      # 对同一实体的伤害间隔（tick，20 = 1 秒）
  death-track-window: 100  # 死亡归因窗口（tick）

# 危险设置
hazards:
  water:
    eject-delay: 20        # 落水后弹出前的延迟（tick）
  lava:
    instant-destroy: true

# 视觉效果
effects:
  exhaust:
    enabled: true
    type: SMOKE            # 轻量轨迹；CAMPFIRE_SIGNAL_SMOKE 是浓烟柱
    count: 1
    only-when-accelerating: true
```

---

## 铁轨兼容性

HikariVehicle 在设计上与轨道交通插件共存：

- 铁轨上的矿车使用原版 / 铁路插件的行为。
- 地面上的矿车使用 HikariVehicle 的驾驶逻辑。
- 驶上铁轨时，会**把矿车交回铁路行为并保留乘客**（不再把你甩下车）。回到地面重新上车即可恢复驾驶。

---

## 构建

```bash
git clone https://github.com/HyacinthHaru/HikariVehicle.git
cd HikariVehicle
mvn clean package
```

构建产物位于 `target/HikariVehicle-x.x.x.jar`。

---

## 许可证

基于 MIT 许可证授权 —— 见 [LICENSE](LICENSE)。

---

## 致谢

为 HikariCraft 光服开发。
