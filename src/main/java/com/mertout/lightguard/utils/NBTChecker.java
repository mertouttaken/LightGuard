package com.mertout.lightguard.utils;

import com.mertout.lightguard.LightGuard;
import net.minecraft.server.v1_16_R3.*;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class NBTChecker {

    private static volatile int maxListSize = 500;
    private static volatile int maxArraySize = 1024;
    private static volatile int maxTotalNodes = 2000;

    public static void reload() {
        var config = LightGuard.getInstance().getConfig();
        maxListSize = config.getInt("checks.item-exploit.max-list-size", 500);
        maxArraySize = config.getInt("checks.item-exploit.max-array-size", 1024);
        maxTotalNodes = config.getInt("checks.item-exploit.max-total-nodes", 2000);
    }

    public static boolean isNBTDangerous(NBTTagCompound rootTag, int maxDepth) {
        if (rootTag == null) return false;

        Set<NBTBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int[] nodeCounter = {0};

        try {
            return checkRecursively(rootTag, visited, 0, maxDepth, nodeCounter);
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean checkRecursively(NBTBase current, Set<NBTBase> visited, int depth, int maxDepth, int[] nodeCounter) {
        if (depth > maxDepth || ++nodeCounter[0] > maxTotalNodes) return true;
        if (!visited.add(current)) return true;

        if (current instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) current;
            if (list.size() > maxListSize) return true;
            for (int i = 0; i < list.size(); i++) {
                if (checkRecursively(list.get(i), visited, depth + 1, maxDepth, nodeCounter)) return true;
            }
        }
        else if (current instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) current;
            if (compound.getKeys().size() > maxListSize) return true;
            for (String key : compound.getKeys()) {
                if (checkRecursively(compound.get(key), visited, depth + 1, maxDepth, nodeCounter)) return true;
            }
        }
        else if (current instanceof NBTTagIntArray) {
            if (((NBTTagIntArray) current).getInts().length > maxArraySize) return true;
        }
        else if (current instanceof NBTTagByteArray) {
            if (((NBTTagByteArray) current).getBytes().length > maxArraySize) return true;
        }

        return false;
    }
}