package com.mertout.lightguard.utils;

import net.minecraft.server.v1_16_R3.*;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class NBTChecker {

    private static final int MAX_LIST_SIZE = 500;
    private static final int MAX_ARRAY_SIZE = 1024;

    public static boolean isNBTDangerous(NBTTagCompound rootTag, int maxDepth) {
        if (rootTag == null) return false;
        Set<NBTBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            return checkRecursively(rootTag, visited, 0, maxDepth);
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean checkRecursively(NBTBase current, Set<NBTBase> visited, int depth, int maxDepth) {
        if (depth > maxDepth) return true;
        if (visited.contains(current)) return true;

        visited.add(current);

        try {
            if (current instanceof NBTTagList) {
                NBTTagList list = (NBTTagList) current;
                if (list.size() > MAX_LIST_SIZE) return true;

                for (int i = 0; i < list.size(); i++) {
                    NBTBase child = list.get(i);
                    if (checkRecursively(child, visited, depth + 1, maxDepth)) return true;
                }
            } else if (current instanceof NBTTagCompound) {
                NBTTagCompound compound = (NBTTagCompound) current;
                for (String key : compound.getKeys()) {
                    NBTBase child = compound.get(key);
                    if (checkRecursively(child, visited, depth + 1, maxDepth)) return true;
                }
            } else if (current instanceof NBTTagIntArray) {
                if (((NBTTagIntArray) current).getInts().length > MAX_ARRAY_SIZE) return true;
            } else if (current instanceof NBTTagByteArray) {
                if (((NBTTagByteArray) current).getBytes().length > MAX_ARRAY_SIZE) return true;
            }
        } finally {
            visited.remove(current);
        }
        return false;
    }
}