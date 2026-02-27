
# ltsxcore (NeoForge 1.21.1) - Core + Modules Architecture

This project is the **core mod** (`modId = ltsxcore`) for a multi-mod ecosystem.
It is server-first and provides shared infrastructure for independent modules (industry/agriculture/cuisine/town/guild/economy, etc.).

## 1. Core Responsibilities

`ltsxcore` provides:
- module discovery and load ordering (`ServiceLoader`)
- unified service registry (`CoreServices`)
- unified network layer (`CoreNetwork`)
- unified config (`CoreConfig`)
- unified data adapter (`CoreData`, `CoreGlobalSavedData`)
- unified event bridge (`CoreEvents`)
- root command (`/ltsxcore ...`)

## 2. How A Module Attaches To Core

Each module is an independent mod jar with its own `modId`.

### Step 1: Depend on core API

Your module should compile against `ltsxcore` classes, especially:
- `link.botwmcs.core.api.module.ICoreModule`
- `link.botwmcs.core.api.module.CoreModuleContext`
- `link.botwmcs.core.service.CoreServices`

### Step 2: Implement `ICoreModule`

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
        // register services, event listeners, network payloads, etc.
    }
}
```

### Step 3: Add ServiceLoader provider file

Create this file in your module resources:

`src/main/resources/META-INF/services/link.botwmcs.core.api.module.ICoreModule`

File content:

```text
com.example.ltsx.industry.IndustryCoreModule
```

### Step 4: Add dependency in `neoforge.mods.toml`

In your module's `neoforge.mods.toml`, make `ltsxcore` a required dependency (typically `ordering = "AFTER"`).

## 3. Core Service Registration Pattern

Use `CoreServices` for decoupled service contracts:
- provider module: `CoreServices.register(IService.class, impl)`
- consumer module: `CoreServices.get(IService.class)` or `getOptional(IService.class)`

Example:

```java
CoreServices.register(IEconomyService.class, new EconomyServiceImpl());
CoreServices.getOptional(IEconomyService.class).ifPresent(service -> service.deposit(player, 100));
```

## 4. Pseudocode: Test Module Under Core

The following is pseudocode for a test module `ltsxcore_test` that registers under `ltsxcore`.

### 4.1 Main mod entry (independent mod jar)

```java
@Mod("ltsxcore_test")
public final class LtsxCoreTestMod {
    public LtsxCoreTestMod(IEventBus modBus) {
        // Keep this lightweight; core integration happens in ICoreModule implementation.
    }
}
```

### 4.2 Test service contract and impl

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

### 4.3 Core module implementation (discovered by ServiceLoader)

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

        // 1) register service
        CoreServices.register(ITestEchoService.class, new TestEchoService());

        // 2) register NeoForge event listeners
        ctx.neoForgeBus().addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            CoreServices.getOptional(ITestEchoService.class).ifPresent(s -> {
                ctx.logger().info(s.echo("player login: " + e.getEntity().getName().getString()));
            });
        });

        // 3) optional: register payload handlers via mod bus
        // ctx.modBus().addListener(... RegisterPayloadHandlersEvent ...)
    }
}
```

### 4.4 ServiceLoader file for test module

`src/main/resources/META-INF/services/link.botwmcs.core.api.module.ICoreModule`

```text
com.example.ltsx.test.TestCoreModule
```

## 5. Runtime Verification Checklist

- start dedicated server with both `ltsxcore` and your module jar
- verify logs show module loaded by `ModuleManager`
- run `/ltsxcore modules`
- check your service is reachable through `CoreServices`
