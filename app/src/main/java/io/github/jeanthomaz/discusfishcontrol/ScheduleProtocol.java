package io.github.jeanthomaz.discusfishcontrol;

import java.util.Locale;

/** Fixed record layout observed in the NOUR 1.0.8 app. */
final class ScheduleProtocol {
    static final int COLOR_POINTS = 8;
    static final int TOTAL_POINTS = 10;
    static final int DAWN_SLOT = 8;
    static final int DUSK_SLOT = 9;

    private ScheduleProtocol() { }

    static String writeCommand(int slot, int hour, int minute, boolean enabled, int[] levels) {
        if (slot < 0 || slot >= TOTAL_POINTS || hour < 0 || hour > 23
                || minute < 0 || minute > 59 || levels.length != 4) {
            throw new IllegalArgumentException("Invalid schedule point");
        }
        StringBuilder command = new StringBuilder("w_a_d").append(slot)
                .append(enabled ? "30" : "00")
                .append(String.format(Locale.US, "%02d%02d", hour, minute));
        for (int level : levels) {
            // Slots 8 and 9 are the zero-light start/end anchors in the original app.
            int intensity = slot < COLOR_POINTS ? Math.max(0, Math.min(100, level)) : 0;
            command.append(String.format(Locale.US, "%02x", intensity));
        }
        return command.toString();
    }

    static String clearCommand(int slot) {
        // NOUR exposes fixed slots: remove a point by disabling and clearing its data.
        return writeCommand(slot, 0, 0, false, new int[4]);
    }

    static boolean isClearedRecord(byte[] record) {
        if (record == null || record.length < 8) return false;
        // Retain the slot ID (byte 0) so the position can be reused after reconnecting.
        for (int i = 1; i < 8; i++) if (record[i] != 0) return false;
        return true;
    }

    static boolean validWindow(int dawn, int dusk) {
        return dawn >= 0 && dusk < 24 * 60 && dawn < dusk;
    }

    static boolean insideWindow(int minute, int dawn, int dusk) {
        return validWindow(dawn, dusk) && minute > dawn && minute < dusk;
    }

    static int availableColorTime(int dawn, int dusk, int[] occupied) {
        if (!validWindow(dawn, dusk)) return -1;
        int first = Math.max(dawn + 1, Math.min(12 * 60, dusk - 1));
        for (int offset = 0; offset < dusk - dawn - 1; offset++) {
            int candidate = dawn + 1 + (first - dawn - 1 + offset) % (dusk - dawn - 1);
            boolean used = false;
            for (int minute : occupied) if (minute == candidate) used = true;
            if (!used) return candidate;
        }
        return -1;
    }
}
