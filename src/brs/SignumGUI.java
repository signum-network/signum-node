package brs;

import java.awt.*;
import java.awt.TrayIcon.MessageType;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.LookAndFeel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYStepAreaRenderer;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import brs.at.AtController;

import brs.fluxcapacitor.FluxValues;
import brs.props.PropertyService;
import brs.props.Props;
import brs.util.Convert;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

import java.awt.font.TextAttribute;
import java.util.Map;
import java.util.function.Consumer;

@SuppressWarnings("serial")
public class SignumGUI extends JFrame {
    private static final String FAILED_TO_START_MESSAGE = "Signum caught exception while starting";
    private static final String UNEXPECTED_EXIT_MESSAGE = "Signum Quit unexpectedly! Exit code ";

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss yyyy-MM-dd");

    private static final int OUTPUT_MAX_LINES = 500;
    private static final int MAX_SPEED_BPS = 10 * 1024 * 1024; // 10 MB/s

    private static final Logger LOGGER = LoggerFactory.getLogger(SignumGUI.class);
    private static String[] args;

    private boolean userClosed = false;
    private String iconLocation;
    private TrayIcon trayIcon = null;
    private JPanel toolBar = null;
    private JLabel infoLable = null;
    private JProgressBar syncProgressBar = null;
    private JScrollPane textScrollPane = null;
    private String programName = null;
    private String version = null;
    Color iconColor = Color.BLACK;

    private JProgressBar syncProgressBarDownloadedBlocks;
    private JProgressBar syncProgressBarUnverifiedBlocks;

    // New fields for performance chart
    private static final int CHART_HISTORY_SIZE = 1000;
    private final LinkedList<Long> blockTimestamps = new LinkedList<>();
    private final LinkedList<Integer> transactionCounts = new LinkedList<>();
    private final LinkedList<Long> pushTimes = new LinkedList<>();
    private final LinkedList<Long> dbTimes = new LinkedList<>();
    private final LinkedList<Long> atTimes = new LinkedList<>();
    private final LinkedList<Long> calculationTimes = new LinkedList<>();
    private final LinkedList<Double> blocksPerSecondHistory = new LinkedList<>();
    private final LinkedList<Double> transactionsPerSecondHistory = new LinkedList<>();
    private int movingAverageWindow = 100; // Default value
    private XYSeries blocksPerSecondSeries;
    private XYSeries transactionsPerSecondSeries;
    private XYSeries transactionsPerBlockSeries;
    private XYSeries pushTimePerBlockSeries;
    private XYSeries uploadSpeedSeries;
    private XYSeries downloadSpeedSeries;
    private XYSeries dbTimePerBlockSeries;
    private XYSeries calculationTimePerBlockSeries;
    private XYSeries atTimePerBlockSeries;

    private JProgressBar blocksPerSecondProgressBar;
    private JProgressBar transactionsPerSecondProgressBar;
    private JProgressBar transactionsPerBlockProgressBar;
    private int oclUnverifiedQueueThreshold;
    private JSlider movingAverageSlider;
    private JLabel peersLabel;
    private JLabel connectedPeersLabel;
    private JLabel peersCountLabel;
    private JLabel uploadSpeedLabel;
    private JLabel downloadSpeedLabel;
    private JLabel uploadVolumeLabel;
    private JLabel metricsUploadVolumeLabel;
    private JLabel metricsDownloadVolumeLabel;
    private JLabel downloadVolumeLabel;
    private String tooltip;

    private long lastNetVolumeUpdateTime = 0;
    private long lastUploadedVolume = 0;
    private long lastDownloadedVolume = 0;

    long lastNetVolumeUpdateTimeChart = 0;
    long lastUploadedVolumeChart = 0;
    long lastDownloadedVolumeChart = 0;

    private JLabel pushTimeLabel;
    private JLabel dbTimeLabel;
    private JLabel calculationTimeLabel;
    private JLabel atTimeLabel;
    private JProgressBar pushTimeProgressBar;
    private JProgressBar dbTimeProgressBar;
    private JProgressBar calculationTimeProgressBar;
    private JProgressBar atTimeProgressBar;
    private JProgressBar uploadSpeedProgressBar;
    private JProgressBar downloadSpeedProgressBar;

    private ChartPanel performanceChartPanel;
    private ChartPanel timingChartPanel;
    private ChartPanel netSpeedChartPanel;
    private JCheckBox showPopOffCheckbox;
    private JCheckBox showMetricsCheckbox;
    private boolean showMetrics = false; // Default: metrics shown
    private boolean showPopOff = false; // Default: pop-off not shown

    private JButton openPhoenixButton;
    private JButton openClassicButton;
    private JButton openApiButton;
    private JButton editConfButton;
    private JButton popOff10Button;
    private JButton popOff100Button;
    private JButton shutdownButton;
    private JButton restartButton;

    private final LinkedList<Double> uploadSpeedHistory = new LinkedList<>();
    private final LinkedList<Double> downloadSpeedHistory = new LinkedList<>();

    private XYSeries uploadVolumeSeries;
    private XYSeries downloadVolumeSeries;
    private static final int SPEED_HISTORY_SIZE = 1000;

    private JPanel checkboxPanel = null;

    private long uploadedVolume = 0;
    private long downloadedVolume = 0;

    private JPanel metricsPanel;
    private JLabel measurementLabel;
    private JLabel experimentalLabel;
    private JSeparator measurementSeparator;
    private JSeparator experimentalSeparator;
    private JPanel measurementPanel;
    private JPanel experimentalPanel;

    /**
     * Panel to hold the time tracking labels. Only visible when experimental
     * features are enabled.
     */
    private JPanel timePanel;

    private JSeparator timeSeparator;

    /**
     * Label to display the total elapsed time since the GUI was started.
     */
    private JLabel totalTimeLabel;
    /**
     * Label to display the accumulated time spent syncing the blockchain.
     */
    private JLabel syncInProgressTimeLabel;
    /**
     * Stores the total elapsed time in milliseconds, updated by the GUI timer.
     */
    private long guiAccumulatedSyncTimeMs = 0;
    /**
     * Stores the accumulated time in milliseconds spent actively syncing (when more
     * than 10 blocks behind).
     */
    private long guiAccumulatedSyncInProgressTimeMs = 0;
    /**
     * Flag for the hysteresis logic, indicating if the node is currently considered
     * to be syncing.
     */
    private boolean isSyncing = false; // For hysteresis
    /**
     * Label for the separator between time labels.
     */
    private JLabel timeSeparatorLabel;
    /**
     * Timer to update the GUI time labels every second.
     */
    private Timer guiTimer;
    private boolean guiTimerStarted = false;

    private final ExecutorService chartUpdateExecutor = Executors.newSingleThreadExecutor();

    private JProgressBar createProgressBar(int min, int max, Color color, String initialString, Dimension size) {
        JProgressBar bar = new JProgressBar(min, max);
        bar.setBackground(color);
        bar.setPreferredSize(size);
        bar.setMinimumSize(size);
        bar.setStringPainted(true);
        bar.setString(initialString);
        bar.setValue(min);
        return bar;
    }

    private JLabel createLabel(String text, Color color, String tooltip) {
        JLabel label = new JLabel(text);
        if (color != null) {
            label.setForeground(color);
        }
        if (tooltip != null) {
            addInfoTooltip(label, tooltip);
        }
        return label;
    }

    private void addInfoTooltip(JLabel label, String text) {
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    String title = label.getText();
                    // Remove trailing colon for a cleaner title
                    if (title.endsWith(":")) {
                        title = title.substring(0, title.length() - 1);
                    }
                    // Wrap the text in HTML to control the width of the dialog.
                    String htmlText = "<html><body><p style='width: 300px;'>" + text.replace("\n", "<br>")
                            + "</p></body></html>";
                    JOptionPane.showMessageDialog(SignumGUI.this, htmlText, title, JOptionPane.PLAIN_MESSAGE);
                }
            }
        });
    }

    private void addComponent(JPanel panel, Component comp, int x, int y, int gridwidth, int weightx, int weighty,
            int anchor, int fill, Insets insets) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = gridwidth;
        gbc.weightx = weightx;
        gbc.weighty = weighty;
        gbc.anchor = anchor;
        gbc.fill = fill;
        gbc.insets = insets;
        panel.add(comp, gbc);
    }

    public static void main(String[] args) {
        new SignumGUI("Signum Node", Props.ICON_LOCATION.getDefaultValue(), Signum.VERSION.toString(), args);
    }

    public SignumGUI(String programName, String iconLocation, String version, String[] args) {
        SignumGUI.args = args;
        this.programName = programName;
        this.version = version;
        setTitle(programName + " " + version);
        this.iconLocation = iconLocation;

        Class<?> lafc = null;
        try {
            lafc = Class.forName("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
        }
        if (lafc == null) {
            try {
                lafc = Class.forName("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception e) {
            }
        }
        if (lafc != null) {
            try {
                UIManager.put("control", new Color(128, 128, 128));
                UIManager.put("info", new Color(128, 128, 128));
                UIManager.put("nimbusBase", new Color(18, 30, 49));
                UIManager.put("nimbusAlertYellow", new Color(248, 187, 0));
                UIManager.put("nimbusDisabledText", new Color(128, 128, 128));
                UIManager.put("nimbusFocus", new Color(115, 164, 209));
                UIManager.put("nimbusGreen", new Color(176, 179, 50));
                UIManager.put("nimbusInfoBlue", new Color(66, 139, 221));
                UIManager.put("nimbusLightBackground", new Color(18, 30, 49));
                UIManager.put("nimbusOrange", new Color(191, 98, 4));
                UIManager.put("nimbusRed", new Color(169, 46, 34));
                UIManager.put("nimbusSelectedText", new Color(255, 255, 255));
                UIManager.put("nimbusSelectionBackground", new Color(104, 93, 156));
                UIManager.put("text", new Color(230, 230, 230));
                LookAndFeel laf = (LookAndFeel) lafc.getConstructor().newInstance();
                UIManager.setLookAndFeel(laf);
            } catch (Exception e) {
                e.printStackTrace();
                IconFontSwing.register(FontAwesome.getIconFont());

            }
        }
        IconFontSwing.register(FontAwesome.getIconFont());

        JTextArea textArea = new JTextArea() {
            @Override
            public void append(String str) {
                super.append(str);

                while (getText().split("\n", -1).length > OUTPUT_MAX_LINES) {
                    int fle = getText().indexOf('\n');
                    super.replaceRange("", 0, fle + 1);
                }
                JScrollBar vertical = textScrollPane.getVerticalScrollBar();
                vertical.setValue(vertical.getMaximum());
            }
        };
        iconColor = textArea.getForeground();
        textArea.setEditable(false);
        sendJavaOutputToTextArea(textArea);
        textScrollPane = new JScrollPane(textArea);
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setContentPane(content);

        toolBar = new JPanel();
        toolBar.setLayout(new BoxLayout(toolBar, BoxLayout.X_AXIS));

        content.add(toolBar, BorderLayout.PAGE_START);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        content.add(bottomPanel, BorderLayout.PAGE_END);

        syncProgressBar = new JProgressBar(0, 100);
        syncProgressBar.setStringPainted(true);
        infoLable = new JLabel("Latest block info");

        // === Metrics Panel ===
        metricsPanel = new JPanel(new GridBagLayout());
        metricsPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        GridBagConstraints mainGbc = new GridBagConstraints();
        mainGbc.gridy = 0;
        mainGbc.insets = new Insets(0, 5, 0, 5);

        // --- Common sizes ---
        Dimension progressBarSize = new Dimension(200, 20);
        Insets labelInsets = new Insets(2, 5, 2, 0);
        Insets barInsets = new Insets(2, 5, 2, 5);

        // === Performance Metrics Panel ===
        JPanel performanceMetricsPanel = new JPanel(new GridBagLayout());

        // Download Panel (Progress Bars)
        JPanel downloadPanel = new JPanel(new GridBagLayout());

        // Verified/Total Blocks
        tooltip = "Shows the number of blocks verified against the total number of blocks in the queue. A high number of unverified blocks may indicate a slow verification process.";
        JLabel verifLabel = createLabel("Verified/Total Blocks:", null, tooltip);
        syncProgressBarDownloadedBlocks = createProgressBar(0, 100, Color.GREEN, "0 / 0 - 0%", progressBarSize);
        addComponent(downloadPanel, verifLabel, 0, 0, 1, 0, 0, GridBagConstraints.LINE_END, GridBagConstraints.NONE,
                labelInsets);
        addComponent(downloadPanel, syncProgressBarDownloadedBlocks, 1, 0, 1, 1, 0, GridBagConstraints.LINE_START,
                GridBagConstraints.HORIZONTAL, barInsets);

        // Unverified Blocks
        tooltip = "The number of blocks in the download queue that are waiting for PoC (Proof-of-Capacity) verification. A persistently high number might indicate that the CPU or GPU is unable to keep up with the network.";
        JLabel unVerifLabel = createLabel("Unverified Blocks:", null, tooltip);
        syncProgressBarUnverifiedBlocks = createProgressBar(0, 2000, Color.GREEN, "0", progressBarSize);
        addComponent(downloadPanel, unVerifLabel, 0, 1, 1, 0, 0, GridBagConstraints.LINE_END, GridBagConstraints.NONE,
                labelInsets);
        addComponent(downloadPanel, syncProgressBarUnverifiedBlocks, 1, 1, 1, 1, 0, GridBagConstraints.LINE_START,
                GridBagConstraints.HORIZONTAL, barInsets);

        // Separator
        JSeparator separator1 = new JSeparator(SwingConstants.HORIZONTAL);
        addComponent(downloadPanel, separator1, 0, 2, 2, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                barInsets);

        // Blocks/Second (Moving Average)
        tooltip = "The moving average of blocks processed per second. This indicates the speed at which your node is catching up with the blockchain.";
        JLabel blocksPerSecondLabel = createLabel("Blocks/Sec (MA):", Color.CYAN, tooltip);
        blocksPerSecondProgressBar = createProgressBar(0, 200, null, "0", progressBarSize);
        addComponent(downloadPanel, blocksPerSecondLabel, 0, 3, 1, 0, 0, GridBagConstraints.LINE_END,
                GridBagConstraints.NONE, labelInsets);
        addComponent(downloadPanel, blocksPerSecondProgressBar, 1, 3, 1, 0, 0, GridBagConstraints.LINE_START,
                GridBagConstraints.HORIZONTAL, barInsets);

        // Transactions/Second (Moving Average)
        tooltip = "The moving average of transactions processed per second. This metric reflects the current transactional throughput of the network as seen by your node.";
        JLabel txPerSecondLabel = createLabel("Transactions/Sec (MA):", Color.GREEN, tooltip);
        transactionsPerSecondProgressBar = createProgressBar(0, 2000, null, "0", progressBarSize);
        addComponent(downloadPanel, txPerSecondLabel, 0, 4, 1, 0, 0, GridBagConstraints.LINE_END,
                GridBagConstraints.NONE, labelInsets);
        addComponent(downloadPanel, transactionsPerSecondProgressBar, 1, 4, 1, 0, 0, GridBagConstraints.LINE_START,
                GridBagConstraints.HORIZONTAL, barInsets);

        // Transactions/Block (Moving Average)
        tooltip = "The moving average of the number of transactions included in each block. This provides insight into how full blocks are on average.";
        JLabel txPerBlockLabel = createLabel("Transactions/Block (MA):", new Color(255, 165, 0), tooltip);
        transactionsPerBlockProgressBar = createProgressBar(0, 255, null, "0", progressBarSize);
        addComponent(downloadPanel, txPerBlockLabel, 0, 5, 1, 0, 0, GridBagConstraints.LINE_END,
                GridBagConstraints.NONE, labelInsets);
        addComponent(downloadPanel, transactionsPerBlockProgressBar, 1, 5, 1, 0, 0, GridBagConstraints.LINE_START,
                GridBagConstraints.HORIZONTAL, barInsets);

        // Moving Average Slider
        tooltip = "The number of recent blocks used to calculate the moving average for performance metrics. A larger window provides a smoother but less responsive trend, while a smaller window is more reactive to recent changes.";
        JLabel maWindowLabel = createLabel("MA Window (Blocks):", null, tooltip);

        // Define the discrete values for the slider
        final int[] maWindowValues = { 10, 100, 200, 300, 400, 500 };
        // Find the initial index for the default movingAverageWindow
        int initialIndex = -1;
        for (int i = 0; i < maWindowValues.length; i++) {
            if (maWindowValues[i] == movingAverageWindow) {
                initialIndex = i;
                break;
            }
        }
        if (initialIndex == -1) { // If default is not in our list, use a sane default
            initialIndex = 1; // 100
            movingAverageWindow = maWindowValues[initialIndex];
        }

        movingAverageSlider = new JSlider(JSlider.HORIZONTAL, 0, maWindowValues.length - 1, initialIndex);
        movingAverageSlider.setSnapToTicks(true);
        movingAverageSlider.setMajorTickSpacing(1);
        movingAverageSlider.setPaintTicks(true);
        movingAverageSlider.setPaintLabels(true);
        movingAverageSlider.setPreferredSize(new Dimension(150, 45));

        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        for (int i = 0; i < maWindowValues.length; i++) {
            labelTable.put(i, new JLabel(String.valueOf(maWindowValues[i])));
        }

        movingAverageSlider.setLabelTable(labelTable);

        movingAverageSlider.addChangeListener(e -> {
            JSlider source = (JSlider) e.getSource();
            if (!source.getValueIsAdjusting()) {
                movingAverageWindow = maWindowValues[source.getValue()];
            }
        });

        addComponent(downloadPanel, maWindowLabel, 0, 6, 1, 0, 0, GridBagConstraints.LINE_END, GridBagConstraints.NONE,
                labelInsets);
        addComponent(downloadPanel, movingAverageSlider, 1, 6, 2, 1, 0, GridBagConstraints.LINE_START,
                GridBagConstraints.HORIZONTAL, barInsets);

        // Add downloadPanel to performanceMetricsPanel
        addComponent(performanceMetricsPanel, downloadPanel, 0, 0, 1, 0, 0, GridBagConstraints.NORTHWEST,
                GridBagConstraints.NONE, new Insets(0, 0, 0, 0));

        // Performance chart
        performanceChartPanel = createPerformanceChartPanel();
        JPanel performanceChartContainer = new JPanel();
        performanceChartContainer.setLayout(new BoxLayout(performanceChartContainer, BoxLayout.Y_AXIS));
        performanceChartContainer.add(performanceChartPanel);
        // TODO: change insets
        addComponent(performanceMetricsPanel, performanceChartContainer, 1, 0, 1, 0, 0, GridBagConstraints.NORTHWEST,
                GridBagConstraints.NONE, new Insets(0, 0, 0, 0));

        addToggleListener(blocksPerSecondLabel, performanceChartPanel, 0, 0);
        addToggleListener(txPerSecondLabel, performanceChartPanel, 0, 1);

        // Add to main metrics panel
        addComponent(metricsPanel, performanceMetricsPanel, 0, 0, 1, 0, 0, GridBagConstraints.CENTER,
                GridBagConstraints.NONE, new Insets(0, 0, 0, 0));

        // Create the net speed chart panel early so its listeners can be attached
        netSpeedChartPanel = createNetSpeedChartPanel();

        // === Timing Metrics Panel ===
        JPanel timingMetricsPanel = new JPanel(new GridBagLayout());

        // Timing Info Panel (Progress Bars + Labels)
        JPanel timingInfoPanel = new JPanel(new GridBagLayout());

        // --- Push Time ---
        tooltip = "The moving average of the total time taken to process and push a new block to the blockchain, including all validations and database operations.";
        pushTimeLabel = createLabel("Push Time/Block (MA):", Color.BLUE, tooltip);
        pushTimeProgressBar = createProgressBar(0, 100, null, "0 ms", progressBarSize);
        addComponent(timingInfoPanel, pushTimeLabel, 0, 1, 1, 0, 0, GridBagConstraints.LINE_END,
                GridBagConstraints.NONE, labelInsets);
        addComponent(timingInfoPanel, pushTimeProgressBar, 1, 1, 1, 0, 0, GridBagConstraints.LINE_START,
                GridBagConstraints.HORIZONTAL, barInsets);

        // --- DB Time ---
        tooltip = "The moving average of the time spent on database operations for each block. High values may indicate a slow disk or database contention.";
        dbTimeLabel = createLabel("DB Time/Block (MA):", Color.YELLOW, tooltip);
        dbTimeProgressBar = createProgressBar(0, 100, null, "0 ms", progressBarSize);
        addComponent(timingInfoPanel, dbTimeLabel, 0, 2, 1, 0, 0, GridBagConstraints.LINE_END, GridBagConstraints.NONE,
                labelInsets);
        addComponent(timingInfoPanel, dbTimeProgressBar, 1, 2, 1, 0, 0, GridBagConstraints.LINE_START,
                GridBagConstraints.HORIZONTAL, barInsets);

        // --- AT Time ---
        tooltip = "The moving average of the time spent processing Automated Transactions (ATs) within each block. This metric is relevant for assessing the performance impact of smart contracts on the network.";
        atTimeLabel = createLabel("AT Time/Block (MA):", new Color(153, 0, 76), tooltip);
        atTimeProgressBar = createProgressBar(0, 100, null, "0 ms", progressBarSize);
        addComponent(timingInfoPanel, atTimeLabel, 0, 3, 1, 0, 0, GridBagConstraints.LINE_END, GridBagConstraints.NONE,
                labelInsets);
        addComponent(timingInfoPanel, atTimeProgressBar, 1, 3, 1, 0, 0, GridBagConstraints.LINE_START,
                GridBagConstraints.HORIZONTAL, barInsets);

        // --- Calculation Time ---
        tooltip = "The moving average of the CPU time spent on calculations for each block, excluding database and Automated Transaction (AT) processing time. This includes signature verifications and other cryptographic operations.";
        calculationTimeLabel = createLabel("Calc Time/Block (MA):", new Color(128, 0, 128),
                "Moving average CPU calculation time per block.");
        calculationTimeProgressBar = createProgressBar(0, 100, null, "0 ms", progressBarSize);
        addComponent(timingInfoPanel, calculationTimeLabel, 0, 4, 1, 0, 0, GridBagConstraints.LINE_END,
                GridBagConstraints.NONE, labelInsets);
        addComponent(timingInfoPanel, calculationTimeProgressBar, 1, 4, 1, 0, 0, GridBagConstraints.LINE_START,
                GridBagConstraints.HORIZONTAL, barInsets);

        // --- Separator ---
        JSeparator separator2 = new JSeparator(SwingConstants.HORIZONTAL);
        addComponent(timingInfoPanel, separator2, 0, 5, 2, 1, 0, GridBagConstraints.CENTER,
                GridBagConstraints.HORIZONTAL, barInsets);

        // --- Upload Speed ---
        tooltip = "The current data upload speed to other peers in the network. This reflects how much blockchain data your node is sharing.";
        uploadSpeedLabel = createLabel("Upload Speed (MA):", new Color(128, 0, 0), tooltip);
        uploadSpeedProgressBar = createProgressBar(0, MAX_SPEED_BPS, null, "0 B/s", progressBarSize);
        addComponent(timingInfoPanel, uploadSpeedLabel, 0, 6, 1, 0, 0, GridBagConstraints.LINE_END,
                GridBagConstraints.NONE, labelInsets);
        addComponent(timingInfoPanel, uploadSpeedProgressBar, 1, 6, 1, 0, 0, GridBagConstraints.LINE_START,
                GridBagConstraints.HORIZONTAL, barInsets);

        // --- Download Speed ---
        tooltip = "The current data download speed from other peers in the network. This indicates how quickly your node is receiving blockchain data.";
        downloadSpeedLabel = createLabel("Download Speed (MA):", new Color(0, 100, 0), tooltip);
        downloadSpeedProgressBar = createProgressBar(0, MAX_SPEED_BPS, null, "0 B/s", progressBarSize);
        addComponent(timingInfoPanel, downloadSpeedLabel, 0, 7, 1, 0, 0, GridBagConstraints.LINE_END,
                GridBagConstraints.NONE, labelInsets);
        addComponent(timingInfoPanel, downloadSpeedProgressBar, 1, 7, 1, 0, 0, GridBagConstraints.LINE_START,
                GridBagConstraints.HORIZONTAL, barInsets);

        // --- Combined Volume ---
        JPanel combinedVolumePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        combinedVolumePanel.setOpaque(false);
        tooltip = "The total amount of data uploaded to and downloaded from the network during this session. The format is Uploaded / Downloaded.";
        JLabel volumeTitleLabel = createLabel("Volume:", null, tooltip);
        tooltip = "The total amount of data uploaded to the network during this session.";
        metricsUploadVolumeLabel = createLabel("", new Color(233, 150, 122), tooltip);
        tooltip = "The total amount of data downloaded from the network during this session.";
        metricsDownloadVolumeLabel = createLabel("", new Color(50, 205, 50), tooltip);
        combinedVolumePanel.add(volumeTitleLabel);
        combinedVolumePanel.add(metricsUploadVolumeLabel);
        combinedVolumePanel.add(new JLabel("/"));
        combinedVolumePanel.add(metricsDownloadVolumeLabel);
        addComponent(timingInfoPanel, combinedVolumePanel, 0, 8, 2, 1, 0, GridBagConstraints.CENTER,
                GridBagConstraints.NONE, barInsets);

        // Add timingInfoPanel to timingMetricsPanel
        addComponent(timingMetricsPanel, timingInfoPanel, 0, 0, 1, 0, 0, GridBagConstraints.NORTHWEST,
                GridBagConstraints.NONE, new Insets(0, 0, 0, 0));

        // --- Timing Chart Panel ---
        timingChartPanel = createTimingChartPanel();
        JPanel timingChartContainer = new JPanel();
        timingChartContainer.setLayout(new BoxLayout(timingChartContainer, BoxLayout.Y_AXIS));
        timingChartContainer.add(timingChartPanel);
        addComponent(timingMetricsPanel, timingChartContainer, 1, 0, 1, 0, 0, GridBagConstraints.NORTHWEST,
                GridBagConstraints.NONE, new Insets(0, 0, 0, 0));

        // --- Net Speed Chart Panel ---
        netSpeedChartPanel = createNetSpeedChartPanel();
        JPanel netSpeedChartContainer = new JPanel();
        netSpeedChartContainer.setLayout(new BoxLayout(netSpeedChartContainer, BoxLayout.Y_AXIS));
        netSpeedChartContainer.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        netSpeedChartContainer.add(netSpeedChartPanel);
        addComponent(timingMetricsPanel, netSpeedChartContainer, 2, 0, 1, 0, 0, GridBagConstraints.NORTHWEST,
                GridBagConstraints.NONE, new Insets(0, 0, 0, 5));

        // Add the timing metrics group to the main metrics panel
        addComponent(metricsPanel, timingMetricsPanel, 1, 0, 1, 0, 0, GridBagConstraints.CENTER,
                GridBagConstraints.NONE, new Insets(0, 0, 0, 0));

        addDualChartToggleListener(txPerBlockLabel, performanceChartPanel, 1, 0, timingChartPanel, 1, 0);
        addToggleListener(pushTimeLabel, timingChartPanel, 0, 0);
        addToggleListener(dbTimeLabel, timingChartPanel, 0, 1);
        addToggleListener(calculationTimeLabel, timingChartPanel, 0, 2);
        addToggleListener(atTimeLabel, timingChartPanel, 0, 3);
        addToggleListener(uploadSpeedLabel, netSpeedChartPanel, 0, 0);
        addToggleListener(downloadSpeedLabel, netSpeedChartPanel, 0, 1);

        Color uploadVolumeColor = new Color(233, 150, 122, 128); // Red
        Color downloadVolumeColor = new Color(50, 205, 50, 128); // Green
        addPaintToggleListener(metricsUploadVolumeLabel, netSpeedChartPanel, 1, 1, uploadVolumeColor);
        addPaintToggleListener(metricsDownloadVolumeLabel, netSpeedChartPanel, 1, 0, downloadVolumeColor);

        // === Add checkboxes to toolBar ===
        checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));

        showPopOffCheckbox = new JCheckBox("Pop off");
        // showPopOffCheckbox.setHorizontalTextPosition(SwingConstants.LEFT);
        showPopOffCheckbox.setSelected(showPopOff);
        showPopOffCheckbox.addActionListener(e -> {
            showPopOff = showPopOffCheckbox.isSelected();
            popOff10Button.setVisible(showPopOff);
            popOff100Button.setVisible(showPopOff);
        });

        showMetricsCheckbox = new JCheckBox("Metrics");
        // showMetricsCheckbox.setHorizontalTextPosition(SwingConstants.LEFT);
        showMetricsCheckbox.setSelected(showMetrics); // default visible
        showMetricsCheckbox.addActionListener(e -> {
            showMetrics = showMetricsCheckbox.isSelected();
            metricsPanel.setVisible(showMetrics);
        });

        checkboxPanel.add(showPopOffCheckbox);
        checkboxPanel.add(showMetricsCheckbox);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.add(toolBar);
        topPanel.add(metricsPanel);
        metricsPanel.setVisible(showMetrics);

        content.add(topPanel, BorderLayout.NORTH);
        content.add(textScrollPane, BorderLayout.CENTER);

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.X_AXIS));

        // --- Time Labels ---
        timePanel = new JPanel();
        timePanel.setLayout(new BoxLayout(timePanel, BoxLayout.X_AXIS));
        timePanel.setOpaque(false);
        tooltip = "Displays the total elapsed time since the node application was started.";
        totalTimeLabel = createLabel("0s", null, tooltip);
        tooltip = "Displays the total time the node has spent in synchronization mode. The timer is active only when the blockchain is more than 10 blocks behind the network.";
        syncInProgressTimeLabel = createLabel("0s", null, tooltip);

        timeSeparator = new JSeparator(SwingConstants.VERTICAL);
        timeSeparatorLabel = new JLabel(" / ");

        timePanel.add(totalTimeLabel);
        timePanel.add(timeSeparatorLabel);
        timePanel.add(syncInProgressTimeLabel);
        timePanel.add(Box.createHorizontalStrut(5));
        timePanel.add(timeSeparator);
        timePanel.add(Box.createHorizontalStrut(5));
        timePanel.setVisible(false);

        // --- Peers ---
        JPanel peersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tooltip = "The number of peers your node is currently connected to.";
        connectedPeersLabel = createLabel("000", null, tooltip);
        tooltip = "The total number of peers discovered by your node.";
        peersCountLabel = createLabel("000", null, tooltip);

        peersPanel.add(new JLabel("Peers: "));
        peersPanel.add(connectedPeersLabel);
        peersPanel.add(new JLabel(" / "));
        peersPanel.add(peersCountLabel);

        // --- Volume ---
        JPanel volumePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

        // Upload
        tooltip = "The total amount of data your node has uploaded to other peers since the application started.";
        uploadVolumeLabel = createLabel("▲ 000 MB", null, tooltip);

        // Download
        tooltip = "The total amount of data your node has downloaded from other peers since the application started.";
        downloadVolumeLabel = createLabel("▼ 000 MB", null, tooltip);

        volumePanel.add(uploadVolumeLabel);
        volumePanel.add(new JLabel(" / "));
        volumePanel.add(downloadVolumeLabel);

        infoPanel.add(timePanel);

        infoPanel.add(peersPanel);
        infoPanel.add(Box.createHorizontalStrut(5));
        infoPanel.add(new JSeparator(SwingConstants.VERTICAL));
        infoPanel.add(Box.createHorizontalStrut(5));
        infoPanel.add(volumePanel);
        infoPanel.add(Box.createHorizontalStrut(5));
        infoPanel.add(new JSeparator(SwingConstants.VERTICAL));
        infoPanel.add(Box.createHorizontalStrut(5));

        // --- Measurement ---
        measurementPanel = new JPanel();
        measurementPanel.setLayout(new BoxLayout(measurementPanel, BoxLayout.X_AXIS));
        measurementPanel.setOpaque(false);
        measurementSeparator = new JSeparator(SwingConstants.VERTICAL);
        tooltip = "Performance measurement is active. \n"
                + "Detailed syncronization datas are collecting for each blocks and being saved to \n"
                + "measurement/sync_measurement.csv and \n"
                + "measurement/sync_progress.csv for analysis.";
        measurementLabel = createLabel("🔬 MEAS", null, tooltip);
        measurementPanel.setVisible(false);

        measurementPanel.add(measurementLabel);
        measurementPanel.add(Box.createHorizontalStrut(5));
        measurementPanel.add(measurementSeparator);
        measurementPanel.add(Box.createHorizontalStrut(5));

        infoPanel.add(measurementPanel);

        // --- Experimental ---
        experimentalPanel = new JPanel();
        experimentalPanel.setLayout(new BoxLayout(experimentalPanel, BoxLayout.X_AXIS));
        experimentalPanel.setOpaque(false);
        experimentalSeparator = new JSeparator(SwingConstants.VERTICAL);
        tooltip = "Experimental feature is enabled.\n"
                + "Symplified datas are collecting and being saved to \n"
                + "measurement/sync_progress.csv file for analysis.";
        experimentalLabel = createLabel("⚗ EXP", null, tooltip);
        experimentalPanel.setVisible(false);

        experimentalPanel.add(experimentalLabel);
        experimentalPanel.add(Box.createHorizontalStrut(5));
        experimentalPanel.add(experimentalSeparator);
        experimentalPanel.add(Box.createHorizontalStrut(5));

        infoPanel.add(experimentalPanel);

        infoPanel.add(syncProgressBar);

        bottomPanel.add(infoLable, BorderLayout.CENTER);
        bottomPanel.add(infoPanel, BorderLayout.LINE_END);

        pack();
        setSize(Math.max(topPanel.getPreferredSize().width, metricsPanel.getPreferredSize().width), 800);
        setLocationRelativeTo(null);
        try {
            setIconImage(ImageIO.read(getClass().getResourceAsStream(iconLocation)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (trayIcon == null) {
                    if (JOptionPane.showConfirmDialog(SignumGUI.this,
                            "This will stop the node. Are you sure?", "Exit and stop node",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                        shutdown();
                    }
                } else {
                    trayIcon.displayMessage("Signum GUI closed", "Note that Signum is still running", MessageType.INFO);
                    setVisible(false);
                }
            }
        });

        // Timer to periodically update the network speed chart so it flows even with no
        // traffic
        Timer netSpeedChartUpdater = new Timer(100, e -> {
            updateNetVolumeAndSpeedChart(uploadedVolume, downloadedVolume);
        });
        netSpeedChartUpdater.start();

        showWindow();

        // Start BRS
        new Thread(this::startSignumWithGUI).start();
    }

    /**
     * A generic helper method to add a toggle listener to a JLabel.
     * It handles the common logic of toggling a 'visible' client property,
     * switching the font between normal and strikethrough, and then executing a
     * custom action.
     *
     * @param label          The label to attach the listener to.
     * @param onToggleAction The specific action to perform when the label is
     *                       toggled.
     *                       It receives the new visibility state as a boolean.
     */
    private void addLabelToggleListener(JLabel label, Consumer<Boolean> onToggleAction) {
        label.putClientProperty("visible", true);
        final Font originalFont = label.getFont();
        // Create a strikethrough version of the font to indicate a disabled state
        final Map<TextAttribute, Object> attributes = new HashMap<>(originalFont.getAttributes());
        attributes.put(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
        final Font strikethroughFont = originalFont.deriveFont(attributes);

        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                if (SwingUtilities.isLeftMouseButton(evt)) {
                    // Toggle the visibility state
                    boolean isVisible = !((boolean) label.getClientProperty("visible"));
                    label.putClientProperty("visible", isVisible);

                    // Update the label's font to show the state
                    label.setFont(isVisible ? originalFont : strikethroughFont);

                    // Perform the specific toggle action
                    onToggleAction.accept(isVisible);
                }
            }
        });
    }

    private void addToggleListener(JLabel label, ChartPanel chartPanel, int rendererIndex, int seriesIndex) {
        addLabelToggleListener(label, isVisible -> chartPanel.getChart().getXYPlot().getRenderer(rendererIndex)
                .setSeriesVisible(seriesIndex, isVisible));
    }

    private void addPaintToggleListener(JLabel label, ChartPanel chartPanel, int rendererIndex, int seriesIndex,
            Color originalColor) {
        final Color transparentColor = new Color(0, 0, 0, 0);
        addLabelToggleListener(label, isVisible -> {
            org.jfree.chart.renderer.xy.AbstractXYItemRenderer renderer = (org.jfree.chart.renderer.xy.AbstractXYItemRenderer) chartPanel
                    .getChart().getXYPlot().getRenderer(rendererIndex);

            renderer.setSeriesVisible(seriesIndex, isVisible);
            renderer.setSeriesPaint(seriesIndex, isVisible ? originalColor : transparentColor);
        });
    }

    private void addDualChartToggleListener(JLabel label,
            ChartPanel chartPanel1, int rendererIndex1, int seriesIndex1,
            ChartPanel chartPanel2, int rendererIndex2, int seriesIndex2) {
        addLabelToggleListener(label, isVisible -> {
            chartPanel1.getChart().getXYPlot().getRenderer(rendererIndex1).setSeriesVisible(seriesIndex1, isVisible);
            chartPanel2.getChart().getXYPlot().getRenderer(rendererIndex2).setSeriesVisible(seriesIndex2, isVisible);
        });
    }

    public SignumGUI(String programName, String iconLocation, String version, Signum signum) {
        this.programName = programName;
        this.version = version;
    }

    private void shutdown() {
        userClosed = true;

        new Thread(() -> {
            Signum.shutdown(false);

            if (trayIcon != null && SystemTray.isSupported()) {
                SystemTray.getSystemTray().remove(trayIcon);
            }
            chartUpdateExecutor.shutdown();
            System.exit(0);
        }).start();
    }

    private void showTrayIcon() {
        if (trayIcon == null) { // Don't start running in tray twice
            trayIcon = createTrayIcon();
        }
    }

    private TrayIcon createTrayIcon() {
        PopupMenu popupMenu = new PopupMenu();

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));

        MenuItem openPheonixWalletItem = new MenuItem("Phoenix Wallet");
        MenuItem openClassicWalletItem = new MenuItem("Classic Wallet");
        MenuItem openApiItem = new MenuItem("API doc");
        MenuItem showItem = new MenuItem("Show the node window");
        MenuItem shutdownItem = new MenuItem("Shutdown the node");

        openPhoenixButton = new JButton(openPheonixWalletItem.getLabel(),
                IconFontSwing.buildIcon(FontAwesome.FIRE, 18, iconColor));
        openClassicButton = new JButton(openClassicWalletItem.getLabel(),
                IconFontSwing.buildIcon(FontAwesome.WINDOW_RESTORE, 18, iconColor));
        openApiButton = new JButton(openApiItem.getLabel(),
                IconFontSwing.buildIcon(FontAwesome.BOOK, 18, iconColor));
        editConfButton = new JButton("Edit conf file",
                IconFontSwing.buildIcon(FontAwesome.PENCIL, 18, iconColor));
        popOff10Button = new JButton("Pop off 10 blocks",
                IconFontSwing.buildIcon(FontAwesome.STEP_BACKWARD, 18, iconColor));
        popOff100Button = new JButton("Pop off 100 blocks",
                IconFontSwing.buildIcon(FontAwesome.BACKWARD, 18, iconColor));
        /*
         * restartButton = new JButton("Restart",
         * IconFontSwing.buildIcon(FontAwesome.REFRESH, 18, iconColor));
         */
        shutdownButton = new JButton("Shutdown",
                IconFontSwing.buildIcon(FontAwesome.POWER_OFF, 18, iconColor));
        // TODO: find a way to actually store permanently the max block available to
        // pop-off, otherwise we can break it
        // JButton popOffMaxButton = new JButton("Pop off max",
        // IconFontSwing.buildIcon(FontAwesome.FAST_BACKWARD, 18, iconColor));

        openPhoenixButton.addActionListener(e -> openWebUi("/phoenix"));
        openClassicButton.addActionListener(e -> openWebUi("/classic"));
        openApiButton.addActionListener(e -> openWebUi("/api-doc"));
        editConfButton.addActionListener(e -> editConf());
        popOff10Button.addActionListener(e -> popOff(10));
        popOff100Button.addActionListener(e -> popOff(100));
        // popOffMaxButton.addActionListener(e -> popOff(0));

        File phoenixIndex = new File("html/ui/phoenix/index.html");
        File classicIndex = new File("html/ui/classic/index.html");

        shutdownButton.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(SignumGUI.this,
                    "This will stop the node. Are you sure?", "Shutdown node",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                shutdown();
            }
        });
        /*
         * restartButton.addActionListener(e -> {
         * if (JOptionPane.showConfirmDialog(SignumGUI.this,
         * "This will restart the node. Are you sure?", "Restart node",
         * JOptionPane.YES_NO_OPTION,
         * JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
         * restart();
         * }
         * });
         */

        if (phoenixIndex.isFile() && phoenixIndex.exists()) {
            leftButtons.add(openPhoenixButton);
        }
        if (classicIndex.isFile() && classicIndex.exists()) {
            leftButtons.add(openClassicButton);
        }
        leftButtons.add(editConfButton);
        leftButtons.add(openApiButton);

        leftButtons.add(popOff10Button);
        popOff10Button.setVisible(showPopOff);
        leftButtons.add(popOff100Button);
        popOff100Button.setVisible(showPopOff);
        // toolBar.add(popOffMaxButton);

        // leftButtons.add(restartButton);
        leftButtons.add(shutdownButton);

        toolBar.add(leftButtons);
        toolBar.add(Box.createHorizontalGlue());
        toolBar.add(checkboxPanel);
        toolBar.add(Box.createHorizontalStrut(10));

        openPheonixWalletItem.addActionListener(e -> openWebUi("/phoenix"));
        openClassicWalletItem.addActionListener(e -> openWebUi("/classic"));
        showItem.addActionListener(e -> showWindow());
        shutdownItem.addActionListener(e -> shutdown());

        popupMenu.add(openClassicWalletItem);
        popupMenu.add(showItem);
        popupMenu.add(shutdownItem);

        getContentPane().validate();

        try {
            String newIconLocation = Signum.getPropertyService().getString(Props.ICON_LOCATION);
            if (!newIconLocation.equals(iconLocation)) {
                // update the icon
                iconLocation = newIconLocation;
                setIconImage(ImageIO.read(getClass().getResourceAsStream(iconLocation)));
            }
            TrayIcon newTrayIcon = new TrayIcon(
                    Toolkit.getDefaultToolkit().createImage(SignumGUI.class.getResource(iconLocation)), "Signum Node",
                    popupMenu);
            newTrayIcon.setImage(
                    newTrayIcon.getImage().getScaledInstance(newTrayIcon.getSize().width, -1, Image.SCALE_SMOOTH));
            if (phoenixIndex.isFile() && phoenixIndex.exists()) {
                newTrayIcon.addActionListener(e -> openWebUi("/phoenix"));
            }

            SystemTray systemTray = SystemTray.getSystemTray();
            systemTray.add(newTrayIcon);

            newTrayIcon.displayMessage("Signum Running",
                    "Signum is running on background, use this icon to interact with it.", MessageType.INFO);

            return newTrayIcon;
        } catch (Exception e) {
            LOGGER.info("Could not create tray icon");
            return null;
        }
    }

    private void showWindow() {
        setVisible(true);
    }

    private void popOff(int blocks) {
        LOGGER.info("Pop off requested, this can take a while...");
        int height = blocks > 0 ? Signum.getBlockchain().getLastBlock().getHeight() - blocks
                : Signum.getBlockchainProcessor().getMinRollbackHeight();
        new Thread(() -> Signum.getBlockchainProcessor().popOffTo(height)).start();
    }
    /*
     * private void restart() {
     * new Thread(() -> Signum.restart()).start();
     * }
     */

    private void editConf() {
        File file = new File(Signum.CONF_FOLDER, Signum.PROPERTIES_NAME);
        if (!file.exists()) {
            file = new File(Signum.CONF_FOLDER, Signum.DEFAULT_PROPERTIES_NAME);
            if (!file.exists()) {
                file = new File(Signum.DEFAULT_PROPERTIES_NAME);
            }
        }

        if (!file.exists()) {
            JOptionPane.showMessageDialog(this, "Could not find conf file: " + Signum.DEFAULT_PROPERTIES_NAME,
                    "File not found", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(file);
        } catch (IOException e) {
            LOGGER.error("Could not edit conf file", e);
        }
    }

    private void openWebUi(String path) {
        try {
            PropertyService propertyService = Signum.getPropertyService();
            int port = propertyService.getInt(Props.API_PORT);
            String httpPrefix = propertyService.getBoolean(Props.API_SSL) ? "https://" : "http://";
            String address = httpPrefix + "localhost:" + port + path;
            try {
                Desktop.getDesktop().browse(new URI(address));
            } catch (Exception e) { // Catches parse exception or exception when opening browser
                LOGGER.error("Could not open browser", e);
                showMessage("Error opening web UI. Please open your browser and navigate to " + address);
            }
        } catch (Exception e) { // Catches error accessing PropertyService
            LOGGER.error("Could not access PropertyService", e);
            showMessage("Could not open web UI as could not read the configuration file.");
        }
    }

    private void initListeners() {
        BlockchainProcessor blockchainProcessor = Signum.getBlockchainProcessor();
        blockchainProcessor.addListener(block -> onQueueStatus(), BlockchainProcessor.Event.QUEUE_STATUS_CHANGED);
        blockchainProcessor.addListener(block -> onPeerCountChanged(), BlockchainProcessor.Event.PEER_COUNT_CHANGED);
        blockchainProcessor.addListener(block -> onNetVolumeChanged(), BlockchainProcessor.Event.NET_VOLUME_CHANGED);
        blockchainProcessor.addListener(this::onPerformanceStatsUpdated,
                BlockchainProcessor.Event.PERFORMANCE_STATS_UPDATED);
        blockchainProcessor.addListener(this::onBlockPushed, BlockchainProcessor.Event.BLOCK_PUSHED);
    }

    public void onQueueStatus() {
        BlockchainProcessor.QueueStatus status = Signum.getBlockchainProcessor().getQueueStatus();
        if (status != null) {
            SwingUtilities.invokeLater(
                    () -> updateQueueStatus(status.unverifiedSize, status.verifiedSize, status.totalSize));
        }
    }

    public void onPeerCountChanged() {
        BlockchainProcessor blockchainProcessor = Signum.getBlockchainProcessor();
        SwingUtilities.invokeLater(() -> updatePeerCount(blockchainProcessor.getLastKnownConnectedPeerCount(),
                blockchainProcessor.getLastKnownPeerCount()));
    }

    public void onNetVolumeChanged() {
        BlockchainProcessor blockchainProcessor = Signum.getBlockchainProcessor();
        long newDownloadedVolume = blockchainProcessor.getDownloadedVolume();
        SwingUtilities.invokeLater(() -> {
            updateNetVolume(blockchainProcessor.getUploadedVolume(), newDownloadedVolume);

            // Start the GUI timer only once, when the first download volume is received,
            // and if experimental features are enabled in the config.
            if (Signum.getPropertyService().getBoolean(Props.EXPERIMENTAL) && !guiTimerStarted
                    && newDownloadedVolume > 0) {
                startGuiTimer();
                guiTimerStarted = true;
            }
        });
    }

    private void startGuiTimer() {
        guiTimer = new Timer(1000, e -> {
            if (Signum.getBlockchain() != null && Signum.getBlockchainProcessor() != null) {

                // The timer is started when the first download volume is received, so we
                // increment total elapsed time.
                guiAccumulatedSyncTimeMs += 1000;
                totalTimeLabel.setText("🕒 " + formatDuration(guiAccumulatedSyncTimeMs));

                int height = Signum.getBlockchain().getHeight();
                int feederHeight = Signum.getBlockchainProcessor().getLastBlockchainFeederHeight();
                int diff = feederHeight > 0 ? feederHeight - height : 0;

                if (!isSyncing && diff >= 10) {
                    isSyncing = true;
                } else if (isSyncing && diff <= 1) {
                    isSyncing = false;
                }

                if (isSyncing) {
                    guiAccumulatedSyncInProgressTimeMs += 1000;
                }
                syncInProgressTimeLabel
                        .setText("🔄 " + formatDuration(guiAccumulatedSyncInProgressTimeMs));

                updateTimeLabelVisibility();
            }
        });
        guiTimer.start();
    }

    public void onPerformanceStatsUpdated(Block block) {
        BlockchainProcessor.PerformanceStats stats = Signum.getBlockchainProcessor().getPerformanceStats();
        if (stats != null && block != null) {
            chartUpdateExecutor.submit(() -> {
                updateTimingChart(stats.totalTimeMs, stats.dbTimeMs, stats.atTimeMs, block);
            });
        }
    }

    private void onBlockPushed(Block block) {
        if (block == null)
            return;

        // Submit the heavy lifting to a background thread
        chartUpdateExecutor.submit(() -> updatePerformanceChart(block));
        SwingUtilities.invokeLater(() -> updateLatestBlock(block));
    }

    public void startSignumWithGUI() {
        try {
            // signum.init();
            Signum.main(args);

            // Now that properties are loaded, set the correct values for the GUI
            oclUnverifiedQueueThreshold = Signum.getPropertyService().getInt(Props.GPU_UNVERIFIED_QUEUE);
            showPopOff = Signum.getPropertyService().getBoolean(Props.EXPERIMENTAL);
            showMetrics = Signum.getPropertyService().getBoolean(Props.EXPERIMENTAL);
            boolean measurementActive = Signum.getPropertyService().getBoolean(Props.MEASUREMENT_ACTIVE);
            boolean experimentalActive = Signum.getPropertyService().getBoolean(Props.EXPERIMENTAL);

            try {
                SwingUtilities.invokeLater(() -> {
                    showTrayIcon();
                    // Sync checkbox states with loaded properties
                    showPopOffCheckbox.setSelected(showPopOff);
                    showMetricsCheckbox.setSelected(showMetrics);
                    // Sync panel visibility with loaded properties
                    metricsPanel.setVisible(showMetrics);
                    if (measurementActive) {
                        measurementPanel.setVisible(true);
                    }
                    if (experimentalActive) {
                        experimentalPanel.setVisible(true);
                        timePanel.setVisible(true);
                    }
                });

                updateTitle();

                initListeners();
                if (Signum.getPropertyService().getBoolean(Props.EXPERIMENTAL)) {
                    // Initialize timers from the log file.
                    BlockchainProcessor blockchainProcessor = Signum.getBlockchainProcessor();
                    if (blockchainProcessor != null) {
                        this.guiAccumulatedSyncTimeMs = blockchainProcessor.getAccumulatedSyncTimeMs();
                        this.guiAccumulatedSyncInProgressTimeMs = blockchainProcessor
                                .getAccumulatedSyncInProgressTimeMs();
                    }
                    // Update labels with initial values from log file
                    SwingUtilities.invokeLater(() -> {
                        totalTimeLabel.setText("🕒 " + formatDuration(guiAccumulatedSyncTimeMs));
                        syncInProgressTimeLabel
                                .setText("🔄 " + formatDuration(guiAccumulatedSyncInProgressTimeMs));
                        updateTimeLabelVisibility(); // Initial visibility check
                    });
                }
                if (Signum.getBlockchain() == null)
                    onBrsStopped();
            } catch (Exception t) {
                LOGGER.error("Could not determine if running in testnet mode", t);
            }
        } catch (Exception t) {
            LOGGER.error(FAILED_TO_START_MESSAGE, t);
            showMessage(FAILED_TO_START_MESSAGE);
            onBrsStopped();
        }

    }

    private void updateTimeLabelVisibility() {
        boolean showTotalTime = guiAccumulatedSyncTimeMs != guiAccumulatedSyncInProgressTimeMs;
        totalTimeLabel.setVisible(showTotalTime);
        timeSeparatorLabel.setVisible(showTotalTime);
    }

    /*
     * * This method is called when the Signum service is restarted.
     * * It re-initializes the GUI components and updates the state to reflect the
     * new
     * * service instances.
     */
    /*
     * public void reinitOnRestart() {
     * // Re-register listeners to the new service instances
     * initListeners();
     * 
     * // Manually update the UI with the current state after restart
     * updateTitle();
     * if (Signum.getBlockchain() != null) {
     * updateLatestBlock(Signum.getBlockchain().getLastBlock());
     * }
     * updatePeerCount(Peers.getAllPeers().size(), Peers.getActivePeers().size());
     * }
     */

    private void updateTitle() {
        String networkName = Signum.getPropertyService().getString(Props.NETWORK_NAME);
        SwingUtilities.invokeLater(() -> setTitle(
                this.programName + " [" + networkName + "] " + this.version));
        if (trayIcon != null)
            trayIcon.setToolTip(trayIcon.getToolTip() + " " + networkName);
    }

    private void updateLatestBlock(Block block) {
        if (block == null) {
            return;
        }
        Date blockDate = Convert.fromEpochTime(block.getTimestamp());
        infoLable.setText("Latest block: " + block.getHeight() +
                " Timestamp: " + DATE_FORMAT.format(blockDate));

        Date now = new Date();
        long blockTime = Signum.getFluxCapacitor().getValue(FluxValues.BLOCK_TIME);

        int missingBlocks = (int) ((now.getTime() - blockDate.getTime()) / (blockTime * 1000));
        if (missingBlocks < 0) {
            missingBlocks = 0;
        }

        float prog = 0;
        int totalBlocks = block.getHeight() + missingBlocks;
        if (totalBlocks > 0) {
            // Use 100.0f to force floating-point division, preserving decimal places
            prog = (float) block.getHeight() * 100.0f / totalBlocks;
        }

        if (prog > 100.0f) {
            prog = 100.0f;
        }
        syncProgressBar.setValue((int) prog);
        syncProgressBar.setString(String.format("%.2f %%", prog));
    }

    private void updateQueueStatus(int downloadCacheUnverifiedSize,
            int downloadCacheVerifiedSize,
            int downloadCacheTotalSize) {
        if (!showMetrics) {
            return; // Don't update queue status if metrics are not shown
        }

        syncProgressBarDownloadedBlocks.setStringPainted(true);
        syncProgressBarUnverifiedBlocks.setStringPainted(true);

        syncProgressBarDownloadedBlocks.setPreferredSize(new java.awt.Dimension(150, 20));
        syncProgressBarUnverifiedBlocks.setPreferredSize(new java.awt.Dimension(150, 20));

        if (downloadCacheTotalSize != 0) {
            syncProgressBarDownloadedBlocks.setString(downloadCacheVerifiedSize + " / " + downloadCacheTotalSize + " - "
                    + 100 * downloadCacheVerifiedSize / downloadCacheTotalSize + "%");
            syncProgressBarDownloadedBlocks.setValue(100 * downloadCacheVerifiedSize / downloadCacheTotalSize);

        } else {
            syncProgressBarDownloadedBlocks.setString("0 / 0 - 0%");
            syncProgressBarDownloadedBlocks.setValue(0);
        }

        syncProgressBarUnverifiedBlocks.setString(downloadCacheUnverifiedSize + "");
        syncProgressBarUnverifiedBlocks.setValue(downloadCacheUnverifiedSize);

        if (downloadCacheUnverifiedSize > oclUnverifiedQueueThreshold) {
            syncProgressBarUnverifiedBlocks.setForeground(Color.RED);
        } else {
            syncProgressBarUnverifiedBlocks.setForeground(Color.GREEN);
        }
    }

    private void updateNetVolumeAndSpeedChart(long uploadedVolume, long downloadedVolume) {
        chartUpdateExecutor.submit(() -> {
            // --- Calculations on background thread ---
            long currentTime = System.currentTimeMillis();
            if (lastNetVolumeUpdateTime == 0) {
                lastNetVolumeUpdateTime = currentTime;
                lastUploadedVolume = uploadedVolume;
                lastDownloadedVolume = downloadedVolume;
                return;
            }

            long deltaTime = currentTime - lastNetVolumeUpdateTime;
            if (deltaTime <= 0) {
                return; // Avoid division by zero or negative time intervals
            }

            long deltaUploaded = uploadedVolume - lastUploadedVolume;
            long deltaDownloaded = downloadedVolume - lastDownloadedVolume;

            double currentUploadSpeed = (double) deltaUploaded * 1000 / deltaTime; // bytes per second
            double currentDownloadSpeed = (double) deltaDownloaded * 1000 / deltaTime; // bytes per second

            // Add current speed to history and maintain size
            uploadSpeedHistory.add(currentUploadSpeed);
            if (uploadSpeedHistory.size() > SPEED_HISTORY_SIZE) {
                uploadSpeedHistory.removeFirst();
            }

            downloadSpeedHistory.add(currentDownloadSpeed);
            if (downloadSpeedHistory.size() > SPEED_HISTORY_SIZE) {
                downloadSpeedHistory.removeFirst();
            }

            int currentWindowSize = Math.min(uploadSpeedHistory.size(), movingAverageWindow);
            if (currentWindowSize < 1) {
                return;
            }

            double avgUploadSpeed = uploadSpeedHistory.stream()
                    .skip(Math.max(0, uploadSpeedHistory.size() - currentWindowSize))
                    .mapToDouble(d -> d)
                    .average().orElse(0.0);

            double avgDownloadSpeed = downloadSpeedHistory.stream()
                    .skip(Math.max(0, downloadSpeedHistory.size() - currentWindowSize))
                    .mapToDouble(d -> d)
                    .average().orElse(0.0);

            lastNetVolumeUpdateTime = currentTime;
            lastUploadedVolume = uploadedVolume;
            lastDownloadedVolume = downloadedVolume;

            // --- UI Updates on EDT ---
            SwingUtilities.invokeLater(() -> {
                uploadVolumeLabel.setText("▲ " + formatDataSize(uploadedVolume));
                downloadVolumeLabel.setText("▼ " + formatDataSize(downloadedVolume));

                if (metricsUploadVolumeLabel != null) {
                    metricsUploadVolumeLabel.setText("▲ " + formatDataSize(uploadedVolume));
                }
                if (metricsDownloadVolumeLabel != null) {
                    metricsDownloadVolumeLabel.setText("▼ " + formatDataSize(downloadedVolume));
                }

                uploadSpeedProgressBar.setValue((int) avgUploadSpeed);
                uploadSpeedProgressBar.setString(formatDataRate(avgUploadSpeed));
                downloadSpeedProgressBar.setValue((int) avgDownloadSpeed);
                downloadSpeedProgressBar.setString(formatDataRate(avgDownloadSpeed));

                if (uploadedVolume > 0 || downloadedVolume > 0) {
                    uploadSpeedSeries.add(currentTime, avgUploadSpeed);
                    downloadSpeedSeries.add(currentTime, avgDownloadSpeed);
                    uploadVolumeSeries.add(currentTime, uploadedVolume);
                    downloadVolumeSeries.add(currentTime, downloadedVolume);

                    while (uploadSpeedSeries.getItemCount() > SPEED_HISTORY_SIZE) {
                        uploadSpeedSeries.remove(0);
                    }
                    while (downloadSpeedSeries.getItemCount() > SPEED_HISTORY_SIZE) {
                        downloadSpeedSeries.remove(0);
                    }
                    while (uploadVolumeSeries.getItemCount() > SPEED_HISTORY_SIZE) {
                        uploadVolumeSeries.remove(0);
                    }
                    while (downloadVolumeSeries.getItemCount() > SPEED_HISTORY_SIZE) {
                        downloadVolumeSeries.remove(0);
                    }
                }
            });
        });
    }

    private void updateNetVolume(long uploadedVolume, long downloadedVolume) {

        this.uploadedVolume = uploadedVolume;
        this.downloadedVolume = downloadedVolume;

    }

    private void updatePeerCount(int newConnectedCount, int count) {
        connectedPeersLabel.setText(newConnectedCount + "");
        peersCountLabel.setText(count + "");
    }

    private String formatDataSize(double bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        String[] units = { "B", "KB", "MB", "GB", "TB", "PB", "EB" };
        int unitIndex = 0;
        while (bytes >= 1024 && unitIndex < units.length - 1) {
            bytes /= 1024;
            unitIndex++;
        }
        return String.format("%.2f %s", bytes, units[unitIndex]);
    }

    private String formatDataRate(double bytesPerSecond) {
        if (bytesPerSecond <= 0) {
            return "0 B/s";
        }
        String[] units = { "B", "KB", "MB", "GB", "TB", "PB", "EB" };
        int unitIndex = 0;
        while (bytesPerSecond >= 1024 && unitIndex < units.length - 1) {
            bytesPerSecond /= 1024;
            unitIndex++;
        }
        return String.format("%.2f %s/s", bytesPerSecond, units[unitIndex]);
    }

    private void updateTimingChart(long totalTimeMs, long dbTimeMs, long atTimeMs, Block block) {

        if (!showMetrics || block == null) {
            return;
        }

        int blockHeight = block.getHeight();

        long calculationTimeMs = Math.max(0, totalTimeMs - dbTimeMs - atTimeMs);

        atTimes.add(atTimeMs);
        pushTimes.add(totalTimeMs);
        dbTimes.add(dbTimeMs);
        calculationTimes.add(calculationTimeMs);

        while (pushTimes.size() > CHART_HISTORY_SIZE) {
            pushTimes.removeFirst();
        }
        while (dbTimes.size() > CHART_HISTORY_SIZE) {
            dbTimes.removeFirst();
        }
        while (atTimes.size() > CHART_HISTORY_SIZE) {
            atTimes.removeFirst();
        }
        while (calculationTimes.size() > CHART_HISTORY_SIZE) {
            calculationTimes.removeFirst();
        }

        int currentWindowSize = Math.min(pushTimes.size(), movingAverageWindow);
        if (currentWindowSize < 1) {
            return;
        }

        long maxPushTime = pushTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long maxDbTime = dbTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long maxAtTime = atTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long maxCalculationTime = calculationTimes.stream().mapToLong(Long::longValue).max().orElse(0);

        long displayPushTime = (long) pushTimes.stream()
                .skip(Math.max(0, pushTimes.size() - currentWindowSize))
                .mapToLong(Long::longValue)
                .average().orElse(0.0);

        long displayDbTime = (long) dbTimes.stream()
                .skip(Math.max(0, dbTimes.size() - currentWindowSize))
                .mapToLong(Long::longValue)
                .average().orElse(0.0);

        long displayAtTime = (long) atTimes.stream()
                .skip(Math.max(0, atTimes.size() - currentWindowSize))
                .mapToLong(Long::longValue)
                .average().orElse(0.0);

        long displayCalculationTime = (long) calculationTimes.stream()
                .skip(Math.max(0, calculationTimes.size() - currentWindowSize))
                .mapToLong(Long::longValue)
                .average().orElse(0.0);

        SwingUtilities.invokeLater(() -> {
            pushTimeProgressBar.setValue((int) displayPushTime);
            pushTimeProgressBar.setString(String.format("%d ms - max: %d ms", displayPushTime, maxPushTime));
            dbTimeProgressBar.setValue((int) displayDbTime);
            dbTimeProgressBar.setString(String.format("%d ms - max: %d ms", displayDbTime, maxDbTime));
            atTimeProgressBar.setValue((int) displayAtTime);
            atTimeProgressBar.setString(String.format("%d ms - max: %d ms", displayAtTime, maxAtTime));
            calculationTimeProgressBar.setValue(Math.max(0, (int) displayCalculationTime));
            calculationTimeProgressBar
                    .setString(String.format("%d ms - max: %d ms", Math.max(0, displayCalculationTime),
                            Math.max(0, maxCalculationTime)));
            // Update timing chart series
            pushTimePerBlockSeries.add(blockHeight, displayPushTime);
            dbTimePerBlockSeries.add(blockHeight, displayDbTime);
            calculationTimePerBlockSeries.add(blockHeight, Math.max(0, displayCalculationTime));
            atTimePerBlockSeries.add(blockHeight, displayAtTime);

            // Keep history size for timing chart
            while (pushTimePerBlockSeries.getItemCount() > CHART_HISTORY_SIZE) {
                pushTimePerBlockSeries.remove(0);
            }
            while (dbTimePerBlockSeries.getItemCount() > CHART_HISTORY_SIZE) {
                dbTimePerBlockSeries.remove(0);
            }
            while (calculationTimePerBlockSeries.getItemCount() > CHART_HISTORY_SIZE) {
                calculationTimePerBlockSeries.remove(0);
            }
            while (atTimePerBlockSeries.getItemCount() > CHART_HISTORY_SIZE) {
                atTimePerBlockSeries.remove(0);
            }
        });
    }

    private ChartPanel createPerformanceChartPanel() {
        blocksPerSecondSeries = new XYSeries("Blocks/Second (MA)");
        transactionsPerSecondSeries = new XYSeries("Transactions/Second (MA)");
        transactionsPerBlockSeries = new XYSeries("Transactions/Block (MA)");

        XYSeriesCollection lineDataset = new XYSeriesCollection();
        lineDataset.addSeries(blocksPerSecondSeries);
        lineDataset.addSeries(transactionsPerSecondSeries);

        XYSeriesCollection transactionsDataset = new XYSeriesCollection(transactionsPerBlockSeries);

        // Create chart with no title or axis labels to save space
        JFreeChart chart = ChartFactory.createXYLineChart(
                null, // No title
                null, // No X-axis label
                null, // No Y-axis label
                lineDataset);

        // Remove the legend to maximize plot area
        chart.removeLegend();
        chart.setBorderVisible(false);

        XYPlot plot = chart.getXYPlot();
        plot.getDomainAxis().setLowerMargin(0.0);
        plot.getDomainAxis().setUpperMargin(0.0);
        plot.setBackgroundPaint(Color.DARK_GRAY);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);

        plot.getRenderer().setSeriesPaint(0, Color.CYAN);
        plot.getRenderer().setSeriesPaint(1, Color.GREEN);

        // Set line thickness
        plot.getRenderer().setSeriesStroke(0, new java.awt.BasicStroke(1.2f));
        plot.getRenderer().setSeriesStroke(1, new java.awt.BasicStroke(1.2f));

        // Hide axis tick labels (the numbers on the axes)
        plot.getDomainAxis().setTickLabelsVisible(false);
        plot.getRangeAxis().setTickLabelsVisible(false);

        // Second Y-axis for transaction count
        NumberAxis transactionAxis = new NumberAxis(null); // No label for the second axis
        transactionAxis.setTickLabelsVisible(false);
        plot.setRangeAxis(1, transactionAxis);
        plot.setDataset(1, transactionsDataset);
        plot.mapDatasetToRangeAxis(1, 1);

        // Renderer for transaction bars
        XYBarRenderer transactionRenderer = new XYBarRenderer(0);
        transactionRenderer.setBarPainter(new StandardXYBarPainter());
        transactionRenderer.setShadowVisible(false);
        transactionRenderer.setSeriesPaint(0, new Color(255, 165, 0, 128)); // Orange, semi-transparent
        plot.setRenderer(1, transactionRenderer);

        // Remove all padding around the plot area
        plot.setInsets(new RectangleInsets(0, 0, 0, 0));
        plot.setAxisOffset(new RectangleInsets(0, 0, 0, 0));

        ChartPanel chartPanel = new ChartPanel(chart);
        // Set a fixed 4:3 aspect ratio size, 2x the previous size
        Dimension newSize = new Dimension(240, 180);
        chartPanel.setPreferredSize(newSize);
        chartPanel.setMinimumSize(newSize);
        chartPanel.setMaximumSize(newSize);
        return chartPanel;
    }

    private ChartPanel createTimingChartPanel() {
        pushTimePerBlockSeries = new XYSeries("Push Time/Block (MA)");
        dbTimePerBlockSeries = new XYSeries("DB Time/Block (MA)");
        calculationTimePerBlockSeries = new XYSeries("Calculation Time/Block (MA)");
        atTimePerBlockSeries = new XYSeries("AT Time/Block (MA)");

        XYSeriesCollection lineDataset = new XYSeriesCollection();
        lineDataset.addSeries(pushTimePerBlockSeries);
        lineDataset.addSeries(dbTimePerBlockSeries);
        lineDataset.addSeries(calculationTimePerBlockSeries);
        lineDataset.addSeries(atTimePerBlockSeries);

        XYSeriesCollection transactionsDataset = new XYSeriesCollection(transactionsPerBlockSeries);

        // Create chart with no title or axis labels to save space
        JFreeChart chart = ChartFactory.createXYLineChart(
                null, // No title
                null, // No X-axis label
                null, // No Y-axis label
                lineDataset);

        // Remove the legend to maximize plot area
        chart.removeLegend();
        chart.setBorderVisible(false);

        XYPlot plot = chart.getXYPlot();
        plot.getDomainAxis().setLowerMargin(0.0);
        plot.getDomainAxis().setUpperMargin(0.0);
        plot.setBackgroundPaint(Color.DARK_GRAY);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);

        plot.getRenderer().setSeriesPaint(0, Color.BLUE);
        plot.getRenderer().setSeriesPaint(1, Color.YELLOW);
        plot.getRenderer().setSeriesPaint(2, new Color(128, 0, 128));
        plot.getRenderer().setSeriesPaint(3, new Color(153, 0, 76));

        // Set line thickness
        plot.getRenderer().setSeriesStroke(0, new java.awt.BasicStroke(1.2f));
        plot.getRenderer().setSeriesStroke(1, new java.awt.BasicStroke(1.2f));
        plot.getRenderer().setSeriesStroke(2, new java.awt.BasicStroke(1.2f));
        plot.getRenderer().setSeriesStroke(3, new java.awt.BasicStroke(1.2f));

        // Hide axis tick labels (the numbers on the axes)
        plot.getDomainAxis().setTickLabelsVisible(false);
        plot.getRangeAxis().setTickLabelsVisible(false);

        // Second Y-axis for transaction count
        NumberAxis transactionAxis = new NumberAxis(null); // No label for the second axis
        transactionAxis.setTickLabelsVisible(false);
        plot.setRangeAxis(1, transactionAxis);
        plot.setDataset(1, transactionsDataset);
        plot.mapDatasetToRangeAxis(1, 1);

        // Renderer for transaction bars
        XYBarRenderer transactionRenderer = new XYBarRenderer(0);
        transactionRenderer.setBarPainter(new StandardXYBarPainter());
        transactionRenderer.setShadowVisible(false);
        transactionRenderer.setSeriesPaint(0, new Color(255, 165, 0, 128)); // Orange, semi-transparent
        plot.setRenderer(1, transactionRenderer);

        // Remove all padding around the plot area
        plot.setInsets(new RectangleInsets(0, 0, 0, 0));
        plot.setAxisOffset(new RectangleInsets(0, 0, 0, 0));

        ChartPanel chartPanel = new ChartPanel(chart);
        // Set a fixed 4:3 aspect ratio size, 2x the previous size
        Dimension newSize = new Dimension(240, 180);
        chartPanel.setPreferredSize(newSize);
        chartPanel.setMinimumSize(newSize);
        chartPanel.setMaximumSize(newSize);
        return chartPanel;
    }

    private ChartPanel createNetSpeedChartPanel() {
        uploadSpeedSeries = new XYSeries("Upload Speed");
        downloadSpeedSeries = new XYSeries("Download Speed");
        uploadVolumeSeries = new XYSeries("Upload Volume");
        downloadVolumeSeries = new XYSeries("Download Volume");

        XYSeriesCollection lineDataset = new XYSeriesCollection();
        lineDataset.addSeries(uploadSpeedSeries);
        lineDataset.addSeries(downloadSpeedSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
                null, // No title
                null, // No X-axis label
                null, // No Y-axis label
                lineDataset);

        // Remove the legend to maximize plot area
        chart.removeLegend();
        chart.setBorderVisible(false);

        XYPlot plot = chart.getXYPlot();
        plot.getDomainAxis().setLowerMargin(0.0);
        plot.getDomainAxis().setUpperMargin(0.0);
        plot.setBackgroundPaint(Color.DARK_GRAY);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);

        plot.getRenderer().setSeriesPaint(0, new Color(128, 0, 0)); // Upload - Red, semi-transparent
        plot.getRenderer().setSeriesPaint(1, new Color(0, 100, 0)); // Download - Green, semi-transparent

        // Set line thickness
        plot.getRenderer().setSeriesStroke(0, new java.awt.BasicStroke(1.2f));
        plot.getRenderer().setSeriesStroke(1, new java.awt.BasicStroke(1.2f));

        // Hide axis tick labels (the numbers on the axes)
        plot.getDomainAxis().setTickLabelsVisible(false);
        plot.getRangeAxis().setTickLabelsVisible(false);

        // Second Y-axis for volume
        NumberAxis volumeAxis = new NumberAxis(null); // No label for the second axis
        volumeAxis.setTickLabelsVisible(false);
        plot.setRangeAxis(1, volumeAxis); // Use axis index 1 for volume

        // A single dataset and renderer for both volume series.
        XYSeriesCollection volumeDataset = new XYSeriesCollection();
        volumeDataset.addSeries(downloadVolumeSeries); // Series 0: Download (top layer)
        volumeDataset.addSeries(uploadVolumeSeries); // Series 1: Upload (bottom layer)

        XYStepAreaRenderer volumeRenderer = new XYStepAreaRenderer();
        volumeRenderer.setShapesVisible(false);
        volumeRenderer.setSeriesPaint(0, new Color(50, 205, 50, 128)); // Download - Green
        volumeRenderer.setSeriesPaint(1, new Color(233, 150, 122, 128)); // Upload - Red
        plot.setDataset(1, volumeDataset);
        plot.setRenderer(1, volumeRenderer);
        plot.mapDatasetToRangeAxis(1, 1);

        // Remove all padding around the plot area
        plot.setInsets(new RectangleInsets(0, 0, 0, 0));
        plot.setAxisOffset(new RectangleInsets(0, 0, 0, 0));

        ChartPanel chartPanel = new ChartPanel(chart);
        // Set a fixed 4:3 aspect ratio
        Dimension newSize = new Dimension(240, 180);
        chartPanel.setPreferredSize(newSize);
        chartPanel.setMinimumSize(newSize);
        chartPanel.setMaximumSize(newSize);
        return chartPanel;
    }

    private void updatePerformanceChart(Block block) {

        if (!showMetrics) {
            return;
        }

        blockTimestamps.add(System.currentTimeMillis());

        int totalTxCount = block.getTransactions().size();
        if (block.getBlockAts() != null) {
            try {
                totalTxCount += AtController.getATsFromBlock(block.getBlockAts()).size();
            } catch (Exception e) {
                LOGGER.warn("Could not parse ATs from block", e);
            }
        }
        transactionCounts.add(totalTxCount);

        while (blockTimestamps.size() > CHART_HISTORY_SIZE) {
            blockTimestamps.removeFirst();
            transactionCounts.removeFirst();
            if (!blocksPerSecondHistory.isEmpty()) {
                blocksPerSecondHistory.removeFirst();
            }
            if (!transactionsPerSecondHistory.isEmpty()) {
                transactionsPerSecondHistory.removeFirst();
            }
        }

        long timeSpanMs = blockTimestamps.getLast()
                - blockTimestamps.get(blockTimestamps.size() - Math.min(blockTimestamps.size(), movingAverageWindow));
        double blocksPerSecond = (timeSpanMs > 0)
                ? (double) Math.min(blockTimestamps.size(), movingAverageWindow) * 1000.0 / timeSpanMs
                : 0;
        blocksPerSecondHistory.add(blocksPerSecond);

        double avgTransactions = transactionCounts.stream()
                .skip(Math.max(0, transactionCounts.size() - Math.min(transactionCounts.size(), movingAverageWindow)))
                .mapToInt(Integer::intValue)
                .average().orElse(0.0);

        double transactionsPerSecond = avgTransactions * blocksPerSecond;
        transactionsPerSecondHistory.add(transactionsPerSecond);

        double maxBlocksPerSecond = blocksPerSecondHistory.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        int maxTransactionsPerBlock = transactionCounts.stream().mapToInt(Integer::intValue).max().orElse(0);
        double maxTransactionsPerSecond = transactionsPerSecondHistory.stream().mapToDouble(Double::doubleValue).max()
                .orElse(0.0);

        // Now, schedule only the UI updates on the EDT
        SwingUtilities.invokeLater(() -> {
            // Prune series on EDT before adding new data
            while (blocksPerSecondSeries.getItemCount() >= CHART_HISTORY_SIZE) {
                blocksPerSecondSeries.remove(0);
            }
            while (transactionsPerSecondSeries.getItemCount() >= CHART_HISTORY_SIZE) {
                transactionsPerSecondSeries.remove(0);
            }
            while (transactionsPerBlockSeries.getItemCount() >= CHART_HISTORY_SIZE) {
                transactionsPerBlockSeries.remove(0);
            }

            blocksPerSecondSeries.add(block.getHeight(), blocksPerSecond);
            transactionsPerBlockSeries.add(block.getHeight(), avgTransactions);
            blocksPerSecondProgressBar.setValue((int) (blocksPerSecond));
            blocksPerSecondProgressBar
                    .setString(String.format("%.2f - max: %.2f", blocksPerSecond, maxBlocksPerSecond));

            transactionsPerSecondSeries.add(block.getHeight(), transactionsPerSecond);
            transactionsPerSecondProgressBar.setValue((int) transactionsPerSecond);
            transactionsPerSecondProgressBar
                    .setString(String.format("%.2f - max: %.2f", transactionsPerSecond, maxTransactionsPerSecond));

            transactionsPerBlockProgressBar.setValue((int) avgTransactions);
            transactionsPerBlockProgressBar
                    .setString(String.format("%.2f - max: %d", avgTransactions, maxTransactionsPerBlock));
        });
    }

    private void onBrsStopped() {
        SwingUtilities.invokeLater(() -> setTitle(getTitle() + " (STOPPED)"));
        if (trayIcon != null)
            trayIcon.setToolTip(trayIcon.getToolTip() + " (STOPPED)");
    }

    private void sendJavaOutputToTextArea(JTextArea textArea) {
        System.setOut(new PrintStream(new TextAreaOutputStream(textArea, System.out)));
        System.setErr(new PrintStream(new TextAreaOutputStream(textArea, System.err)));
    }

    private void showMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            System.err.println("Showing message: " + message);
            JOptionPane.showMessageDialog(this, message, "Signum Message", JOptionPane.ERROR_MESSAGE);
        });
    }

    /**
     * Formats a duration in milliseconds into a human-readable string (y:d:h:m:s).
     * Omits larger units if they are zero.
     *
     * @param millis The duration in milliseconds.
     * @return A formatted string representing the duration.
     */
    private String formatDuration(long millis) {
        if (millis <= 0) {
            return "0s";
        }

        long totalSeconds = millis / 1000;

        final long SEC_PER_MINUTE = 60;
        final long SEC_PER_HOUR = SEC_PER_MINUTE * 60;
        final long SEC_PER_DAY = SEC_PER_HOUR * 24;
        final long SEC_PER_YEAR = SEC_PER_DAY * 365; // Approximation

        long years = totalSeconds / SEC_PER_YEAR;
        long secondsAfterYears = totalSeconds % SEC_PER_YEAR;

        long days = secondsAfterYears / SEC_PER_DAY;
        long secondsAfterDays = secondsAfterYears % SEC_PER_DAY;

        long hours = secondsAfterDays / SEC_PER_HOUR;
        long secondsAfterHours = secondsAfterDays % SEC_PER_HOUR;

        long minutes = secondsAfterHours / SEC_PER_MINUTE;
        long seconds = secondsAfterHours % SEC_PER_MINUTE;

        StringBuilder sb = new StringBuilder();
        if (years > 0) {
            sb.append(years).append("y:");
        }
        if (days > 0 || sb.length() > 0) {
            sb.append(String.format(sb.length() > 0 ? "%02d" : "%d", days)).append("d:");
        }
        if (hours > 0 || sb.length() > 0) {
            sb.append(String.format(sb.length() > 0 ? "%02d" : "%d", hours)).append("h:");
        }
        if (minutes > 0 || sb.length() > 0) {
            sb.append(String.format(sb.length() > 0 ? "%02d" : "%d", minutes)).append("m:");
        }
        sb.append(String.format(sb.length() > 0 ? "%02d" : "%d", seconds)).append("s");

        return sb.toString();
    }

    private static class TextAreaOutputStream extends OutputStream {
        private final JTextArea textArea;
        private final PrintStream actualOutput;

        private StringBuilder lineBuilder = new StringBuilder();

        private TextAreaOutputStream(JTextArea textArea, PrintStream actualOutput) {
            this.textArea = textArea;
            this.actualOutput = actualOutput;
        }

        @Override
        public void write(int b) {
            writeString(new String(new byte[] { (byte) b }));
        }

        @Override
        public void write(byte[] b) {
            writeString(new String(b));
        }

        @Override
        public void write(byte[] b, int off, int len) {
            writeString(new String(b, off, len));
        }

        private void writeString(String string) {
            lineBuilder.append(string);
            String line = lineBuilder.toString();
            if (line.contains("\n")) {
                actualOutput.print(line);
                if (textArea != null)
                    SwingUtilities.invokeLater(() -> textArea.append(line));
                lineBuilder.delete(0, lineBuilder.length());
            }
        }
    }
}
