# Xaero's Chunk Marks

[English](README_EN.md) | 简体中文

[![build](https://github.com/SupremeTheSnail/xaero-chunk-marks/actions/workflows/build.yml/badge.svg)](https://github.com/SupremeTheSnail/xaero-chunk-marks/actions/workflows/build.yml)
[![release](https://img.shields.io/github/v/release/SupremeTheSnail/xaero-chunk-marks?label=release)](https://github.com/SupremeTheSnail/xaero-chunk-marks/releases)
[![license](https://img.shields.io/github/license/SupremeTheSnail/xaero-chunk-marks)](LICENSE)

Xaero's World Map 的客户端附属模组。在世界地图的右键菜单中添加「标记区块」，被标记的区块在地图上以半透明色块显示。

标记只写入本地配置文件，不发送网络数据包，不声明区块，不强加载区块。

## 环境要求

| 依赖 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.x |
| Xaero's World Map | 1.39 及以上 |

## 安装

1. 安装 Forge 1.20.1。
2. 将 Xaero's World Map 放入 `mods/`。
3. 将 `xaerochunkmark-1.0.0.jar` 放入 `mods/`。

下载：[Releases](https://github.com/SupremeTheSnail/xaero-chunk-marks/releases) ·
[直链](https://github.com/SupremeTheSnail/xaero-chunk-marks/releases/download/v1.0.0/xaerochunkmark-1.0.0.jar)

## 使用

- 在世界地图上右键区块，菜单中出现「标记区块 (x, z)」。
- 按住右键拖动可框选多个区块，菜单变为「标记 N 个区块」。
- 对已标记的区块再次右键，菜单显示「取消标记」。
- 鼠标悬停在已标记区块上，显示「已标记区块 (x, z)」。

## 配置

配置文件为 `config/xaerochunkmark-client.toml`：

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `appearance.color` | `FF9D2E` | 标记颜色，RRGGBB |
| `appearance.fillOpacity` | `60` | 区块内部不透明度，0–255 |
| `appearance.borderOpacity` | `220` | 与未标记区块相邻处的不透明度，0–255 |

修改后地图自动重绘。

## 数据存储

```
config/xaerochunkmark/<世界标识>-<哈希>.json
```

按存档与维度分别保存，格式为 JSON：

```json
{
  "format": 1,
  "world": "Multiplayer_play.example.com|",
  "dimensions": {
    "minecraft:overworld": [[0, 0], [0, 1], [1, 1]]
  }
}
```

删除对应文件即清空该世界的标记。

## 实现

- 通过 `ChunkHighlighter` 接入 Xaero 的区块高亮，`coveringOutsideDiscovered = true`。
- 高亮器在 `ClientTickEvent` 中反射注册到 `MapProcessor#getHighlighterRegistry()`。
- 标记变化后调用 `clearCachedHash()`，触发相关 region 的高亮纹理重建。
- 右键菜单通过 Mixin 注入 `GuiMap#getRightClickOptions()`，区块坐标与选区从 `GuiMap` 私有字段读取。

不包含网络数据包与 `setChunkForced` 调用。

## 构建

需要 JDK 17 与网络。

```bash
./gradlew build
```

产物为 `build/libs/xaerochunkmark-1.0.0.jar`。

仓库不包含 Xaero 的 jar 文件。构建前按 [libs/README.md](libs/README.md) 将 Xaero's World Map 与 XaeroLib 放入 `libs/`；
GitHub Actions 会自动完成这一步并构建。

## 兼容性

- 注入点 `GuiMap#getRightClickOptions()Ljava/util/ArrayList;` 在 1.39.12 与 1.47.0 上一致。
- `mixins.json` 中 `injectors.defaultRequire = 0`：注入失败仅记录日志，不显示菜单项，不崩溃。
- 反射字段缺失时禁用菜单项并记录日志。
- 仅作用于世界地图，不影响 Xaero 小地图。

## 已知限制

- 地图上没有数据的区块，需等该区域有地图数据后才显示颜色。
- 标记保存在 `config/`，不受地图缓存清理影响；删除该目录会清空所有标记。

## AI 生成声明

本仓库的代码与文档由 AI 编程助手生成，工具为 DeepSeek Harness，模型 `deepseek-flash`，生成日期 2026-11-11。
生成范围、验证情况与未验证项见 [AI_DISCLOSURE.md](AI_DISCLOSURE.md)。

## 许可证

[MIT](LICENSE)。本项目与 Xaero 无隶属关系。
