package com.sk89q.worldguard.neoforge.command;

import com.sk89q.worldguard.neoforge.region.Region;
import com.sk89q.worldguard.neoforge.WorldGuardMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Optional integration: no WorldEdit classes are linked when the mod is absent. */
final class WorldEditSelectionBridge {
    private static final String ADAPTER = "com.sk89q.worldedit.neoforge.NeoForgeAdapter";

    private WorldEditSelectionBridge() {
    }

    static boolean available() {
        try {
            Class.forName(ADAPTER, false, WorldEditSelectionBridge.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }

    static boolean handlesSelectionWand(ServerPlayer player) {
        if (!available()) return false;
        try {
            Object actor = call(Class.forName(ADAPTER), "adaptPlayer", player);
            return Boolean.TRUE.equals(call(actor, "hasPermission", "worldedit.selection.pos"));
        } catch (ReflectiveOperationException | LinkageError exception) {
            WorldGuardMod.LOGGER.debug("Using native wooden-axe selection", exception);
            return false;
        }
    }

    static Region selection(ServerPlayer player, ServerLevel level, String id) {
        if (!available()) {
            return null;
        }
        try {
            Object actor = call(Class.forName(ADAPTER), "adaptPlayer", player);
            Object world = call(Class.forName(ADAPTER), "adapt", level);
            Object session = session(actor);
            Object selected = call(session, "getSelection", world);
            Object min = call(selected, "getMinimumPoint");
            Object max = call(selected, "getMaximumPoint");
            if (selected.getClass().getName().equals("com.sk89q.worldedit.regions.CuboidRegion")) {
                return Region.cuboid(id, coordinate(min, "x"), coordinate(min, "y"), coordinate(min, "z"),
                        coordinate(max, "x"), coordinate(max, "y"), coordinate(max, "z"));
            }
            if (selected.getClass().getName().equals("com.sk89q.worldedit.regions.Polygonal2DRegion")) {
                List<Region.Point> points = new ArrayList<>();
                for (Object point : (Iterable<?>) call(selected, "getPoints")) {
                    points.add(new Region.Point(coordinate(point, "x"), coordinate(point, "z")));
                }
                return Region.polygon(id, points, coordinate(min, "y"), coordinate(max, "y"));
            }
            throw new IllegalArgumentException("WorldGuard supports WorldEdit cuboid and polygon2d selections. Use //sel cuboid or //sel poly.");
        } catch (ReflectiveOperationException exception) {
            Throwable cause = rootCause(exception);
            if (cause.getClass().getName().endsWith("IncompleteRegionException")) {
                throw new IllegalArgumentException("Complete a WorldEdit selection, or use /rg pos1 and /rg pos2.");
            }
            throw new IllegalStateException("Cannot read WorldEdit selection: " + cause.getClass().getSimpleName(), cause);
        }
    }

    static void select(ServerPlayer player, ServerLevel level, Region region) {
        if (!available()) {
            return;
        }
        try {
            Object actor = call(Class.forName(ADAPTER), "adaptPlayer", player);
            Object world = call(Class.forName(ADAPTER), "adapt", level);
            Object session = session(actor);
            Object selector;
            if (region.type().equals("poly2d")) {
                Class<?> vector = Class.forName("com.sk89q.worldedit.math.BlockVector2");
                List<Object> points = new ArrayList<>();
                for (Region.Point point : region.points()) {
                    points.add(call(vector, "at", point.x(), point.z()));
                }
                selector = construct(Class.forName("com.sk89q.worldedit.regions.selector.Polygonal2DRegionSelector"),
                        world, points, region.minY(), region.maxY());
            } else {
                Class<?> vector = Class.forName("com.sk89q.worldedit.math.BlockVector3");
                Object min = call(vector, "at", region.minX(), region.minY(), region.minZ());
                Object max = call(vector, "at", region.maxX(), region.maxY(), region.maxZ());
                selector = construct(Class.forName("com.sk89q.worldedit.regions.selector.CuboidRegionSelector"), world, min, max);
            }
            call(session, "setRegionSelector", world, selector);
            // Plain Minecraft clients have not negotiated the WorldEdit CUI payload.
            if (Boolean.TRUE.equals(call(session, "hasCUISupport"))
                    || Boolean.TRUE.equals(call(session, "shouldUseServerCUI"))) {
                call(session, "dispatchCUISelection", actor);
            }
        } catch (ReflectiveOperationException exception) {
            WorldGuardMod.LOGGER.warn("Could not update WorldEdit selection", rootCause(exception));
            throw new IllegalStateException("Native selection saved, but WorldEdit selection could not be updated: "
                    + rootCause(exception).getClass().getSimpleName(), rootCause(exception));
        }
    }

    private static Object session(Object actor) throws ReflectiveOperationException {
        Object worldEdit = call(Class.forName("com.sk89q.worldedit.WorldEdit"), "getInstance");
        return call(call(worldEdit, "getSessionManager"), "get", actor);
    }

    private static int coordinate(Object vector, String axis) throws ReflectiveOperationException {
        return ((Number) call(vector, axis)).intValue();
    }

    private static Object call(Object target, String methodName, Object... arguments) throws ReflectiveOperationException {
        Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && compatible(method.getParameterTypes(), arguments)) {
                return method.invoke(target instanceof Class<?> ? null : target, arguments);
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + methodName);
    }

    private static Object construct(Class<?> type, Object... arguments) throws ReflectiveOperationException {
        for (Constructor<?> constructor : type.getConstructors()) {
            if (compatible(constructor.getParameterTypes(), arguments)) {
                return constructor.newInstance(arguments);
            }
        }
        throw new NoSuchMethodException(type.getName() + " constructor");
    }

    private static boolean compatible(Class<?>[] parameters, Object[] arguments) {
        if (parameters.length != arguments.length) {
            return false;
        }
        for (int index = 0; index < parameters.length; index++) {
            Class<?> parameter = parameters[index] == int.class ? Integer.class : parameters[index];
            if (arguments[index] != null && !parameter.isInstance(arguments[index])) {
                return false;
            }
        }
        return true;
    }

    private static Throwable rootCause(Throwable throwable) {
        while (throwable instanceof InvocationTargetException && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }
        return throwable;
    }
}
