package brs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.URI;
import java.security.Permission;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brs.fluxcapacitor.FluxValues;
import brs.props.PropertyService;
import brs.props.Props;
import brs.util.Convert;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

@SuppressWarnings("serial")
public class SignumGUI extends JFrame {
    private static final String FAILED_TO_START_MESSAGE = "Signum caught exception while starting";
    private static final String UNEXPECTED_EXIT_MESSAGE = "Signum Quit unexpectedly! Exit code ";

	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss yyyy-MM-dd");

    private static final int OUTPUT_MAX_LINES = 500;

    private static final Logger LOGGER = LoggerFactory.getLogger(SignumGUI.class);
    private static String []args;

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

    public static void main(String []args) {
        new SignumGUI("Signum Node", Props.ICON_LOCATION.getDefaultValue(), Signum.VERSION.toString(), args);
    }

    public SignumGUI(String programName, String iconLocation, String version, String []args) {
        try {
            System.setSecurityManager(new SignaGUISecurityManager());
        } catch (UnsupportedOperationException e) {
            // Java 17+ / 21+: Setting a SecurityManager is not supported anymore
            System.err.println("SecurityManager not supported, skipping setup");
        }
        SignumGUI.args = args;
        this.programName = programName;
        this.version = version;
        setTitle(programName + " " + version);
        this.iconLocation = iconLocation;

        Class<?> lafc = null;
        try {
          lafc = Class.forName("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
        }
        catch (Exception e) {}
        if(lafc==null) {
          try {
            lafc =  Class.forName("javax.swing.plaf.nimbus.NimbusLookAndFeel");
          }
          catch (Exception e) {}
        }
        if(lafc!=null) {
          try {
            UIManager.put( "control", new Color( 128, 128, 128) );
            UIManager.put( "info", new Color(128,128,128) );
            UIManager.put( "nimbusBase", new Color( 18, 30, 49) );
            UIManager.put( "nimbusAlertYellow", new Color( 248, 187, 0) );
            UIManager.put( "nimbusDisabledText", new Color( 128, 128, 128) );
            UIManager.put( "nimbusFocus", new Color(115,164,209) );
            UIManager.put( "nimbusGreen", new Color(176,179,50) );
            UIManager.put( "nimbusInfoBlue", new Color( 66, 139, 221) );
            UIManager.put( "nimbusLightBackground", new Color( 18, 30, 49) );
            UIManager.put( "nimbusOrange", new Color(191,98,4) );
            UIManager.put( "nimbusRed", new Color(169,46,34) );
            UIManager.put( "nimbusSelectedText", new Color( 255, 255, 255) );
            UIManager.put( "nimbusSelectionBackground", new Color( 104, 93, 156) );
            UIManager.put( "text", new Color( 230, 230, 230) );
            LookAndFeel laf = (LookAndFeel) lafc.getConstructor().newInstance();
            UIManager.setLookAndFeel(laf);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
		IconFontSwing.register(FontAwesome.getIconFont());

        JTextArea textArea = new JTextArea() {
        	@Override
        	public void append(String str) {
        		super.append(str);

                while(getText().split("\n", -1).length > OUTPUT_MAX_LINES) {
                    int fle = getText().indexOf('\n');
                    super.replaceRange("", 0, fle+1);
                }
                JScrollBar vertical = textScrollPane.getVerticalScrollBar();
                vertical.setValue( vertical.getMaximum() );
        	}
        };
        iconColor = textArea.getForeground();
        textArea.setEditable(false);
        sendJavaOutputToTextArea(textArea);
        textScrollPane = new JScrollPane(textArea);
        JPanel content = new JPanel(new BorderLayout());
        setContentPane(content);
        content.add(textScrollPane, BorderLayout.CENTER);

        toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        content.add(toolBar, BorderLayout.PAGE_START);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        content.add(bottomPanel, BorderLayout.PAGE_END);

        syncProgressBar = new JProgressBar(0, 100);
        syncProgressBar.setStringPainted(true);
        infoLable = new JLabel("Latest block info");

        bottomPanel.add(infoLable, BorderLayout.CENTER);
        bottomPanel.add(syncProgressBar, BorderLayout.LINE_END);

        pack();
        setSize(960, 600);
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
        		if(trayIcon == null) {
        			if (JOptionPane.showConfirmDialog(SignumGUI.this,
        					"This will stop the node. Are you sure?", "Exit and stop node",
        					JOptionPane.YES_NO_OPTION,
        					JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION){
        				shutdown();
        			}
        		}
        		else {
        			trayIcon.displayMessage("Signum GUI closed", "Note that Signum is still running", MessageType.INFO);
        			setVisible(false);
        		}
        	}
        });

        showWindow();
        new Timer(5000, e -> {
        	try {
        		Blockchain blockChain = Signum.getBlockchain();
        		if(blockChain != null) {
        			Block block = blockChain.getLastBlock();
        			if(block != null) {
        				Date blockDate = Convert.fromEpochTime(block.getTimestamp());
        				infoLable.setText("Latest block: " + block.getHeight() +
        						" Timestamp: " + DATE_FORMAT.format(blockDate));

        				Date now = new Date();
        			    long blockTime = Signum.getFluxCapacitor().getValue(FluxValues.BLOCK_TIME);

        				int missingBlocks = (int) ((now.getTime() - blockDate.getTime())/(blockTime * 1000));
        				int prog = block.getHeight()*100/(block.getHeight() + missingBlocks);
        				syncProgressBar.setValue(prog);
        				syncProgressBar.setString(prog + " %");
        			}
        		}
        	}
        	finally {
				// do nothing on error here
			}
        }).start();

        // Start BRS
        new Thread(this::runBrs).start();
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

        MenuItem openPheonixWalletItem = new MenuItem("Phoenix Wallet");
        MenuItem openClassicWalletItem = new MenuItem("Classic Wallet");
        MenuItem openApiItem = new MenuItem("API doc");
    	MenuItem showItem = new MenuItem("Show the node window");
    	MenuItem shutdownItem = new MenuItem("Shutdown the node");

    	JButton openPhoenixButton = new JButton(openPheonixWalletItem.getLabel(), IconFontSwing.buildIcon(FontAwesome.FIRE, 18, iconColor));
        JButton openClassicButton = new JButton(openClassicWalletItem.getLabel(), IconFontSwing.buildIcon(FontAwesome.WINDOW_RESTORE, 18, iconColor));
        JButton openApiButton = new JButton(openApiItem.getLabel(), IconFontSwing.buildIcon(FontAwesome.BOOK, 18, iconColor));
    	JButton editConfButton = new JButton("Edit conf file", IconFontSwing.buildIcon(FontAwesome.PENCIL, 18, iconColor));
        JButton popOff10Button = new JButton("Pop off 10 blocks", IconFontSwing.buildIcon(FontAwesome.STEP_BACKWARD, 18, iconColor));
        JButton popOff100Button = new JButton("Pop off 100 blocks", IconFontSwing.buildIcon(FontAwesome.BACKWARD, 18, iconColor));
        // TODO: find a way to actually store permanently the max block available to pop-off, otherwise we can break it
        // JButton popOffMaxButton = new JButton("Pop off max", IconFontSwing.buildIcon(FontAwesome.FAST_BACKWARD, 18, iconColor));

        openPhoenixButton.addActionListener(e -> openWebUi("/phoenix"));
        openClassicButton.addActionListener(e -> openWebUi("/classic"));
        openApiButton.addActionListener(e -> openWebUi("/api-doc"));
    	editConfButton.addActionListener(e -> editConf());
        popOff10Button.addActionListener(e -> popOff(10));
        popOff100Button.addActionListener(e -> popOff(100));
        //popOffMaxButton.addActionListener(e -> popOff(0));

        File phoenixIndex = new File("html/ui/phoenix/index.html");
        File classicIndex = new File("html/ui/classic/index.html");
        if(phoenixIndex.isFile() && phoenixIndex.exists()) {
          toolBar.add(openPhoenixButton);
        }
        if(classicIndex.isFile() && classicIndex.exists()) {
          toolBar.add(openClassicButton);
        }
    	toolBar.add(editConfButton);
    	toolBar.add(openApiButton);
    	if(Signum.getPropertyService().getBoolean(Props.EXPERIMENTAL)) {
          toolBar.add(popOff10Button);
          toolBar.add(popOff100Button);
//          toolBar.add(popOffMaxButton);
    	}

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
        if(!newIconLocation.equals(iconLocation)){
          // update the icon
          iconLocation = newIconLocation;
          setIconImage(ImageIO.read(getClass().getResourceAsStream(iconLocation)));
        }
    		TrayIcon newTrayIcon = new TrayIcon(Toolkit.getDefaultToolkit().createImage(SignumGUI.class.getResource(iconLocation)), "Signum Node", popupMenu);
    		newTrayIcon.setImage(newTrayIcon.getImage().getScaledInstance(newTrayIcon.getSize().width, -1, Image.SCALE_SMOOTH));
    		if(phoenixIndex.isFile() && phoenixIndex.exists()) {
    		  newTrayIcon.addActionListener(e -> openWebUi("/phoenix"));
    		}

    		SystemTray systemTray = SystemTray.getSystemTray();
    		systemTray.add(newTrayIcon);

    		newTrayIcon.displayMessage("Signum Running", "Signum is running on background, use this icon to interact with it.", MessageType.INFO);

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
    	int height = blocks > 0 ? Signum.getBlockchain().getLastBlock().getHeight() - blocks : Signum.getBlockchainProcessor().getMinRollbackHeight();
    	new Thread(() -> Signum.getBlockchainProcessor().popOffTo(height)).start();
    }

    private void editConf() {
    	File file = new File(Signum.CONF_FOLDER, Signum.PROPERTIES_NAME);
    	if(!file.exists()) {
        	file = new File(Signum.CONF_FOLDER, Signum.DEFAULT_PROPERTIES_NAME);
        	if(!file.exists()) {
        		file = new File(Signum.DEFAULT_PROPERTIES_NAME);
        	}
    	}

    	if(!file.exists()) {
    		JOptionPane.showMessageDialog(this, "Could not find conf file: " + Signum.DEFAULT_PROPERTIES_NAME, "File not found", JOptionPane.ERROR_MESSAGE);
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

    private void runBrs() {
        try {
            Signum.main(args);
            try {
            	SwingUtilities.invokeLater(() -> showTrayIcon());

                updateTitle();
                if (Signum.getBlockchain() == null)
                	onBrsStopped();
            } catch (Exception t) {
                LOGGER.error("Could not determine if running in testnet mode", t);
            }
        } catch (SecurityException ignored) {
        } catch (Exception t) {
            LOGGER.error(FAILED_TO_START_MESSAGE, t);
            showMessage(FAILED_TO_START_MESSAGE);
            onBrsStopped();
        }
    }

    private void updateTitle() {
        String networkName = Signum.getPropertyService().getString(Props.NETWORK_NAME);
        SwingUtilities.invokeLater(() -> setTitle(
            this.programName + " [" + networkName + "] " + this.version)
            );
        if(trayIcon != null)
        	trayIcon.setToolTip(trayIcon.getToolTip() + " " + networkName);
    }

    private void onBrsStopped() {
        SwingUtilities.invokeLater(() -> setTitle(getTitle() + " (STOPPED)"));
        if(trayIcon != null)
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
            writeString(new String(new byte[]{(byte)b}));
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

    private class SignaGUISecurityManager extends SecurityManager {

        @Override
        public void checkExit(int status) {
            if (!userClosed && status != 0) {
                LOGGER.error("{} {}", UNEXPECTED_EXIT_MESSAGE, status);
                SwingUtilities.invokeLater(() -> showWindow());
                showMessage(UNEXPECTED_EXIT_MESSAGE + String.valueOf(status));
                onBrsStopped();
                throw new SecurityException();
            }
        }

        @Override
        public void checkPermission(Permission perm) {
            // No need to check.
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            // No need to check.
        }

        @Override
        public void checkCreateClassLoader() {
            // No need to check.
        }

        @Override
        public void checkAccess(Thread t) {
            // No need to check.
        }

        @Override
        public void checkAccess(ThreadGroup g) {
            // No need to check.
        }

        @Override
        public void checkExec(String cmd) {
            // No need to check.
        }

        @Override
        public void checkLink(String lib) {
            // No need to check.
        }

        @Override
        public void checkRead(FileDescriptor fd) {
            // No need to check.
        }

        @Override
        public void checkRead(String file) {
            // No need to check.
        }

        @Override
        public void checkRead(String file, Object context) {
            // No need to check.
        }

        @Override
        public void checkWrite(FileDescriptor fd) {
            // No need to check.
        }

        @Override
        public void checkWrite(String file) {
            // No need to check.
        }

        @Override
        public void checkDelete(String file) {
            // No need to check.
        }

        @Override
        public void checkConnect(String host, int port) {
            // No need to check.
        }

        @Override
        public void checkConnect(String host, int port, Object context) {
            // No need to check.
        }

        @Override
        public void checkListen(int port) {
            // No need to check.
        }

        @Override
        public void checkAccept(String host, int port) {
            // No need to check.
        }

        @Override
        public void checkMulticast(InetAddress maddr) {
            // No need to check.
        }

        @Override
        public void checkPropertiesAccess() {
            // No need to check.
        }

        @Override
        public void checkPropertyAccess(String key) {
            // No need to check.
        }

        @Override
        public void checkPrintJobAccess() {
            // No need to check.
        }

        @Override
        public void checkPackageAccess(String pkg) {
            // No need to check.
        }

        @Override
        public void checkPackageDefinition(String pkg) {
            // No need to check.
        }

        @Override
        public void checkSetFactory() {
            // No need to check.
        }

        @Override
        public void checkSecurityAccess(String target) {
            // No need to check.
        }
    }
}
