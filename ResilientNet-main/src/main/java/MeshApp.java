import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.stage.Stage;
import java.util.*;
import java.util.concurrent.*;

// OSHI for real device battery
import oshi.SystemInfo;
import oshi.hardware.PowerSource;

public class MeshApp extends Application implements MeshEventListener {

    // ─── Fixed admin password ─────────────────────────────────────────────────
    private static final String ADMIN_PASSWORD = "mesh@admin123";

    // ─── Fonts ────────────────────────────────────────────────────────────────
    private static final String FONT_UI   = "'Segoe UI', 'Helvetica Neue', Arial, sans-serif";
    private static final String FONT_MONO = "Consolas, 'Courier New', monospace";

    // ─── Colours ──────────────────────────────────────────────────────────────
    private static final String C_BG        = "#0D1117";
    private static final String C_SURFACE   = "#161B22";
    private static final String C_SURFACE2  = "#21262D";
    private static final String C_BORDER    = "#30363D";
    private static final String C_ACCENT    = "#3FB950";
    private static final String C_ACCENT_DIM= "#238636";
    private static final String C_TEXT      = "#E6EDF3";
    private static final String C_TEXT_MUTED= "#8B949E";
    private static final String C_GOLD      = "#D29922";
    private static final String C_RED       = "#F85149";
    private static final String C_BLUE      = "#58A6FF";
    private static final String C_ORANGE    = "#E3B341";

    // ─── State ────────────────────────────────────────────────────────────────
    private Stage    primaryStage;
    private MeshNode node;
    private boolean  isAdmin;

    // ─── Device battery polling ───────────────────────────────────────────────
    private ScheduledExecutorService batteryPoller;
    private boolean deviceBatteryAvailable = false;

    // ─── Main UI refs ─────────────────────────────────────────────────────────
    private VBox      chatFeed;
    private ScrollPane chatScroll;
    private TextField  inputField;
    private Label      nodeIdLabel, roleLabel, batteryLabel, signalLabel, bufferLabel;
    private Label      deviceBattLabel;    // shows real device battery
    private Circle     signalDot;
    private ProgressBar batteryBar;        // mesh node battery (manual)
    private ProgressBar deviceBattBar;     // real device battery
    private TextField  batteryField;
    private Button     toggleSignalBtn, sendBtn;
    private CheckBox   targetedCheckbox;
    private TextField  targetField;
    private VBox       ackFeed, systemFeed;
    private ScrollPane ackScroll, systemScroll;

    // ─── Priority selector ────────────────────────────────────────────────────
    private ToggleGroup  priorityGroup;
    private ToggleButton btnLow, btnNormal, btnHigh, btnCritical;

    // ─── Admin dashboard ──────────────────────────────────────────────────────
    private VBox  dashboardTable;
    private Label dashOnlineCount, dashOfflineCount, dashTotalCount;

    // ─── Left panel ───────────────────────────────────────────────────────────
    private ListView<String> peerList;

    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        showLoginScreen();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOGIN SCREEN  (stable, no animation on hex grid)
    // ══════════════════════════════════════════════════════════════════════════
    private void showLoginScreen() {
        primaryStage.setTitle("ResilientNet");

        // Root: dark background, card centered
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + C_BG + ";");

        // ── Card ─────────────────────────────────────────────────────────────
        VBox card = new VBox(0);
        card.setMinWidth(420);
        card.setMaxWidth(420);
        card.setPrefWidth(420);
        card.setAlignment(Pos.TOP_CENTER);
        card.setStyle(
            "-fx-background-color: " + C_SURFACE + ";" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-width: 1;" +
            "-fx-background-radius: 10;" +
            "-fx-border-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 28, 0.5, 0, 6);"
        );

        // ── Header ───────────────────────────────────────────────────────────
        VBox header = new VBox(8);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(40, 40, 28, 40));
        header.setStyle(
            "-fx-background-color: " + C_SURFACE2 + ";" +
            "-fx-background-radius: 10 10 0 0;"
        );

        Label logo = new Label("⬡  RESILIENTNET");
        logo.setStyle(
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 26px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + C_ACCENT + ";" +
            "-fx-effect: dropshadow(gaussian, " + C_ACCENT + ", 10, 0.4, 0, 0);"
        );
        Label tagline = new Label("Mesh Communication Node  ·  Port 9876");
        tagline.setStyle(
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 12px;" +
            "-fx-text-fill: " + C_TEXT_MUTED + ";"
        );
        header.getChildren().addAll(logo, tagline);

        // ── Thin accent line ──────────────────────────────────────────────────
        Rectangle accentBar = new Rectangle(420, 2);
        accentBar.setFill(Color.web(C_ACCENT_DIM));

        // ── Body ─────────────────────────────────────────────────────────────
        VBox body = new VBox(20);
        body.setPadding(new Insets(32, 40, 40, 40));

        // Role selector label
        Label roleSelLabel = new Label("SELECT ROLE");
        roleSelLabel.setStyle(
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 10px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + C_TEXT_MUTED + ";" +
            "-fx-letter-spacing: 1.5;"
        );

        // Role toggle buttons
        ToggleGroup roleGroup = new ToggleGroup();
        ToggleButton userBtn  = makeLoginRoleBtn("👤  Join as User",   roleGroup, true);
        ToggleButton adminBtn = makeLoginRoleBtn("🔑  Login as Admin", roleGroup, false);

        HBox roleRow = new HBox(10, userBtn, adminBtn);
        roleRow.setFillHeight(true);
        HBox.setHgrow(userBtn,  Priority.ALWAYS);
        HBox.setHgrow(adminBtn, Priority.ALWAYS);

        // Password section
        Label passLbl = new Label("ADMIN PASSWORD");
        passLbl.setStyle(
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 10px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + C_TEXT_MUTED + ";"
        );

        PasswordField passField = new PasswordField();
        passField.setPromptText("Enter admin password");
        passField.setPrefHeight(42);
        passField.setStyle(
            "-fx-background-color: " + C_BG + ";" +
            "-fx-text-fill: " + C_TEXT + ";" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 13px;" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-width: 1;" +
            "-fx-background-radius: 6;" +
            "-fx-border-radius: 6;" +
            "-fx-padding: 0 14 0 14;" +
            "-fx-prompt-text-fill: " + C_TEXT_MUTED + ";"
        );

        Label errorLbl = new Label("");
        errorLbl.setMinHeight(20);
        errorLbl.setStyle(
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 12px;" +
            "-fx-text-fill: " + C_RED + ";"
        );

        VBox passPane = new VBox(8, passLbl, passField, errorLbl);
        passPane.setVisible(false);
        passPane.setManaged(false);

        adminBtn.selectedProperty().addListener((obs, o, sel) -> {
            passPane.setVisible(sel);
            passPane.setManaged(sel);
            errorLbl.setText("");
            if (sel) Platform.runLater(passField::requestFocus);
        });

        // Connect button
        Button connectBtn = new Button("Connect to Mesh");
        connectBtn.setMaxWidth(Double.MAX_VALUE);
        connectBtn.setPrefHeight(44);
        String connStyleNormal =
            "-fx-background-color: " + C_ACCENT_DIM + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;";
        String connStyleHover =
            "-fx-background-color: " + C_ACCENT + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;";
        connectBtn.setStyle(connStyleNormal);
        connectBtn.setOnMouseEntered(e -> connectBtn.setStyle(connStyleHover));
        connectBtn.setOnMouseExited(e  -> connectBtn.setStyle(connStyleNormal));

        // Footer version label
        Label footer = new Label("ResilientNet v1.0  ·  UDP Mesh Network");
        footer.setStyle(
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 10px;" +
            "-fx-text-fill: " + C_SURFACE2 + ";"  // very subtle, blends with bg
        );

        body.getChildren().addAll(roleSelLabel, roleRow, passPane, connectBtn, footer);
        card.getChildren().addAll(header, accentBar, body);

        // Center the card perfectly
        BorderPane.setAlignment(card, Pos.CENTER);
        root.setCenter(card);
        BorderPane.setMargin(card, new Insets(0));

        // ── Login logic ───────────────────────────────────────────────────────
        Runnable doLogin = () -> {
            if (adminBtn.isSelected() && !ADMIN_PASSWORD.equals(passField.getText())) {
                errorLbl.setText("✗  Incorrect password. Access denied.");
                passField.clear();
                // Subtle shake — only the error label, not the whole card
                TranslateTransition tt = new TranslateTransition(Duration.millis(60), errorLbl);
                tt.setByX(8); tt.setCycleCount(5); tt.setAutoReverse(true);
                tt.setOnFinished(ev -> errorLbl.setTranslateX(0));
                tt.play();
                return;
            }
            launchMainApp(adminBtn.isSelected());
        };
        connectBtn.setOnAction(e -> doLogin.run());
        passField.setOnAction(e -> doLogin.run());

        // Simple fade-in of the card only (not background — avoids jitter)
        card.setOpacity(0);
        Scene scene = new Scene(root, 700, 480);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.centerOnScreen();
        primaryStage.show();

        FadeTransition ft = new FadeTransition(Duration.millis(400), card);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    private ToggleButton makeLoginRoleBtn(String text, ToggleGroup group, boolean selected) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group);
        btn.setSelected(selected);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(42);
        String unsel =
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 13px;" +
            "-fx-background-color: " + C_SURFACE2 + ";" +
            "-fx-text-fill: " + C_TEXT_MUTED + ";" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-width: 1;" +
            "-fx-background-radius: 6;" +
            "-fx-border-radius: 6;" +
            "-fx-cursor: hand;";
        String sel =
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-color: " + C_ACCENT_DIM + ";" +
            "-fx-text-fill: white;" +
            "-fx-border-color: " + C_ACCENT + ";" +
            "-fx-border-width: 1;" +
            "-fx-background-radius: 6;" +
            "-fx-border-radius: 6;" +
            "-fx-cursor: hand;";
        btn.setStyle(selected ? sel : unsel);
        btn.selectedProperty().addListener((obs, o, n) -> btn.setStyle(n ? sel : unsel));
        return btn;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TRANSITION  login → main
    // ══════════════════════════════════════════════════════════════════════════
    private void launchMainApp(boolean adminMode) {
        this.isAdmin = adminMode;
        Scene mainScene = buildMainScene(adminMode);

        FadeTransition out = new FadeTransition(Duration.millis(200), primaryStage.getScene().getRoot());
        out.setToValue(0);
        out.setOnFinished(e -> {
            primaryStage.setScene(mainScene);
            primaryStage.setResizable(true);
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(640);
            primaryStage.centerOnScreen();

            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), mainScene.getRoot());
            fadeIn.setFromValue(0); fadeIn.setToValue(1); fadeIn.play();

            try {
                node = new MeshNode(adminMode, this);
                node.startListening();
                String id = node.getNodeId();
                primaryStage.setTitle("ResilientNet  ·  " + id + (adminMode ? "  [ADMIN]" : ""));
                nodeIdLabel.setText(id);
                roleLabel.setText(adminMode ? "ADMIN" : "USER");
                if (!adminMode) targetedCheckbox.setDisable(true);
            } catch (Exception ex) {
                postSystemEvent("ERROR: Failed to start networking — " + ex.getMessage());
            }

            startDeviceBatteryPoller();
        });
        out.play();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DEVICE BATTERY  (OSHI — real hardware reading)
    // ══════════════════════════════════════════════════════════════════════════
    private void startDeviceBatteryPoller() {
        batteryPoller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "battery-poller");
            t.setDaemon(true);
            return t;
        });
        batteryPoller.scheduleAtFixedRate(() -> {
            try {
                SystemInfo si = new SystemInfo();
                List<PowerSource> sources = si.getHardware().getPowerSources();
                if (!sources.isEmpty()) {
                    PowerSource ps = sources.get(0);
                    double pct = ps.getRemainingCapacityPercent() * 100.0;
                    boolean charging = ps.isCharging() || ps.isPowerOnLine();
                    int level = (int) Math.round(pct);
                    deviceBatteryAvailable = true;
                    Platform.runLater(() -> updateDeviceBattUI(level, charging));
                } else {
                    deviceBatteryAvailable = false;
                    Platform.runLater(() -> {
                        if (deviceBattLabel != null) deviceBattLabel.setText("No battery");
                    });
                }
            } catch (Exception ignored) {
                deviceBatteryAvailable = false;
            }
        }, 0, 30, TimeUnit.SECONDS);  // poll every 30s — battery doesn't change fast
    }

    private void updateDeviceBattUI(int level, boolean charging) {
        if (deviceBattBar == null || deviceBattLabel == null) return;
        deviceBattBar.setProgress(level / 100.0);
        String icon = charging ? " ⚡" : "";
        deviceBattLabel.setText(level + "%" + icon);
        String col = level <= 15 ? C_RED : level <= 30 ? C_ORANGE : C_ACCENT;
        deviceBattBar.setStyle("-fx-accent: " + col + ";");
        deviceBattLabel.setStyle(
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 11px;" +
            "-fx-text-fill: " + col + ";"
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAIN SCENE
    // ══════════════════════════════════════════════════════════════════════════
    private Scene buildMainScene(boolean adminMode) {

        // ── TOP BAR ──────────────────────────────────────────────────────────
        Label appTitle = new Label("⬡ ResilientNet");
        appTitle.setStyle(
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 15px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + C_ACCENT + ";"
        );

        Label nodePrefix = new Label("Node:");
        nodePrefix.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-text-fill: " + C_TEXT_MUTED + ";");
        nodeIdLabel = new Label("...");
        nodeIdLabel.setStyle("-fx-font-family: " + FONT_MONO + "; -fx-font-size: 12px; -fx-text-fill: " + C_TEXT + ";");

        roleLabel = new Label(adminMode ? "ADMIN" : "USER");
        roleLabel.setStyle(
            "-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-font-weight: bold;" +
            "-fx-text-fill: " + (adminMode ? C_GOLD : C_BLUE) + ";" +
            "-fx-background-color: " + (adminMode ? "#2A1F00" : "#0D2040") + ";" +
            "-fx-padding: 2 9 2 9; -fx-background-radius: 4;"
        );

        // Signal indicator
        signalDot = new Circle(5, Color.web(C_ACCENT));
        signalLabel = new Label("Online");
        signalLabel.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-text-fill: " + C_ACCENT + ";");
        HBox signalBox = new HBox(5, signalDot, signalLabel);
        signalBox.setAlignment(Pos.CENTER_LEFT);
        signalBox.setPadding(new Insets(2, 10, 2, 8));
        signalBox.setStyle("-fx-background-color: #0D1F0D; -fx-background-radius: 4;");

        // Mesh-node battery (manual, software level)
        Label meshBattPrefix = new Label("Node Bat:");
        meshBattPrefix.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 10px; -fx-text-fill: " + C_TEXT_MUTED + ";");
        batteryBar = new ProgressBar(1.0);
        batteryBar.setPrefWidth(60); batteryBar.setPrefHeight(8);
        batteryBar.setStyle("-fx-accent: " + C_ACCENT + ";");
        batteryLabel = new Label("100%");
        batteryLabel.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-text-fill: " + C_TEXT_MUTED + ";");
        HBox meshBattBox = new HBox(5, meshBattPrefix, batteryBar, batteryLabel);
        meshBattBox.setAlignment(Pos.CENTER_LEFT);
        meshBattBox.setPadding(new Insets(2, 8, 2, 8));
        meshBattBox.setStyle("-fx-background-color: " + C_SURFACE2 + "; -fx-background-radius: 4;");

        // Device battery (real hardware via OSHI)
        Label devBattPrefix = new Label("Device:");
        devBattPrefix.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 10px; -fx-text-fill: " + C_TEXT_MUTED + ";");
        deviceBattBar = new ProgressBar(0);
        deviceBattBar.setPrefWidth(60); deviceBattBar.setPrefHeight(8);
        deviceBattBar.setStyle("-fx-accent: " + C_ACCENT + ";");
        deviceBattLabel = new Label("...");
        deviceBattLabel.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-text-fill: " + C_TEXT_MUTED + ";");
        HBox devBattBox = new HBox(5, devBattPrefix, deviceBattBar, deviceBattLabel);
        devBattBox.setAlignment(Pos.CENTER_LEFT);
        devBattBox.setPadding(new Insets(2, 8, 2, 8));
        devBattBox.setStyle("-fx-background-color: " + C_SURFACE2 + "; -fx-background-radius: 4;");

        bufferLabel = new Label("");
        bufferLabel.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-text-fill: " + C_ORANGE + ";");

        Region tSpacer = new Region();
        HBox.setHgrow(tSpacer, Priority.ALWAYS);

        HBox topBar = new HBox(12,
            appTitle, sep3px(),
            nodePrefix, nodeIdLabel, roleLabel,
            tSpacer,
            bufferLabel, signalBox, meshBattBox, devBattBox
        );
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(9, 16, 9, 16));
        topBar.setStyle(
            "-fx-background-color: " + C_SURFACE + ";" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-width: 0 0 1 0;"
        );

        // ── PANELS ────────────────────────────────────────────────────────────
        VBox leftPanel = buildLeftPanel(adminMode);
        leftPanel.setPrefWidth(195);
        leftPanel.setMinWidth(195);
        leftPanel.setMaxWidth(195);

        VBox centerCol = buildCenterColumn(adminMode);

        VBox rightCol = buildRightColumn();
        rightCol.setPrefWidth(255);
        rightCol.setMinWidth(255);
        rightCol.setMaxWidth(255);

        HBox mainArea = new HBox(leftPanel, centerCol, rightCol);
        HBox.setHgrow(centerCol, Priority.ALWAYS);
        VBox.setVgrow(mainArea, Priority.ALWAYS);

        VBox bottomBar = buildBottomBar(adminMode);

        VBox root = new VBox(topBar, mainArea, bottomBar);
        VBox.setVgrow(mainArea, Priority.ALWAYS);
        root.setStyle("-fx-background-color: " + C_BG + ";");

        Scene scene = new Scene(root, 1100, 680);
        scene.getStylesheets().add(buildCss());

        primaryStage.setOnCloseRequest(e -> {
            if (batteryPoller != null) batteryPoller.shutdownNow();
            if (node != null) node.shutdown();
            Platform.exit();
            System.exit(0);
        });
        return scene;
    }

    // ── LEFT PANEL ────────────────────────────────────────────────────────────
    private VBox buildLeftPanel(boolean adminMode) {
        Label header = panelHeader("Active Peers");

        peerList = new ListView<>();
        peerList.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(peerList, Priority.ALWAYS);

        Button statusBtn = ghostButton("⊞  Node Status");
        statusBtn.setMaxWidth(Double.MAX_VALUE);
        statusBtn.setOnAction(e -> { if (node != null) postSystemEvent(node.getStatusReport()); });
        statusBtn.setDisable(!adminMode);

        Button setPriorityBtn = ghostButton("⚑  Set Node Priority");
        setPriorityBtn.setMaxWidth(Double.MAX_VALUE);
        setPriorityBtn.setOnAction(e -> showPriorityDialog());
        setPriorityBtn.setDisable(!adminMode);

        VBox panel = new VBox(0);
        panel.getChildren().addAll(header, peerList, styledSep(), padded(statusBtn), padded(setPriorityBtn));
        VBox.setVgrow(peerList, Priority.ALWAYS);
        panel.setStyle(
            "-fx-background-color: " + C_SURFACE + ";" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-width: 0 1 0 0;"
        );
        return panel;
    }

    // ── CENTER COLUMN ─────────────────────────────────────────────────────────
    private VBox buildCenterColumn(boolean adminMode) {
        Label header = panelHeader("Messages");

        chatFeed = new VBox(6);
        chatFeed.setPadding(new Insets(10));
        chatFeed.setFillWidth(true);

        chatScroll = new ScrollPane(chatFeed);
        chatScroll.setFitToWidth(true);
        chatScroll.setStyle(
            "-fx-background-color: " + C_BG + ";" +
            "-fx-background: " + C_BG + ";" +
            "-fx-border-color: transparent;"
        );
        chatScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chatScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        VBox centerCol = new VBox(0);
        VBox.setVgrow(centerCol, Priority.ALWAYS);
        if (adminMode) {
            VBox dashPanel = buildDashboard();
            dashPanel.setMinHeight(240);
            dashPanel.setMaxHeight(240);
            centerCol.getChildren().addAll(header, chatScroll, dashPanel);
        } else {
            centerCol.getChildren().addAll(header, chatScroll);
        }
        VBox.setVgrow(chatScroll, Priority.ALWAYS);
        centerCol.setStyle("-fx-background-color: " + C_BG + ";");
        return centerCol;
    }

    // ── ADMIN DASHBOARD ───────────────────────────────────────────────────────
    private VBox buildDashboard() {
        Label header = panelHeader("Admin Dashboard  —  Node Registry");

        dashOnlineCount  = new Label("0");
        dashOnlineCount.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + C_ACCENT + ";");
        dashOfflineCount = new Label("0");
        dashOfflineCount.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + C_RED + ";");
        dashTotalCount   = new Label("0");
        dashTotalCount.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + C_BLUE + ";");

        VBox statOnline  = statBox(dashOnlineCount,  "Online");
        VBox statOffline = statBox(dashOfflineCount, "Offline");
        VBox statTotal   = statBox(dashTotalCount,   "Total");

        HBox stats = new HBox(10, statOnline, statOffline, statTotal);
        stats.setPadding(new Insets(10, 14, 10, 14));

        // Table header row
        HBox tblHeader = dashRow("NODE ID", "ADDRESS", "STATUS", "PRIORITY", true);
        tblHeader.setStyle("-fx-background-color: " + C_SURFACE2 + "; -fx-padding: 4 14 4 14;");

        dashboardTable = new VBox(2);
        dashboardTable.setPadding(new Insets(4, 6, 4, 6));

        ScrollPane dashScroll = new ScrollPane(dashboardTable);
        dashScroll.setFitToWidth(true);
        dashScroll.setStyle("-fx-background-color: " + C_BG + "; -fx-background: " + C_BG + "; -fx-border-color: transparent;");
        VBox.setVgrow(dashScroll, Priority.ALWAYS);

        VBox panel = new VBox(0, header, stats, styledSep(), tblHeader, dashScroll);
        VBox.setVgrow(dashScroll, Priority.ALWAYS);
        panel.setStyle(
            "-fx-background-color: " + C_SURFACE + ";" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-width: 1 0 0 0;"
        );
        return panel;
    }

    // ── RIGHT COLUMN ──────────────────────────────────────────────────────────
    private VBox buildRightColumn() {
        Label ackHeader = panelHeader("Delivery Receipts");
        ackFeed = new VBox(4);
        ackFeed.setPadding(new Insets(8));
        ackScroll = new ScrollPane(ackFeed);
        ackScroll.setFitToWidth(true);
        ackScroll.setPrefHeight(220);
        ackScroll.setMinHeight(180);
        ackScroll.setMaxHeight(220);
        ackScroll.setStyle("-fx-background-color: " + C_BG + "; -fx-background: " + C_BG + "; -fx-border-color: transparent;");

        Label sysHeader = panelHeader("System Events");
        systemFeed = new VBox(4);
        systemFeed.setPadding(new Insets(8));
        systemScroll = new ScrollPane(systemFeed);
        systemScroll.setFitToWidth(true);
        systemScroll.setStyle("-fx-background-color: " + C_BG + "; -fx-background: " + C_BG + "; -fx-border-color: transparent;");
        VBox.setVgrow(systemScroll, Priority.ALWAYS);

        VBox col = new VBox(0, ackHeader, ackScroll, styledSep(), sysHeader, systemScroll);
        VBox.setVgrow(systemScroll, Priority.ALWAYS);
        col.setStyle(
            "-fx-background-color: " + C_SURFACE + ";" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-width: 0 0 0 1;"
        );
        return col;
    }

    // ── BOTTOM INPUT BAR ──────────────────────────────────────────────────────
    private VBox buildBottomBar(boolean adminMode) {

        // Priority strip
        priorityGroup = new ToggleGroup();
        btnLow      = priorityChip("Low",      priorityGroup, false, "#2D3748", C_TEXT_MUTED);
        btnNormal   = priorityChip("Normal",   priorityGroup, true,  "#1A3A1A", C_ACCENT);
        btnHigh     = priorityChip("High",     priorityGroup, false, "#3A2800", C_ORANGE);
        btnCritical = priorityChip("⚠ Critical", priorityGroup, false, "#3A0A0A", C_RED);

        Label priPrefix = new Label("Priority:");
        priPrefix.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-text-fill: " + C_TEXT_MUTED + ";");
        HBox priorityRow = new HBox(8, priPrefix, btnLow, btnNormal, btnHigh, btnCritical);
        priorityRow.setAlignment(Pos.CENTER_LEFT);
        priorityRow.setPadding(new Insets(8, 14, 4, 14));

        // Targeted row
        targetedCheckbox = new CheckBox("Targeted Send");
        targetedCheckbox.setStyle(
            "-fx-text-fill: " + C_TEXT_MUTED + ";" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 12px;"
        );
        targetedCheckbox.setDisable(!adminMode);

        targetField = new TextField();
        targetField.setPromptText("node-id1, node-id2 ...");
        targetField.setDisable(true);
        targetField.setPrefWidth(240);
        targetField.setStyle(inputStyle());
        targetedCheckbox.selectedProperty().addListener((obs, o, n) -> targetField.setDisable(!n));

        HBox targetRow = new HBox(10, targetedCheckbox, targetField);
        targetRow.setAlignment(Pos.CENTER_LEFT);
        targetRow.setPadding(new Insets(2, 14, 4, 14));

        // Message input row
        inputField = new TextField();
        inputField.setPromptText("Type a message... (SOS / HELP auto-escalates to Critical)");
        inputField.setPrefHeight(40);
        inputField.setStyle(inputStyle());
        HBox.setHgrow(inputField, Priority.ALWAYS);

        sendBtn = new Button("Send  ▶");
        sendBtn.setPrefHeight(40);
        String sendNormal =
            "-fx-background-color: " + C_ACCENT_DIM + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 0 22 0 22;" +
            "-fx-cursor: hand;";
        String sendHover =
            "-fx-background-color: " + C_ACCENT + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 0 22 0 22;" +
            "-fx-cursor: hand;";
        sendBtn.setStyle(sendNormal);
        sendBtn.setOnMouseEntered(e -> sendBtn.setStyle(sendHover));
        sendBtn.setOnMouseExited(e  -> sendBtn.setStyle(sendNormal));
        sendBtn.setOnAction(e -> sendMessage());
        inputField.setOnAction(e -> sendMessage());

        HBox inputRow = new HBox(10, inputField, sendBtn);
        inputRow.setAlignment(Pos.CENTER);
        inputRow.setPadding(new Insets(4, 14, 6, 14));

        // Control row: signal toggle + mesh battery setter
        toggleSignalBtn = ghostButton("⇋  Toggle Signal");
        toggleSignalBtn.setOnAction(e -> { if (node != null) node.toggleSignal(); });

        Label batLbl = new Label("Node Battery %:");
        batLbl.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-text-fill: " + C_TEXT_MUTED + ";");
        batteryField = new TextField("100");
        batteryField.setPrefWidth(52);
        batteryField.setPrefHeight(32);
        batteryField.setStyle(inputStyle());
        Button setBatBtn = ghostButton("Set");
        setBatBtn.setOnAction(e -> {
            try {
                int val = Integer.parseInt(batteryField.getText().trim());
                if (val < 0 || val > 100) throw new NumberFormatException();
                if (node != null) node.setBatteryLevel(val);
                updateMeshBattUI(val);
            } catch (NumberFormatException ex) {
                postSystemEvent("⚠ Invalid battery value — enter 0–100.");
            }
        });

        Region ctrlSpacer = new Region();
        HBox.setHgrow(ctrlSpacer, Priority.ALWAYS);

        HBox ctrlRow = new HBox(10, toggleSignalBtn, ctrlSpacer, batLbl, batteryField, setBatBtn);
        ctrlRow.setAlignment(Pos.CENTER_LEFT);
        ctrlRow.setPadding(new Insets(0, 14, 10, 14));

        VBox bar = new VBox(0, styledSep(), priorityRow, targetRow, inputRow, ctrlRow);
        bar.setStyle("-fx-background-color: " + C_SURFACE + ";");
        return bar;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SEND LOGIC
    // ══════════════════════════════════════════════════════════════════════════
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || node == null) return;

        Message.Priority pri = selectedPriority();
        String result;
        String label;

        if (targetedCheckbox.isSelected() && !targetedCheckbox.isDisabled()) {
            String raw = targetField.getText().trim();
            if (raw.isEmpty()) { postSystemEvent("⚠ Enter target node IDs (comma-separated)."); return; }
            Set<String> targets = new HashSet<>(Arrays.asList(raw.split("[,\\s]+")));
            result = node.sendTargeted(text, targets, pri);
            label  = "You → [" + raw + "]";
        } else {
            result = node.broadcastMessage(text, pri);
            label  = "You (broadcast)";
        }

        postOutgoingMessage(label, text, pri);
        if (result != null) postSystemEvent(result);
        updateMeshBattUI(node.getBatteryLevel());
        inputField.clear();
    }

    private Message.Priority selectedPriority() {
        if (btnLow.isSelected())      return Message.Priority.LOW;
        if (btnHigh.isSelected())     return Message.Priority.HIGH;
        if (btnCritical.isSelected()) return Message.Priority.CRITICAL;
        return Message.Priority.NORMAL;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRIORITY DIALOG (Admin assigns node priority label)
    // ══════════════════════════════════════════════════════════════════════════
    private void showPriorityDialog() {
        if (node == null) return;
        Set<String> peers = node.getKnownNodeIds();
        if (peers.isEmpty()) { postSystemEvent("No known nodes to assign priority to."); return; }

        Stage dlg = new Stage();
        dlg.setTitle("Set Node Priority");
        dlg.initOwner(primaryStage);
        dlg.setResizable(false);

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: " + C_SURFACE + ";");

        Label title = new Label("Assign Priority Label to Node");
        title.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + C_TEXT + ";");

        Label nodeLbl = new Label("SELECT NODE");
        nodeLbl.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + C_TEXT_MUTED + ";");

        ComboBox<String> nodeCombo = new ComboBox<>();
        nodeCombo.getItems().addAll(peers);
        nodeCombo.getSelectionModel().selectFirst();
        nodeCombo.setMaxWidth(Double.MAX_VALUE);
        nodeCombo.setPrefHeight(38);
        nodeCombo.setStyle(
            "-fx-background-color: " + C_BG + ";" +
            "-fx-text-fill: " + C_TEXT + ";" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-width: 1;" +
            "-fx-background-radius: 6;" +
            "-fx-border-radius: 6;"
        );

        Label priLbl = new Label("ASSIGN PRIORITY");
        priLbl.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + C_TEXT_MUTED + ";");

        ToggleGroup priGroup = new ToggleGroup();
        ToggleButton pLow      = dialogPriChip("LOW",      priGroup, C_TEXT_MUTED, C_SURFACE2);
        ToggleButton pNormal   = dialogPriChip("NORMAL",   priGroup, C_ACCENT,     "#1A3A1A");
        ToggleButton pHigh     = dialogPriChip("HIGH",     priGroup, C_ORANGE,     "#3A2800");
        ToggleButton pCritical = dialogPriChip("CRITICAL", priGroup, C_RED,        "#3A0A0A");
        pNormal.setSelected(true);

        HBox priRow = new HBox(8, pLow, pNormal, pHigh, pCritical);
        priRow.setAlignment(Pos.CENTER_LEFT);
        for (Node n : priRow.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        Button applyBtn = new Button("Apply Priority");
        applyBtn.setMaxWidth(Double.MAX_VALUE);
        applyBtn.setPrefHeight(40);
        applyBtn.setStyle(
            "-fx-background-color: " + C_ACCENT_DIM + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        );
        applyBtn.setOnAction(e -> {
            String selNode = nodeCombo.getSelectionModel().getSelectedItem();
            ToggleButton selBtn = (ToggleButton) priGroup.getSelectedToggle();
            if (selNode != null && selBtn != null) {
                String pri = (String) selBtn.getUserData();
                node.setNodePriority(selNode, pri);
                postSystemEvent("Priority for " + selNode + " set to " + pri);
            }
            dlg.close();
        });

        root.getChildren().addAll(title, styledSep(), nodeLbl, nodeCombo, priLbl, priRow, applyBtn);
        dlg.setScene(new Scene(root, 360, 290));
        dlg.getScene().getStylesheets().add(buildCss());
        dlg.show();
    }

    private ToggleButton dialogPriChip(String label, ToggleGroup group, String fg, String bg) {
        ToggleButton btn = new ToggleButton(label);
        btn.setToggleGroup(group);
        btn.setUserData(label);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(36);
        String base = "-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-font-weight: bold;" +
                      "-fx-background-radius: 4; -fx-border-radius: 4; -fx-cursor: hand;";
        String unsel = base + "-fx-background-color: " + C_SURFACE2 + "; -fx-text-fill: " + C_TEXT_MUTED + "; -fx-border-color: " + C_BORDER + "; -fx-border-width: 1;";
        String sel   = base + "-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; -fx-border-color: " + fg + "99; -fx-border-width: 1;";
        btn.setStyle(unsel);
        btn.selectedProperty().addListener((obs, o, n) -> btn.setStyle(n ? sel : unsel));
        return btn;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MESSAGE CARD BUILDERS
    // ══════════════════════════════════════════════════════════════════════════
    private void postIncomingMessage(Message msg) {
        String[] pc = priorityColors(msg.priority);

        // Top-border card
        HBox card = new HBox(10);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(
            "-fx-background-color: " + C_SURFACE + ";" +
            "-fx-border-color: " + pc[0] + " transparent transparent transparent;" +
            "-fx-border-width: 2 0 0 0;" +
            "-fx-background-radius: 6;"
        );

        // Avatar
        Circle av = new Circle(15, Color.web(nodeColor(msg.senderId)));
        Label avLbl = new Label(msg.senderId.substring(0, 1).toUpperCase());
        avLbl.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: white;");
        StackPane avPane = new StackPane(av, avLbl);

        // Sender + badges
        Label senderLbl = new Label(msg.senderId);
        senderLbl.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + C_TEXT + ";");

        Label roleBadge = badge(msg.senderRole, C_TEXT_MUTED, C_SURFACE2);
        Label priBadge  = badge(msg.priority != null ? msg.priority.name() : "NORMAL", pc[2], pc[1]);

        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter
                .ofPattern("HH:mm:ss").withZone(java.time.ZoneId.systemDefault());
        Label timeLbl = new Label(dtf.format(java.time.Instant.ofEpochMilli(msg.timestamp)));
        timeLbl.setStyle("-fx-font-family: " + FONT_MONO + "; -fx-font-size: 10px; -fx-text-fill: " + C_TEXT_MUTED + ";");

        Region ms = new Region(); HBox.setHgrow(ms, Priority.ALWAYS);
        HBox metaRow = new HBox(6, senderLbl, roleBadge, priBadge, ms, timeLbl);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Label contentLbl = new Label(msg.content);
        contentLbl.setWrapText(true);
        contentLbl.setMaxWidth(Double.MAX_VALUE);
        contentLbl.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 13px; -fx-text-fill: " + C_TEXT + ";");

        VBox textBox = new VBox(4, metaRow, contentLbl);
        HBox.setHgrow(textBox, Priority.ALWAYS);
        card.getChildren().addAll(avPane, textBox);

        Platform.runLater(() -> {
            chatFeed.getChildren().add(card);
            scrollToBottom(chatScroll);
        });
    }

    private void postOutgoingMessage(String label, String text, Message.Priority pri) {
        String[] pc = priorityColors(pri);

        HBox card = new HBox(10);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(
            "-fx-background-color: #0D1F10;" +
            "-fx-border-color: " + pc[0] + " transparent transparent transparent;" +
            "-fx-border-width: 2 0 0 0;" +
            "-fx-background-radius: 6;"
        );

        Circle av = new Circle(15, Color.web(C_ACCENT_DIM));
        Label avLbl = new Label("Y");
        avLbl.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: white;");
        StackPane avPane = new StackPane(av, avLbl);

        Label senderLbl = new Label(label);
        senderLbl.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + C_ACCENT + ";");

        Label priBadge = badge(pri.name(), pc[2], pc[1]);

        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter
                .ofPattern("HH:mm:ss").withZone(java.time.ZoneId.systemDefault());
        Label timeLbl = new Label(dtf.format(java.time.Instant.now()));
        timeLbl.setStyle("-fx-font-family: " + FONT_MONO + "; -fx-font-size: 10px; -fx-text-fill: " + C_TEXT_MUTED + ";");

        Region ms = new Region(); HBox.setHgrow(ms, Priority.ALWAYS);
        HBox metaRow = new HBox(6, senderLbl, priBadge, ms, timeLbl);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Label contentLbl = new Label(text);
        contentLbl.setWrapText(true);
        contentLbl.setMaxWidth(Double.MAX_VALUE);
        contentLbl.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 13px; -fx-text-fill: " + C_TEXT + ";");

        VBox textBox = new VBox(4, metaRow, contentLbl);
        HBox.setHgrow(textBox, Priority.ALWAYS);
        card.getChildren().addAll(avPane, textBox);
        chatFeed.getChildren().add(card);
        scrollToBottom(chatScroll);
    }

    private void postAckEvent(String fromId) {
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter
                .ofPattern("HH:mm:ss").withZone(java.time.ZoneId.systemDefault());
        String ts = dtf.format(java.time.Instant.now());

        HBox row = new HBox(8);
        row.setPadding(new Insets(5, 10, 5, 10));
        row.setStyle("-fx-background-color: #0A1A10; -fx-background-radius: 4;");
        row.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("✓");
        icon.setStyle("-fx-font-size: 13px; -fx-text-fill: " + C_ACCENT + "; -fx-font-weight: bold;");
        Label lbl = new Label("Delivered → " + fromId);
        lbl.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 12px; -fx-text-fill: " + C_TEXT + ";");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label time = new Label(ts);
        time.setStyle("-fx-font-family: " + FONT_MONO + "; -fx-font-size: 10px; -fx-text-fill: " + C_TEXT_MUTED + ";");

        row.getChildren().addAll(icon, lbl, sp, time);
        ackFeed.getChildren().add(row);
        scrollToBottom(ackScroll);
    }

    private void postSystemEvent(String text) {
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter
                .ofPattern("HH:mm:ss").withZone(java.time.ZoneId.systemDefault());
        String ts = dtf.format(java.time.Instant.now());

        String fg = C_TEXT_MUTED, bg = C_SURFACE2;
        if (text.contains("ERROR") || text.contains("DENIED") || text.contains("BLOCKED")
                || text.contains("SOS") || text.contains("ALERT")) {
            fg = C_RED; bg = "#1A0808";
        } else if (text.contains("ONLINE") || text.contains("joined") || text.contains("RECOVERED")
                   || text.contains("online") || text.contains("flushed")) {
            fg = C_ACCENT; bg = "#0A1A0A";
        } else if (text.contains("OFFLINE") || text.contains("DISCONNECT") || text.contains("LOST")
                   || text.contains("BUFFERED") || text.contains("buffered")) {
            fg = C_ORANGE; bg = "#1A1200";
        }

        HBox row = new HBox(8);
        row.setPadding(new Insets(5, 10, 5, 10));
        row.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 4;");
        row.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setMaxWidth(Double.MAX_VALUE);
        lbl.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-text-fill: " + fg + ";");
        HBox.setHgrow(lbl, Priority.ALWAYS);

        Label time = new Label(ts);
        time.setStyle("-fx-font-family: " + FONT_MONO + "; -fx-font-size: 10px; -fx-text-fill: " + C_TEXT_MUTED + ";");

        row.getChildren().addAll(lbl, time);
        if (systemFeed != null) {
            systemFeed.getChildren().add(row);
            scrollToBottom(systemScroll);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DASHBOARD UPDATE
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public void onDashboardUpdate(Map<String, String> statuses,
                                  Map<String, String> priorities,
                                  Map<String, String> addresses) {
        Platform.runLater(() -> {
            if (dashboardTable == null) return;
            dashboardTable.getChildren().clear();

            long online  = statuses.values().stream().filter("ONLINE"::equals).count();
            long offline = statuses.values().stream().filter("OFFLINE"::equals).count();
            dashOnlineCount.setText(String.valueOf(online));
            dashOfflineCount.setText(String.valueOf(offline));
            dashTotalCount.setText(String.valueOf(statuses.size()));

            statuses.forEach((id, status) -> {
                String pri  = priorities.getOrDefault(id, "NORMAL");
                String addr = addresses.getOrDefault(id, "—");
                HBox row = dashRow(id, addr, status, pri, false);
                dashboardTable.getChildren().add(row);
            });
        });
    }

    private HBox dashRow(String id, String addr, String status, String pri, boolean isHeader) {
        HBox row = new HBox(0);
        row.setPadding(new Insets(5, 8, 5, 8));
        if (!isHeader)
            row.setStyle("-fx-background-color: " + C_SURFACE + "; -fx-background-radius: 4;");

        String baseStyle = "-fx-font-family: " + (isHeader ? FONT_UI : FONT_MONO) + "; -fx-font-size: " + (isHeader ? "10" : "12") + "px;";
        String textColor = isHeader ? C_TEXT_MUTED : C_TEXT;

        Label lId   = new Label(id);   lId.setPrefWidth(110);   lId.setStyle(baseStyle + "-fx-text-fill: " + textColor + (isHeader ? "; -fx-font-weight: bold;" : "; -fx-font-weight: bold;"));
        Label lAddr = new Label(addr); lAddr.setPrefWidth(130); lAddr.setStyle(baseStyle + "-fx-text-fill: " + textColor + ";");

        Label lStatus;
        if (isHeader) {
            lStatus = new Label(status); lStatus.setPrefWidth(80);
            lStatus.setStyle(baseStyle + "-fx-text-fill: " + textColor + "; -fx-font-weight: bold;");
        } else {
            boolean online = "ONLINE".equals(status);
            lStatus = new Label((online ? "● " : "○ ") + status);
            lStatus.setPrefWidth(80);
            lStatus.setStyle(baseStyle + "-fx-text-fill: " + (online ? C_ACCENT : C_RED) + "; -fx-font-weight: bold;");
        }

        Label lPri;
        if (isHeader) {
            lPri = new Label(pri); lPri.setPrefWidth(80);
            lPri.setStyle(baseStyle + "-fx-text-fill: " + textColor + "; -fx-font-weight: bold;");
        } else {
            String[] pc = priorityColors(pri);
            lPri = badge(pri, pc[2], pc[1]);
            lPri.setPrefWidth(80);
        }

        row.getChildren().addAll(lId, lAddr, lStatus, lPri);
        return row;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MeshEventListener implementations
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public void onMessageReceived(Message msg) {
        postIncomingMessage(msg);
    }

    @Override
    public void onPeerDiscovered(String peerId) {
        Platform.runLater(() -> {
            if (!peerList.getItems().contains(peerId)) peerList.getItems().add(peerId);
            postSystemEvent("🔵 Peer joined: " + peerId);
        });
    }

    @Override
    public void onPeerLost(String peerId) {
        Platform.runLater(() -> {
            peerList.getItems().remove(peerId);
            postSystemEvent("🔴 Peer disconnected: " + peerId);
        });
    }

    @Override
    public void onPeerReturned(String peerId) {
        Platform.runLater(() -> postSystemEvent("🟢 Peer back online: " + peerId));
    }

    @Override
    public void onSystemMessage(String text) {
        Platform.runLater(() -> postSystemEvent(text));
    }

    @Override
    public void onSignalToggled(boolean signalOn) {
        Platform.runLater(() -> {
            signalDot.setFill(Color.web(signalOn ? C_ACCENT : C_RED));
            signalLabel.setText(signalOn ? "Online" : "Offline");
            signalLabel.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-text-fill: " + (signalOn ? C_ACCENT : C_RED) + ";");
            if (signalLabel.getParent() instanceof HBox hb) {
                hb.setStyle("-fx-background-color: " + (signalOn ? "#0D1F0D" : "#1F0D0D") + "; -fx-padding: 2 10 2 8; -fx-background-radius: 4;");
            }
            postSystemEvent(signalOn ? "Signal recovered — flushing buffer..." : "Signal lost — messages will be buffered.");
        });
    }

    @Override
    public void onBufferUpdate(int count) {
        Platform.runLater(() -> bufferLabel.setText(count > 0 ? "⏳ " + count + " buffered" : ""));
    }

    @Override
    public void onDeliveryAck(String fromId) {
        Platform.runLater(() -> postAckEvent(fromId));
    }

    @Override
    public void onBatteryUpdate(int level) {
        Platform.runLater(() -> updateMeshBattUI(level));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UI HELPERS
    // ══════════════════════════════════════════════════════════════════════════
    private void updateMeshBattUI(int level) {
        batteryLabel.setText(level + "%");
        batteryBar.setProgress(level / 100.0);
        batteryField.setText(String.valueOf(level));
        String col = level <= 15 ? C_RED : level <= 30 ? C_ORANGE : C_ACCENT;
        batteryBar.setStyle("-fx-accent: " + col + ";");
        batteryLabel.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-text-fill: " + col + ";");
    }

    private void scrollToBottom(ScrollPane sp) {
        Platform.runLater(() -> sp.setVvalue(1.0));
    }

    private String[] priorityColors(Message.Priority p) {
        return p != null ? priorityColors(p.name()) : new String[]{C_BORDER, C_SURFACE2, C_TEXT_MUTED};
    }

    private String[] priorityColors(String p) {
        if (p == null) return new String[]{C_BORDER, C_SURFACE2, C_TEXT_MUTED};
        return switch (p) {
            case "LOW"      -> new String[]{C_TEXT_MUTED, "#2D3748", C_TEXT_MUTED};
            case "HIGH"     -> new String[]{C_ORANGE,     "#3A2800", C_ORANGE};
            case "CRITICAL" -> new String[]{C_RED,        "#3A0A0A", C_RED};
            default         -> new String[]{C_ACCENT,     "#1A3A1A", C_ACCENT};
        };
    }

    private String nodeColor(String s) {
        String[] pal = {"#1f6feb","#388bfd","#e3b341","#f85149","#3fb950","#58a6ff","#bc8cff","#ff7b72"};
        return pal[Math.abs(s.hashCode()) % pal.length];
    }

    private Label panelHeader(String text) {
        Label l = new Label(text);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setPadding(new Insets(8, 14, 8, 14));
        l.setStyle(
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + C_TEXT_MUTED + ";" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-width: 0 0 1 0;" +
            "-fx-background-color: " + C_SURFACE + ";"
        );
        return l;
    }

    private Label badge(String text, String fg, String bg) {
        Label l = new Label(text);
        l.setStyle(
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 10px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + fg + ";" +
            "-fx-background-color: " + bg + ";" +
            "-fx-padding: 1 7 1 7;" +
            "-fx-background-radius: 3;"
        );
        return l;
    }

    private VBox statBox(Label bigNum, String smallText) {
        Label lbl = new Label(smallText);
        lbl.setStyle("-fx-font-family: " + FONT_UI + "; -fx-font-size: 10px; -fx-text-fill: " + C_TEXT_MUTED + ";");
        VBox b = new VBox(2, bigNum, lbl);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setPadding(new Insets(6, 18, 6, 18));
        b.setStyle("-fx-background-color: " + C_SURFACE2 + "; -fx-background-radius: 6;");
        HBox.setHgrow(b, Priority.ALWAYS);
        return b;
    }

    private Separator styledSep() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: " + C_BORDER + ";");
        return s;
    }

    private Label sep3px() {
        Label l = new Label("  ");
        return l;
    }

    private Button ghostButton(String text) {
        Button b = new Button(text);
        b.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + C_TEXT_MUTED + ";" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-font-size: 12px;" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-width: 1;" +
            "-fx-background-radius: 6;" +
            "-fx-border-radius: 6;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 6 14 6 14;"
        );
        return b;
    }

    private ToggleButton priorityChip(String label, ToggleGroup group, boolean sel,
                                       String bg, String fg) {
        ToggleButton btn = new ToggleButton(label);
        btn.setToggleGroup(group);
        btn.setSelected(sel);
        btn.setPrefHeight(30);
        String base = "-fx-font-family: " + FONT_UI + "; -fx-font-size: 11px; -fx-font-weight: bold;" +
                      "-fx-background-radius: 5; -fx-border-radius: 5; -fx-cursor: hand; -fx-padding: 0 12 0 12;";
        String unsel = base + "-fx-background-color: " + C_SURFACE2 + "; -fx-text-fill: " + C_TEXT_MUTED + "; -fx-border-color: " + C_BORDER + "; -fx-border-width: 1;";
        String active = base + "-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; -fx-border-color: " + fg + "88; -fx-border-width: 1;";
        btn.setStyle(sel ? active : unsel);
        btn.selectedProperty().addListener((obs, o, n) -> btn.setStyle(n ? active : unsel));
        return btn;
    }

    private String inputStyle() {
        return
            "-fx-background-color: " + C_SURFACE2 + ";" +
            "-fx-text-fill: " + C_TEXT + ";" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-width: 1;" +
            "-fx-background-radius: 6;" +
            "-fx-border-radius: 6;" +
            "-fx-padding: 0 12 0 12;" +
            "-fx-prompt-text-fill: " + C_TEXT_MUTED + ";";
    }

    private VBox padded(Node n) {
        VBox b = new VBox(n);
        b.setPadding(new Insets(4, 8, 4, 8));
        return b;
    }

    private String buildCss() {
        return
            ".scroll-bar{-fx-background-color:" + C_SURFACE + ";}" +
            ".scroll-bar .thumb{-fx-background-color:" + C_BORDER + ";-fx-background-radius:4;}" +
            ".scroll-bar .track{-fx-background-color:transparent;}" +
            ".scroll-bar .increment-button,.scroll-bar .decrement-button{-fx-background-color:transparent;-fx-padding:0;}" +
            ".list-cell{-fx-background-color:transparent;-fx-text-fill:" + C_TEXT + ";-fx-font-family:" + FONT_MONO + ";-fx-font-size:12px;-fx-padding:5 12 5 12;}" +
            ".list-cell:filled:hover{-fx-background-color:" + C_SURFACE2 + ";}" +
            ".list-cell:filled:selected{-fx-background-color:" + C_SURFACE2 + ";-fx-text-fill:" + C_ACCENT + ";}" +
            ".list-view{-fx-background-color:transparent;-fx-border-color:transparent;}" +
            ".combo-box{-fx-background-color:" + C_BG + ";-fx-border-color:" + C_BORDER + ";}" +
            ".combo-box .list-cell{-fx-background-color:" + C_BG + ";-fx-text-fill:" + C_TEXT + ";}" +
            ".combo-box-popup .list-cell{-fx-background-color:" + C_SURFACE + ";-fx-text-fill:" + C_TEXT + ";}" +
            ".combo-box-popup .list-cell:hover{-fx-background-color:" + C_SURFACE2 + ";}" +
            ".check-box .box{-fx-background-color:" + C_SURFACE2 + ";-fx-border-color:" + C_BORDER + ";}" +
            ".check-box:selected .box{-fx-background-color:" + C_ACCENT_DIM + ";-fx-border-color:" + C_ACCENT + ";}" +
            ".separator .line{-fx-border-color:" + C_BORDER + ";}";
    }

    public static void main(String[] args) { launch(args); }
}