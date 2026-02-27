# ltsxcore（NeoForge 1.21.1）Core + Modules 架构说明

本项目是多模组体系中的**核心模组**（`modId = ltsxcore`），采用 **Core + 独立模块 jar** 的组织方式，默认服务器优先（Server-first）。

## 1. core 提供了什么

`ltsxcore` 统一提供以下基础设施：
- 模块发现与加载顺序管理（`ServiceLoader` + `ModuleManager`）
- 服务注册中心（`CoreServices`）
- 网络层（`CoreNetwork`）
- 配置（`CoreConfig`）
- 数据存储适配（`CoreData` / `CoreGlobalSavedData`）
- 事件桥（`CoreEvents`）
- 核心命令（`/ltsxcore ...`）

## 2. 模块如何挂到 core 上

每个业务模块是**独立 jar、独立 modId**，并通过 `ICoreModule` 接入 core。

### 步骤 1：依赖 core API

模块开发时至少依赖这些类：
- `link.botwmcs.core.api.module.ICoreModule`
- `link.botwmcs.core.api.module.CoreModuleContext`
- `link.botwmcs.core.service.CoreServices`

### 步骤 2：实现 `ICoreModule`

```java
package com.example.ltsx.industry;

import link.botwmcs.core.api.module.CoreModuleContext;
import link.botwmcs.core.api.module.ICoreModule;

public final class IndustryCoreModule implements ICoreModule {
    @Override
    public String moduleId() {
        return "ltsxindustry";
    }

    @Override
    public int loadOrder() {
        return 100;
    }

    @Override
    public void onRegister(CoreModuleContext ctx) {
        // 在这里注册服务、事件监听、网络包等
    }
}
```

### 步骤 3：写 ServiceLoader 文件

模块资源中新增：

`src/main/resources/META-INF/services/link.botwmcs.core.api.module.ICoreModule`

内容写实现类全限定名（每行一个）：

```text
com.example.ltsx.industry.IndustryCoreModule
```

### 步骤 4：在 `neoforge.mods.toml` 声明依赖

在模块自己的 `neoforge.mods.toml` 中把 `ltsxcore` 声明为 required（通常 `ordering = "AFTER"`）。

## 3. 服务注册与消费（推荐模式）

通过 `CoreServices` 做解耦：
- 服务提供方：`CoreServices.register(IService.class, impl)`
- 服务消费方：`CoreServices.get(IService.class)` 或 `getOptional(IService.class)`

示例：

```java
CoreServices.register(IEconomyService.class, new EconomyServiceImpl());
CoreServices.getOptional(IEconomyService.class).ifPresent(service -> service.deposit(player, 100));
```

## 4. 测试模组（挂载到 core）伪代码

下面给一个 `ltsxcore_test` 测试模组的伪代码逻辑。

### 4.1 测试模组主类（独立 mod）

```java
@Mod("ltsxcore_test")
public final class LtsxCoreTestMod {
    public LtsxCoreTestMod(IEventBus modBus) {
        // 尽量保持轻量，真正的 core 接入逻辑放到 ICoreModule 实现中
    }
}
```

### 4.2 测试服务接口与实现

```java
public interface ITestEchoService {
    String echo(String input);
}

public final class TestEchoService implements ITestEchoService {
    @Override
    public String echo(String input) {
        return "[TEST-ECHO] " + input;
    }
}
```

### 4.3 通过 `ICoreModule` 注册到 core

```java
public final class TestCoreModule implements ICoreModule {
    @Override
    public String moduleId() {
        return "ltsxcore_test";
    }

    @Override
    public int loadOrder() {
        return 9999;
    }

    @Override
    public void onRegister(CoreModuleContext ctx) {
        ctx.logger().info("[ltsxcore_test] registering...");

        // 1) 注册服务到 core
        CoreServices.register(ITestEchoService.class, new TestEchoService());

        // 2) 挂事件（NeoForge 总线）
        ctx.neoForgeBus().addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            CoreServices.getOptional(ITestEchoService.class).ifPresent(s -> {
                ctx.logger().info(s.echo("player login: " + e.getEntity().getName().getString()));
            });
        });

        // 3) 如需网络注册，可在 modBus 监听 RegisterPayloadHandlersEvent
        // ctx.modBus().addListener(...)
    }
}
```

### 4.4 测试模组的 ServiceLoader 文件

`src/main/resources/META-INF/services/link.botwmcs.core.api.module.ICoreModule`

```text
com.example.ltsx.test.TestCoreModule
```

## 5. 联调检查清单

- 同时放入 `ltsxcore` 与模块 jar 启动专用服务器
- 日志中确认 `ModuleManager` 已加载模块
- 执行 `/ltsxcore modules` 查看模块列表
- 验证模块服务可被 `CoreServices` 成功获取
