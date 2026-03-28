package com.mertout.lightguard.netty.nbt;

import io.netty.buffer.ByteBuf;

public class NBTStreamingScanner {

    private static final int MAX_DEPTH = 7;
    private static final int MAX_NODES = 1500;
    private static final int MAX_STRING_LENGTH = 1024;

    public static boolean scan(ByteBuf buf) {
        try {
            int[] nodeCount = new int[]{0};
            if (!buf.isReadable()) return true;

            byte rootTag = buf.readByte();
            if (rootTag == 0) return true; // TAG_End
            if (rootTag != 10) return false; // Root

            int nameLength = buf.readUnsignedShort();
            if (buf.readableBytes() < nameLength) return false;
            buf.skipBytes(nameLength);

            return scanTag(buf, 10, 1, nodeCount);

        } catch (Exception e) {
            return false;
        }
    }

    private static boolean scanTag(ByteBuf buf, int tagType, int depth, int[] nodeCount) {
        if (depth > MAX_DEPTH || ++nodeCount[0] > MAX_NODES) return false;

        switch (tagType) {
            case 1: if (buf.readableBytes() < 1) return false; buf.skipBytes(1); break; // Byte
            case 2: if (buf.readableBytes() < 2) return false; buf.skipBytes(2); break; // Short
            case 3: if (buf.readableBytes() < 4) return false; buf.skipBytes(4); break; // Int
            case 4: if (buf.readableBytes() < 8) return false; buf.skipBytes(8); break; // Long
            case 5: if (buf.readableBytes() < 4) return false; buf.skipBytes(4); break; // Float
            case 6: if (buf.readableBytes() < 8) return false; buf.skipBytes(8); break; // Double
            case 7: // Byte Array
                int bLen = buf.readInt();
                if (bLen < 0 || bLen > 10000 || buf.readableBytes() < bLen) return false;
                buf.skipBytes(bLen);
                break;
            case 8: // String
                int sLen = buf.readUnsignedShort();
                if (sLen > MAX_STRING_LENGTH || buf.readableBytes() < sLen) return false;
                buf.skipBytes(sLen);
                break;
            case 9: // List
                byte lType = buf.readByte();
                int lLen = buf.readInt();
                if (lLen < 0 || lLen > 500) return false;
                for (int i = 0; i < lLen; i++) {
                    if (!scanTag(buf, lType, depth + 1, nodeCount)) return false;
                }
                break;
            case 10: // Compound
                while (true) {
                    if (!buf.isReadable()) return false;
                    byte inner = buf.readByte();
                    if (inner == 0) break; // TAG_End

                    int nLen = buf.readUnsignedShort();
                    if (nLen > 256 || buf.readableBytes() < nLen) return false;
                    buf.skipBytes(nLen);

                    if (!scanTag(buf, inner, depth + 1, nodeCount)) return false;
                }
                break;
            case 11: // Int Array
                int iLen = buf.readInt();
                if (iLen < 0 || iLen > 5000 || buf.readableBytes() < (iLen * 4)) return false;
                buf.skipBytes(iLen * 4);
                break;
            case 12: // Long Array
                int loLen = buf.readInt();
                if (loLen < 0 || loLen > 5000 || buf.readableBytes() < (loLen * 8)) return false;
                buf.skipBytes(loLen * 8);
                break;
            default: return false;
        }
        return true;
    }
}