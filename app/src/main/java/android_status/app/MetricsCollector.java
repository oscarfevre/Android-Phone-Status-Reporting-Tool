package android_status.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;
import java.util.regex.Pattern;

public class MetricsCollector {
    private static final String TAG = "MetricsCollector";

    public static long[] readCpuStat() {
        // returns array: user, nice, system, idle, iowait, irq, softirq
        File f = new File("/proc/stat");
        if (!f.exists()) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line = br.readLine();
            if (line == null) return null;
            String[] toks = line.trim().split("\\s+");
            if (toks.length < 5) return null;
            long[] vals = new long[7];
            for (int i = 0; i < vals.length && i + 1 < toks.length; i++) {
                vals[i] = Long.parseLong(toks[i + 1]);
            }
            return vals;
        } catch (Exception ex) {
            Log.w(TAG, "readCpuStat failed", ex);
            return null;
        }
    }

    public static double readMemUsagePercent() {
        File f = new File("/proc/meminfo");
        if (!f.exists()) return 0.0;
        long total = 0, avail = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    total = Long.parseLong(line.replaceAll("[^0-9]", ""));
                } else if (line.startsWith("MemAvailable:")) {
                    avail = Long.parseLong(line.replaceAll("[^0-9]", ""));
                    break;
                }
            }
            if (total <= 0) return 0.0;
            double used = (double) (total - avail);
            return (used / (double) total) * 100.0;
        } catch (Exception ex) {
            Log.w(TAG, "readMemUsagePercent failed", ex);
            return 0.0;
        }
    }

    public static Double readTempCelsius(Context ctx) {
        // Try common thermal zone paths, then hwmon, then battery temp as fallback
        Double tzTemp = readThermalZones();
        if (tzTemp != null) return tzTemp;

        Double hwmonTemp = readHwmonInputs();
        if (hwmonTemp != null) return hwmonTemp;

        Double battTemp = readBatteryTemp(ctx);
        return battTemp;
    }

    private static Double readThermalZones() {
        try {
            File base = new File("/sys/class/thermal");
            File[] zones = base.listFiles((dir, name) -> name.startsWith("thermal_zone"));
            if (zones == null) return null;
            Pattern prefer = Pattern.compile("(?i)(cpu|ap|a7|a53|soc)");
            Double firstValid = null;
            for (File z : zones) {
                String type = readFirstLine(new File(z, "type"));
                Double temp = parseTempFile(new File(z, "temp"));
                if (temp != null && temp > 0) {
                    if (firstValid == null) firstValid = temp;
                    if (type != null && prefer.matcher(type).find()) {
                        return temp; // prefer CPU-like sensors
                    }
                }
            }
            return firstValid;
        } catch (Exception ex) {
            Log.w(TAG, "readThermalZones failed", ex);
            return null;
        }
    }

    private static Double readHwmonInputs() {
        try {
            File base = new File("/sys/class/hwmon");
            File[] hmons = base.listFiles();
            if (hmons == null) return null;
            for (File hm : hmons) {
                File[] temps = hm.listFiles((dir, name) -> name.startsWith("temp") && name.endsWith("_input"));
                if (temps == null) continue;
                for (File t : temps) {
                    Double val = parseTempFile(t);
                    if (val != null && val > 0) return val;
                }
            }
        } catch (Exception ex) {
            Log.w(TAG, "readHwmonInputs failed", ex);
        }
        return null;
    }

    private static Double readBatteryTemp(Context ctx) {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent b = ctx.registerReceiver(null, ifilter);
            if (b != null) {
                int t = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
                if (t != Integer.MIN_VALUE) {
                    return t / 10.0; // tenths of a degree C
                }
            }
        } catch (Exception ex) {
            Log.w(TAG, "readBatteryTemp failed", ex);
        }
        // filesystem fallback
        try {
            File f = new File("/sys/class/power_supply/battery/temp");
            Double v = parseTempFile(f);
            if (v != null) return v;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String readFirstLine(File f) {
        if (f == null || !f.exists()) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            return br.readLine();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Double parseTempFile(File f) {
        if (f == null || !f.exists()) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String s = br.readLine();
            if (s == null) return null;
            long raw = Long.parseLong(s.trim());
            if (raw > 1000) return raw / 1000.0;
            return (double) raw;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static class BatteryInfo {
        public int level = 0;
        public int voltageMv = 0;
        public boolean charging = false;
    }

    public static BatteryInfo readBattery(Context ctx) {
        BatteryInfo bi = new BatteryInfo();
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent b = ctx.registerReceiver(null, ifilter);
            if (b != null) {
                int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int voltage = b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                bi.level = (level >= 0 && scale > 0) ? (level * 100 / scale) : 0;
                bi.voltageMv = voltage;
                bi.charging = (status == BatteryManager.BATTERY_STATUS_CHARGING) || (status == BatteryManager.BATTERY_STATUS_FULL);
            }
        } catch (Exception ex) {
            Log.w(TAG, "readBattery failed", ex);
        }
        return bi;
    }

    public static String deviceId(Context ctx) {
        try {
            String id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (id != null && !id.isEmpty()) return id;
        } catch (Exception ignored) {
        }
        return Build.MODEL != null ? Build.MODEL.replaceAll("\\s+", "_") : "device";
    }
}
