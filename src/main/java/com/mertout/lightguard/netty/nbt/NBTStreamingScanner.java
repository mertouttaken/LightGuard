package com.mertout.lightguard.netty.nbt;

import io.netty.buffer.ByteBuf;

public class NBTStreamingScanner {

    public static int MAX_DEPTH = 7;
    public static int MAX_NODES = 1500;
    public static int MAX_STRING_LENGTH = 1024;

    public static boolean scan(ByteBuf buf) {
        if (buf == null || !buf.isReadable()) return true;

        int originalIndex = buf.readerIndex();
        boolean isSafe = true;

        try {
            while (buf.isReadable()) {
                buf.markReaderIndex();
                byte b = buf.readByte();

                if (b == 10) {
                    int[] nodeCount = new int[]{0};
                    int result = attemptScanRoot(buf, nodeCount);

                    if (result == -1) {
                        isSafe = false;
                        break;
                    }
                }

                buf.resetReaderIndex();
                buf.readByte();
            }
        } catch (Exception ignored) {
        } finally {
            buf.readerIndex(originalIndex);
        }

        return isSafe;
    }

    private static int attemptScanRoot(ByteBuf buf, int[] nodeCount) {
        try {
            if (buf.readableBytes() < 2) return 0;
            int nameLength = buf.readUnsignedShort();
            if (buf.readableBytes() < nameLength) return 0;
            buf.skipBytes(nameLength);

            return scanTag(buf, 10, 1, nodeCount);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int scanTag(ByteBuf buf, int tagType, int depth, int[] nodeCount) {
        if (depth > MAX_DEPTH || ++nodeCount[0] > MAX_NODES) return -1;

        try {
            switch (tagType) {
                case 1: if (buf.readableBytes() < 1) return 0; buf.skipBytes(1); break; // Byte
                case 2: if (buf.readableBytes() < 2) return 0; buf.skipBytes(2); break; // Short
                case 3: if (buf.readableBytes() < 4) return 0; buf.skipBytes(4); break; // Int
                case 4: if (buf.readableBytes() < 8) return 0; buf.skipBytes(8); break; // Long
                case 5: if (buf.readableBytes() < 4) return 0; buf.skipBytes(4); break; // Float
                case 6: if (buf.readableBytes() < 8) return 0; buf.skipBytes(8); break; // Double
                case 7: // Byte Array
                    if (buf.readableBytes() < 4) return 0;
                    int bLen = buf.readInt();
                    if (bLen < 0 || buf.readableBytes() < bLen) return 0;
                    if (bLen > 25000) return -1;
                    buf.skipBytes(bLen);
                    break;
                case 8: // String
                    if (buf.readableBytes() < 2) return 0;
                    int sLen = buf.readUnsignedShort();
                    if (buf.readableBytes() < sLen) return 0;
                    if (sLen > MAX_STRING_LENGTH) return -1;
                    buf.skipBytes(sLen);
                    break;
                case 9: // List
                    if (buf.readableBytes() < 5) return 0;
                    byte lType = buf.readByte();
                    int lLen = buf.readInt();
                    if (lLen < 0 || buf.readableBytes() < lLen) return 0;
                    if (lLen > 1000) return -1;
                    for (int i = 0; i < lLen; i++) {
                        int res = scanTag(buf, lType, depth + 1, nodeCount);
                        if (res != 1) return res;
                    }
                    break;
                case 10: // Compound
                    while (true) {
                        if (!buf.isReadable()) return 0;
                        byte inner = buf.readByte();
                        if (inner == 0) break;

                        if (buf.readableBytes() < 2) return 0;
                        int nLen = buf.readUnsignedShort();
                        if (buf.readableBytes() < nLen) return 0;
                        if (nLen > 256) return -1;
                        buf.skipBytes(nLen);

                        int res = scanTag(buf, inner, depth + 1, nodeCount);
                        if (res != 1) return res;
                    }
                    break;
                case 11: // Int Array
                    if (buf.readableBytes() < 4) return 0;
                    int iLen = buf.readInt();
                    if (iLen < 0 || buf.readableBytes() < (iLen * 4)) return 0;
                    if (iLen > 10000) return -1;
                    buf.skipBytes(iLen * 4);
                    break;
                case 12: // Long Array
                    if (buf.readableBytes() < 4) return 0;
                    int loLen = buf.readInt();
                    if (loLen < 0 || buf.readableBytes() < (loLen * 8)) return 0;
                    if (loLen > 10000) return -1;
                    buf.skipBytes(loLen * 8);
                    break;
                default: return 0;
            }
        } catch (Exception e) {
            return 0;
        }
        return 1;
    }
}