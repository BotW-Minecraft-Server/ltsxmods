package link.botwmcs.core.event;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import link.botwmcs.core.config.CoreConfig;
import link.botwmcs.core.util.CoreKeys;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Unified event bridge for common server events.
 * <p>
 * Thread/performance note: tick handler runs only budgeted lightweight tasks.
 */
public final class CoreEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Queue<Runnable> SCHEDULED_TASKS = new ConcurrentLinkedQueue<>();

    private CoreEvents() {
    }

    public static void init(IEventBus neoForgeBus) {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        neoForgeBus.addListener(CoreEvents::onServerStarting);
        neoForgeBus.addListener(CoreEvents::onServerStopping);
        neoForgeBus.addListener(CoreEvents::onPlayerLoggedIn);
        neoForgeBus.addListener(CoreEvents::onPlayerLoggedOut);
        neoForgeBus.addListener(CoreEvents::onServerTick);

        LOGGER.info("{}CoreEvents registered.", CoreKeys.LOG_PREFIX);
    }

    /**
     * Lightweight scheduler: queues work for server ticks with a per-tick time budget.
     */
    public static void schedule(Runnable task) {
        SCHEDULED_TASKS.add(Objects.requireNonNull(task, "task"));
    }

    private static void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("{}Server starting: {}", CoreKeys.LOG_PREFIX, event.getServer().getServerVersion());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        final int remaining = SCHEDULED_TASKS.size();
        SCHEDULED_TASKS.clear();
        LOGGER.info("{}Server stopping, cleared {} queued task(s).", CoreKeys.LOG_PREFIX, remaining);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!CoreConfig.moduleLogging()) {
            return;
        }
        LOGGER.info("{}Player login: {}", CoreKeys.LOG_PREFIX, event.getEntity().getName().getString());
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!CoreConfig.moduleLogging()) {
            return;
        }
        LOGGER.info("{}Player logout: {}", CoreKeys.LOG_PREFIX, event.getEntity().getName().getString());
    }

    private static void onServerTick(ServerTickEvent.Pre event) {
        final int budgetMicros = CoreConfig.schedulerBudgetMicros();
        if (budgetMicros <= 0 || SCHEDULED_TASKS.isEmpty()) {
            return;
        }
        runScheduledTasksWithBudget(event.getServer(), budgetMicros);
    }

    private static void runScheduledTasksWithBudget(MinecraftServer server, int budgetMicros) {
        final long deadline = System.nanoTime() + budgetMicros * 1_000L;
        int executed = 0;

        Runnable task;
        while ((task = SCHEDULED_TASKS.poll()) != null) {
            try {
                task.run();
            } catch (Throwable throwable) {
                LOGGER.error("{}Scheduled task failed on server tick.", CoreKeys.LOG_PREFIX, throwable);
            }
            executed++;
            if (System.nanoTime() >= deadline) {
                break;
            }
        }

        if (executed > 0 && CoreConfig.enableDebug()) {
            LOGGER.debug("{}Tick scheduler executed {} task(s), remaining={}, server={}.",
                    CoreKeys.LOG_PREFIX,
                    executed,
                    SCHEDULED_TASKS.size(),
                    server.getMotd());
        }
    }
}
