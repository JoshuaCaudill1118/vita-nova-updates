package main.java.util;

import main.java.db.DatabaseManager;
import main.java.model.User;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class StaffDashboardAppearanceAudit {
    private static final List<String> DASHBOARDS = List.of(
            "UserDashboard", "TodayOperationsBoardFrame", "MyWorkFrame", "SupervisorExceptionsDialog",
            "EventDashboard", "GuestLogDashboard", "ReportDashboard", "StaffNotificationDialog",
            "PendingCheckInVerificationFrame", "IntakeHubFrame", "MassEmailDialog", "ServiceRoleAssignmentFrame",
            "SleepSpotDashboard", "MasterFormsDashboard", "FAUHealthRosterFrame", "DonationReceiptFrame",
            "BusPassDashboard", "ProgramEnrollmentDashboardFrame", "ConsentReleaseDashboardFrame",
            "ReferralPartnerResponseDashboardFrame"
    );

    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args[0]);
        Files.createDirectories(folder);
        System.setProperty("vitalink.appearance.preview", "true");
        System.setProperty("vitanova.db.url", "jdbc:postgresql://127.0.0.1:55472/postgres?connectTimeout=3&socketTimeout=8");
        System.setProperty("vitanova.db.user", "merge_test");
        System.setProperty("vitanova.db.password", "synthetic-only");
        DesktopAppearance.installLookAndFeel();
        VitaNovaTheme.install();
        DesktopTextMenu.install();
        DesktopAppearance.installStaffAppearance();
        DesktopZoomManager.install();
        LoggedInUserSession.setCurrentUser(new User(11, "Preview Staff", "", "", "preview", "", "ADMIN"));
        JFrame owner = new JFrame();
        List<String> results = new ArrayList<>();
        List<String> screens = args.length > 1
                ? Arrays.asList(args).subList(1, args.length)
                : new ArrayList<>(DASHBOARDS);
        if (args.length == 1) {
            screens.add(0, "DashboardNavigator");
        }
        for (String name : screens) {
            Window[] displayed = new Window[1];
            try {
                SwingUtilities.invokeAndWait(() -> displayed[0] = open(name, owner));
                Thread.sleep(280);
                waitForReportData(name, displayed[0]);
                SwingUtilities.invokeAndWait(() -> record(name, displayed[0], folder, results));
                if ("DashboardNavigator".equals(name)) {
                    SwingUtilities.invokeAndWait(() -> verifySettings((JFrame) displayed[0], folder));
                }
            } catch (Exception failure) {
                Throwable reason = failure.getCause() == null ? failure : failure.getCause();
                results.add(name + " | FAILED | " + reason);
            } finally {
                if (displayed[0] != null) {
                    SwingUtilities.invokeAndWait(displayed[0]::dispose);
                }
            }
        }
        owner.dispose();
        DatabaseManager.shutdown();
        Files.write(folder.resolve(args.length == 1 ? "dashboard-audit.txt" : "targeted-audit.txt"), results);
        results.forEach(System.out::println);
        boolean issueFound = results.stream().anyMatch(line -> line.contains("FAILED")
                || !line.contains(" | 0 low-contrast text controls | 0 clipped labels or controls"));
        System.exit(issueFound ? 1 : 0);
    }

    private static void verifySettings(JFrame navigator, Path folder) {
        var account = findButton(navigator, "Account Settings");
        var appearance = findButton(navigator, "Appearance Settings");
        if (account == null || appearance == null || account.getParent() != appearance.getParent()
                || account.getParent().getComponentZOrder(appearance) != account.getParent().getComponentZOrder(account) + 1
                || navigator.getJMenuBar() != null) {
            throw new IllegalStateException("Appearance Settings button must be next to Account Settings");
        }
        var settings = java.util.prefs.Preferences.userNodeForPackage(DesktopAppearance.class).node("appearance-preview");
        String saved = settings.get("settings", null);
        String[] expected = new String[1];
        RuntimeException[] problem = new RuntimeException[1];
        javax.swing.Timer dialogCheck = new javax.swing.Timer(100, event -> {
            for (Window window : navigator.getOwnedWindows()) {
                if (!(window instanceof JDialog dialog) || !dialog.isShowing()
                        || !"Appearance Settings".equals(dialog.getTitle())) {
                    continue;
                }
                ((javax.swing.Timer) event.getSource()).stop();
                try {
                    var selector = findSelector(dialog);
                    var lowVision = findCheckBox(dialog, "Low Vision");
                    var colorblind = findCheckBox(dialog, "Colorblind-Friendly");
                    if (selector == null || selector.getItemCount() != 3
                            || !selector.getItemAt(0).toString().equals("Follow Computer")
                            || !selector.getItemAt(1).toString().equals("Light")
                            || !selector.getItemAt(2).toString().equals("Dark")
                            || lowVision == null || colorblind == null) {
                        throw new IllegalStateException("The theme or independent accessibility controls are incomplete");
                    }
                    var initial = new DesktopAppearance.Settings((DesktopAppearance.Theme) selector.getSelectedItem(),
                            lowVision.isSelected(), colorblind.isSelected());
                    if (!initial.encode().equals(System.getProperty("vitalink.appearance.preview.settings"))) {
                        throw new IllegalStateException("The dialog did not show the selected theme and options");
                    }
                    List<String> dialogAudit = new ArrayList<>();
                    record("AppearanceSettings", dialog, folder, dialogAudit);
                    if (dialogAudit.stream().anyMatch(line -> !line.contains("0 low-contrast text controls | 0 clipped labels or controls"))) {
                        throw new IllegalStateException(dialogAudit.toString());
                    }
                    int next = (selector.getSelectedIndex() + 1) % selector.getItemCount();
                    lowVision.setSelected(false);
                    colorblind.setSelected(false);
                    lowVision.doClick(0);
                    colorblind.doClick(0);
                    selector.setSelectedIndex(next);
                    if (!lowVision.isSelected() || !colorblind.isSelected()) {
                        throw new IllegalStateException("Changing the theme affected the accessibility checkboxes");
                    }
                    lowVision.doClick(0);
                    if (!colorblind.isSelected()) {
                        throw new IllegalStateException("Changing Low Vision affected Colorblind-Friendly");
                    }
                    lowVision.doClick(0);
                    expected[0] = new DesktopAppearance.Settings((DesktopAppearance.Theme) selector.getSelectedItem(),
                            true, true).encode();
                    findButton(dialog, "Save").doClick(0);
                } catch (RuntimeException failure) {
                    problem[0] = failure;
                    dialog.dispose();
                }
                return;
            }
        });
        dialogCheck.start();
        try {
            appearance.doClick(0);
            if (dialogCheck.isRunning()) {
                throw new IllegalStateException("Appearance Settings did not open");
            }
            if (problem[0] != null) {
                throw problem[0];
            }
            if (!settings.get("settings", "").equals(expected[0])) {
                throw new IllegalStateException("Saving appearance did not update the local preference");
            }
        } finally {
            dialogCheck.stop();
            if (saved == null) {
                settings.remove("settings");
            } else {
                settings.put("settings", saved);
            }
        }
    }

    private static javax.swing.JCheckBox findCheckBox(Component component, String text) {
        if (component instanceof javax.swing.JCheckBox checkBox && text.equals(checkBox.getText())) {
            return checkBox;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                var checkBox = findCheckBox(child, text);
                if (checkBox != null) {
                    return checkBox;
                }
            }
        }
        return null;
    }

    private static javax.swing.JComboBox<?> findSelector(Component component) {
        if (component instanceof javax.swing.JComboBox<?> combo) {
            return combo;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                var combo = findSelector(child);
                if (combo != null) {
                    return combo;
                }
            }
        }
        return null;
    }

    private static javax.swing.JButton findButton(Component component, String text) {
        if (component instanceof javax.swing.JButton button && text.equals(button.getText())) {
            return button;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                var button = findButton(child, text);
                if (button != null) {
                    return button;
                }
            }
        }
        return null;
    }

    private static void waitForReportData(String name, Window window) throws Exception {
        if (!"ReportDashboard".equals(name)) {
            return;
        }
        for (int attempt = 0; attempt < 60; attempt++) {
            boolean[] ready = new boolean[1];
            SwingUtilities.invokeAndWait(() -> ready[0] = hasReportTimestamp(window));
            if (ready[0]) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("The report overview did not finish loading");
    }

    private static boolean hasReportTimestamp(Component component) {
        if (component instanceof JLabel label && label.getText() != null
                && label.getText().startsWith("Last Refreshed: ")) {
            return !label.getText().endsWith("Not yet loaded");
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (hasReportTimestamp(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Window open(String name, JFrame owner) {
        try {
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
            window.setSize(DesktopAppearance.style().lowVision() ? 1100 : 1240, 820);
            window.setVisible(true);
            return window;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not construct " + name, failure);
        }
    }

    private static void record(String name, Window window, Path folder, List<String> results) {
        window.validate();
        JComponent root = ((RootPaneContainer) window).getRootPane();
        if (root.getWidth() == 0 || root.getHeight() == 0 || root.getComponentCount() == 0) {
            results.add(name + " | FAILED | No rendered UI");
            return;
        }
        List<String> contrast = new ArrayList<>();
        StaffAppearancePreview.auditVisible(root, VitaNovaTheme.PAGE_BG, contrast);
        List<String> clipping = new ArrayList<>();
        auditLayout(root, clipping, contrast);
        BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            root.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        try {
            ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        results.add(name + " | Rendered | " + contrast.size() + " low-contrast text controls"
                + (contrast.isEmpty() ? "" : " | " + String.join(", ", contrast))
                + " | " + clipping.size() + " clipped labels or controls"
                + (clipping.isEmpty() ? "" : " | " + String.join(", ", clipping)));
    }

    private static void auditLayout(Component component, List<String> clipping, List<String> contrast) {
        if (!component.isShowing() || component.getWidth() <= 0 || component.getHeight() <= 0) {
            return;
        }
        if (component instanceof JLabel label && label.getText() != null && !label.getText().startsWith("<html>")) {
            var insets = label.getInsets();
            String shown = SwingUtilities.layoutCompoundLabel(label, label.getFontMetrics(label.getFont()),
                    label.getText(), label.getIcon(), label.getVerticalAlignment(), label.getHorizontalAlignment(),
                    label.getVerticalTextPosition(), label.getHorizontalTextPosition(),
                    new Rectangle(insets.left, insets.top,
                            Math.max(0, label.getWidth() - insets.left - insets.right),
                            Math.max(0, label.getHeight() - insets.top - insets.bottom)),
                    new Rectangle(), new Rectangle(), label.getIconTextGap());
            if (!label.getText().equals(shown)) {
                clipping.add(label.getText());
            }
        } else if (component instanceof AbstractButton button && button.getText() != null && !button.getText().startsWith("<html>")) {
            var insets = button.getInsets();
            String shown = SwingUtilities.layoutCompoundLabel(button, button.getFontMetrics(button.getFont()),
                    button.getText(), button.getIcon(), button.getVerticalAlignment(), button.getHorizontalAlignment(),
                    button.getVerticalTextPosition(), button.getHorizontalTextPosition(),
                    new Rectangle(insets.left, insets.top,
                            Math.max(0, button.getWidth() - insets.left - insets.right),
                            Math.max(0, button.getHeight() - insets.top - insets.bottom)),
                    new Rectangle(), new Rectangle(), button.getIconTextGap());
            if (!button.getText().equals(shown)) {
                clipping.add(button.getText());
            }
        } else if (component instanceof JTextArea area && !area.isEditable() && !(area.getParent() instanceof JViewport)
                && area.getDocument().getLength() > 0) {
            try {
                Rectangle2D lastLine = area.modelToView2D(area.getDocument().getLength() - 1);
                if (lastLine != null && lastLine.getMaxY() > area.getHeight() + 1) {
                    clipping.add("Read-only text area");
                }
            } catch (javax.swing.text.BadLocationException failure) {
                throw new IllegalStateException(failure);
            }
        } else if (component instanceof JTable table) {
            for (int row = 0; row < Math.min(table.getRowCount(), 4); row++) {
                for (int column = 0; column < Math.min(table.getColumnCount(), 12); column++) {
                    Component cell = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
                    if (cell.getForeground() != null && cell.getBackground() != null
                            && DesktopAppearance.contrast(cell.getForeground(), cell.getBackground()) < 4.5) {
                        contrast.add("Table column " + table.getColumnName(column));
                    }
                }
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                auditLayout(child, clipping, contrast);
            }
        }
    }
}


