import com.sun.jna.Callback;
import com.sun.jna.FunctionMapper;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Menubar2 {

    // --- Core Application Logic ---
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static Thread readerThread;
    private static NativeMacMenuBar nativeMenuBar;

    private static volatile String wattage = "- W";
    private static volatile String cpuFreq = "- GHz";
    private static volatile String gpuFreq = "- MHz";
    private static volatile String charge = "--:--";

    private static volatile String selectedSource = "Wattage";
    private static final String[] sourceNames = {"Wattage", "CPU Freq", "GPU Freq", "Charge"};
    private static final Map<String, Pointer> sourceMenuItems = new HashMap<>();

    public static void main(String[] args) {
        // This is the most critical change in the entire file.
        // All UI initialization MUST be scheduled to run on the AWT Event Dispatch Thread.
        // On macOS, this is the same as the main AppKit thread.
        // This solves the 'NSWindow drag regions should only be invalidated on the Main Thread!' crash.
        SwingUtilities.invokeLater(() -> {
            // --- This block is now guaranteed to run on the correct thread ---

            // NOTE: Password prompt removed for easy verification.
            String userPwd = "";

            // --- UI SETUP ---
            // Because we are on the correct thread, this will now succeed.
            nativeMenuBar = new NativeMacMenuBar();
            buildMenu();

            // --- DATA MONITORING ---
            updateUi(); // Initial update
            startPowermetricsMonitor(userPwd);
            startChargeMonitor();
        });
    }

    private static void buildMenu() {
        for (String sourceName : sourceNames) {
            Runnable action = () -> {
                selectedSource = sourceName;
                updateUi();
            };
            Pointer menuItem = nativeMenuBar.addCheckboxMenuItem(sourceName, action);
            sourceMenuItems.put(sourceName, menuItem);
        }

        nativeMenuBar.addSeparator();

        nativeMenuBar.addMenuItem("About Watter...", "", () -> {
            JFrame frame = new JFrame("About Watter");
            frame.setSize(300, 150);
            frame.setLocationRelativeTo(null);
            JLabel label = new JLabel("Watter: A Pure JNA Menu Bar Monitor", SwingConstants.CENTER);
            frame.add(label);
            frame.setVisible(true);
        });

        nativeMenuBar.addMenuItem("Quit Watter", "q", () -> {
            scheduler.shutdownNow();
            if (readerThread != null) readerThread.interrupt();
            System.exit(0);
        });
    }

    private static synchronized void updateUi() {
        if (nativeMenuBar == null) return;
        nativeMenuBar.setTitle(getSourceValue(selectedSource));

        for (String sourceName : sourceNames) {
            String label = sourceName + ": " + getSourceValue(sourceName);
            Pointer menuItem = sourceMenuItems.get(sourceName);
            boolean isSelected = sourceName.equals(selectedSource);
            nativeMenuBar.updateMenuItem(menuItem, label, isSelected);
        }
    }

    private static String getSourceValue(String sourceName) {
        return switch (sourceName) {
            case "Wattage" -> wattage;
            case "CPU Freq" -> cpuFreq;
            case "GPU Freq" -> gpuFreq;
            case "Charge" -> charge;
            default -> "N/A";
        };
    }

    // --- Data Fetching Methods ---

    private static void startPowermetricsMonitor(String password) {
        String command = "sudo -S powermetrics -i 2000 --samplers cpu_power,gpu_power";
        try {
            Process process = new ProcessBuilder("zsh", "-c", "echo \"" + password + "\" | " + command).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            readerThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        boolean updated = false;
                        if (line.contains("power")) {
                            wattage = line.substring(line.indexOf(":") + 1).trim(); updated = true;
                        } else if (line.contains("System Average")) {
                            cpuFreq = (Math.round((Double.parseDouble(line.substring(line.indexOf("(") + 1, line.indexOf("Mhz)")).trim()) / 1000.0) * 100.0) / 100.0) + "GHz"; updated = true;
                        } else if (line.contains("active frequency as")) {
                            gpuFreq = Math.round((Double.parseDouble(line.substring(line.lastIndexOf("(") + 1, line.lastIndexOf("Mhz)")).trim()) * 100.0) / 100.0) + "MHz"; updated = true;
                        }
                        if (updated) {
                            // UI updates from a background thread MUST be scheduled back onto the UI thread.
                            SwingUtilities.invokeLater(Menubar2::updateUi);
                        }
                    }
                } catch (IOException ignored) {}
            });
            readerThread.setDaemon(true);
            readerThread.start();
        } catch (IOException e) {
            wattage = "Error";
            SwingUtilities.invokeLater(Menubar2::updateUi);
        }
    }

    private static void startChargeMonitor() {
        Runnable chargeTask = () -> {
            try {
                String result = new String(Runtime.getRuntime().exec("pmset -g batt").getInputStream().readAllBytes());
                String newCharge = charge;
                if (result.contains("discharging") || result.contains("charging")) newCharge = result.substring(result.lastIndexOf(";") + 1, result.indexOf(" remaining")).trim();
                else if (result.contains("charged")) newCharge = "Full";
                else if (result.contains("not charging")) newCharge = "Paused";
                else if (result.contains("AC Power")) newCharge = "AC";

                if (!newCharge.equals(charge)) {
                    charge = newCharge;
                    SwingUtilities.invokeLater(Menubar2::updateUi);
                }
            } catch (IOException e) {
                charge = "Error";
                SwingUtilities.invokeLater(Menubar2::updateUi);
            }
        };
        scheduler.scheduleAtFixedRate(chargeTask, 0, 30, TimeUnit.SECONDS);
    }
}

/**
 * Manages a 100% native NSStatusItem. All methods in this class assume
 * they are already being called on the main AppKit thread.
 */
class NativeMacMenuBar {

    private final Pointer statusItem;
    private final Pointer menu;
    private Pointer target;
    private final Map<Pointer, Runnable> callbackMap = new HashMap<>();
    private static final Pointer nsObjectClass = ObjC.INSTANCE.objc_getClass("NSObject");

    private interface ObjC extends Library {
        FunctionMapper functionMapper = (library, method) -> method.getName().startsWith("objc_msgSend") ? "objc_msgSend" : method.getName();
        Map<String, Object> OPTIONS = new HashMap<>() {{ put(Library.OPTION_FUNCTION_MAPPER, functionMapper); }};
        ObjC INSTANCE = Native.load("objc", ObjC.class, OPTIONS);

        Pointer objc_getClass(String name);
        Pointer sel_getUid(String name);
        boolean class_addMethod(Pointer cls, Pointer selector, Callback callback, String types);
        Pointer objc_msgSend(Pointer receiver, Pointer selector);
        Pointer objc_msgSend(Pointer receiver, Pointer selector, Pointer arg1);
        void objc_msgSend_void(Pointer receiver, Pointer selector, Pointer arg1);
        void objc_msgSend_void(Pointer receiver, Pointer selector, long state);
        void objc_msgSend_void(Pointer receiver, Pointer selector);
        Pointer objc_msgSend_str(Pointer receiver, Pointer selector, String arg1);
    }

    private interface ActionCallback extends Callback { void invoke(Pointer self, Pointer selector, Pointer sender); }

    public NativeMacMenuBar() {
        Pointer pool = objc_msgSend_alloc_init("NSAutoreleasePool");

        Pointer nsStatusBarClass = ObjC.INSTANCE.objc_getClass("NSStatusBar");
        Pointer systemStatusBar = ObjC.INSTANCE.objc_msgSend(nsStatusBarClass, sel("systemStatusBar"));
        this.statusItem = ObjC.INSTANCE.objc_msgSend(systemStatusBar, sel("statusItemWithLength:"), new Pointer(-1L));

        this.menu = objc_msgSend_alloc_init("NSMenu");
        ObjC.INSTANCE.objc_msgSend_void(this.statusItem, sel("setMenu:"), this.menu);

        this.target = ObjC.INSTANCE.objc_msgSend(nsObjectClass, sel("alloc"));
        this.target = ObjC.INSTANCE.objc_msgSend(this.target, sel("init"));
        ActionCallback actionCallback = (self, selector, sender) -> {
            Runnable runnable = callbackMap.get(sender);
            if (runnable != null) runnable.run();
        };
        ObjC.INSTANCE.class_addMethod(nsObjectClass, sel("handleAction:"), actionCallback, "v@:@");

        ObjC.INSTANCE.objc_msgSend_void(pool, sel("drain"));
    }

    public void setTitle(String text) {
        Pointer pool = objc_msgSend_alloc_init("NSAutoreleasePool");
        Pointer button = ObjC.INSTANCE.objc_msgSend(this.statusItem, sel("button"));
        ObjC.INSTANCE.objc_msgSend_void(button, sel("setTitle:"), toNSString(text));
        ObjC.INSTANCE.objc_msgSend_void(pool, sel("drain"));
    }

    public void addMenuItem(String title, String key, Runnable action) {
        Pointer menuItem = createMenuItem(title, "handleAction:", key);
        callbackMap.put(menuItem, action);
        ObjC.INSTANCE.objc_msgSend_void(this.menu, sel("addItem:"), menuItem);
    }

    public Pointer addCheckboxMenuItem(String title, Runnable action) {
        Pointer menuItem = createMenuItem(title, "handleAction:", "");
        callbackMap.put(menuItem, action);
        ObjC.INSTANCE.objc_msgSend_void(this.menu, sel("addItem:"), menuItem);
        return menuItem;
    }

    public void updateMenuItem(Pointer menuItem, String title, boolean isChecked) {
        ObjC.INSTANCE.objc_msgSend_void(menuItem, sel("setTitle:"), toNSString(title));
        ObjC.INSTANCE.objc_msgSend_void(menuItem, sel("setState:"), isChecked ? 1L : 0L);
    }

    public void addSeparator() {
        Pointer separator = ObjC.INSTANCE.objc_msgSend(ObjC.INSTANCE.objc_getClass("NSMenuItem"), sel("separatorItem"));
        ObjC.INSTANCE.objc_msgSend_void(this.menu, sel("addItem:"), separator);
    }

    private Pointer createMenuItem(String title, String action, String key) {
        Pointer menuItem = objc_msgSend_alloc_init("NSMenuItem");
        ObjC.INSTANCE.objc_msgSend_void(menuItem, sel("setTitle:"), toNSString(title));
        ObjC.INSTANCE.objc_msgSend_void(menuItem, sel("setKeyEquivalent:"), toNSString(key));
        ObjC.INSTANCE.objc_msgSend_void(menuItem, sel("setTarget:"), this.target);
        ObjC.INSTANCE.objc_msgSend_void(menuItem, sel("setAction:"), sel(action));
        return menuItem;
    }

    private static Pointer toNSString(String s) {
        return ObjC.INSTANCE.objc_msgSend_str(ObjC.INSTANCE.objc_getClass("NSString"), sel("stringWithUTF8String:"), s);
    }

    private static Pointer objc_msgSend_alloc_init(String className) {
        Pointer clazz = ObjC.INSTANCE.objc_getClass(className);
        Pointer instance = ObjC.INSTANCE.objc_msgSend(clazz, sel("alloc"));
        return ObjC.INSTANCE.objc_msgSend(instance, sel("init"));
    }

    private static Pointer sel(String selector) { return ObjC.INSTANCE.sel_getUid(selector); }
}