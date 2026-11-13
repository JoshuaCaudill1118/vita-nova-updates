package main.java.util;

import main.java.db.DatabaseManager;
import main.java.model.User;
import main.java.ui.UserDashboard;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;

public final class ProfileDashboardProbe {
    private static final Color[] CORNERS = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        System.setProperty("vitanova.db.url", "jdbc:postgresql://127.0.0.1:55472/postgres?connectTimeout=3&socketTimeout=8");
        System.setProperty("vitanova.db.user", "merge_test");
        System.setProperty("vitanova.db.password", "synthetic-only");
        LoggedInUserSession.setCurrentUser(new User(11, "Preview Staff", "", "", "preview", "", "ADMIN"));
        DesktopAppearance.installLookAndFeel();
        VitaNovaTheme.install();
        DesktopTextMenu.install();
        DesktopAppearance.installStaffAppearance();
        DesktopZoomManager.install();

        try {
            createFixtures();
            verify(output, Integer.parseInt(args[1]));
            System.out.println("First-open details and uncropped portrait and landscape photos passed.");
        } finally {
            for (var window : java.awt.Window.getWindows()) {
                SwingUtilities.invokeAndWait(window::dispose);
            }
            DatabaseManager.shutdown();
        }
    }

    private static void verify(Path output, int width) throws Exception {
        var open = new FutureTask<>(() -> {
            var dashboard = new UserDashboard("PhotoFixture");
            dashboard.setExtendedState(Frame.NORMAL);
            dashboard.setAutoRequestFocus(false);
            dashboard.setLocation(0, 0);
            dashboard.setSize(width, 730);
            dashboard.setVisible(true);
            return dashboard;
        });
        SwingUtilities.invokeAndWait(open);
        var dashboard = open.get();
        waitUntil(() -> field(dashboard, "profileTable", JTable.class).getRowCount() == 2);
        inspectOnEdt(() -> {
            var scroll = field(dashboard, "formScrollPane", JScrollPane.class);
            var photo = field(dashboard, "profilePhotoPreviewLabel", JLabel.class);
            var split = (JSplitPane) scroll.getParent();
            var viewport = scroll.getViewport();
            require(dashboard.getContentPane() instanceof javax.swing.JPanel, "Dashboard opened inside an outer scroller");
            require(viewport.getViewPosition().equals(new Point()), "Details did not start at top left");
            require(scroll.getWidth() >= 430, "Details opened too narrow: " + scroll.getWidth());
            require(split.getDividerLocation() > 200, "Profiles list is too narrow");
            var preview = SwingUtilities.convertRectangle(photo.getParent(), photo.getBounds(), viewport);
            require(preview.width >= 150 && viewport.getVisibleRect().contains(preview),
                    "Photo frame is clipped by the details viewport: " + preview + " / " + viewport.getVisibleRect());
            require(photo.getIcon() == null && photo.getText().contains("to view photo"), "Incorrect initial photo prompt");
            for (String name : new String[]{"programFilterButton", "clearProgramFilterButton"}) {
                var button = field(dashboard, name, javax.swing.JButton.class);
                require(button.getVisibleRect().width == button.getWidth()
                                && button.getVisibleRect().height == button.getHeight(),
                        "The program filter button is clipped: " + button.getText());
            }
            snapshot(dashboard.getRootPane(), output.resolve("first-open.png"));
        });

        for (var fixture : new String[]{"Portrait", "Landscape"}) {
            inspectOnEdt(() -> {
                var table = field(dashboard, "profileTable", JTable.class);
                for (int row = 0; row < table.getRowCount(); row++) {
                    if (fixture.equals(table.getModel().getValueAt(table.convertRowIndexToModel(row), 3))) {
                        table.setRowSelectionInterval(row, row);
                        return;
                    }
                }
                throw new AssertionError("Missing fixture " + fixture);
            });
            waitUntil(() -> field(dashboard, "profilePhotoPreviewLabel", JLabel.class).getIcon() != null);
            inspectOnEdt(() -> {
                var label = field(dashboard, "profilePhotoPreviewLabel", JLabel.class);
                var icon = (ImageIcon) label.getIcon();
                int w = icon.getIconWidth();
                int h = icon.getIconHeight();
                require(w <= label.getWidth() && h <= label.getHeight(), "Photo exceeds its frame");
                require(Math.abs((w / (double) h) - ("Portrait".equals(fixture) ? 0.5 : 2.0)) < 0.08,
                        "Photo proportions changed: " + w + "x" + h);
                var image = (BufferedImage) icon.getImage();
                int[] pixels = {image.getRGB(2, 2), image.getRGB(w - 3, 2), image.getRGB(2, h - 3), image.getRGB(w - 3, h - 3)};
                for (int i = 0; i < pixels.length; i++) {
                    require(pixels[i] == CORNERS[i].getRGB(), "Source corner " + i + " was cropped");
                }
                snapshot(dashboard.getRootPane(), output.resolve(fixture.toLowerCase() + ".png"));
            });
        }

        inspectOnEdt(() -> {
            var scroll = field(dashboard, "formScrollPane", JScrollPane.class);
            var split = (JSplitPane) scroll.getParent();
            split.setDividerLocation(split.getWidth() - 310);
            dashboard.validate();
            require(scroll.getHorizontalScrollBar().isVisible(), "Narrow details pane cannot scroll horizontally");
            var tabs = find(scroll, javax.swing.JTabbedPane.class);
            require(tabs != null && tabs.getTabCount() == 6, "Profile tabs are missing");
            tabs.setSelectedIndex(5);
        });
    }

    private static void createFixtures() throws Exception {
        try (var connection = DatabaseManager.getConnection()) {
            try (var profile = connection.prepareStatement("""
                    INSERT INTO profiles (id, first_name, last_name, name, birth_date, age)
                    VALUES (?, 'PhotoFixture', ?, ?, '01-01-2002', 24) ON CONFLICT DO NOTHING
                    """);
                 var file = connection.prepareStatement("""
                    INSERT INTO profile_files (id, profile_id, file_name, mime_type, file_data, category)
                    VALUES (?, ?, ?, 'image/png', ?, 'Profile Photo')
                    ON CONFLICT (id) DO UPDATE SET file_data = EXCLUDED.file_data
                    """)) {
                for (int index = 0; index < 2; index++) {
                    int id = 913100 + index;
                    String kind = index == 0 ? "Portrait" : "Landscape";
                    profile.setInt(1, id);
                    profile.setString(2, kind);
                    profile.setString(3, "PhotoFixture " + kind);
                    profile.executeUpdate();
                    int w = index == 0 ? 200 : 400;
                    int h = index == 0 ? 400 : 200;
                    var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics();
                    for (int corner = 0; corner < CORNERS.length; corner++) {
                        graphics.setColor(CORNERS[corner]);
                        graphics.fillRect((corner % 2) * (w / 2), (corner / 2) * (h / 2), w / 2, h / 2);
                    }
                    graphics.dispose();
                    var bytes = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", bytes);
                    file.setInt(1, id);
                    file.setInt(2, id);
                    file.setString(3, kind + ".png");
                    file.setBytes(4, bytes.toByteArray());
                    file.executeUpdate();
                }
            }
        }
    }

    private static void snapshot(JRootPane root, Path output) throws Exception {
        root.validate();
        var image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        root.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static <T> T field(Object instance, String name, Class<T> type) throws Exception {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(instance));
    }

    private static <T> T find(Component component, Class<T> type) {
        if (type.isInstance(component)) {
            return type.cast(component);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void waitUntil(java.util.concurrent.Callable<Boolean> check) throws Exception {
        for (int attempt = 0; attempt < 80; attempt++) {
            var result = new FutureTask<>(check);
            SwingUtilities.invokeAndWait(result);
            if (result.get()) {
                SwingUtilities.invokeAndWait(() -> {});
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Dashboard data did not finish loading");
    }

    private static void inspectOnEdt(CheckedAction check) throws Exception {
        var result = new FutureTask<Void>(() -> { check.run(); return null; });
        SwingUtilities.invokeAndWait(result);
        result.get();
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }
}
