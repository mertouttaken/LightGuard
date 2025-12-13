package com.mertout.lightguard.utils;

import net.minecraft.server.v1_16_R3.NBTBase;
import net.minecraft.server.v1_16_R3.NBTTagCompound;
import net.minecraft.server.v1_16_R3.NBTTagList;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class NBTChecker {

    private static final int DEFAULT_MAX_DEPTH = 15;

    public static boolean isNBTDangerous(NBTTagCompound rootTag, int maxDepth) {
        if (rootTag == null) return false;

        Set<NBTBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        try {
            return checkRecursively(rootTag, visited, 0, maxDepth);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Rekürsif tarama metodu.
     */
    private static boolean checkRecursively(NBTBase current, Set<NBTBase> visited, int depth, int maxDepth) {
        if (depth > maxDepth) {
            return true;
        }

        if (visited.contains(current)) {
            return true;
        }

        visited.add(current);

        try {
            if (current instanceof NBTTagCompound) {
                NBTTagCompound compound = (NBTTagCompound) current;

                for (String key : compound.getKeys()) {
                    NBTBase child = compound.get(key);
                    if (checkRecursively(child, visited, depth + 1, maxDepth)) {
                        return true;
                    }
                }
            }
            else if (current instanceof NBTTagList) {
                NBTTagList list = (NBTTagList) current;

                for (int i = 0; i < list.size(); i++) {
                    NBTBase child = list.get(i);
                    if (checkRecursively(child, visited, depth + 1, maxDepth)) {
                        return true;
                    }
                }
            }

        } finally {
            visited.remove(current);
        }

        return false;
    }
}