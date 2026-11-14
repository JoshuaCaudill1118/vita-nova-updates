package main.java.util;

import main.java.db.DatabaseManager;
import main.java.model.User;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JDialog;
import javax.swing.JTextArea;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public final class DisplayProbe {
    private static final String[] CLIENT_SCREENS = {
        "AccessPortalFrame", "ClientPortalFrame", "CheckInCheckOutForm", "NonClientCheckInForm",
        "ClientEventSignupFrame", "ClientEventAttendanceFrame", "FAUCommunityHealthSignupFrame",
        "SharedEventCalendarFrame", "ClientUtilityUsageForm"
    };

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        System.setProperty("vitalink.appearance.preview", "true");
        System.setProperty("vitanova.db.url", "jdbc:postgresql://127.0.0.1:55472/postgres?connectTimeout=3&socketTimeout=8");
        System.setProperty("vitanova.db.user", "merge_test");
        System.setProperty("vitanova.db.password", "synthetic-only");
        DesktopAppearance.installLookAndFeel();
        VitaNovaTheme.install();
        DesktopAppearance.installStaffAppearance();
        DesktopZoomManager.install();
        LoggedInUserSession.setCurrentUser(new User(11, "Preview Staff", "", "", "preview", "", "ADMIN"));

        List<String> issues = new ArrayList<>();
        int width = Integer.parseInt(args[1]);
        for (String name : CLIENT_SCREENS) {
            JFrame screen = onEdt(() -> open(name, width));
            Thread.sleep(250);
            onEdt(() -> {
                screenshot(screen.getRootPane(), output.resolve(name + ".png"));
                if (name.equals("ClientPortalFrame")) {
                    verifyClientPortal(screen, issues);
                }
                inspectVisibleText(screen.getRootPane(), VitaNovaTheme.PAGE_BG, name, issues);
                screen.dispose();
                return null;
            });
        }
        JFrame reports = onEdt(() -> open("ReportDashboard", width));
        verifyReportNavigation(reports, issues);
        onEdt(() -> {
            screenshot(reports.getRootPane(), output.resolve("ReportDashboard.png"));
            verifyReports(reports, issues);
            return null;
        });
        onEdt(() -> { for (Window window : Window.getWindows()) { window.dispose(); } return null; });
        DatabaseManager.shutdown();
        Files.write(output.resolve("review.txt"), issues);
        issues.forEach(System.out::println);
        System.exit(issues.isEmpty() ? 0 : 1);
    }

    private static void verifyReportNavigation(JFrame reports, List<String> issues) throws Exception {
        var dashboardType = reports.getClass();
        var tabsField = dashboardType.getDeclaredField("tabbedPane");
        var loadingField = dashboardType.getDeclaredField("loadingReports");
        var pendingField = dashboardType.getDeclaredField("loadingReportKeys");
        var searchField = dashboardType.getDeclaredField("tableSearchField");
        var refresh = dashboardType.getDeclaredMethod("refreshReports");
        for (var member : List.of(tabsField, loadingField, pendingField, searchField, refresh)) {
            member.setAccessible(true);
        }
        var tabs = (javax.swing.JTabbedPane) tabsField.get(reports);
        var search = (javax.swing.JTextField) searchField.get(reports);
        Callable<Boolean> loaded = () -> !loadingField.getBoolean(reports) && ((java.util.Set<?>) pendingField.get(reports)).isEmpty();
        if (!await(loaded)) {
            issues.add("Reports overview did not finish loading");
            return;
        }
        onEdt(() -> {
            for (int tab = 0; tab < tabs.getTabCount(); tab++) {
                if ("Services".equals(tabs.getTitleAt(tab))) {
                    tabs.setSelectedIndex(tab);
                    return null;
                }
            }
            throw new AssertionError("Services report tab is missing");
        });
        if (!await(loaded)) {
            issues.add("Services report did not finish loading");
            return;
        }
        var servicesField = dashboardType.getDeclaredField("serviceSummaryTable");
        servicesField.setAccessible(true);
        var table = (JTable) servicesField.get(reports);
        onEdt(() -> {
            if (table.getColumnCount() <= 1 && table.getColumnName(0).equals("Status")) {
                issues.add("Services remained on its loading placeholder");
            }
            search.setText("no-report-row-should-match-4721");
            if (table.getRowCount() != 0) {
                issues.add("Report search did not filter the displayed rows");
            }
            search.setText("");
            refresh.invoke(reports);
            return null;
        });
        if (!await(loaded)) {
            issues.add("Reports refresh did not finish");
        }
    }

    private static boolean await(Callable<Boolean> condition) throws Exception {
        for (int attempt = 0; attempt < 90; attempt++) {
            if (onEdt(condition)) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private static JFrame open(String name, int width) throws Exception {
        var type = Class.forName("main.java.ui." + name);
        JFrame screen = switch (name) {
            case "ClientEventSignupFrame", "ClientEventAttendanceFrame", "FAUCommunityHealthSignupFrame" ->
                    (JFrame) type.getConstructor(JFrame.class).newInstance((JFrame) null);
            case "ClientUtilityUsageForm" -> openUtilityForm(type);
            default -> (JFrame) type.getConstructor().newInstance();
        };
        screen.setExtendedState(JFrame.NORMAL);
        screen.setAutoRequestFocus(false);
        screen.setLocation(50, 50);
        screen.setSize(width, 720);
        screen.setVisible(true);
        return screen;
    }

    private static JFrame openUtilityForm(Class<?> type) throws Exception {
        // The read-only fixture can show a notice if nobody is checked in.
        var closeFixtureNotice = new javax.swing.Timer(100, event -> {
            for (Window window : Window.getWindows()) {
                if (window instanceof JDialog dialog && dialog.isShowing()) {
                    dialog.dispose();
                }
            }
        });
        closeFixtureNotice.start();
        try {
            return (JFrame) type.getConstructor(JFrame.class, String.class).newInstance(null, "Laundry");
        } finally {
            closeFixtureNotice.stop();
        }
    }

    private static void verifyClientPortal(JFrame screen, List<String> issues) {
        List<JTextArea> messages = new ArrayList<>();
        collect(screen, JTextArea.class, messages);
        if (messages.size() < 8) {
            issues.add("Client Portal help still uses fixed-width HTML");
        }
        for (JTextArea message : messages) {
            try {
                var last = message.modelToView2D(message.getDocument().getLength());
                if (last != null && last.getMaxY() > message.getHeight() + 1) {
                    issues.add("Client Portal clips help text: " + message.getText());
                }
            } catch (javax.swing.text.BadLocationException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    private static void verifyReports(JFrame reports, List<String> issues) throws Exception {
        var factory = reports.getClass().getDeclaredMethod("createTable");
        factory.setAccessible(true);
        JTable table = (JTable) factory.invoke(reports);
        String narrative = "A long report value should remain available in full. Youth & Guests <fixture>. ".repeat(3);
        table.setModel(new DefaultTableModel(new Object[][]{{"10", narrative}, {"2", "short"}},
                new String[]{"Count", "Long report header that needs more than seventy-five pixels"}));
        var scroller = new JScrollPane(table);
        scroller.setSize(300, 150);
        scroller.doLayout();
        table.setSize(table.getPreferredSize());
        table.doLayout();
        scroller.doLayout();
        var column = table.getColumnModel().getColumn(1);
        if (column.getWidth() < 300 || !scroller.getHorizontalScrollBar().isVisible()) {
            issues.add("Long report header did not grow or the report cannot scroll horizontally");
        }
        Rectangle rect = table.getCellRect(0, 1, false);
        var hover = new MouseEvent(table, MouseEvent.MOUSE_MOVED, 0, 0, rect.x + 3, rect.y + 3, 0, false);
        String tooltip = table.getToolTipText(hover);
        if (tooltip == null || !tooltip.contains("&lt;fixture&gt;") || !tooltip.contains("&amp;")) {
            issues.add("Clipped report cell did not expose its full escaped value");
        }
        table.setRowSelectionInterval(0, 0);
        var selected = table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
        if (!selected.getBackground().equals(table.getSelectionBackground())) {
            issues.add("Report selection did not use the selection palette");
        }
        @SuppressWarnings("unchecked")
        var sorter = (TableRowSorter<DefaultTableModel>) table.getRowSorter();
        sorter.setRowFilter(RowFilter.regexFilter("short"));
        if (table.getRowCount() != 1 || !"short".equals(table.getValueAt(0, 1))) {
            issues.add("Report filter did not expose the matching row");
        }
        table.setModel(new DefaultTableModel(new Object[][]{{"Loading report…"}}, new String[]{"Status"}));
        table.doLayout();
        table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);

        if (DesktopAppearance.style().dark()) {
            List<JPanel> panels = new ArrayList<>();
            collect(reports, JPanel.class, panels);
            for (var panel : panels) {
                if (panel.getClass().getSimpleName().endsWith("ChartPanel")) {
                    panel.setSize(400, 220);
                    var image = new BufferedImage(400, 220, BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics();
                    panel.printAll(graphics);
                    graphics.dispose();
                    if (image.getRGB(390, 210) == 0xffffffff) {
                        issues.add("Report chart paints white in Dark mode: " + panel.getClass().getSimpleName());
                    }
                }
            }
        }
    }

    private static <T> void collect(Component component, Class<T> type, List<T> matches) {
        if (type.isInstance(component)) { matches.add(type.cast(component)); }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) { collect(child, type, matches); }
        }
    }

    private static void inspectVisibleText(Component component, java.awt.Color surface, String screen, List<String> issues) {
        if (!component.isVisible() || component.getWidth() <= 0 || component.getHeight() <= 0) {
            return;
        }
        if (component instanceof javax.swing.JComponent value && value.isOpaque() && value.getBackground() != null) {
            surface = value.getBackground();
        }
        String text = component instanceof JLabel label ? label.getText()
                : component instanceof AbstractButton button ? button.getText()
                : component instanceof JTextComponent input ? input.getText() : null;
        if (text != null && !text.isBlank() && component.isEnabled() && component.getForeground() != null
                && DesktopAppearance.contrast(component.getForeground(), surface) < 4.5) {
            issues.add(screen + " low contrast: " + text.replace('\n', ' '));
        }
        if (component instanceof AbstractButton button && text != null && !text.isBlank() && !text.startsWith("<html>")) {
            var insets = button.getInsets();
            if (button.getFontMetrics(button.getFont()).stringWidth(text) > button.getWidth() - insets.left - insets.right + 3) {
                issues.add(screen + " clipped button: " + text);
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                inspectVisibleText(child, surface, screen, issues);
            }
        }
    }

    private static void screenshot(Component root, Path file) throws Exception {
        var image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        root.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", file.toFile());
    }

    private static <T> T onEdt(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
