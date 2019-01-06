package brs;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Permission;

public class BurstGUI extends Application {
    private static String[] args;
    private static boolean userClosed = false;
    private static final Logger LOGGER = LoggerFactory.getLogger(BurstGUI.class);
    private static Stage stage;
    private static TrayIcon trayIcon = null;

    public static void main(String[] args) {
        BurstGUI.args = args;
        addToClasspath("./conf");
        System.setSecurityManager(new BurstGUISecurityManager());
        Platform.setImplicitExit(false);
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Burst Reference Software version " + Burst.VERSION);
        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        sendJavaOutputToTextArea(textArea);
        primaryStage.setScene(new Scene(textArea, 800, 450));
        stage = primaryStage;
        showTrayIcon();
        new Thread(BurstGUI::runBrs).start();
    }

    /**
     * Adds a path to add to the classpath
     * @param s The path to add to the classpath
     */
    public static void addToClasspath(String s) {
        try {
            File f = new File(s);
            URI u = f.toURI();
            URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Class<URLClassLoader> urlClass = URLClassLoader.class;
            Method method = urlClass.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(urlClassLoader, u.toURL());
        } catch (Exception e) {
            LOGGER.error("Could not add path \"" + s + "\" to classpath", e);
        }
    }

    private static void shutdown() {
        userClosed = true;
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        System.exit(0); // BRS shutdown handled by exit hook
    }

    private static void showTrayIcon() {
        if (trayIcon == null) { // Don't start running in tray twice
            trayIcon = createTrayIcon();
            if (trayIcon != null) {
                stage.setOnCloseRequest(event -> hideWindow());
            } else {
                stage.show();
                stage.setOnCloseRequest(event -> shutdown());
            }
        }
    }

    /**
     * @return A tray icon if successful or null otherwise
     */
    private static TrayIcon createTrayIcon() {
        try {
            SystemTray systemTray = SystemTray.getSystemTray();
            PopupMenu popupMenu = new PopupMenu();

            MenuItem openWebUiButton = new MenuItem("Open Web GUI");
            MenuItem showItem = new MenuItem("Show BRS output");
            MenuItem shutdownItem = new MenuItem("Shutdown BRS");

            openWebUiButton.addActionListener(e -> openWebUi());
            showItem.addActionListener(e -> showWindow());
            shutdownItem.addActionListener(e -> shutdown());

            popupMenu.add(openWebUiButton);
            popupMenu.add(showItem);
            popupMenu.add(shutdownItem);

            TrayIcon newTrayIcon = new TrayIcon(ImageIO.read(BurstGUI.class.getResourceAsStream("/images/burst_overlay_logo.png")), "Burst Reference Software", popupMenu);
            newTrayIcon.setImage(newTrayIcon.getImage().getScaledInstance(newTrayIcon.getSize().width, -1, Image.SCALE_SMOOTH));
            newTrayIcon.addActionListener(e -> openWebUi());
            systemTray.add(newTrayIcon);
            return newTrayIcon;
        } catch (Exception e) {
            LOGGER.error("Could not create tray icon", e);
            return null;
        }
    }

    private static void showWindow() {
        Platform.runLater(stage::show);
    }

    private static void hideWindow() {
        Platform.runLater(stage::hide);
    }

    private static void openWebUi() {
        try {
            Desktop.getDesktop().browse(new URI("http://localhost:8125"));
        } catch (Exception e) {
            showMessage("Error opening web UI. Please open your browser and navigate to \"http://localhost:8125\" or \"https://localhost:8125\" if the node is configured to use SSL");
        }
    }

    private static void runBrs() {
        try {
            Burst.main(args);
        } catch (Throwable t1) {
            if (!(t1 instanceof SecurityException)) {
                LOGGER.error("BurstGUI caught exception starting BRS", t1);
                showMessage("BurstGUI caught exception starting BRS");
                onBrsStopped();
            }
        }
    }

    private static void onBrsStopped() {
        stage.setTitle(stage.getTitle() + " (STOPPED)");
        trayIcon.setToolTip(trayIcon.getToolTip() + " (STOPPED)");
    }

    private static void sendJavaOutputToTextArea(TextArea textArea) {
        System.setOut(new PrintStream(new TextAreaOutputStream(textArea, System.out)));
        System.setErr(new PrintStream(new TextAreaOutputStream(textArea, System.err)));
    }

    private static void showMessage(String message) {
        Platform.runLater(() -> {
            System.out.println("Showing message: " + message);
            Dialog dialog = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
            dialog.setGraphic(null);
            dialog.setHeaderText(null);
            dialog.setTitle("BRS Message");
            dialog.show();
        });
    }

    private static class TextAreaOutputStream extends OutputStream {
        private final TextArea textArea;
        private final PrintStream actualOutput;

        private TextAreaOutputStream(TextArea textArea, PrintStream actualOutput) {
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

        private void writeString(String string) {
            actualOutput.print(string);
            if (textArea != null) {
                Platform.runLater(() -> textArea.appendText(string));
            }
        }
    }

    private static class BurstGUISecurityManager extends SecurityManager {

        @Override
        public void checkExit(int status) {
            if (!userClosed) {
                LOGGER.error("BRS Quit unexpectedly! Exit code " + String.valueOf(status));
                showMessage("BRS Quit unexpectedly! Exit code " + String.valueOf(status));
                onBrsStopped();
                throw new SecurityException();
            }
        }

        @Override
        public void checkPermission(Permission perm) {
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
        }

        @Override
        public void checkCreateClassLoader() {
        }

        @Override
        public void checkAccess(Thread t) {
        }

        @Override
        public void checkAccess(ThreadGroup g) {
        }

        @Override
        public void checkExec(String cmd) {
        }

        @Override
        public void checkLink(String lib) {
        }

        @Override
        public void checkRead(FileDescriptor fd) {
        }

        @Override
        public void checkRead(String file) {
        }

        @Override
        public void checkRead(String file, Object context) {
        }

        @Override
        public void checkWrite(FileDescriptor fd) {
        }

        @Override
        public void checkWrite(String file) {
        }

        @Override
        public void checkDelete(String file) {
        }

        @Override
        public void checkConnect(String host, int port) {
        }

        @Override
        public void checkConnect(String host, int port, Object context) {
        }

        @Override
        public void checkListen(int port) {
        }

        @Override
        public void checkAccept(String host, int port) {
        }

        @Override
        public void checkMulticast(InetAddress maddr) {
        }

        @Override
        public void checkPropertiesAccess() {
        }

        @Override
        public void checkPropertyAccess(String key) {
        }

        @Override
        public void checkPrintJobAccess() {
        }

        @Override
        public void checkPackageAccess(String pkg) {
        }

        @Override
        public void checkPackageDefinition(String pkg) {
        }

        @Override
        public void checkSetFactory() {
        }

        @Override
        public void checkSecurityAccess(String target) {
        }
    }
}
