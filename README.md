# LauncherTV

Apple TV 风格的 Android TV 桌面，当前实现提取自 [CleanYourTV](https://github.com/Gathin23/cleanyourtv) 的 `launcher/app` 模块（来源提交 `f63a1ebcdcd63aae1ec1b6f62aa4d2a6858c116b`）。

## 当前范围

- 仅替换桌面实现和对应资源。
- 默认包名：`com.gathin.tvhome`。
- 当前上游桌面 `minSdk` 为 26（Android 8.0），本次没有做 Android 4.4/API 19 适配。

## 构建

```sh
./gradlew :launcher:assembleRelease
```

也可以通过 Gradle 属性覆盖包名：

```sh
./gradlew :launcher:assembleRelease -PlauncherApplicationId=com.example.launchertv
```

## 来源与授权

- CleanYourTV launcher：MIT，见 [launcher/LICENSE-MIT](launcher/LICENSE-MIT)。
- Outfit 字体：SIL Open Font License 1.1，见 [launcher/FONT-LICENSE-OFL.txt](launcher/FONT-LICENSE-OFL.txt)。
- Fluent Emoji 图片：MIT，见 [launcher/ICONS-LICENSE-FLUENT-EMOJI.txt](launcher/ICONS-LICENSE-FLUENT-EMOJI.txt)。
