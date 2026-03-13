package com.abilitycombat.combat;

import com.abilitycombat.AbilityCombat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SweepPacketSuppressor implements Listener {

    private static final String HANDLER_NAME = "abilitycombat_sweep_filter";
    private static final String PACKET_HANDLER_NAME = "packet_handler";
    private static final String LEVEL_PARTICLE_PACKET_NAME = "ClientboundLevelParticlesPacket";
    private static final String SOUND_PACKET_NAME = "ClientboundSoundPacket";
    private static final String SOUND_ENTITY_PACKET_NAME = "ClientboundSoundEntityPacket";

    private final AbilityCombat plugin;
    private final Map<UUID, Object> handlers = new ConcurrentHashMap<>();
    private Class<?> channelOutboundHandlerInterface;
    private boolean available;

    public SweepPacketSuppressor(AbilityCombat plugin) {
        this.plugin = plugin;
    }

    public void start() {
        available = resolveNettyInterfaces();
        if (!available) {
            plugin.getLogger().warning("Sweep packet suppressor unavailable: Netty interfaces not found.");
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            inject(player);
        }
    }

    public void stop() {
        if (!available) {
            return;
        }
        HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            uninject(player);
        }
        handlers.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        inject(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        uninject(event.getPlayer());
    }

    private boolean resolveNettyInterfaces() {
        try {
            channelOutboundHandlerInterface = Class.forName("io.netty.channel.ChannelOutboundHandler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void inject(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (handlers.containsKey(playerId)) {
            return;
        }
        Object channel = resolveChannel(player);
        if (channel == null) {
            return;
        }
        Object pipeline = invokeNoArgs(channel, "pipeline");
        if (pipeline == null) {
            return;
        }
        if (hasHandler(pipeline, HANDLER_NAME)) {
            return;
        }

        Object handler = createOutboundHandler(playerId);
        if (handler == null) {
            return;
        }

        if (!addBeforePacketHandler(pipeline, handler)) {
            return;
        }

        handlers.put(playerId, handler);
    }

    private void uninject(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        handlers.remove(playerId);

        Object channel = resolveChannel(player);
        if (channel == null) {
            return;
        }
        Object pipeline = invokeNoArgs(channel, "pipeline");
        if (pipeline == null) {
            return;
        }
        invokeWithArgs(pipeline, "remove", HANDLER_NAME);
    }

    private boolean hasHandler(Object pipeline, String name) {
        Object existing = invokeWithArgs(pipeline, "get", name);
        return existing != null;
    }

    private boolean addBeforePacketHandler(Object pipeline, Object handler) {
        Object result = invokeWithArgs(pipeline, "addBefore", PACKET_HANDLER_NAME, HANDLER_NAME, handler);
        return result != null;
    }

    private Object createOutboundHandler(UUID playerId) {
        InvocationHandler invocationHandler = (proxy, method, args) -> handleOutboundInvocation(proxy, playerId, method, args);
        return Proxy.newProxyInstance(
                channelOutboundHandlerInterface.getClassLoader(),
                new Class<?>[] { channelOutboundHandlerInterface },
                invocationHandler);
    }

    private Object handleOutboundInvocation(Object proxy, UUID playerId, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            String objectMethod = method.getName();
            if ("toString".equals(objectMethod)) {
                return HANDLER_NAME;
            }
            if ("hashCode".equals(objectMethod)) {
                return System.identityHashCode(this);
            }
            if ("equals".equals(objectMethod) && args != null && args.length == 1) {
                return proxy == args[0];
            }
        }

        String name = method.getName();
        if ("write".equals(name) && args != null && args.length >= 3) {
            Object context = args[0];
            Object packet = args[1];
            Object promise = args[2];

            if (shouldSuppressSweepEffect(playerId, packet)) {
                completePromise(promise);
                return null;
            }
            invokeWithArgs(context, "write", packet, promise);
            return null;
        }

        if (args == null || args.length == 0) {
            return null;
        }

        Object context = args[0];
        Object[] forwardedArgs = Arrays.copyOfRange(args, 1, args.length);
        Object forwarded = invokeWithArgs(context, name, forwardedArgs);
        if (forwarded != null) {
            return null;
        }
        if ("handlerAdded".equals(name) || "handlerRemoved".equals(name)) {
            return null;
        }
        if ("exceptionCaught".equals(name) && forwardedArgs.length >= 1) {
            invokeWithArgs(context, "fireExceptionCaught", forwardedArgs[0]);
        }
        return null;
    }

    private boolean shouldSuppressSweepEffect(UUID playerId, Object packet) {
        if (playerId == null || packet == null) {
            return false;
        }
        if (plugin.getConfig().getBoolean("combat.attack-cooldown", true)) {
            return false;
        }
        if (isSweepParticlePacket(packet)) {
            return !SweepEffectAllowance.consumeAbilitySweepParticleAllowance();
        }
        if (isSweepSoundPacket(packet)) {
            return !SweepEffectAllowance.consumeAbilitySweepSoundAllowance();
        }
        return false;
    }

    private boolean isSweepParticlePacket(Object packet) {
        Class<?> packetClass = packet.getClass();
        if (!packetClass.getSimpleName().contains(LEVEL_PARTICLE_PACKET_NAME)) {
            return false;
        }

        Object particleOptions = invokeNoArgs(packet, "getParticle");
        if (particleOptions == null) {
            particleOptions = findFieldValue(packet, value -> value.getClass().getSimpleName().contains("Particle"));
        }
        if (particleOptions == null) {
            return false;
        }

        String text = String.valueOf(particleOptions).toLowerCase(Locale.ROOT);
        if (text.contains("sweep")) {
            return true;
        }

        Object type = invokeNoArgs(particleOptions, "getType");
        if (type != null) {
            String typeText = String.valueOf(type).toLowerCase(Locale.ROOT);
            if (typeText.contains("sweep")) {
                return true;
            }
        }
        return false;
    }

    private boolean isSweepSoundPacket(Object packet) {
        String simpleName = packet.getClass().getSimpleName();
        if (!simpleName.contains(SOUND_PACKET_NAME) && !simpleName.contains(SOUND_ENTITY_PACKET_NAME)) {
            return false;
        }

        String packetText = String.valueOf(packet).toLowerCase(Locale.ROOT);
        if (packetText.contains("entity.player.attack.sweep") || packetText.contains("attack.sweep")) {
            return true;
        }

        Object sound = invokeNoArgs(packet, "getSound");
        if (sound != null) {
            String soundText = String.valueOf(sound).toLowerCase(Locale.ROOT);
            return soundText.contains("entity.player.attack.sweep") || soundText.contains("attack.sweep");
        }
        return false;
    }

    private Object resolveChannel(Player player) {
        Object handle = invokeNoArgs(player, "getHandle");
        if (handle == null) {
            return null;
        }

        Object packetListener = getFieldValue(handle, "connection");
        if (packetListener == null) {
            packetListener = findFieldValue(handle,
                    value -> value.getClass().getSimpleName().contains("ServerGamePacketListenerImpl"));
        }
        if (packetListener == null) {
            return null;
        }

        Object networkConnection = getFieldValue(packetListener, "connection");
        if (networkConnection == null) {
            networkConnection = findFieldValue(packetListener,
                    value -> value.getClass().getName().contains(".network.Connection"));
        }
        if (networkConnection == null) {
            return null;
        }

        Object channel = getFieldValue(networkConnection, "channel");
        if (channel != null) {
            return channel;
        }
        return findFieldValue(networkConnection,
                value -> implementsInterfaceNamed(value.getClass(), "io.netty.channel.Channel"));
    }

    private boolean implementsInterfaceNamed(Class<?> type, String interfaceName) {
        if (type == null) {
            return false;
        }
        for (Class<?> iface : type.getInterfaces()) {
            if (interfaceName.equals(iface.getName())) {
                return true;
            }
            if (implementsInterfaceNamed(iface, interfaceName)) {
                return true;
            }
        }
        return implementsInterfaceNamed(type.getSuperclass(), interfaceName);
    }

    private void completePromise(Object promise) {
        if (promise == null) {
            return;
        }
        Object done = invokeNoArgs(promise, "trySuccess");
        if (done != null) {
            return;
        }
        invokeNoArgs(promise, "setSuccess");
    }

    private Object invokeNoArgs(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = findMethod(target.getClass(), methodName);
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object invokeWithArgs(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = findCompatibleMethod(target.getClass(), methodName, args);
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Method findMethod(Class<?> type, String methodName) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private Method findCompatibleMethod(Class<?> type, String methodName, Object[] args) {
        Class<?> current = type;
        if (args == null) {
            args = new Object[0];
        }
        int argCount = args.length;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                if (method.getParameterCount() != argCount) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                boolean compatible = true;
                for (int i = 0; i < argCount; i++) {
                    Object arg = args[i];
                    if (arg == null) {
                        continue;
                    }
                    if (!wrap(params[i]).isAssignableFrom(arg.getClass())) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private Object getFieldValue(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private Object findFieldValue(Object target, FieldPredicate predicate) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value != null && predicate.test(value)) {
                        return value;
                    }
                } catch (Throwable ignored) {
                    // continue scanning
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    @FunctionalInterface
    private interface FieldPredicate {
        boolean test(Object value);
    }
}
