package main.java.util;

import main.java.ui.IntakePacketFrame;
import main.java.ui.RhyServicesPlanFrame;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class StaffAppearancePreview {
    public static void main(String[] args) throws Exception {
        System.setProperty("vitalink.appearance.preview", "true");
        System.setProperty("vitanova.db.url", "jdbc:postgresql://127.0.0.1:1/appearance_preview");
        System.setProperty("vitanova.db.user", "appearance_preview");
        System.setProperty("vitanova.db.password", "appearance_preview");
        DesktopAppearance.installLookAndFeel();
        VitaNovaTheme.install();
        DesktopTextMenu.install();
        SwingUtilities.invokeAndWait(() -> {
            JFrame preview = new JFrame("Vita Link Staff Appearance Preview");
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Staff Dashboard", dashboard());
            tabs.addTab("Intake", blankForm(new IntakePacketFrame(null)));
            tabs.addTab("RHY Service Plan", blankForm(new RhyServicesPlanFrame(null)));
            preview.setContentPane(tabs);
            preview.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            preview.setSize(1240, 860);
            DesktopAppearance.applyLegacyContrast(preview);
            DesktopZoomManager.applyScaleForTesting(preview, Math.max(1.0f, DesktopAppearance.style().minimumScale()));
            if (args.length == 2 && "--verify".equals(args[0])) {
                try {
                    verifyAndRender(preview, tabs, Path.of(args[1]));
                } catch (IOException failure) {
                    throw new IllegalStateException(failure);
                } finally {
                    preview.dispose();
                }
            } else {
                preview.setLocationRelativeTo(null);
                preview.setVisible(true);
            }
        });
    }

    private static JPanel dashboard() {
        JPanel page = new JPanel(new BorderLayout(16, 16));
        page.setBackground(VitaNovaTheme.PAGE_BG);
        page.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JPanel header = new JPanel(new BorderLayout(8, 6));
        header.setBackground(VitaNovaTheme.CARD_BG);
        header.setBorder(VitaNovaTheme.cardBorder());
        JLabel title = new JLabel("Staff Dashboard");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        header.add(title, BorderLayout.NORTH);
        JLabel subtitle = new JLabel("Appearance preview");
        subtitle.setForeground(VitaNovaTheme.TEXT_MUTED);
        header.add(subtitle, BorderLayout.CENTER);
        page.add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(new GridLayout(2, 1, 0, 14));
        content.setOpaque(false);
        JTable table = new JTable(new DefaultTableModel(new String[]{"Form", "Status", "Assigned to"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        });
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.addRow(new String[]{"Intake", "Needs review", "Intake staff"});
        model.addRow(new String[]{"RHY Service Plan", "In progress", "Case manager"});
        model.addRow(new String[]{"RHY Service Plan", "Complete", "Case manager"});
        table.setRowHeight(30);
        VitaNovaTheme.styleTable(table);
        table.setRowSelectionInterval(1, 1);
        content.add(new JScrollPane(table));

        JPanel notes = new JPanel(new BorderLayout(8, 8));
        notes.setBackground(VitaNovaTheme.CARD_BG);
        notes.setBorder(VitaNovaTheme.cardBorder());
        JLabel notesLabel = new JLabel("Sample notes");
        notesLabel.setFont(notesLabel.getFont().deriveFont(Font.BOLD));
        notes.add(notesLabel, BorderLayout.NORTH);
        JTextArea text = new JTextArea("Sample text for checking the clipboard menu and text size.", 4, 48);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        notes.add(new JScrollPane(text), BorderLayout.CENTER);
        JPanel statuses = new JPanel(new GridLayout(1, 3, 8, 0));
        statuses.setOpaque(false);
        JButton complete = new JButton("Complete");
        JButton review = new JButton("Needs Review");
        JButton canceled = new JButton("Canceled");
        VitaNovaTheme.styleSuccessButton(complete);
        VitaNovaTheme.styleWarningButton(review);
        VitaNovaTheme.styleDangerButton(canceled);
        statuses.add(complete);
        statuses.add(review);
        statuses.add(canceled);
        notes.add(statuses, BorderLayout.SOUTH);
        content.add(notes);
        page.add(content, BorderLayout.CENTER);
        return page;
    }

    private static Container blankForm(JFrame form) {
        Container content = form.getContentPane();
        form.setContentPane(new JPanel());
        form.dispose();
        disableFormCommands(content);
        return content;
    }

    private static void disableFormCommands(Component component) {
        if (component instanceof JButton button && button.getText() != null && !button.getText().isBlank()) {
            for (var listener : button.getActionListeners()) {
                button.removeActionListener(listener);
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                disableFormCommands(child);
            }
        }
    }

    private static void verifyAndRender(JFrame frame, JTabbedPane tabs, Path folder) throws IOException {
        Files.createDirectories(folder);
        verifyPalettes();
        frame.setAutoRequestFocus(false);
        frame.setLocation(-10000, -10000);
        frame.setVisible(true);
        List<String> issues = new ArrayList<>();
        for (int tab = 0; tab < tabs.getTabCount(); tab++) {
            tabs.setSelectedIndex(tab);
            frame.validate();
            Component view = tabs.getSelectedComponent();
            String name = tabs.getTitleAt(tab).toLowerCase().replace(' ', '-');
            auditVisible(view, VitaNovaTheme.PAGE_BG, issues);
            render(frame, folder.resolve(name + ".png"));
            JTabbedPane formSections = findTabs(view);
            if (formSections != null) {
                for (int section = 1; section < formSections.getTabCount(); section++) {
                    formSections.setSelectedIndex(section);
                    frame.validate();
                    auditVisible(formSections.getSelectedComponent(), VitaNovaTheme.PAGE_BG, issues);
                    render(frame, folder.resolve(name + "-" + (section + 1) + ".png"));
                }
                formSections.setSelectedIndex(0);
            }
        }
        Files.write(folder.resolve("contrast-issues.txt"), issues);
        if (!issues.isEmpty()) {
            throw new AssertionError("Low-contrast visible text: " + issues);
        }
        System.out.println("Rendered and checked dashboard, intake, and RHY plan. " + DesktopAppearance.style());
    }

    private static JTabbedPane findTabs(Component component) {
        if (component instanceof JTabbedPane tabs) {
            return tabs;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTabbedPane tabs = findTabs(child);
                if (tabs != null) {
                    return tabs;
                }
            }
        }
        return null;
    }

    private static void render(JFrame frame, Path destination) throws IOException {
        var root = frame.getRootPane();
        var image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            root.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", destination.toFile());
    }

    private static void verifyPalettes() {
        var light = new DesktopAppearance.ComputerAppearance(false, false, 0.75f);
        var dark = new DesktopAppearance.ComputerAppearance(true, false, 0.75f);
        var contrast = new DesktopAppearance.ComputerAppearance(false, true, 1.50f);
        if (DesktopAppearance.resolve(DesktopAppearance.Mode.SYSTEM, light).dark()
                || !DesktopAppearance.resolve(DesktopAppearance.Mode.SYSTEM, dark).dark()
                || !DesktopAppearance.resolve(DesktopAppearance.Mode.DARK, light).dark()
                || !DesktopAppearance.resolve(DesktopAppearance.Mode.COLORBLIND, dark).colorblind()
                || DesktopAppearance.resolve(DesktopAppearance.Mode.LOW_VISION, light).minimumScale() < 1.30f
                || DesktopAppearance.resolve(DesktopAppearance.Mode.SYSTEM, contrast).minimumScale() < 1.50f) {
            throw new AssertionError("Computer appearance or override did not resolve as expected");
        }
        double textMinimum = DesktopAppearance.style().lowVision() ? 7.0 : 4.5;
        requireContrast(VitaNovaTheme.TEXT_PRIMARY, VitaNovaTheme.PAGE_BG, textMinimum, "Primary text");
        requireContrast(VitaNovaTheme.TEXT_PRIMARY, VitaNovaTheme.FIELD_BG, textMinimum, "Input text");
        requireContrast(VitaNovaTheme.TEXT_PRIMARY, VitaNovaTheme.TABLE_SELECTION_BG, textMinimum, "Selected text");
        requireContrast(VitaNovaTheme.TEXT_MUTED, VitaNovaTheme.CARD_BG, 4.5, "Secondary text");
        requireContrast(VitaNovaTheme.TABLE_HEADER_TEXT, VitaNovaTheme.TABLE_HEADER_BG, 4.5, "Table heading");
    }

    private static void requireContrast(Color text, Color background, double minimum, String label) {
        double ratio = DesktopAppearance.contrast(text, background);
        if (ratio < minimum) {
            throw new AssertionError(label + " contrast is " + ratio);
        }
    }

    static void auditVisible(Component component, Color parentSurface, List<String> issues) {
        if (!component.isVisible() || component.getWidth() == 0 || component.getHeight() == 0) {
            return;
        }
        Color surface = component instanceof JComponent swing && swing.isOpaque() && swing.getBackground() != null
                ? swing.getBackground() : parentSurface;
        String text = component instanceof JLabel label ? label.getText()
                : component instanceof AbstractButton button ? button.getText()
                : component instanceof javax.swing.text.JTextComponent input ? input.getText() : "";
        Color foreground = component.isEnabled() ? component.getForeground() : null;
        if (component instanceof JButton button && !button.isEnabled() && button.isOpaque() && button.isContentAreaFilled()) {
            foreground = UIManager.getColor("Button.disabledText");
        }
        if (text != null && !text.isBlank() && foreground != null && DesktopAppearance.contrast(foreground, surface) < 4.5) {
            issues.add(component.getClass().getSimpleName() + " " + text.strip());
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                auditVisible(child, surface, issues);
            }
        }
    }
}
