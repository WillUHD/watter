import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class Menubar {

    public static String userPwd;
    private static final SystemTray tray = SystemTray.getSystemTray();
    private static TrayIcon trayIcon;
    private static PopupMenu menu;
    private static MenuItem aboutItem;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static volatile ScheduledFuture<?> drawTask;
    private static Thread readerThread;
    private static final Color color = new Color(255, 255, 255, 225);
    private static Font SFCompact;

    private static volatile String wattage = "- W";
    private static volatile String cpuFreq = "- GHz";
    private static volatile String gpuFreq = "- MHz";
    private static volatile String charge = "--:--";

    private static volatile String selectedSource = "Wattage";
    private static final Map<String, CheckboxMenuItem> sourceMenuItems = new HashMap<>();
    private static final String[] sourceNames = {"Wattage", "CPU Freq", "GPU Freq", "Charge"};

    static {
        try {
            SFCompact = Font.createFont(Font.TRUETYPE_FONT,
                            Objects.requireNonNull(Menubar.class.getResourceAsStream("SFCompact.otf")))
                            .deriveFont(Font.BOLD, 64f);
        } catch (FontFormatException | IOException ignored) {}
    }


    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "Watter");
        System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua");
        Taskbar.getTaskbar().setIconImage(readImg("watter.png"));

        do {
            JPasswordField passwordField = new JPasswordField(20);
            Object[] dialogContent = {
                    """
                        Please enter an administrator's password
                        in order to properly monitor performance.
                        Your data will not be misused.
                        """,
                    passwordField
            };

            passwordField.addAncestorListener(new AncestorListener() {
                public void ancestorAdded(AncestorEvent event) {passwordField.requestFocusInWindow();}
                public void ancestorRemoved(AncestorEvent event) {}
                public void ancestorMoved(AncestorEvent event) {}
            });

            int option = JOptionPane.showConfirmDialog(null, dialogContent, "Enter Password", JOptionPane.OK_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (option == JOptionPane.OK_OPTION) {
                char[] password = passwordField.getPassword();
                userPwd = new String(password);
            } else System.exit(0);
        } while (SMC.start("echo \"" + userPwd + "\" | sudo -S pmset").contains("incorrect"));

        makeMenu();
        trayIcon = new TrayIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        trayIcon.setImageAutoSize(false);
        trayIcon.setPopupMenu(menu);
        try {tray.add(trayIcon);}
        catch (AWTException e) {throw new RuntimeException(e);}
        updateTray();
        startTask();
        monitorCharge();
    }

    private static void startTask() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(SMC.send("echo \"" + userPwd + "\" | sudo -S zsh -c 'sudo powermetrics -i 2000 --samplers cpu_power,gpu_power'")));

        readerThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    boolean updated = false;
                    if (line.contains("power")) {
                        String newWattage = line.substring(line.indexOf(":") + 1).trim();
                        if (!newWattage.equals(wattage)) {wattage = newWattage; updated = true;}
                    } else if (line.contains("System Average")) {
                        String newCpuFreq = (Math.round((Double.parseDouble(line.substring(line.indexOf("(") + 1, line.indexOf("Mhz)")).trim()) / 1000.0) * 100.0) / 100.0) + "GHz";
                        if (!newCpuFreq.equals(cpuFreq)) {cpuFreq = newCpuFreq; updated = true;}
                    } else if (line.contains("active frequency as")) {
                        String newGpuFreq = Math.round((Double.parseDouble(line.substring(line.lastIndexOf("(") + 1, line.lastIndexOf("Mhz)")).trim()) * 100.0) / 100.0) + "MHz";
                        if (!newGpuFreq.equals(gpuFreq)) {gpuFreq = newGpuFreq; updated = true;}
                    }
                    if (updated) updateTray();
                }
            } catch (IOException ignored) {}
        });

        readerThread.setDaemon(true);
        readerThread.start();
    }

    private static void monitorCharge() {
        Runnable chargeTask = Menubar::parseCharge;
        scheduler.scheduleAtFixedRate(chargeTask, 0, 60, TimeUnit.SECONDS);
    }

    private static void parseCharge() {
        String result = SMC.start("pmset -g batt");
        String newCharge = charge;
        if (result.contains("discharging") || result.contains("charging"))
            newCharge = result.substring(result.lastIndexOf(";") + 1, result.indexOf(" remaining")).trim();
        else if (result.contains("charged")) newCharge = "Full";
        else if (result.contains("not charging")) newCharge = "Paused";
        else if (result.contains("AC Power")) newCharge = "AC";
        if (!newCharge.equals(charge)) {charge = newCharge; updateTray();}
    }

    private static String getSource(String sourceName) {
        return switch (sourceName) {
            case "Wattage" -> wattage;
            case "CPU Freq" -> cpuFreq;
            case "GPU Freq" -> gpuFreq;
            case "Charge" -> charge;
            default -> "N/A";
        };
    }

    private static synchronized void updateTray() {
        if (trayIcon == null) return;
        String mainText = getSource(selectedSource);
        trayIcon.setImage(makeTextImage(mainText));

        List<String> otherSources = Arrays.stream(sourceNames).filter(name -> !name.equals(selectedSource)).toList();
        StringBuilder tooltipText = new StringBuilder();
        for (int i = 0; i < otherSources.size(); i++) {
            String sourceName = otherSources.get(i);
            tooltipText.append(sourceName).append(": ").append(getSource(sourceName));
            if (i < otherSources.size() - 1) tooltipText.append("\n");
        }
        trayIcon.setToolTip(tooltipText.toString());

        for (String sourceName : sourceNames) {
            CheckboxMenuItem item = sourceMenuItems.get(sourceName);
            if (item != null) {
                String label = sourceName + ": " + getSource(sourceName);
                item.setLabel(label);
            }
        }
    }

    private static Image makeTextImage(String text) {
        final int scale = 2;
        final int padding = 12;
        BufferedImage temp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tg = temp.createGraphics();
        tg.setFont(SFCompact);
        FontMetrics fm = tg.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textAscent = fm.getAscent();
        int textDescent = fm.getDescent();
        tg.dispose();
        int scaledWidth = (textWidth + 2) * scale;
        int scaledHeight = (textAscent + textDescent + padding * 2) * scale;
        BufferedImage highRes = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = highRes.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(scale, scale);
        g.setFont(SFCompact);
        g.setColor(color);
        g.drawString(text, 0, padding + textAscent);
        g.dispose();
        int finalWidth = textWidth + 2;
        int finalHeight = textAscent + textDescent + padding * 2;
        BufferedImage finalImage = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = finalImage.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.drawImage(highRes, 0, 0, finalWidth, finalHeight, null);
        g2.dispose();
        return finalImage;
    }

    private static void makeMenu(){
        menu = new PopupMenu();

        ItemListener radioListener = e -> {
            CheckboxMenuItem selectedItem = (CheckboxMenuItem) e.getSource();
            if (!selectedItem.getState()) {selectedItem.setState(true); return;}

            String newSource = selectedItem.getActionCommand();
            if (!newSource.equals(selectedSource)) {
                CheckboxMenuItem oldItem = sourceMenuItems.get(selectedSource);
                if (oldItem != null) oldItem.setState(false);
                selectedSource = newSource;
                updateTray();
            }
        };

        for (String name : sourceNames) {
            CheckboxMenuItem cbItem = new CheckboxMenuItem(name);
            cbItem.setActionCommand(name);
            cbItem.setState(name.equals(selectedSource));
            cbItem.addItemListener(radioListener);
            menu.add(cbItem);
            sourceMenuItems.put(name, cbItem);
        }

        menu.addSeparator();

        aboutItem = new MenuItem("About Watter...");
        aboutItem.addActionListener(e -> showAbout());

        MenuItem quit = new MenuItem("Quit Watter");
        quit.setShortcut(new MenuShortcut(KeyEvent.VK_Q));
        quit.addActionListener(e -> {
            if(drawTask != null) drawTask.cancel(false);
            scheduler.shutdownNow();
            if (readerThread != null) readerThread.interrupt();
            System.exit(0);
        });

        menu.add(aboutItem);
        menu.add(quit);
    }

    private static void showAbout() {
        aboutItem.setEnabled(false);
        BufferedImage about = readImg("about.png");
        JFrame frame = new JFrame("About Watter");
        frame.getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        frame.getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        frame.getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        frame.setResizable(false);
        JPanel panel = new JPanel(){
            protected void paintComponent(Graphics g){
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.drawImage(about, 0, 0, 480, 240, null);
                g2d.dispose();
            }
        };
        panel.setLayout(null);
        JButton button = new JButton();
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setOpaque(false);
        button.setFocusPainted(false);
        button.setBounds(190,100,250,72);
        button.addActionListener(e -> {
            Desktop d = Desktop.getDesktop();
            try {d.browse(URI.create("https://github.com/WillUHD"));}
            catch (IOException ignored){}
            frame.hide();
            aboutItem.setEnabled(true);
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                frame.hide();
                aboutItem.setEnabled(true);
            }
        });
        frame.setAutoRequestFocus(true);
        frame.setContentPane(panel);
        frame.add(button);
        frame.setSize(480,240);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static BufferedImage readImg(String path){
        BufferedImage img = null;
        try {img = ImageIO.read(Objects.requireNonNull(
                Menubar.class.getClassLoader().getResourceAsStream(path)));
        } catch (IOException ignored){}
        return img;
    }
}