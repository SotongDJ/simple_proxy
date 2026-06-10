package com.corsproxy;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

public class CorsProxyApp extends JFrame {
    private ProxyServer proxyServer;
    private File activeHtmlFile;

    // GUI Components
    private JTextField portField;
    private JTextField proxyPathField;
    private JLabel activeFileLabel;
    private JLabel statusBadge;
    private JButton startBtn;
    private JButton stopBtn;
    private JButton openBrowserBtn;
    private JTable logTable;
    private DefaultTableModel tableModel;
    private JPanel dropZone;

    // Styling constants
    private static final Color BG_DARK = new Color(0x1e1e24);
    private static final Color BG_PANEL = new Color(0x25252b);
    private static final Color BG_INPUT = new Color(0x15151a);
    private static final Color TEXT_LIGHT = new Color(0xf0f0f5);
    private static final Color TEXT_MUTED = new Color(0xa0a0ab);
    private static final Color ACCENT_TEAL = new Color(0x3ec3b0);
    private static final Color ACCENT_RED = new Color(0xff6b61);
    private static final Color BORDER_COLOR = new Color(0x33343a);

    public CorsProxyApp() {
        super("CORS Proxy & Web Server");
        initUI();
        setupDragAndDrop();
        checkDefaultHtmlFile();
    }

    /**
     * Dynamically scales a pixel dimension based on the calculated scaling factor.
     * Prevents UI truncation and layout squashing on High-DPI displays.
     */
    private int scale(int pixels) {
        double factor = 1.0;
        try {
            String activeScaleStr = System.getProperty("corsproxy.activeScale");
            if (activeScaleStr != null) {
                factor = Double.parseDouble(activeScaleStr);
            }
        } catch (Exception ignored) {}
        return (int) Math.round(pixels * factor);
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(scale(950), scale(620));
        setMinimumSize(new Dimension(scale(800), scale(520)));
        setLocationRelativeTo(null);

        // Apply dark mode theme to root pane
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(scale(10), scale(10)));

        // 1. Header Panel
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // 2. Center Panel (Split into Settings and Logs)
        JPanel mainContentPanel = new JPanel(new BorderLayout(scale(10), scale(10)));
        mainContentPanel.setOpaque(false);
        mainContentPanel.setBorder(new EmptyBorder(0, scale(12), scale(12), scale(12)));

        JPanel settingsPanel = createSettingsPanel();
        mainContentPanel.add(settingsPanel, BorderLayout.WEST);

        JPanel logsPanel = createLogsPanel();
        mainContentPanel.add(logsPanel, BorderLayout.CENTER);

        add(mainContentPanel, BorderLayout.CENTER);

        // Set dark styling for Dialogs/Choosers
        UIManager.put("OptionPane.background", BG_DARK);
        UIManager.put("OptionPane.messageForeground", TEXT_LIGHT);
        UIManager.put("Panel.background", BG_DARK);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(scale(12), scale(16), scale(12), scale(16))
        ));

        // Title and Subtitle container
        JPanel titleContainer = new JPanel(new GridLayout(2, 1, scale(2), scale(2)));
        titleContainer.setOpaque(false);
        
        JLabel titleLabel = new JLabel("CORS Proxy & Static Web Server");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, scale(18)));
        titleLabel.setForeground(TEXT_LIGHT);
        titleContainer.add(titleLabel);

        JLabel subtitleLabel = new JLabel("Serves app.html and proxies CORS-restricted APIs locally");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, scale(12)));
        subtitleLabel.setForeground(TEXT_MUTED);
        titleContainer.add(subtitleLabel);

        panel.add(titleContainer, BorderLayout.WEST);

        // Status Container
        JPanel statusContainer = new JPanel(new FlowLayout(FlowLayout.RIGHT, scale(10), scale(8)));
        statusContainer.setOpaque(false);

        statusBadge = new JLabel(" STOPPED ");
        statusBadge.setOpaque(true);
        statusBadge.setBackground(ACCENT_RED);
        statusBadge.setForeground(Color.BLACK);
        statusBadge.setFont(new Font("SansSerif", Font.BOLD, scale(12)));
        statusBadge.setBorder(BorderFactory.createEmptyBorder(scale(4), scale(10), scale(4), scale(10)));
        statusContainer.add(statusBadge);

        panel.add(statusContainer, BorderLayout.EAST);

        return panel;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(BG_PANEL);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(scale(260), 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(scale(12), scale(12), scale(12), scale(12))
        ));

        // Form Fields Title
        JLabel title = new JLabel("SERVER SETTINGS");
        title.setFont(new Font("SansSerif", Font.BOLD, scale(13)));
        title.setForeground(TEXT_LIGHT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createRigidArea(new Dimension(0, scale(10))));

        // Port Field
        panel.add(createFieldLabel("HTTP Port"));
        portField = createStyledTextField("8080");
        panel.add(portField);
        panel.add(Box.createRigidArea(new Dimension(0, scale(8))));

        // Proxy Path Field
        panel.add(createFieldLabel("Proxy Route"));
        proxyPathField = createStyledTextField("/proxy");
        panel.add(proxyPathField);
        panel.add(Box.createRigidArea(new Dimension(0, scale(12))));

        // Active File Label
        panel.add(createFieldLabel("HTML File to Serve"));
        activeFileLabel = new JLabel("<html><i>Scanning...</i></html>");
        activeFileLabel.setFont(new Font("SansSerif", Font.PLAIN, scale(12)));
        activeFileLabel.setForeground(ACCENT_TEAL);
        activeFileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(activeFileLabel);
        panel.add(Box.createRigidArea(new Dimension(0, scale(8))));

        // Drag & Drop Area
        dropZone = new JPanel(new GridBagLayout());
        dropZone.setBackground(BG_INPUT);
        dropZone.setOpaque(true);
        dropZone.setPreferredSize(new Dimension(0, scale(100)));
        dropZone.setMaximumSize(new Dimension(Short.MAX_VALUE, scale(100)));
        
        Border dashed = BorderFactory.createDashedBorder(BORDER_COLOR, 2f, 4f);
        Border margin = BorderFactory.createEmptyBorder(scale(10), scale(10), scale(10), scale(10));
        dropZone.setBorder(BorderFactory.createCompoundBorder(dashed, margin));
        dropZone.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel dropLabel = new JLabel("<html><center>Drag & Drop <b>app.html</b> here<br><span style='font-size:10px; color:#a0a0ab;'>or click to choose file</span></center></html>");
        dropLabel.setForeground(TEXT_LIGHT);
        dropLabel.setFont(new Font("SansSerif", Font.PLAIN, scale(12)));
        dropZone.add(dropLabel);

        // Click on dropzone to browse file
        dropZone.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                browseFile();
            }
        });

        panel.add(dropZone);
        panel.add(Box.createRigidArea(new Dimension(0, scale(15))));

        // Control Buttons
        startBtn = createStyledButton("Start Server", ACCENT_TEAL, Color.BLACK);
        startBtn.addActionListener(this::startServer);
        panel.add(startBtn);
        panel.add(Box.createRigidArea(new Dimension(0, scale(8))));

        stopBtn = createStyledButton("Stop Server", ACCENT_RED, Color.BLACK);
        stopBtn.setEnabled(false);
        stopBtn.addActionListener(this::stopServer);
        panel.add(stopBtn);
        panel.add(Box.createRigidArea(new Dimension(0, scale(8))));

        openBrowserBtn = createStyledButton("Open Page in Browser", BG_DARK, TEXT_LIGHT);
        openBrowserBtn.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        openBrowserBtn.addActionListener(this::openInBrowser);
        panel.add(openBrowserBtn);

        return panel;
    }

    private JPanel createLogsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(scale(12), scale(12), scale(12), scale(12))
        ));

        // Logs Title Bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JLabel title = new JLabel("REQUEST LOGS");
        title.setFont(new Font("SansSerif", Font.BOLD, scale(13)));
        title.setForeground(TEXT_LIGHT);
        topBar.add(title, BorderLayout.WEST);

        JButton clearBtn = new JButton("Clear Logs");
        clearBtn.setFont(new Font("SansSerif", Font.PLAIN, scale(11)));
        clearBtn.setBackground(BG_INPUT);
        clearBtn.setForeground(TEXT_MUTED);
        clearBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(scale(3), scale(8), scale(3), scale(8))
        ));
        clearBtn.setFocusPainted(false);
        clearBtn.addActionListener(e -> tableModel.setRowCount(0));
        topBar.add(clearBtn, BorderLayout.EAST);

        panel.add(topBar, BorderLayout.NORTH);
        panel.add(Box.createRigidArea(new Dimension(0, scale(8))), BorderLayout.CENTER);

        // Logs Table
        String[] columnNames = {"Time", "Method", "Path", "Target URL", "Status", "Duration"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // read-only
            }
        };

        logTable = new JTable(tableModel);
        logTable.setBackground(BG_INPUT);
        logTable.setForeground(TEXT_LIGHT);
        logTable.setGridColor(BORDER_COLOR);
        logTable.setSelectionBackground(new Color(0x33343a));
        logTable.setSelectionForeground(TEXT_LIGHT);
        logTable.setFont(new Font("Monospaced", Font.PLAIN, scale(12)));
        
        // Calculate row height dynamically based on font metrics to prevent text clipping
        int fontHeight = logTable.getFontMetrics(logTable.getFont()).getHeight();
        logTable.setRowHeight(fontHeight + scale(8));
        
        // Custom header styling
        logTable.getTableHeader().setBackground(BG_PANEL);
        logTable.getTableHeader().setForeground(TEXT_LIGHT);
        logTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, scale(11)));
        logTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
        
        int headerHeight = logTable.getTableHeader().getFontMetrics(logTable.getTableHeader().getFont()).getHeight() + scale(10);
        logTable.getTableHeader().setPreferredSize(new Dimension(0, headerHeight));
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // Adjust widths (proportional, scaled)
        logTable.getColumnModel().getColumn(0).setPreferredWidth(scale(75));  // Time
        logTable.getColumnModel().getColumn(1).setPreferredWidth(75);  // Method
        logTable.getColumnModel().getColumn(2).setPreferredWidth(110); // Path
        logTable.getColumnModel().getColumn(3).setPreferredWidth(260); // Target
        logTable.getColumnModel().getColumn(4).setPreferredWidth(55);  // Status
        logTable.getColumnModel().getColumn(5).setPreferredWidth(75);  // Duration

        // Alignments and colors for status
        logTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setForeground(TEXT_LIGHT);
                
                // Color status codes and methods
                if (column == 4 && value != null) { // Status
                    try {
                        int status = Integer.parseInt(value.toString());
                        if (status >= 200 && status < 300) {
                            c.setForeground(ACCENT_TEAL);
                        } else if (status >= 400) {
                            c.setForeground(ACCENT_RED);
                        } else {
                            c.setForeground(Color.YELLOW);
                        }
                    } catch (NumberFormatException ignored) {}
                } else if (column == 1 && value != null) { // Method
                    String method = value.toString();
                    if ("GET".equals(method)) {
                        c.setForeground(new Color(0x61afef)); // blue
                    } else if ("POST".equals(method)) {
                        c.setForeground(new Color(0x98c379)); // green
                    } else if ("OPTIONS".equals(method)) {
                        c.setForeground(TEXT_MUTED);
                    } else {
                        c.setForeground(new Color(0xe5c07b)); // orange/yellow
                    }
                }
                
                // Keep selected color visible
                if (isSelected) {
                    setBackground(new Color(0x2d3139));
                } else {
                    setBackground(BG_INPUT);
                }
                
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(logTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scrollPane.getViewport().setBackground(BG_INPUT);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, scale(11)));
        label.setForeground(TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JTextField createStyledTextField(String text) {
        JTextField tf = new JTextField(text);
        tf.setBackground(BG_INPUT);
        tf.setForeground(TEXT_LIGHT);
        tf.setCaretColor(TEXT_LIGHT);
        tf.setFont(new Font("SansSerif", Font.PLAIN, scale(13)));
        tf.setMaximumSize(new Dimension(Short.MAX_VALUE, scale(36)));
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(scale(6), scale(8), scale(6), scale(8))
        ));
        tf.setAlignmentX(Component.LEFT_ALIGNMENT);
        return tf;
    }

    private JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, scale(13)));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setMaximumSize(new Dimension(Short.MAX_VALUE, scale(36)));
        btn.setBorder(BorderFactory.createEmptyBorder(scale(6), scale(12), scale(6), scale(12)));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void browseFile() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setDialogTitle("Select app.html File");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("HTML Files (*.html, *.htm)", "html", "htm"));
        
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            setActiveHtmlFile(chooser.getSelectedFile());
        }
    }

    private void setActiveHtmlFile(File file) {
        if (file != null && file.exists() && file.isFile()) {
            activeHtmlFile = file;
            activeFileLabel.setText("<html>Serving custom: <b>" + file.getName() + "</b><br><span style='font-size:10px; color:#a0a0ab;'>" + file.getParent() + "</span></html>");
            appendSystemLog("Active file set to: " + file.getAbsolutePath());
        }
    }

    private void checkDefaultHtmlFile() {
        File localFile = new File("app.html");
        if (localFile.exists() && localFile.isFile()) {
            activeHtmlFile = localFile;
            activeFileLabel.setText("<html>Serving local default: <b>app.html</b></html>");
            appendSystemLog("Found local default app.html next to executable.");
        } else {
            activeFileLabel.setText("<html><span style='color:#ff6b61;'>No app.html selected!</span> (Double-click drop zone)</html>");
            appendSystemLog("No local default app.html found next to executable.");
        }
    }

    private void setupDragAndDrop() {
        dropZone.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    List<?> list = (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (list != null && !list.isEmpty()) {
                        File file = (File) list.get(0);
                        if (file.getName().toLowerCase().endsWith(".html") || file.getName().toLowerCase().endsWith(".htm")) {
                            setActiveHtmlFile(file);
                            return true;
                        } else {
                            JOptionPane.showMessageDialog(CorsProxyApp.this,
                                    "Invalid file type. Please drag in an HTML file (.html or .htm).",
                                    "Invalid File", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                } catch (Exception e) {
                    appendSystemLog("Drag-and-Drop error: " + e.getMessage());
                }
                return false;
            }
        });
    }

    private void startServer(ActionEvent e) {
        String portText = portField.getText().trim();
        String proxyPath = proxyPathField.getText().trim();
        
        int port;
        try {
            port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Port must be a valid number between 1 and 65535.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (proxyPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Proxy route cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            proxyServer = new ProxyServer(
                    port,
                    proxyPath,
                    () -> activeHtmlFile,
                    this::appendRequestLog
            );
            proxyServer.start();

            // Update GUI State
            statusBadge.setText(" RUNNING ");
            statusBadge.setBackground(ACCENT_TEAL);
            
            portField.setEnabled(false);
            proxyPathField.setEnabled(false);
            startBtn.setEnabled(false);
            stopBtn.setEnabled(false); 
            stopBtn.setEnabled(true);
            
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to start server: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            appendSystemLog("Server start failed: " + ex.getMessage());
        }
    }

    private void stopServer(ActionEvent e) {
        if (proxyServer != null) {
            proxyServer.stop();
            proxyServer = null;
        }

        // Update GUI State
        statusBadge.setText(" STOPPED ");
        statusBadge.setBackground(ACCENT_RED);

        portField.setEnabled(true);
        proxyPathField.setEnabled(true);
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
    }

    private void openInBrowser(ActionEvent e) {
        String portText = portField.getText().trim();
        String url = "http://localhost:" + portText + "/app.html";
        
        appendSystemLog("Launching browser: " + url);
        
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI(url));
                return;
            } catch (Exception ignored) {}
        }
        
        // Fallback for Linux / xdg-open
        try {
            Runtime.getRuntime().exec(new String[]{"xdg-open", url});
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to automatically open browser.\nPlease open this URL manually:\n" + url,
                    "Open Web Page", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void appendRequestLog(RequestLog log) {
        SwingUtilities.invokeLater(() -> {
            tableModel.addRow(new Object[]{
                    log.getTimestamp(),
                    log.getMethod(),
                    log.getPath(),
                    log.getTargetUrl(),
                    log.getStatus(),
                    log.getDurationMs() > 0 ? log.getDurationMs() + " ms" : "-"
            });
            
            // Limit rows in table to avoid memory pressure
            if (tableModel.getRowCount() > 500) {
                tableModel.removeRow(0);
            }
            
            // Auto-scroll table to bottom
            logTable.scrollRectToVisible(logTable.getCellRect(logTable.getRowCount() - 1, 0, true));
        });
    }

    private void appendSystemLog(String message) {
        String time = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        appendRequestLog(new RequestLog(time, "SYSTEM", "-", message, 200, 0));
    }

    private static void scaleSystemFonts(double factor) {
        if (factor == 1.0) return;
        
        String[] fontKeys = {
            "Label.font", "Button.font", "Table.font", "TableHeader.font",
            "TextField.font", "TextArea.font", "Spinner.font", "ToggleButton.font",
            "Border.font", "Panel.font", "List.font", "ComboBox.font",
            "ScrollPane.font", "Viewport.font", "TabbedPane.font", "TitledBorder.font"
        };
        
        for (String key : fontKeys) {
            Font font = UIManager.getFont(key);
            if (font != null) {
                float newSize = (float) (font.getSize() * factor);
                UIManager.put(key, font.deriveFont(newSize));
            }
        }
    }

    public static void main(String[] args) {
        // Force system properties for correct subpixel rendering and scaling
        System.setProperty("sun.java2d.uiScale", "true");

        // Calculate scaling factor from environment
        double sysScale = 1.0;
        try {
            sysScale = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration()
                    .getDefaultTransform()
                    .getScaleX();
        } catch (Exception ignored) {}
        
        double fontFactor = 1.0;
        Font defaultFont = UIManager.getFont("Label.font");
        if (defaultFont != null) {
            fontFactor = defaultFont.getSize() / 12.0;
        }
        
        double finalFactor = Math.max(sysScale, fontFactor);
        
        // Command-line override or system property override: e.g. java -jar cors_proxy.jar 1.5
        String customScale = System.getProperty("scale");
        if (customScale == null && args.length > 0) {
            customScale = args[0];
        }
        if (customScale != null) {
            try {
                finalFactor = Double.parseDouble(customScale);
            } catch (NumberFormatException ignored) {}
        }

        // Apply scale factor to system fonts
        scaleSystemFonts(finalFactor);

        // Store active scale factor in system properties for runtime layout scaling
        System.setProperty("corsproxy.activeScale", String.valueOf(finalFactor));

        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
            
            // Custom Nimbus styling overrides to ensure high-fidelity dark colors
            UIManager.put("nimbusBase", BG_DARK);
            UIManager.put("nimbusBlueGrey", BG_PANEL);
            UIManager.put("control", BG_DARK);
            UIManager.put("text", TEXT_LIGHT);
            UIManager.put("nimbusLightBackground", BG_INPUT);
            UIManager.put("nimbusSelectedText", TEXT_LIGHT);
            UIManager.put("nimbusSelectionBackground", new Color(0x33343a));
            UIManager.put("info", BG_PANEL);
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            CorsProxyApp app = new CorsProxyApp();
            app.setVisible(true);
        });
    }
}
