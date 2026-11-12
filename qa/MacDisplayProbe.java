import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

class MacDisplayProbe {
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new AssertionError("No macOS graphical session");
        }
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        if (!UIManager.getLookAndFeel().getClass().getName().contains("Aqua")) {
            throw new AssertionError("Expected the native macOS look and feel");
        }
        JFrame[] window = new JFrame[1];
        Point[] target = new Point[1];
        CountDownLatch opened = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("Mac desktop input probe");
            window[0] = frame;
            JTextField field = new JTextField("Synthetic test text");
            JPopupMenu popup = new JPopupMenu();
            popup.add(new JMenuItem("Copy"));
            popup.addPopupMenuListener(new PopupMenuListener() {
                public void popupMenuWillBecomeVisible(PopupMenuEvent event) { opened.countDown(); }
                public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {}
                public void popupMenuCanceled(PopupMenuEvent event) {}
            });
            field.setComponentPopupMenu(popup);
            frame.add(field);
            frame.setSize(360, 120);
            frame.setLocation(120, 120);
            frame.setAlwaysOnTop(true);
            frame.setVisible(true);
            frame.toFront();
            target[0] = field.getLocationOnScreen();
        });
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(150);
            robot.mouseMove(target[0].x + 32, target[0].y + 24);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.keyPress(KeyEvent.VK_CONTROL);
            try {
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            } finally {
                robot.keyRelease(KeyEvent.VK_CONTROL);
            }
            if (!opened.await(6, TimeUnit.SECONDS)) {
                throw new AssertionError("Native Control-click did not open the Swing popup");
            }
            System.out.println("Native macOS Control-click opened the menu under " + UIManager.getLookAndFeel().getName());
        } finally {
            SwingUtilities.invokeAndWait(window[0]::dispose);
        }
    }
}
