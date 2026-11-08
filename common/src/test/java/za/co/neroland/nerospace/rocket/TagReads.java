package za.co.neroland.nerospace.rocket;

import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
//? if <1.21.5 {
/*import net.minecraft.nbt.Tag;
*///?}

/**
 * NBT reads for the save-compat tests that work on every cell: 1.21.5 added the {@code get*Or} / {@code *OrEmpty}
 * accessors; before it the plain getters already return an empty value for a missing key.
 */
final class TagReads {

    private TagReads() {
    }

    static int intOr(CompoundTag tag, String key, int fallback) {
        //? if >=1.21.5 {
        return tag.getIntOr(key, fallback);
        //?} else {
        /*return tag.contains(key) ? tag.getInt(key) : fallback;
        *///?}
    }

    static boolean booleanOr(CompoundTag tag, String key, boolean fallback) {
        //? if >=1.21.5 {
        return tag.getBooleanOr(key, fallback);
        //?} else {
        /*return tag.contains(key) ? tag.getBoolean(key) : fallback;
        *///?}
    }

    static ListTag listOrEmpty(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getListOrEmpty(key);
        //?} else {
        /*return tag.getList(key, Tag.TAG_COMPOUND);
        *///?}
    }

    static Set<String> keys(CompoundTag tag) {
        //? if >=1.21.5 {
        return tag.keySet();
        //?} else {
        /*return tag.getAllKeys();
        *///?}
    }

    static CompoundTag compoundAt(ListTag list, int index) {
        //? if >=1.21.5 {
        return list.getCompoundOrEmpty(index);
        //?} else {
        /*return list.getCompound(index);
        *///?}
    }
}
