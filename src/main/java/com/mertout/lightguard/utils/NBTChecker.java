package com.mertout.lightguard.utils;

import com.mertout.lightguard.LightGuard;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class NBTChecker {

    private static int maxListSize = 500;
    private static int maxArraySize = 1024;
    private static int maxTotalNodes = 2000;

    public static void reload() {
        FileConfiguration config = LightGuard.getInstance().getConfig();
        maxListSize = config.getInt("checks.item-exploit.max-list-size-nbt", 500);
        maxArraySize = config.getInt("checks.item-exploit.max-array-size", 1024);
        maxTotalNodes = config.getInt("checks.item-exploit.max-total-nodes", 2000);
    }

    public static boolean isNBTDangerous(NBTTagCompound rootTag, int maxDepth) {
        if (rootTag == null) return false;

        if (rootTag.getKeys().size() > maxListSize) {
            return true;
        }

        Set<NBTBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        AtomicInteger nodeCounter = new AtomicInteger(0);

        try {
            return checkRecursively(rootTag, visited, 0, maxDepth, nodeCounter);
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean checkRecursively(NBTBase current, Set<NBTBase> visited, int depth, int maxDepth, AtomicInteger nodeCounter) {
        if (depth > maxDepth) return true;

        if (nodeCounter.incrementAndGet() > maxTotalNodes) return true;

        if (!visited.add(current)) return true;

        if (current instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) current;
            if (list.size() > maxListSize) return true;

            for (int i = 0; i < list.size(); i++) {
                NBTBase child = list.get(i);
                if (checkRecursively(child, visited, depth + 1, maxDepth, nodeCounter)) return true;
            }
        } else if (current instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) current;

            if (compound.getKeys().size() > maxListSize) return true;

            for (String key : compound.getKeys()) {
                NBTBase child = compound.get(key);
                if (checkRecursively(child, visited, depth + 1, maxDepth, nodeCounter)) return true;
            }
        } else if (current instanceof NBTTagIntArray) {
            if (((NBTTagIntArray) current).getInts().length > maxArraySize) return true;
        } else if (current instanceof NBTTagByteArray) {
            if (((NBTTagByteArray) current).getBytes().length > maxArraySize) return true;
        }

        return false;
    }
}