package com.mertout.lightguard.utils;

import com.mertout.lightguard.LightGuard;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class NBTChecker {

    private static int maxListSize = 500;
    private static int maxArraySize = 1024;

    public static void reload() {
        FileConfiguration config = LightGuard.getInstance().getConfig();
        maxListSize = config.getInt("checks.nbt.max-list-size-nbt", 500);
        maxArraySize = config.getInt("checks.nbt.max-array-size", 1024);
    }

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

        if (!visited.add(current)) return true;

        if (current instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) current;
            if (list.size() > maxListSize) return true;

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
            if (((NBTTagIntArray) current).getInts().length > maxArraySize) return true;
        } else if (current instanceof NBTTagByteArray) {
            if (((NBTTagByteArray) current).getBytes().length > maxArraySize) return true;
        }

        return false;
    }
}