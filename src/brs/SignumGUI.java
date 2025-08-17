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
import java.awt.Dimension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import brs.fluxcapacitor.FluxValues;
import brs.props.PropertyService;
import brs.props.Props;
import brs.util.Convert;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

import java.awt.font.TextAttribute;
import java.util.Map;

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
    private JLabel uploadSpeedLabel;
    private JLabel downloadSpeedLabel;
    private JLabel uploadVolumeLabel;
    private JLabel metricsUploadVolumeLabel;
    private JLabel metricsDownloadVolumeLabel;
    private JLabel downloadVolumeLabel;

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

        // bottomPanel.add(infoLable, BorderLayout.CENTER);
        // bottomPanel.add(syncProgressBar, BorderLayout.LINE_END);

        // === Metrics panel ===
        // This is the main container for all metric groups.
        metricsPanel = new JPanel(new GridBagLayout());
        metricsPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        GridBagConstraints mainMetricsGbc = new GridBagConstraints();
        mainMetricsGbc.anchor = GridBagConstraints.CENTER;
        mainMetricsGbc.insets = new Insets(0, 5, 0, 5);
        mainMetricsGbc.gridy = 0;
        // === Container for the first group of metrics (Performance) ===
        JPanel performanceMetricsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints metricsGbc = new GridBagConstraints();

        // === Start Download Panel ===
        // We use GridBagLayout to align the progress bars vertically
        JPanel downloadPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5); // Padding

        Dimension progressBarSize = new Dimension(150, 20);

        // --- Row 1: Verified/Total Blocks ---
        JLabel verifLabel = new JLabel("Verified/Total Blocks:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        downloadPanel.add(verifLabel, gbc);
        addInfoTooltip(verifLabel,
                "Shows the number of blocks in the download queue that have been verified against the total number of blocks in the queue. A high number of unverified blocks may indicate a slow verification process.");

        syncProgressBarDownloadedBlocks = new JProgressBar();
        syncProgressBarDownloadedBlocks.setBackground(Color.GREEN);
        syncProgressBarDownloadedBlocks.setPreferredSize(progressBarSize);
        syncProgressBarDownloadedBlocks.setMinimumSize(progressBarSize);
        syncProgressBarDownloadedBlocks.setStringPainted(true);
        syncProgressBarDownloadedBlocks.setString("0 / 0 - 0%");
        syncProgressBarDownloadedBlocks.setValue(0);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        downloadPanel.add(syncProgressBarDownloadedBlocks, gbc);

        // --- Row 2: Unverified Blocks ---
        JLabel unVerifLabel = new JLabel("Unverified Blocks:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        downloadPanel.add(unVerifLabel, gbc);
        addInfoTooltip(unVerifLabel,
                "The number of blocks in the download queue that are waiting for PoC (Proof-of-Capacity) verification. A persistently high number might indicate that the CPU or GPU is unable to keep up with the network.");

        syncProgressBarUnverifiedBlocks = new JProgressBar(0, 2000); // Max 2000 unverified blocks, scaled by 100
        syncProgressBarUnverifiedBlocks.setBackground(Color.RED);
        syncProgressBarUnverifiedBlocks.setPreferredSize(progressBarSize);
        syncProgressBarUnverifiedBlocks.setMinimumSize(progressBarSize);
        syncProgressBarUnverifiedBlocks.setStringPainted(true);
        syncProgressBarUnverifiedBlocks.setString("0");
        syncProgressBarUnverifiedBlocks.setValue(0);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        downloadPanel.add(syncProgressBarUnverifiedBlocks, gbc);

        // --- Separator ---
        JSeparator separator1 = new JSeparator(SwingConstants.HORIZONTAL);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3; // Span across all columns
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 5, 2, 5); // Match other components' padding
        downloadPanel.add(separator1, gbc);
        gbc.gridwidth = 1; // Reset gridwidth
        gbc.insets = new Insets(2, 5, 2, 5); // Reset insets

        // --- Row 3: Blocks/Second (Moving Average) ---
        JLabel blocksPerSecondLabel = new JLabel("Blocks/Sec (MA):");
        blocksPerSecondLabel.setForeground(Color.CYAN);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        downloadPanel.add(blocksPerSecondLabel, gbc);
        addInfoTooltip(blocksPerSecondLabel,
                "The moving average of blocks processed per second. This indicates the speed at which your node is catching up with the blockchain.");

        blocksPerSecondProgressBar = new JProgressBar(0, 200); // Max 2 blocks/sec, scaled by 100
        blocksPerSecondProgressBar.setPreferredSize(progressBarSize);
        blocksPerSecondProgressBar.setMinimumSize(progressBarSize);
        blocksPerSecondProgressBar.setStringPainted(true);
        blocksPerSecondProgressBar.setString("0");
        blocksPerSecondProgressBar.setValue(0);
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        downloadPanel.add(blocksPerSecondProgressBar, gbc);

        // --- Row 4: Transactions/Second (Moving Average) ---
        JLabel txPerSecondLabel = new JLabel("Transactions/Sec (MA):");
        txPerSecondLabel.setForeground(Color.GREEN);
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.LINE_END;
        downloadPanel.add(txPerSecondLabel, gbc);
        addInfoTooltip(txPerSecondLabel,
                "The moving average of transactions processed per second. This metric reflects the current transactional throughput of the network as seen by your node.");

        transactionsPerSecondProgressBar = new JProgressBar(0, 2000); // Max 2000 tx/s
        transactionsPerSecondProgressBar.setPreferredSize(progressBarSize);
        transactionsPerSecondProgressBar.setMinimumSize(progressBarSize);
        transactionsPerSecondProgressBar.setStringPainted(true);
        transactionsPerSecondProgressBar.setString("0");
        transactionsPerSecondProgressBar.setValue(0);
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.LINE_START;
        downloadPanel.add(transactionsPerSecondProgressBar, gbc);

        // --- Row 5: Transactions/Block (Moving Average) ---
        JLabel txPerBlockLabel = new JLabel("Transactions/Block (MA):");
        txPerBlockLabel.setForeground(new Color(255, 165, 0)); // Orange
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.anchor = GridBagConstraints.LINE_END;
        downloadPanel.add(txPerBlockLabel, gbc);
        addInfoTooltip(txPerBlockLabel,
                "The moving average of the number of transactions included in each block. This provides insight into how full blocks are on average.");

        transactionsPerBlockProgressBar = new JProgressBar(0, 255); // Max tx/block
        transactionsPerBlockProgressBar.setPreferredSize(progressBarSize);
        transactionsPerBlockProgressBar.setMinimumSize(progressBarSize);
        transactionsPerBlockProgressBar.setStringPainted(true);
        transactionsPerBlockProgressBar.setString("0");
        transactionsPerBlockProgressBar.setValue(0);
        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.anchor = GridBagConstraints.LINE_START;
        downloadPanel.add(transactionsPerBlockProgressBar, gbc);

        // --- Row 6: Moving Average Slider ---
        JLabel maWindowLabel = new JLabel("MA Window (Blocks):");
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.anchor = GridBagConstraints.LINE_END;
        downloadPanel.add(maWindowLabel, gbc);
        addInfoTooltip(maWindowLabel,
                "The number of recent blocks used to calculate the moving average for performance metrics. A larger window provides a smoother but less responsive trend, while a smaller window is more reactive to recent changes.");

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
        gbc.gridx = 1;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.LINE_START;
        downloadPanel.add(movingAverageSlider, gbc);
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        // === End Download Panel ===

        // Add downloadPanel to the left
        metricsGbc.gridx = 0;
        metricsGbc.gridy = 0;
        metricsGbc.weightx = 1.0;
        metricsGbc.fill = GridBagConstraints.HORIZONTAL;
        metricsGbc.anchor = GridBagConstraints.NORTHWEST;
        performanceMetricsPanel.add(downloadPanel, metricsGbc);

        // Create and add chart panel to the right
        JPanel performanceChartContainer = new JPanel();
        performanceChartContainer.setLayout(new BoxLayout(performanceChartContainer, BoxLayout.Y_AXIS));
        performanceChartPanel = createPerformanceChartPanel();
        performanceChartContainer.add(performanceChartPanel);

        addToggleListener(blocksPerSecondLabel, performanceChartPanel, 0, 0);
        addToggleListener(txPerSecondLabel, performanceChartPanel, 0, 1);

        /*
         * // --- Moving Average Slider ---
         * movingAverageSlider = new JSlider(JSlider.HORIZONTAL, 10, 500,
         * movingAverageWindow);
         * movingAverageSlider.add(new Label("MA Window"), BorderLayout.NORTH);
         * movingAverageSlider.setMajorTickSpacing(100);
         * movingAverageSlider.setMinorTickSpacing(10);
         * movingAverageSlider.setPaintTicks(true);
         * movingAverageSlider.setPaintLabels(true);
         * movingAverageSlider.addChangeListener(e -> {
         * JSlider source = (JSlider) e.getSource();
         * // Update only when the user releases the slider
         * if (!source.getValueIsAdjusting()) {
         * movingAverageWindow = source.getValue();
         * }
         * });
         * 
         * JPanel sliderWithLabelPanel = new JPanel(new BorderLayout());
         * sliderWithLabelPanel.add(new VerticalLabel("MA Window"), BorderLayout.WEST);
         * sliderWithLabelPanel.add(movingAverageSlider, BorderLayout.CENTER);
         */
        JPanel chartContainer = new JPanel(new BorderLayout(10, 0));
        chartContainer.add(performanceChartContainer, BorderLayout.CENTER);
        // chartContainer.add(sliderWithLabelPanel, BorderLayout.EAST);

        metricsGbc.gridx = 1;
        metricsGbc.gridy = 0;
        metricsGbc.weightx = 0;
        metricsGbc.weighty = 0;
        metricsGbc.fill = GridBagConstraints.NONE;
        metricsGbc.anchor = GridBagConstraints.NORTHWEST;
        performanceMetricsPanel.add(chartContainer, metricsGbc);

        // Add the performance metrics group to the main metrics panel
        mainMetricsGbc.gridx = 0;
        metricsPanel.add(performanceMetricsPanel, mainMetricsGbc);

        // === Container for the second group of metrics (Timing) ===
        JPanel timingMetricsPanel = new JPanel(new GridBagLayout()); // Main container for this group
        metricsGbc = new GridBagConstraints(); // Re-using this from performance panel, which is
                                               // fine.

        // Create the net speed chart panel early so its listeners can be attached
        netSpeedChartPanel = createNetSpeedChartPanel();

        // === Start Timing Info Panel ===
        // This panel will hold the labels, progress bars, and checkboxes, similar to
        // 'downloadPanel'
        JPanel timingInfoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints timingGbc = new GridBagConstraints();
        timingGbc.insets = new Insets(2, 5, 2, 5);
        timingGbc.anchor = GridBagConstraints.LINE_END;
        timingGbc.fill = GridBagConstraints.NONE;
        timingGbc.weightx = 0;

        Dimension timingProgressBarSize = new Dimension(150, 20);

        // --- Push Time ---
        pushTimeLabel = new JLabel("Push Time/Block (MA):");
        pushTimeLabel.setForeground(Color.BLUE);
        timingGbc.gridx = 0;
        timingGbc.gridy = 1;
        timingInfoPanel.add(pushTimeLabel, timingGbc);
        addInfoTooltip(pushTimeLabel,
                "The moving average of the total time taken to process and push a new block to the blockchain, including all validations and database operations.");

        pushTimeProgressBar = new JProgressBar(0, 100);
        pushTimeProgressBar.setPreferredSize(timingProgressBarSize);
        pushTimeProgressBar.setMinimumSize(timingProgressBarSize);
        pushTimeProgressBar.setStringPainted(true);
        pushTimeProgressBar.setString("0 ms");
        timingGbc.gridx = 1;
        timingGbc.gridy = 1;
        timingInfoPanel.add(pushTimeProgressBar, timingGbc);

        // --- DB Time ---
        dbTimeLabel = new JLabel("DB Time/Block (MA):");
        dbTimeLabel.setForeground(Color.YELLOW);
        timingGbc.gridx = 0;
        timingGbc.gridy = 2;
        timingInfoPanel.add(dbTimeLabel, timingGbc);
        addInfoTooltip(dbTimeLabel,
                "The moving average of the time spent on database operations for each block. High values may indicate a slow disk or database contention.");

        dbTimeProgressBar = new JProgressBar(0, 100);
        dbTimeProgressBar.setPreferredSize(timingProgressBarSize);
        dbTimeProgressBar.setMinimumSize(timingProgressBarSize);
        dbTimeProgressBar.setStringPainted(true);
        dbTimeProgressBar.setString("0 ms");
        timingGbc.gridx = 1;
        timingGbc.gridy = 2;
        timingInfoPanel.add(dbTimeProgressBar, timingGbc);

        // --- AT Time ---
        atTimeLabel = new JLabel("AT Time/Block (MA):");
        atTimeLabel.setForeground(new Color(153, 0, 76)); // Deep Pink
        timingGbc.gridx = 0;
        timingGbc.gridy = 3;
        timingInfoPanel.add(atTimeLabel, timingGbc);
        addInfoTooltip(atTimeLabel,
                "The moving average of the time spent processing Automated Transactions (ATs) within each block. This metric is relevant for assessing the performance impact of smart contracts on the network.");

        atTimeProgressBar = new JProgressBar(0, 100);
        atTimeProgressBar.setPreferredSize(timingProgressBarSize);
        atTimeProgressBar.setMinimumSize(timingProgressBarSize);
        atTimeProgressBar.setStringPainted(true);
        atTimeProgressBar.setString("0 ms");
        timingGbc.gridx = 1;
        timingGbc.gridy = 3;
        timingInfoPanel.add(atTimeProgressBar, timingGbc);

        // --- Calculation Time ---
        calculationTimeLabel = new JLabel("Calc Time/Block (MA):");
        calculationTimeLabel.setForeground(new Color(128, 0, 128));
        timingGbc.gridx = 0;
        timingGbc.gridy = 4;
        timingInfoPanel.add(calculationTimeLabel, timingGbc);
        addInfoTooltip(calculationTimeLabel,
                "The moving average of the CPU time spent on calculations for each block, excluding database and Automated Transaction (AT) processing time. This includes signature verifications and other cryptographic operations.");

        calculationTimeProgressBar = new JProgressBar(0, 100);
        calculationTimeProgressBar.setPreferredSize(timingProgressBarSize);
        calculationTimeProgressBar.setMinimumSize(timingProgressBarSize);
        calculationTimeProgressBar.setStringPainted(true);
        calculationTimeProgressBar.setString("0 ms");
        timingGbc.gridx = 1;
        timingGbc.gridy = 4;
        timingInfoPanel.add(calculationTimeProgressBar, timingGbc);

        // --- Separator ---
        JSeparator separator2 = new JSeparator(SwingConstants.HORIZONTAL);
        timingGbc.gridx = 0;
        timingGbc.gridy = 5;
        timingGbc.gridwidth = 3; // Span across all columns
        timingGbc.fill = GridBagConstraints.HORIZONTAL;
        timingGbc.insets = new Insets(2, 5, 2, 5); // Match other components' padding
        timingInfoPanel.add(separator2, timingGbc);
        timingGbc.gridwidth = 1;
        timingGbc.insets = new Insets(2, 5, 2, 5);

        // --- Upload Speed ---
        uploadSpeedLabel = new JLabel("Upload Speed:", SwingConstants.RIGHT);
        uploadSpeedLabel.setForeground(new Color(128, 0, 0));
        timingGbc.gridx = 0;
        timingGbc.gridy = 6;
        timingInfoPanel.add(uploadSpeedLabel, timingGbc);
        addInfoTooltip(uploadSpeedLabel,
                "The current data upload speed to other peers in the network. This reflects how much blockchain data your node is sharing.");

        uploadSpeedProgressBar = new JProgressBar(0, MAX_SPEED_BPS);
        uploadSpeedProgressBar.setPreferredSize(timingProgressBarSize);
        uploadSpeedProgressBar.setMinimumSize(timingProgressBarSize);
        uploadSpeedProgressBar.setStringPainted(true);
        uploadSpeedProgressBar.setString("0 B/s");
        timingGbc.gridx = 1;
        timingGbc.gridy = 6;
        timingInfoPanel.add(uploadSpeedProgressBar, timingGbc);

        // --- Download Speed ---
        downloadSpeedLabel = new JLabel("Download Speed:", SwingConstants.RIGHT);
        downloadSpeedLabel.setForeground(new Color(0, 100, 0));
        timingGbc.gridx = 0;
        timingGbc.gridy = 7;
        timingInfoPanel.add(downloadSpeedLabel, timingGbc);
        addInfoTooltip(downloadSpeedLabel,
                "The current data download speed from other peers in the network. This indicates how quickly your node is receiving blockchain data.");

        downloadSpeedProgressBar = new JProgressBar(0, MAX_SPEED_BPS);
        downloadSpeedProgressBar.setPreferredSize(new Dimension(150, 20));
        downloadSpeedProgressBar.setStringPainted(true);
        downloadSpeedProgressBar.setString("0 B/s");
        timingGbc.gridx = 1;
        timingGbc.gridy = 7;
        timingInfoPanel.add(downloadSpeedProgressBar, timingGbc);

        // --- Combined Volume ---
        JPanel combinedVolumePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        combinedVolumePanel.setOpaque(false);
        JLabel volumeTitleLabel = new JLabel("Volume:", SwingConstants.RIGHT);
        addInfoTooltip(volumeTitleLabel,
                "The total amount of data uploaded to and downloaded from the network during this session. The format is Uploaded / Downloaded.");
        combinedVolumePanel.add(volumeTitleLabel);

        metricsUploadVolumeLabel = new JLabel();
        metricsUploadVolumeLabel.setForeground(new Color(233, 150, 122)); // Upload color
        metricsDownloadVolumeLabel = new JLabel();
        metricsDownloadVolumeLabel.setForeground(new Color(50, 205, 50)); // Download color
        combinedVolumePanel.add(metricsUploadVolumeLabel);
        combinedVolumePanel.add(new JLabel("/"));
        combinedVolumePanel.add(metricsDownloadVolumeLabel);
        timingGbc.gridx = 0;
        timingGbc.gridy = 8;
        timingGbc.gridwidth = 3;
        timingGbc.anchor = GridBagConstraints.CENTER;
        timingInfoPanel.add(combinedVolumePanel, timingGbc);
        timingGbc.gridwidth = 1; // Reset
        timingGbc.anchor = GridBagConstraints.LINE_END; // Reset anchor
        // === End Timing Info Panel ===

        // Add timingInfoPanel to the left of the timingMetricsPanel
        metricsGbc.gridx = 0;
        metricsGbc.gridy = 0;
        metricsGbc.weightx = 0;
        metricsGbc.fill = GridBagConstraints.NONE;
        metricsGbc.anchor = GridBagConstraints.NORTHWEST;
        timingMetricsPanel.add(timingInfoPanel, metricsGbc);

        // Create and add chart panel to the right
        JPanel timingChartContainer = new JPanel();
        timingChartContainer.setLayout(new BoxLayout(timingChartContainer, BoxLayout.Y_AXIS));
        timingChartPanel = createTimingChartPanel();

        addDualChartToggleListener(txPerBlockLabel, performanceChartPanel, 1, 0, timingChartPanel, 1, 0);
        addToggleListener(pushTimeLabel, timingChartPanel, 0, 0);
        addToggleListener(dbTimeLabel, timingChartPanel, 0, 1);
        addToggleListener(calculationTimeLabel, timingChartPanel, 0, 2);
        addToggleListener(atTimeLabel, timingChartPanel, 0, 3);
        addToggleListener(uploadSpeedLabel, netSpeedChartPanel, 0, 0);
        addToggleListener(downloadSpeedLabel, netSpeedChartPanel, 0, 1);
        addToggleListener(metricsUploadVolumeLabel, netSpeedChartPanel, 1, 0);
        addToggleListener(metricsDownloadVolumeLabel, netSpeedChartPanel, 1, 1);
        timingChartContainer.add(timingChartPanel);

        JPanel timingChartContainerPanel = new JPanel(new BorderLayout(10, 0));
        timingChartContainerPanel.add(timingChartContainer, BorderLayout.CENTER);
        // chartContainer.add(sliderWithLabelPanel, BorderLayout.EAST);

        metricsGbc.gridx = 1;
        metricsGbc.gridy = 0;
        metricsGbc.weightx = 0;
        metricsGbc.weighty = 0;
        metricsGbc.fill = GridBagConstraints.NONE;
        metricsGbc.anchor = GridBagConstraints.NORTHWEST;
        timingMetricsPanel.add(timingChartContainerPanel, metricsGbc);

        // Create and add net speed chart panel to the right
        JPanel netSpeedChartContainer = new JPanel();
        netSpeedChartContainer.setLayout(new BoxLayout(netSpeedChartContainer, BoxLayout.Y_AXIS));
        netSpeedChartContainer.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        netSpeedChartContainer.add(netSpeedChartPanel);

        metricsGbc.gridx = 2;
        metricsGbc.gridy = 0;
        metricsGbc.fill = GridBagConstraints.NONE;
        metricsGbc.anchor = GridBagConstraints.NORTHWEST;
        timingMetricsPanel.add(netSpeedChartContainer, metricsGbc);

        // Add the timing metrics group to the main metrics panel
        mainMetricsGbc.gridx = 1;
        metricsPanel.add(timingMetricsPanel, mainMetricsGbc);

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
        // === Hozzáadás a fő content panelhez ===

        // Font monoFont = new Font("Monospaced", Font.PLAIN, 12);

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.X_AXIS));

        // --- Peers ---
        JPanel peersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        peersPanel.add(new JLabel("Peers:"));
        peersLabel = new JLabel("000 / 000");
        // peersLabel.setFont(monoFont);
        /*
         * Dimension peersDim = new Dimension(
         * peersLabel.getFontMetrics(monoFont).stringWidth("000 / 000"),
         * peersLabel.getPreferredSize().height);
         * peersLabel.setPreferredSize(peersDim);
         */
        peersPanel.add(peersLabel);

        // --- Speed ---
        JPanel speedPanel = new JPanel(new BorderLayout());
        speedPanel.add(new JLabel("Speed:"));

        // --- Volume ---
        JPanel volumePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        // volumePanel.add(new JLabel("Volume:"));

        // Upload
        uploadVolumeLabel = new JLabel("▲ 000 MB");

        // Download
        downloadVolumeLabel = new JLabel("▼ 000 MB");

        // uploadVolumeLabel.setFont(monoFont);
        /*
         * Dimension volumeDim = new Dimension(
         * uploadVolumeLabel.getFontMetrics(monoFont).stringWidth("↑ 000 PB"),
         * uploadVolumeLabel.getPreferredSize().height);
         * uploadVolumeLabel.setPreferredSize(volumeDim);
         */
        volumePanel.add(uploadVolumeLabel);
        volumePanel.add(new JLabel(" / "));
        volumePanel.add(downloadVolumeLabel);

        // --- Main infoPanel sorrend ---
        // infoPanel.removeAll();

        infoPanel.add(peersPanel);
        infoPanel.add(Box.createHorizontalStrut(5));
        infoPanel.add(new JSeparator(SwingConstants.VERTICAL));
        infoPanel.add(Box.createHorizontalStrut(5));
        infoPanel.add(volumePanel);
        infoPanel.add(Box.createHorizontalStrut(5));
        infoPanel.add(new JSeparator(SwingConstants.VERTICAL));
        infoPanel.add(Box.createHorizontalStrut(5));
        infoPanel.add(syncProgressBar);

        bottomPanel.add(infoLable, BorderLayout.CENTER);
        bottomPanel.add(infoPanel, BorderLayout.LINE_END);

        pack();
        setSize(metricsPanel.getPreferredSize().width, 800);
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

    private void addToggleListener(JLabel label, ChartPanel chartPanel, int rendererIndex, int seriesIndex) {
        label.putClientProperty("visible", true);
        Font originalFont = label.getFont();
        Map<TextAttribute, Object> attributes = new HashMap<>(originalFont.getAttributes());
        attributes.put(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
        Font strikethroughFont = originalFont.deriveFont(attributes);

        label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (SwingUtilities.isLeftMouseButton(evt)) {
                    boolean isVisible = (boolean) label.getClientProperty("visible");
                    isVisible = !isVisible;
                    label.putClientProperty("visible", isVisible);

                    chartPanel.getChart().getXYPlot().getRenderer(rendererIndex).setSeriesVisible(seriesIndex,
                            isVisible);

                    label.setFont(isVisible ? originalFont : strikethroughFont);
                }
            }
        });
    }

    private void addDualChartToggleListener(JLabel label,
            ChartPanel chartPanel1, int rendererIndex1, int seriesIndex1,
            ChartPanel chartPanel2, int rendererIndex2, int seriesIndex2) {
        label.putClientProperty("visible", true);
        Font originalFont = label.getFont();
        Map<TextAttribute, Object> attributes = new HashMap<>(originalFont.getAttributes());
        attributes.put(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
        Font strikethroughFont = originalFont.deriveFont(attributes);

        label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (SwingUtilities.isLeftMouseButton(evt)) {
                    boolean isVisible = (boolean) label.getClientProperty("visible");
                    isVisible = !isVisible;
                    label.putClientProperty("visible", isVisible);

                    chartPanel1.getChart().getXYPlot().getRenderer(rendererIndex1).setSeriesVisible(seriesIndex1,
                            isVisible);
                    chartPanel2.getChart().getXYPlot().getRenderer(rendererIndex2).setSeriesVisible(seriesIndex2,
                            isVisible);

                    label.setFont(isVisible ? originalFont : strikethroughFont);
                }
            }
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
        Signum.getBlockchainProcessor().addQueueStatusListener((unverifiedSize, verifiedSize, totalSize) -> {
            SwingUtilities.invokeLater(() -> updateQueueStatus(unverifiedSize, verifiedSize, totalSize));
        });

        Signum.getBlockchainProcessor().addPeerCountListener((newCount, newConnectedCount) -> {
            SwingUtilities.invokeLater(() -> updatePeerCount(newCount, newConnectedCount));
        });

        Signum.getBlockchainProcessor().addNetVolumeListener((uploadedVolume, downloadedVolume) -> {
            SwingUtilities.invokeLater(() -> updateNetVolume(uploadedVolume, downloadedVolume));
        });

        Signum.getBlockchainProcessor().addListener(this::onBlockPushed, BlockchainProcessor.Event.BLOCK_PUSHED);

        Signum.getBlockchainProcessor()
                .addPerformanceListener((totalTimeMs, dbTimeMs, atTimeMs, txCount, blockHeight) -> {
                    SwingUtilities.invokeLater(
                            () -> updateTimingChart(totalTimeMs, dbTimeMs, atTimeMs, txCount, blockHeight));
                });

    }

    private void onBlockPushed(Block block) {
        // All UI updates should be on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            updateLatestBlock(block);
            updatePerformanceChart(block);
        });
    }

    public void startSignumWithGUI() {
        try {
            // signum.init();
            Signum.main(args);

            // Now that properties are loaded, set the correct values for the GUI
            oclUnverifiedQueueThreshold = Signum.getPropertyService().getInt(Props.GPU_UNVERIFIED_QUEUE);
            showPopOff = Signum.getPropertyService().getBoolean(Props.EXPERIMENTAL);
            showMetrics = Signum.getPropertyService().getBoolean(Props.EXPERIMENTAL);

            try {
                SwingUtilities.invokeLater(() -> {
                    showTrayIcon();
                    // Sync checkbox states with loaded properties
                    showPopOffCheckbox.setSelected(showPopOff);
                    showMetricsCheckbox.setSelected(showMetrics);
                    // Sync panel visibility with loaded properties
                    metricsPanel.setVisible(showMetrics);
                });

                updateTitle();

                initListeners();

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

        this.uploadedVolume = uploadedVolume;
        this.downloadedVolume = downloadedVolume;

        uploadVolumeLabel.setText("▲ " + formatDataSize(uploadedVolume));
        downloadVolumeLabel.setText("▼ " + formatDataSize(downloadedVolume));

        if (metricsUploadVolumeLabel != null) {
            // metricsUploadVolumeLabel.setText(formatDataSize(uploadedVolume));
            metricsUploadVolumeLabel.setText("▲ " + formatDataSize(uploadedVolume));
        }
        if (metricsDownloadVolumeLabel != null) {
            // metricsDownloadVolumeLabel.setText(formatDataSize(downloadedVolume));
            metricsDownloadVolumeLabel.setText("▼ " + formatDataSize(downloadedVolume));
        }

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

        int currentWindowSize = Math.min(uploadSpeedHistory.size(), 100);
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

        uploadSpeedProgressBar.setValue((int) avgUploadSpeed);
        uploadSpeedProgressBar.setString(formatDataRate(avgUploadSpeed));
        downloadSpeedProgressBar.setValue((int) avgDownloadSpeed);
        downloadSpeedProgressBar
                .setString(formatDataRate(avgDownloadSpeed));

        lastNetVolumeUpdateTime = currentTime;
        lastUploadedVolume = uploadedVolume;
        lastDownloadedVolume = downloadedVolume;

        // Update chart series
        uploadSpeedSeries.add(currentTime, avgUploadSpeed);
        downloadSpeedSeries.add(currentTime, avgDownloadSpeed);
        uploadVolumeSeries.add(currentTime, uploadedVolume);
        downloadVolumeSeries.add(currentTime, downloadedVolume);

        // Keep history size for chart
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

    private void updateNetVolume(long uploadedVolume, long downloadedVolume) {

        this.uploadedVolume = uploadedVolume;
        this.downloadedVolume = downloadedVolume;

    }

    private void updatePeerCount(int count, int newConnectedCount) {
        peersLabel.setText(newConnectedCount + " / " + count);
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

    private void updateTimingChart(long totalTimeMs, long dbTimeMs, long atTimeMs, int txCount, int blockHeight) {

        if (!showMetrics) {
            return;
        }

        atTimes.add(atTimeMs);

        pushTimes.add(totalTimeMs);
        dbTimes.add(dbTimeMs);

        while (pushTimes.size() > CHART_HISTORY_SIZE) {
            pushTimes.removeFirst();
        }
        while (dbTimes.size() > CHART_HISTORY_SIZE) {
            dbTimes.removeFirst();
        }
        while (atTimes.size() > CHART_HISTORY_SIZE) {
            atTimes.removeFirst();
        }

        int currentWindowSize = Math.min(pushTimes.size(), movingAverageWindow);
        if (currentWindowSize < 1) {
            return;
        }

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

        long calculationTimeMs = displayPushTime - displayDbTime - displayAtTime;
        pushTimeProgressBar.setValue((int) displayPushTime);
        pushTimeProgressBar.setString(String.format("%d ms", displayPushTime));
        dbTimeProgressBar.setValue((int) displayDbTime);
        dbTimeProgressBar.setString(String.format("%d ms", displayDbTime));
        atTimeProgressBar.setValue((int) displayAtTime);
        atTimeProgressBar.setString(String.format("%d ms", displayAtTime));
        calculationTimeProgressBar.setValue(Math.max(0, (int) calculationTimeMs));
        calculationTimeProgressBar.setString(String.format("%d ms", Math.max(0, calculationTimeMs)));

        // Update timing chart series
        pushTimePerBlockSeries.add(blockHeight, displayPushTime);
        dbTimePerBlockSeries.add(blockHeight, displayDbTime);
        calculationTimePerBlockSeries.add(blockHeight, Math.max(0, calculationTimeMs));
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

        XYSeriesCollection volumeDataset = new XYSeriesCollection();
        volumeDataset.addSeries(uploadVolumeSeries);
        volumeDataset.addSeries(downloadVolumeSeries);

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
        plot.setRangeAxis(1, volumeAxis);
        plot.setDataset(1, volumeDataset);
        plot.mapDatasetToRangeAxis(1, 1);

        // Renderer for volume bars
        XYBarRenderer volumeRenderer = new XYBarRenderer(0);
        volumeRenderer.setBarPainter(new StandardXYBarPainter());
        volumeRenderer.setShadowVisible(false);
        volumeRenderer.setSeriesPaint(0, new Color(233, 150, 122, 128)); // Upload - Red, semi-transparent
        volumeRenderer.setSeriesPaint(1, new Color(50, 205, 50, 128)); // Download - Green, semi-transparent
        plot.setRenderer(1, volumeRenderer);

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

    /**
     * Updates the performance chart with the latest block data.
     * This method calculates the blocks per second, transactions per second,
     * and transactions per block, and updates the respective series and progress
     * bars.
     *
     * @param block The latest block to update the performance chart with.
     */
    private void updatePerformanceChart(Block block) {

        if (!showMetrics) {
            return;
        }

        blockTimestamps.add(System.currentTimeMillis());
        transactionCounts.add(block.getTransactions().size());

        // Keep the history to a fixed size
        while (blockTimestamps.size() > CHART_HISTORY_SIZE) {
            blockTimestamps.removeFirst();
            transactionCounts.removeFirst();
            if (!blocksPerSecondSeries.isEmpty()) {
                blocksPerSecondSeries.remove(0);
            }
            if (!transactionsPerSecondSeries.isEmpty()) {
                transactionsPerSecondSeries.remove(0);
            }
            if (!transactionsPerBlockSeries.isEmpty()) {
                transactionsPerBlockSeries.remove(0);
            }
        }

        // Calculate moving average for blocks/second
        long timeSpanMs = blockTimestamps.getLast()
                - blockTimestamps.get(blockTimestamps.size() - Math.min(blockTimestamps.size(), movingAverageWindow));
        double blocksPerSecond = (timeSpanMs > 0)
                ? (double) Math.min(blockTimestamps.size(), movingAverageWindow) * 1000.0 / timeSpanMs
                : 0;

        // Calculate moving average for transaction count
        double avgTransactions = transactionCounts.stream()
                .skip(Math.max(0, transactionCounts.size() - Math.min(transactionCounts.size(), movingAverageWindow)))
                .mapToInt(Integer::intValue)
                .average().orElse(0.0);

        // Update chart series and progress bar
        blocksPerSecondSeries.add(block.getHeight(), blocksPerSecond);
        transactionsPerBlockSeries.add(block.getHeight(), avgTransactions);
        blocksPerSecondProgressBar.setValue((int) (blocksPerSecond));
        blocksPerSecondProgressBar.setString(String.format("%.2f", blocksPerSecond));

        double transactionsPerSecond = avgTransactions * blocksPerSecond;
        transactionsPerSecondSeries.add(block.getHeight(), transactionsPerSecond);
        transactionsPerSecondProgressBar.setValue((int) transactionsPerSecond);
        transactionsPerSecondProgressBar.setString(String.format("%.2f", transactionsPerSecond));

        transactionsPerBlockProgressBar.setValue((int) avgTransactions);
        transactionsPerBlockProgressBar.setString(String.format("%.2f", avgTransactions));
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
