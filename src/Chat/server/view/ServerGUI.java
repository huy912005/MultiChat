package Chat.server.view;

import Chat.server.network.ClientHandler;
import Chat.server.network.Server;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ServerGUI: Giao diện quản lý server với dark theme hiện đại.
 * Cho phép: xem log, kick user, tạo/sửa phòng chat.
 */
public class ServerGUI extends JFrame implements ServerLogger {

    // ─── Bảng màu dark theme ─────────────────────────────────────
    private static final Color BG_DARK        = new Color(15,  17,  26);
    private static final Color BG_PANEL       = new Color(22,  25,  37);
    private static final Color BG_CARD        = new Color(30,  34,  50);
    private static final Color BG_INPUT       = new Color(38,  43,  60);
    private static final Color ACCENT_BLUE    = new Color(82,  130, 255);
    private static final Color ACCENT_GREEN   = new Color(52,  211, 153);
    private static final Color ACCENT_ORANGE  = new Color(251, 146, 60);
    private static final Color ACCENT_RED     = new Color(248, 113, 113);
    private static final Color TEXT_PRIMARY   = new Color(236, 240, 255);
    private static final Color TEXT_SECONDARY = new Color(148, 163, 184);
    private static final Color TEXT_MUTED     = new Color(71,  85,  105);
    private static final Color BORDER_COLOR   = new Color(44,  50,  72);

    // ─── Components chính ────────────────────────────────────────
    private JTextPane logPane;
    private StyledDocument logDoc;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    private JLabel lblOnlineCount;
    private JLabel lblTotalMessages;
    private JLabel lblTotalJoins;
    private JLabel lblServerStatus;
    private JLabel lblPort;

    // ─── Room management components ──────────────────────────────
    private DefaultTableModel roomTableModel;
    private JTable roomTable;

    // ─── Tham chiếu đến Server để thực hiện hành động admin ──────
    private Server server;

    // ─── Thống kê ────────────────────────────────────────────────
    private final AtomicInteger totalMessages = new AtomicInteger(0);
    private final AtomicInteger totalJoins    = new AtomicInteger(0);

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // KẾT NỐI CLOUD ADMIN
    private java.io.PrintWriter adminOut;
    private final String CLOUD_IP = "159.65.134.130";

    public ServerGUI() {
        initFrame();
        initComponents();
        setVisible(true);
        connectToCloudAdmin();
        
        // Bắt đầu tự động cập nhật danh sách các phòng
        SwingUtilities.invokeLater(this::refreshRoomTable);
        new javax.swing.Timer(5000, e -> SwingUtilities.invokeLater(this::refreshRoomTable)).start();
    }

    private void connectToCloudAdmin() {
        new Thread(() -> {
            try {
                logSystem("[CLOUD] Đang cố gắng kết nối tới Cloud Server " + CLOUD_IP + "...");
                java.net.Socket socket = new java.net.Socket(CLOUD_IP, 5000);
                adminOut = new java.io.PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), "UTF-8"));

                Chat.server.model.Message authMsg = new Chat.server.model.Message("ADMIN", "ADMIN_LOGIN|admin123", Chat.server.model.Message.Type.AUTH);
                adminOut.println(authMsg.toNetworkString());

                String line;
                while (true) {
                    line = in.readLine();
                    if (line == null) {
                        logError("[CLOUD] Bị ngắt kết nối với VPS (Nguyên nhân: VPS đang chạy code CŨ chưa được up code mới lên).");
                        break;
                    }
                    Chat.server.model.Message msg = Chat.server.model.Message.fromNetworkString(line);
                    if (msg != null && msg.getType() == Chat.server.model.Message.Type.ADMIN_LOG) {
                        String type = msg.getSender(); 
                        String content = msg.getContent();
                        if ("ADMIN_SYNC".equals(type)) {
                            SwingUtilities.invokeLater(() -> {
                                String[] parts = content.split("\\|", 2);
                                lblOnlineCount.setText(parts[0]);
                                if (userListModel != null) {
                                    userListModel.clear();
                                    if (parts.length > 1 && !parts[1].isEmpty()) {
                                        for (String u : parts[1].split(",")) {
                                            userListModel.addElement(u);
                                        }
                                    }
                                }
                            });
                        } else if ("CHAT".equals(type)) {
                            logChat("Cloud", content);
                        } else if ("ERROR".equals(type)) {
                            logError(content);
                        } else {
                            logSystem(content);
                            if ("JOIN".equals(type) || "LEAVE".equals(type)) {
                                refreshUserList(); 
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logError("[CLOUD] Mất kết nối đến Cloud: " + e.getMessage());
            }
        }).start();
    }

    /** Được gọi từ Server.start() để cấp quyền admin cho GUI (Chỉ dùng khi chạy local) */
    public void setServer(Server server) {
        this.server = server;
    }

    // ══════════════════════════════════════════════════════════════
    //  Khởi tạo Frame
    // ══════════════════════════════════════════════════════════════
    private void initFrame() {
        setTitle("Chat Server — Bảng Điều Khiển");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1150, 780);
        setMinimumSize(new Dimension(950, 650));
        setLocationRelativeTo(null);
        setBackground(BG_DARK);

        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}

        UIManager.put("ScrollBar.thumb",          BG_INPUT);
        UIManager.put("ScrollBar.track",          BG_PANEL);
        UIManager.put("ScrollBar.thumbHighlight", ACCENT_BLUE);
        UIManager.put("ScrollBar.width",          8);
    }

    // ══════════════════════════════════════════════════════════════
    //  Xây dựng UI
    // ══════════════════════════════════════════════════════════════
    private void initComponents() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARK);

        root.add(buildHeader(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLogPanel(), buildSidebarScrollPane());
        split.setDividerLocation(720);
        split.setDividerSize(4);
        split.setBorder(null);
        split.setBackground(BG_DARK);
        split.setResizeWeight(0.72);
        root.add(split, BorderLayout.CENTER);

        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);
    }

    // ──────────────────────────────────────────────
    //  Header
    // ──────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        header.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(14, 24, 14, 24)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);

        JLabel icon = new JLabel("⚡");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
        icon.setForeground(ACCENT_BLUE);

        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 2));
        titlePanel.setOpaque(false);
        JLabel title    = new JLabel("CHAT SERVER");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(TEXT_PRIMARY);
        JLabel subtitle = new JLabel("Bảng Điều Khiển Quản Trị");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subtitle.setForeground(TEXT_SECONDARY);
        titlePanel.add(title);
        titlePanel.add(subtitle);

        left.add(icon);
        left.add(titlePanel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        lblServerStatus = createStatusBadge("● ĐANG CHẠY", ACCENT_GREEN);
        lblPort = new JLabel("Cổng: 5000");
        lblPort.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblPort.setForeground(TEXT_SECONDARY);
        right.add(lblPort);
        right.add(lblServerStatus);

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // ──────────────────────────────────────────────
    //  Log panel (trái)
    // ──────────────────────────────────────────────
    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(16, 20, 16, 10));

        JPanel sectionHeader = new JPanel(new BorderLayout());
        sectionHeader.setOpaque(false);
        sectionHeader.setBorder(new EmptyBorder(0, 0, 10, 0));
        JLabel sectionTitle = new JLabel("📋  Nhật Ký Hoạt Động");
        sectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sectionTitle.setForeground(TEXT_PRIMARY);
        JButton btnClear = createSmallButton("Xóa Log");
        btnClear.addActionListener(e -> clearLog());
        sectionHeader.add(sectionTitle, BorderLayout.WEST);
        sectionHeader.add(btnClear,    BorderLayout.EAST);

        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(BG_CARD);
        logPane.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));
        logPane.setBorder(new EmptyBorder(12, 14, 12, 14));
        logDoc = logPane.getStyledDocument();

        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(new LineBorder(BORDER_COLOR, 1));
        scroll.setBackground(BG_CARD);
        scroll.getViewport().setBackground(BG_CARD);
        styleScrollBar(scroll.getVerticalScrollBar());
        styleScrollBar(scroll.getHorizontalScrollBar());

        panel.add(sectionHeader, BorderLayout.NORTH);
        panel.add(scroll,        BorderLayout.CENTER);
        return panel;
    }

    // ──────────────────────────────────────────────
    //  Sidebar có thể cuộn (phải)
    // ──────────────────────────────────────────────
    private JScrollPane buildSidebarScrollPane() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(BG_DARK);
        sidebar.setBorder(new EmptyBorder(16, 10, 16, 20));

        sidebar.add(buildStatsCard());
        sidebar.add(Box.createVerticalStrut(14));
        sidebar.add(buildUserListCard());        // có nút Kick
        sidebar.add(Box.createVerticalStrut(14));
        sidebar.add(buildRoomManagementCard());  // Quản lý phòng
        sidebar.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(sidebar);
        scroll.setBorder(null);
        scroll.setBackground(BG_DARK);
        scroll.getViewport().setBackground(BG_DARK);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        styleScrollBar(scroll.getVerticalScrollBar());
        return scroll;
    }

    // ──────────────────────────────────────────────
    //  Thẻ thống kê
    // ──────────────────────────────────────────────
    private JPanel buildStatsCard() {
        JPanel card = buildCard("📊  Thống Kê");
        JPanel grid = new JPanel(new GridLayout(3, 1, 0, 8));
        grid.setOpaque(false);
        lblOnlineCount   = buildStatRow(grid, "👥  Đang Online",   "0", ACCENT_GREEN);
        lblTotalMessages = buildStatRow(grid, "💬  Tổng Tin Nhắn", "0", ACCENT_BLUE);
        lblTotalJoins    = buildStatRow(grid, "🚀  Lượt Kết Nối",  "0", ACCENT_ORANGE);
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private JLabel buildStatRow(JPanel parent, String label, String value, Color valueColor) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(6, 0, 6, 0));
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(TEXT_SECONDARY);
        JLabel val = new JLabel(value);
        val.setFont(new Font("Segoe UI", Font.BOLD, 22));
        val.setForeground(valueColor);
        val.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.EAST);
        parent.add(row);
        return val;
    }

    // ──────────────────────────────────────────────
    //  Thẻ danh sách user + nút Kick
    // ──────────────────────────────────────────────
    private JPanel buildUserListCard() {
        JPanel card = buildCard("🟢  Người Dùng Online");

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setBackground(BG_INPUT);
        userList.setForeground(TEXT_PRIMARY);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userList.setSelectionBackground(ACCENT_BLUE.darker());
        userList.setSelectionForeground(Color.WHITE);
        userList.setBorder(new EmptyBorder(4, 8, 4, 8));
        userList.setCellRenderer(new UserListCellRenderer());
        userList.setFixedCellHeight(38);

        JScrollPane scroll = new JScrollPane(userList);
        scroll.setBorder(new LineBorder(BORDER_COLOR, 1));
        scroll.setBackground(BG_INPUT);
        scroll.getViewport().setBackground(BG_INPUT);
        scroll.setPreferredSize(new Dimension(280, 180));
        styleScrollBar(scroll.getVerticalScrollBar());

        // Nút kick
        JButton btnKick = createActionButton("⚡  Kick User", ACCENT_RED);
        btnKick.addActionListener(e -> performKick());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        btnPanel.setOpaque(false);
        btnPanel.add(btnKick);

        card.add(scroll,   BorderLayout.CENTER);
        card.add(btnPanel, BorderLayout.SOUTH);
        return card;
    }

    // ──────────────────────────────────────────────
    //  Thẻ quản lý phòng (MỚI)
    // ──────────────────────────────────────────────
    private JPanel buildRoomManagementCard() {
        JPanel card = buildCard("🚪  Quản Lý Phòng");

        // Table model, không cho chỉnh sửa trực tiếp
        String[] cols = {"ID", "Tên Phòng", "Người", "Giới Hạn"};
        roomTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        roomTable = new JTable(roomTableModel);
        roomTable.setBackground(BG_INPUT);
        roomTable.setForeground(TEXT_PRIMARY);
        roomTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        roomTable.setRowHeight(30);
        roomTable.setShowGrid(false);
        roomTable.setIntercellSpacing(new Dimension(0, 2));
        roomTable.setSelectionBackground(ACCENT_BLUE.darker());
        roomTable.setSelectionForeground(Color.WHITE);
        roomTable.setFillsViewportHeight(true);
        roomTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Độ rộng cột
        TableColumnModel colModel = roomTable.getColumnModel();
        colModel.getColumn(0).setPreferredWidth(28);  colModel.getColumn(0).setMaxWidth(40);
        colModel.getColumn(1).setPreferredWidth(120);
        colModel.getColumn(2).setPreferredWidth(45);  colModel.getColumn(2).setMaxWidth(55);
        colModel.getColumn(3).setPreferredWidth(65);  colModel.getColumn(3).setMaxWidth(80);

        // Header style
        JTableHeader tableHeader = roomTable.getTableHeader();
        tableHeader.setBackground(BG_PANEL);
        tableHeader.setForeground(TEXT_SECONDARY);
        tableHeader.setFont(new Font("Segoe UI", Font.BOLD, 11));
        tableHeader.setBorder(new MatteBorder(0, 0, 1, 0, BORDER_COLOR));
        tableHeader.setReorderingAllowed(false);
        tableHeader.setResizingAllowed(false);

        // Double-click để sửa
        roomTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) showEditRoomDialog();
            }
        });

        JScrollPane scroll = new JScrollPane(roomTable);
        scroll.setBorder(new LineBorder(BORDER_COLOR, 1));
        scroll.setBackground(BG_INPUT);
        scroll.getViewport().setBackground(BG_INPUT);
        scroll.setPreferredSize(new Dimension(280, 165));
        styleScrollBar(scroll.getVerticalScrollBar());

        // Nút hành động
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        btnPanel.setOpaque(false);

        JButton btnRefresh = createSmallButton("🔄 Làm mới");
        btnRefresh.addActionListener(e -> refreshRoomTable());

        JButton btnCreate = createActionButton("＋ Tạo", ACCENT_GREEN);
        btnCreate.addActionListener(e -> showCreateRoomDialog());

        JButton btnEdit = createActionButton("✏ Sửa", ACCENT_BLUE);
        btnEdit.addActionListener(e -> showEditRoomDialog());

        btnPanel.add(btnRefresh);
        btnPanel.add(btnCreate);
        btnPanel.add(btnEdit);

        card.add(scroll,   BorderLayout.CENTER);
        card.add(btnPanel, BorderLayout.SOUTH);
        return card;
    }

    // ──────────────────────────────────────────────
    //  Footer
    // ──────────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(BG_PANEL);
        footer.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, BORDER_COLOR),
                new EmptyBorder(8, 24, 8, 24)));

        JLabel left = new JLabel("TCP Chat Server v1.0  —  Java " + System.getProperty("java.version"));
        left.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        left.setForeground(TEXT_MUTED);

        JLabel right = new JLabel("Ctrl+L: xóa log  |  Double-click phòng để sửa");
        right.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        right.setForeground(TEXT_MUTED);

        footer.add(left,  BorderLayout.WEST);
        footer.add(right, BorderLayout.EAST);

        getRootPane().registerKeyboardAction(
                e -> clearLog(),
                KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        return footer;
    }

    // ══════════════════════════════════════════════════════════════
    //  HÀNH ĐỘNG ADMIN
    // ══════════════════════════════════════════════════════════════

    /** Kick user được chọn trong danh sách bằng cách đánh dấu DB vào trạng thái KICKED */
    private void performKick() {
        String selected = userList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn một user trong danh sách để kick!",
                    "Chưa chọn user", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn kick \"" + selected + "\" khỏi server?",
                "⚠  Xác nhận Kick", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            if (server != null) {
                server.kickUser(selected); // Nếu Cloud chia sẻ JVM (không áp dụng khi Console mode)
            }
            if (adminOut != null) {
                Chat.server.model.Message kickMsg = new Chat.server.model.Message("ADMIN", selected, Chat.server.model.Message.Type.ADMIN_KICK);
                adminOut.println(kickMsg.toNetworkString());
            }
            // Đánh dấu DB KICKED
            String sql = "UPDATE User SET trangThai = 'KICKED' WHERE tenUser = ?";
            try (java.sql.Connection conn = Chat.Dao.DBContext.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, selected);
                ps.executeUpdate();
                logSystem("🔧 Yêu cầu kick: " + selected + " (Cloud Server sẽ xử lý)");
            } catch (Exception e) {
                logError("Lỗi DB khi kick: " + e.getMessage());
            }
        }
    }

    /** Lấy danh sách phòng từ DB */
    public void refreshRoomTable() {
        if (roomTableModel == null) return;
        new Thread(() -> {
            List<Object[]> rooms = new java.util.ArrayList<>();
            String sql = "SELECT maRoom, tenRoom, soLuongHienTai, COALESCE(gioiHan, 999) FROM Room ORDER BY maRoom";
            try (java.sql.Connection conn = Chat.Dao.DBContext.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(sql);
                 java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rooms.add(new Object[]{rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4)});
                }
            } catch (Exception e) {}
            
            SwingUtilities.invokeLater(() -> {
                roomTableModel.setRowCount(0);
                for (Object[] row : rooms) roomTableModel.addRow(row);
            });
        }).start();
    }

    /** Tải lại danh sách users online từ DB */
    public void refreshUserList() {
        if (userListModel == null) return;
        new Thread(() -> {
            Set<String> onlineUsers = new java.util.LinkedHashSet<>();
            String sql = "SELECT tenUser FROM User WHERE trangThai = 'ONLINE'";
            try (java.sql.Connection conn = Chat.Dao.DBContext.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(sql);
                 java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    onlineUsers.add(rs.getString(1));
                }
            } catch (Exception e) {
                // Ignore
            }
            
            SwingUtilities.invokeLater(() -> {
                userListModel.clear();
                for (String username : onlineUsers) {
                    userListModel.addElement(username);
                }
                lblOnlineCount.setText(String.valueOf(onlineUsers.size()));
            });
        }).start();
    }

    /** Dialog tạo phòng mới */
    private void showCreateRoomDialog() {
        JTextField txtName  = createDialogField("Phòng Mới");
        JSpinner   spinLimit = new JSpinner(new SpinnerNumberModel(50, 2, 500, 1));
        styleSpinner(spinLimit);

        JPanel panel = buildDialogPanel(txtName, spinLimit);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Tao Phong Moi", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = txtName.getText().trim();
            int    limit = (int) spinLimit.getValue();
            if (!name.isEmpty()) {
                // Ưu tiên dùng local server, nếu không có thì gửi qua TCP
                if (server != null) {
                    if (server.createRoom(name, limit)) refreshRoomTable();
                    else showError("Tao phong that bai! Xem log de biet them.");
                } else if (adminOut != null) {
                    // Gửi lệnh tạo phòng qua TCP đến cloud server
                    Chat.server.model.Message cmd = new Chat.server.model.Message(
                        "ADMIN", name + "|" + limit,
                        Chat.server.model.Message.Type.ADMIN_CREATE_ROOM);
                    adminOut.println(cmd.toNetworkString());
                    logSystem("[CLOUD] Da gui lenh tao phong: " + name);
                    // Làm mới bảng sau 1 giây
                    new javax.swing.Timer(1000, e -> refreshRoomTable()).start();
                } else {
                    showError("Chua ket noi den server!");
                }
            }
        }
    }

    /** Dialog sửa phòng đang chọn trong bảng */
    private void showEditRoomDialog() {
        int row = roomTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "Vui long chon mot phong trong bang de sua!",
                    "Chua chon phong", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int    roomId   = (int)    roomTableModel.getValueAt(row, 0);
        String curName  = (String) roomTableModel.getValueAt(row, 1);
        int    curLimit = (int)    roomTableModel.getValueAt(row, 3);

        JTextField txtName   = createDialogField(curName);
        JSpinner   spinLimit = new JSpinner(new SpinnerNumberModel(curLimit, 2, 500, 1));
        styleSpinner(spinLimit);

        JPanel panel = buildDialogPanel(txtName, spinLimit);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Sua Phong [ID = " + roomId + "]",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name  = txtName.getText().trim();
            int    limit = (int) spinLimit.getValue();
            if (!name.isEmpty()) {
                if (server != null) {
                    if (server.editRoom(roomId, name, limit)) refreshRoomTable();
                    else showError("Sua phong that bai! Xem log de biet them.");
                } else if (adminOut != null) {
                    // Gửi lệnh sửa phòng qua TCP đến cloud server
                    Chat.server.model.Message cmd = new Chat.server.model.Message(
                        "ADMIN", roomId + "|" + name + "|" + limit,
                        Chat.server.model.Message.Type.ADMIN_EDIT_ROOM);
                    adminOut.println(cmd.toNetworkString());
                    logSystem("[CLOUD] Da gui lenh sua phong ID=" + roomId + " -> " + name);
                    new javax.swing.Timer(1000, e -> refreshRoomTable()).start();
                } else {
                    showError("Chua ket noi den server!");
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PUBLIC API — được gọi từ Server / ClientHandler
    // ══════════════════════════════════════════════════════════════

    public void logJoin(String username, String ip) {
        totalJoins.incrementAndGet();
        SwingUtilities.invokeLater(() -> {
            appendLog("JOIN", username + " đã kết nối  (" + ip + ")", ACCENT_GREEN);
            addUserToList(username);
            refreshUserList();  // Cập nhật ngay
            updateStats();
        });
    }

    public void logLeave(String username, int remaining) {
        SwingUtilities.invokeLater(() -> {
            appendLog("RỜI", username + " đã rời đi  (" + remaining + " người còn lại)", ACCENT_ORANGE);
            removeUserFromList(username);
            refreshUserList();  // Cập nhật ngay
            updateStats();
        });
    }

    public void logChat(String sender, String content) {
        totalMessages.incrementAndGet();
        SwingUtilities.invokeLater(() -> {
            appendLog("CHAT", "[" + sender + "]  " + content, ACCENT_BLUE);
            updateStats();
        });
    }

    public void logError(String message) {
        SwingUtilities.invokeLater(() -> appendLog("LỖI", message, ACCENT_RED));
    }

    public void logSystem(String message) {
        SwingUtilities.invokeLater(() -> appendLog("SYS", message, TEXT_SECONDARY));
    }

    public void setServerStatus(boolean running, int port) {
        SwingUtilities.invokeLater(() -> {
            if (running) {
                lblServerStatus.setText("● ĐANG CHẠY");
                lblServerStatus.setForeground(ACCENT_GREEN);
                lblPort.setText("Cổng: " + port);
            } else {
                lblServerStatus.setText("● ĐÃ DỪNG");
                lblServerStatus.setForeground(ACCENT_RED);
            }
        });
    }

    // ✅ Cập nhật số user online từ Server
    public void updateOnlineUserCount() {
        SwingUtilities.invokeLater(() -> {
            Map<Integer, List<ClientHandler>> rooms = Server.getRoomGroups();
            int totalOnline = 0;
            if (rooms != null) {
                for (List<ClientHandler> members : rooms.values()) {
                    if (members != null) {
                        synchronized (members) {
                            totalOnline += members.size();
                        }
                    }
                }
            }
            lblOnlineCount.setText(totalOnline + " người");
            lblOnlineCount.setForeground(ACCENT_GREEN);
        });
    }

    // ✅ Log tin nhắn chi tiết từ từng client
    public void logMessageDetail(String sender, String room, String content) {
        totalMessages.incrementAndGet();
        SwingUtilities.invokeLater(() -> {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String message = String.format("[%s] 💬 %s (Phòng %s): %s\n", 
                    timestamp, sender, room, content);
                
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setForeground(attrs, ACCENT_BLUE);
                StyleConstants.setFontFamily(attrs, "JetBrains Mono");
                StyleConstants.setFontSize(attrs, 13);
                logDoc.insertString(logDoc.getLength(), message, attrs);
                
                logPane.setCaretPosition(logDoc.getLength());
                updateStats();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  Private helpers
    // ══════════════════════════════════════════════════════════════

    private void addUserToList(String username) {
        if (!userListModel.contains(username)) userListModel.addElement(username);
        lblOnlineCount.setText(String.valueOf(userListModel.size()));
    }

    private void removeUserFromList(String username) {
        userListModel.removeElement(username);
        lblOnlineCount.setText(String.valueOf(userListModel.size()));
    }

    private void updateStats() {
        lblTotalMessages.setText(String.valueOf(totalMessages.get()));
        lblTotalJoins.setText(String.valueOf(totalJoins.get()));
        lblOnlineCount.setText(String.valueOf(userListModel.size()));
    }

    private void clearLog() {
        try {
            logDoc.remove(0, logDoc.getLength());
            appendLog("SYS", "Log đã được xóa", TEXT_MUTED);
        } catch (BadLocationException ignored) {}
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    private void appendLog(String tag, String message, Color tagColor) {
        try {
            String time = LocalDateTime.now().format(TIME_FMT);

            SimpleAttributeSet timeStyle = new SimpleAttributeSet();
            StyleConstants.setForeground(timeStyle, TEXT_MUTED);
            StyleConstants.setFontFamily(timeStyle, "JetBrains Mono");
            StyleConstants.setFontSize(timeStyle, 12);
            logDoc.insertString(logDoc.getLength(), time + "  ", timeStyle);

            SimpleAttributeSet tagStyle = new SimpleAttributeSet();
            StyleConstants.setForeground(tagStyle, tagColor);
            StyleConstants.setBold(tagStyle, true);
            StyleConstants.setFontFamily(tagStyle, "JetBrains Mono");
            StyleConstants.setFontSize(tagStyle, 12);
            logDoc.insertString(logDoc.getLength(),
                    "[" + String.format("%-5s", tag) + "]  ", tagStyle);

            SimpleAttributeSet msgStyle = new SimpleAttributeSet();
            StyleConstants.setForeground(msgStyle, TEXT_PRIMARY);
            StyleConstants.setFontFamily(msgStyle, "JetBrains Mono");
            StyleConstants.setFontSize(msgStyle, 12);
            logDoc.insertString(logDoc.getLength(), message + "\n", msgStyle);

            logPane.setCaretPosition(logDoc.getLength());
        } catch (BadLocationException ignored) {}
    }

    // ── Dialog helpers ────────────────────────────────────────────

    private JPanel buildDialogPanel(JTextField nameField, JSpinner limitSpinner) {
        JPanel p = new JPanel(new GridLayout(2, 2, 10, 12));
        p.setBackground(BG_CARD);
        p.setBorder(new EmptyBorder(12, 4, 8, 4));
        p.add(dialogLabel("Tên phòng:"));
        p.add(nameField);
        p.add(dialogLabel("Giới hạn người:"));
        p.add(limitSpinner);
        return p;
    }

    private JLabel dialogLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_PRIMARY);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return l;
    }

    private JTextField createDialogField(String value) {
        JTextField f = new JTextField(value, 14);
        f.setBackground(BG_INPUT);
        f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(TEXT_PRIMARY);
        f.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1), new EmptyBorder(4, 6, 4, 6)));
        return f;
    }

    private void styleSpinner(JSpinner sp) {
        sp.setBackground(BG_INPUT);
        JComponent ed = sp.getEditor();
        if (ed instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
            tf.setBackground(BG_INPUT);
            tf.setForeground(TEXT_PRIMARY);
            tf.setCaretColor(TEXT_PRIMARY);
        }
    }

    // ── UI component factories ────────────────────────────────────

    private JPanel buildCard(String title) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(BG_CARD);
        card.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(14, 16, 14, 16)));
        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(TEXT_PRIMARY);
        lbl.setBorder(new EmptyBorder(0, 0, 12, 0));
        card.add(lbl, BorderLayout.NORTH);
        return card;
    }

    private JLabel createStatusBadge(String text, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(color);
        lbl.setBorder(new CompoundBorder(
                new LineBorder(color.darker(), 1),
                new EmptyBorder(4, 10, 4, 10)));
        lbl.setOpaque(true);
        lbl.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 25));
        return lbl;
    }

    private JButton createSmallButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setForeground(TEXT_SECONDARY);
        btn.setBackground(BG_INPUT);
        btn.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(4, 10, 4, 10)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(BG_CARD); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(BG_INPUT); }
        });
        return btn;
    }

    private JButton createActionButton(String text, Color color) {
        Color bg = new Color(color.getRed(), color.getGreen(), color.getBlue(), 180);
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setBorder(new CompoundBorder(
                new LineBorder(color.darker(), 1),
                new EmptyBorder(5, 12, 5, 12)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(color); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(bg); }
        });
        return btn;
    }

    private void styleScrollBar(JScrollBar bar) {
        bar.setBackground(BG_PANEL);
        bar.setPreferredSize(new Dimension(6, 6));
        bar.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = new Color(60, 70, 100);
                trackColor = BG_PANEL;
            }
            @Override protected JButton createDecreaseButton(int o) { return zeroBtn(); }
            @Override protected JButton createIncreaseButton(int o) { return zeroBtn(); }
            private JButton zeroBtn() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
        });
    }

    // ── Custom cell renderer ──────────────────────────────────────
    private class UserListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            row.setOpaque(true);
            row.setBackground(isSelected ? ACCENT_BLUE.darker() : BG_INPUT);

            String name    = value.toString();
            String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();

            JLabel avatar = new JLabel(initial) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int hue = Math.abs(name.hashCode()) % 360;
                    g2.setColor(Color.getHSBColor(hue / 360f, 0.6f, 0.75f));
                    g2.fillOval(0, 0, getWidth(), getHeight());
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(initial,
                            (getWidth()  - fm.stringWidth(initial)) / 2,
                            (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    g2.dispose();
                }
            };
            avatar.setPreferredSize(new Dimension(28, 28));

            JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            namePanel.setOpaque(false);
            JLabel dot = new JLabel("●");
            dot.setFont(new Font("Segoe UI", Font.PLAIN, 8));
            dot.setForeground(ACCENT_GREEN);
            JLabel nameLbl = new JLabel(name);
            nameLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            nameLbl.setForeground(TEXT_PRIMARY);
            namePanel.add(dot);
            namePanel.add(nameLbl);

            row.add(avatar);
            row.add(namePanel);
            return row;
        }
    }
}
