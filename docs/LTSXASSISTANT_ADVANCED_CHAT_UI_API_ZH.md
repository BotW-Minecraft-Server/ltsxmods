# LTSXAssistant Advanced Chat UI API (ZH)

## 1. 概览
当前高级聊天窗口（`LtsxChatScreen`）采用固定两块 Fizzy 布局：

- 内容区 `pad`：显示当前激活页面（Page）的 element 集合。
- 按钮区 `pad`：显示按钮（Tab）行。

按钮切换即页面切换。页面支持一个或多个 element，均由 API 动态注册。

## 2. 关键接口

### 2.1 Service 入口
`AdvancedChatWindowService#uiRegistry()` 返回可变 UI 注册表：

- `upsertButton/removeButton/getButton/listButtons`
- `upsertPage/removePage/getPage/listPages`
- `setActivePageId/activePageId`
- `version`（结构变化版本号）

### 2.2 Button 定义
`ChatButtonDefinition` 支持：

- `id`：按钮唯一标识
- `label`：显示文本
- `targetPageId`：点击后切换到页面（可空）
- `order`：排序值（升序）
- `style`：`ChatButtonStyle`（边框/填充/文本色）
- `visibleWhen`：可见性判定
- `onPress`：点击回调（context lambda）

### 2.3 Page 定义
`ChatPageDefinition` 支持：

- `id`：页面唯一标识
- `elements`：`ChatPageElementDefinition` 列表
- `fill(...)`：元素填满内容区
- `byPx(...)`：元素按像素布局

`ChatPageElementFactory` 是 element 工厂。每次 Screen 生命周期会创建新实例，避免跨屏状态污染。

### 2.4 点击上下文
`ChatButtonActionContext` 提供：

- `registry()`：直接增删改查按钮/页面
- `setActivePageId(...)`：切页
- `openScreen(...)`：打开新 Screen
- `openFile(Path)`：打开文件
- `openUri(String)`：打开链接
- `closeScreen()`：关闭当前界面

## 3. 默认内置页与按钮
默认实现 `AssistantAdvancedChatWindowService` 在初始化时注册：

- 页面：`chat`、`group`、`agent`、`admin`
- 按钮：`Chat`、`Group`、`Agent`、`Admin`

其中：

- `chat` 页使用 `ShiftedGlobalChatElement`。
- `admin` 按钮可见性由 LuckPerms 节点 `ltsxassistant.chat.admin` 控制。

## 4. 示例：注册一个新页面和按钮

```java
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.ltsxassistant.api.chat.*;
import link.botwmcs.ltsxassistant.client.elements.DarkPanelElement;
import net.minecraft.network.chat.Component;

AdvancedChatWindowService service = CoreServices.getRequired(AdvancedChatWindowService.class);
AdvancedChatUiRegistry registry = service.uiRegistry();

// 1) 新增页面（一个页面可多个 element）
registry.upsertPage(
        ChatPageDefinition.builder("files")
                .fill(() -> new DarkPanelElement(0xE5161A23, 0xCC10141C))
                .fill(() -> new link.botwmcs.fizzy.ui.element.component.FizzyComponentElement.Builder()
                        .addText(Component.literal("File Tools"))
                        .addText(Component.literal("Click tab button to open log folder"))
                        .align(link.botwmcs.fizzy.client.util.TextRenderer.Align.CENTER)
                        .wrap(true)
                        .shadow(true)
                        .color(0xFFE7ECFF)
                        .clipToPad(true)
                        .allowOverflow(false)
                        .build())
                .build()
);

// 2) 新增按钮（切页 + 自定义动作）
registry.upsertButton(
        ChatButtonDefinition.builder("files_tab", Component.literal("Files"))
                .targetPageId("files")
                .order(50)
                .style(new ChatButtonStyle(0xFF607084, 0xCC243242, 0xFFEAF4FF, 0xFF98A9BC))
                .visibleWhen(ctx -> true)
                .onPress(ctx -> {
                    // 可选动作：打开目录
                    ctx.openFile(java.nio.file.Paths.get("logs"));
                })
                .build()
);
```

## 5. 示例：运行时删改

```java
AdvancedChatUiRegistry registry = service.uiRegistry();

// 改按钮颜色与文案
registry.getButton("files_tab").ifPresent(old ->
        registry.upsertButton(
                ChatButtonDefinition.builder(old.id(), Component.literal("Tools"))
                        .targetPageId(old.targetPageId())
                        .order(old.order())
                        .style(new ChatButtonStyle(0xFF705D4D, 0xCC3D2A1F, 0xFFFFF1E6, 0xFFBEA696))
                        .visibleWhen(old.visiblePredicate())
                        .onPress(old.onPress())
                        .build()
        )
);

// 删页面 + 删按钮
registry.removeButton("files_tab");
registry.removePage("files");
```

## 6. 运行时行为说明

- `LtsxChatScreen` 会跟踪 `registry.version()`。
- 当页面/按钮结构发生变化（`upsert/remove`）时，界面会自动重建一次以刷新布局。
- 切页（`setActivePageId`）本身不增加版本号，走内容区实时切换。

## 7. 设计约束与建议

- 页面内 element 尽量通过工厂创建，不要复用单例 element。
- widget 型 element 会按页面激活状态切换 `visible/active`。
- 若按钮仅承担切页，不写 `onPress` 也可；需要动作时再附加 lambda。
- 建议 `id` 使用稳定、可读、不会冲突的命名（如 `modid_feature_tab`）。

## 8. 相关源码

- `AdvancedChatWindowService`
- `AdvancedChatUiRegistry`
- `ChatButtonDefinition`
- `ChatPageDefinition`
- `SwappableContentElement`
- `ShiftedGlobalChatElement`
- `LtsxChatScreen`
