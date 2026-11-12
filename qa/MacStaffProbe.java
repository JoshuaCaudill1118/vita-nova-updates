package main.java.util;

import main.java.ui.IntakePacketFrame;
import main.java.ui.RhyServicesPlanFrame;

import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.prefs.Preferences;

public final class MacStaffProbe {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        boolean dark = Boolean.parseBoolean(args[1]);
        System.setProperty("vitanova.db.url", "jdbc:postgresql://127.0.0.1:1/appearance_probe");
        System.setProperty("vitanova.db.user", "fixture");
        System.setProperty("vitanova.db.password", "synthetic-only");
        Preferences settings = Preferences.userNodeForPackage(DesktopAppearance.class).node("appearance");
        if ("SYSTEM".equals(mode)) {
            settings.remove("mode");
        } else {
            settings.put("mode", mode);
        }
        settings.flush();
        DesktopAppearance.installLookAndFeel();
        require(DesktopAppearance.style().dark() == dark, "System or override dark appearance");
        require(DesktopAppearance.style().colorblind() == "COLORBLIND".equals(mode), "Colorblind preference");
        require(DesktopAppearance.style().lowVision() == "LOW_VISION".equals(mode), "Low Vision preference");
        if (!dark && !"LOW_VISION".equals(mode)) {
            require(UIManager.getLookAndFeel().getClass().getName().contains("Aqua"), "Native Aqua appearance");
        }
        onEdt(() -> {
            VitaNovaTheme.install();
            DesktopTextMenu.install();
            DesktopAppearance.installStaffMenu();
            DesktopZoomManager.install();
            return null;
        });
        Robot robot = new Robot();
        robot.setAutoDelay(125);
        verify(() -> new IntakePacketFrame(null), robot);
        verify(() -> new RhyServicesPlanFrame(null), robot);
        System.out.println("Native Mac appearance, Control-click, Select All and clipboard states passed for " + mode + " dark=" + dark);
        System.exit(0);
    }

    private static void verify(Callable<JFrame> create, Robot robot) throws Exception {
        JFrame frame = onEdt(() -> {
            JFrame form = create.call();
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            form.setExtendedState(Frame.NORMAL);
            form.setBounds(30, 65, Math.min(1120, screen.width - 70), Math.min(790, screen.height - 105));
            form.setAlwaysOnTop(true);
            form.setVisible(true);
            form.toFront();
            return form;
        });
        try {
            robot.waitForIdle();
            JTextComponent field = onEdt(() -> firstField(frame));
            require(field != null, "Visible editable text field on " + frame.getTitle());
            Point point = onEdt(() -> {
                field.setText("Synthetic field");
                field.requestFocusInWindow();
                field.scrollRectToVisible(new Rectangle(0, 0, 100, 36));
                Point location = field.getLocationOnScreen();
                Rectangle visible = field.getVisibleRect();
                require(!visible.isEmpty(), "Text field scrolled into view");
                return new Point(location.x + visible.x + 12, location.y + visible.y + Math.min(12, visible.height / 2));
            });
            robot.mouseMove(point.x, point.y);
            click(robot);
            onEdt(() -> {
                field.selectAll();
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection("Fixture pasted text"), null);
                return null;
            });
            controlClick(robot);
            JPopupMenu menu = field.getComponentPopupMenu();
            require(menu != null && menuVisible(menu), "Control-click popup on " + frame.getTitle());
            onEdt(() -> {
                require("Synthetic field".equals(field.getSelectedText()), "Preserved selection on " + frame.getTitle());
                require(item(menu, 0, "Copy").isEnabled(), "Copy available with selection");
                require(item(menu, 1, "Paste").isEnabled(), "Paste available with text");
                item(menu, 1, "Paste").doClick(0);
                require("Fixture pasted text".equals(field.getText()), "Clipboard text inserted");
                menu.setVisible(false);
                field.setCaretPosition(0);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
                    public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
                    public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
                    public Object getTransferData(DataFlavor flavor) { return new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB); }
                }, null);
                return null;
            });
            controlClick(robot);
            require(menuVisible(menu), "Control-click reopened the menu");
            onEdt(() -> {
                require(!item(menu, 1, "Paste").isEnabled(), "Paste disabled for an image");
                require(item(menu, 3, "Select All").isEnabled(), "Select All available");
                item(menu, 3, "Select All").doClick(0);
                require("Fixture pasted text".equals(field.getSelectedText()), "Select All selected the whole text");
                menu.setVisible(false);
                return null;
            });
            System.out.println("Passed on " + frame.getTitle());
        } finally {
            onEdt(() -> { frame.dispose(); return null; });
        }
    }

    private static boolean menuVisible(JPopupMenu menu) throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (onEdt(menu::isVisible)) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private static void controlClick(Robot robot) {
        robot.keyPress(KeyEvent.VK_CONTROL);
        try {
            click(robot);
        } finally {
            robot.keyRelease(KeyEvent.VK_CONTROL);
        }
        robot.waitForIdle();
    }

    private static void click(Robot robot) {
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static JMenuItem item(JPopupMenu menu, int index, String name) {
        JMenuItem entry = (JMenuItem) menu.getComponent(index);
        require(name.equals(entry.getText()), "Expected menu item " + name);
        return entry;
    }

    private static JTextComponent firstField(Component component) {
        if (component instanceof JTextArea text && text.isShowing() && text.isEditable() && text.isEnabled()) {
            return text;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTextComponent field = firstField(child);
                if (field != null) {
                    return field;
                }
            }
        }
        return null;
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
