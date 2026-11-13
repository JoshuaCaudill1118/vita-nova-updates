package main.java.util;

import main.java.db.DatabaseManager;
import main.java.model.User;

import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.FutureTask;

public final class DarkTableAudit {
    private static final List<String> DASHBOARDS = List.of(
            "UserDashboard", "TodayOperationsBoardFrame", "MyWorkFrame", "SupervisorExceptionsDialog",
            "EventDashboard", "GuestLogDashboard", "ReportDashboard", "StaffNotificationDialog",
            "PendingCheckInVerificationFrame", "IntakeHubFrame", "MassEmailDialog", "ServiceRoleAssignmentFrame",
            "SleepSpotDashboard", "MasterFormsDashboard", "FAUHealthRosterFrame", "DonationReceiptFrame",
            "BusPassDashboard", "ProgramEnrollmentDashboardFrame", "ConsentReleaseDashboardFrame",
            "ReferralPartnerResponseDashboardFrame"
    );

    public static void main(String[] args) throws Exception {
        System.setProperty("vitalink.appearance.preview", "true");
        System.setProperty("vitanova.db.url", "jdbc:postgresql://127.0.0.1:55472/postgres?connectTimeout=3&socketTimeout=8");
        System.setProperty("vitanova.db.user", "merge_test");
        System.setProperty("vitanova.db.password", "synthetic-only");
        LoggedInUserSession.setCurrentUser(new User(11, "Preview Staff", "", "", "preview", "", "ADMIN"));
        DesktopAppearance.installLookAndFeel();
        VitaNovaTheme.install();
        DesktopAppearance.installStaffAppearance();
        DesktopZoomManager.install();

        List<String> notes = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        JFrame owner = new JFrame();
        try {
            for (String name : DASHBOARDS) {
                Window window = null;
                try {
                    window = onEdt(() -> open(name, owner));
                    Thread.sleep("ReportDashboard".equals(name) ? 1200 : 460);
                    Window opened = window;
                    onEdt(() -> {
                        var tables = new ArrayList<JTable>();
                        collectTables(opened, tables, new IdentityHashMap<>());
                        notes.add(name + " | " + tables.size() + " tables");
                        for (int index = 0; index < tables.size(); index++) {
                            auditTable(name + "[" + (index + 1) + "]", tables.get(index), issues);
                        }
                        return null;
                    });
                } catch (Exception failure) {
                    issues.add(name + " could not be checked: " + failure);
                } finally {
                    if (window != null) {
                        SwingUtilities.invokeAndWait(window::dispose);
                    }
                }
            }
        } finally {
            SwingUtilities.invokeAndWait(owner::dispose);
            DatabaseManager.shutdown();
        }
        notes.addAll(issues);
        notes.forEach(System.out::println);
        Files.write(Path.of(args[0]), notes);
        System.exit(issues.isEmpty() ? 0 : 1);
    }

    private static Window open(String name, JFrame owner) throws Exception {
        Class<?> type = Class.forName("main.java.ui." + name);
        Window window = switch (name) {
            case "SupervisorExceptionsDialog" -> (Window) type.getConstructor(Window.class).newInstance(owner);
            case "StaffNotificationDialog", "MassEmailDialog" -> (Window) type.getConstructor(Frame.class).newInstance(owner);
            default -> (Window) type.getConstructor().newInstance();
        };
        if (window instanceof JDialog dialog) {
            dialog.setModalityType(Dialog.ModalityType.MODELESS);
        }
        window.setAutoRequestFocus(false);
        window.setLocation(-10000, -10000);
        window.setSize(1240, 820);
        window.setVisible(true);
        if ("GuestLogDashboard".equals(name)) {
            var tabs = type.getDeclaredField("youthLogTabbedPane");
            tabs.setAccessible(true);
            ((JTabbedPane) tabs.get(window)).setSelectedIndex(1);
        }
        return window;
    }

    private static void collectTables(Component component, List<JTable> tables, IdentityHashMap<Component, Boolean> seen) {
        if (seen.put(component, true) != null) {
            return;
        }
        if (component instanceof JTable table) {
            tables.add(table);
        }
        if (component instanceof JTabbedPane tabs) {
            for (int index = 0; index < tabs.getTabCount(); index++) {
                collectTables(tabs.getComponentAt(index), tables, seen);
            }
        } else if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectTables(child, tables, seen);
            }
        }
    }

    private static void auditTable(String name, JTable table, List<String> issues) {
        if (table.getColumnCount() == 0) {
            issues.add(name + " did not load any columns");
            return;
        }
        if (!(table.getModel() instanceof DefaultTableModel model)) {
            issues.add(name + " has an unsupported fixture model: " + table.getModel().getClass().getSimpleName());
            return;
        }
        try {
            while (model.getRowCount() < 3) {
                Object[] data = new Object[model.getColumnCount()];
                for (int column = 0; column < data.length; column++) {
                    Class<?> type = model.getColumnClass(column);
                    String label = model.getColumnName(column).toLowerCase(java.util.Locale.ROOT);
                    if (type == Boolean.class) {
                        data[column] = model.getRowCount() % 2 != 0;
                    } else if (type == Integer.class || type == Long.class || Number.class.isAssignableFrom(type)
                            || ("EventDashboard[1]".equals(name) && column == 0)) {
                        data[column] = 42;
                    } else if (label.contains("age")) {
                        data[column] = model.getRowCount() == 0 ? "20" : "24";
                    } else if (label.contains("date")) {
                        data[column] = "11/12/2026";
                    } else if (label.contains("time")) {
                        data[column] = "10:00 AM";
                    } else {
                        data[column] = "Fixture";
                    }
                }
                model.addRow(data);
            }
            table.clearSelection();
            for (int column = 0; column < table.getColumnCount(); column++) {
                if (table.getColumnModel().getColumn(column).getMaxWidth() == 0) {
                    continue;
                }
                var header = table.getTableHeader();
                var renderer = table.getColumnModel().getColumn(column).getHeaderRenderer();
                if (renderer == null) {
                    renderer = header.getDefaultRenderer();
                }
                auditColors(name + " header " + table.getColumnName(column),
                        renderer.getTableCellRendererComponent(table, table.getColumnName(column), false, false, -1, column),
                        false, issues);
                for (int row = 0; row < Math.min(table.getRowCount(), 4); row++) {
                    auditCell(name, table, row, column, false, issues);
                }
                table.setRowSelectionInterval(0, 0);
                auditCell(name, table, 0, column, true, issues);
                table.clearSelection();
            }
        } catch (RuntimeException failure) {
            issues.add(name + " renderer error: " + failure);
        }
    }

    private static void auditCell(String name, JTable table, int row, int column, boolean selected, List<String> issues) {
        Component component = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
        auditColors(name + " " + table.getColumnName(column) + " row " + (row + 1) + (selected ? " selected" : ""),
                component, component instanceof JCheckBox, issues);
    }

    private static void auditColors(String location, Component component, boolean checkbox, List<String> issues) {
        Color bg = component.getBackground();
        Color fg = component.getForeground();
        if (DesktopAppearance.style().dark() && bg != null && DesktopAppearance.luminance(bg) > 0.65) {
            issues.add(location + " has a light background " + Integer.toHexString(bg.getRGB()));
        } else if (!checkbox && fg != null && bg != null && DesktopAppearance.contrast(fg, bg) < 4.5) {
            issues.add(location + " has low contrast " + Integer.toHexString(fg.getRGB()) + "/" + Integer.toHexString(bg.getRGB()));
        }
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
