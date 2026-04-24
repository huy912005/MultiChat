package Chat.client.view;

import Chat.server.model.Message;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

public class ChatFrame extends JFrame {
    public static class AuthRequest {
        private final String username;
        private final String password;
        private final boolean registerMode;

        public AuthRequest(String username, String password, boolean registerMode) {
            this.username = username;
            this.password = password;
            this.registerMode = registerMode;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public boolean isRegisterMode() {
            return registerMode;
        }
    }


    // ===== MÀU SẮC THEME =====
    private static final Color COLOR_BG_MAIN = new Color(0xF0F2F5); // Nền chính (xám nhạt)
    private static final Color COLOR_BG_SIDEBAR = new Color(0xFFFFFF); // Nền sidebar (trắng)
    private static final Color COLOR_BG_CHAT = new Color(0xF0F2F5); // Nền khu chat
    private static final Color COLOR_BG_HEADER = new Color(0x0A66C2); // Header (xanh đậm)
    private static final Color COLOR_BG_INPUT = new Color(0xFFFFFF); // Nền input
    private static final Color COLOR_BUBBLE_MINE = new Color(0x0084FF); // Bubble tin nhắn của mình (xanh)
    private static final Color COLOR_BUBBLE_OTHER = new Color(0xE4E6EB); // Bubble tin nhắn người khác (xám)
    private static final Color COLOR_TEXT_WHITE = new Color(0xFFFFFF); // Chữ trắng
    private static final Color COLOR_TEXT_DARK = new Color(0x1C1E21); // Chữ đậm
    private static final Color COLOR_TEXT_GRAY = new Color(0x65676B); // Chữ xám
    private static final Color COLOR_TEXT_MINE = new Color(0xFFFFFF); // Chữ trong bubble của mình
    private static final Color COLOR_ONLINE_DOT = new Color(0x44B700); // Chấm online (xanh lá)
    private static final Color COLOR_ACCENT = new Color(0x0084FF); // Màu nhấn
    private static final Color COLOR_SEND_BTN = new Color(0x0084FF); // Nút gửi
    private static final Color COLOR_SEND_HOVER = new Color(0x0066CC); // Nút gửi khi hover
    private static final Color COLOR_SIDEBAR_DIVIDER = new Color(0xE4E6EB); // Đường chia sidebar
    private static final Color COLOR_SYSTEM_MSG = new Color(0x888888); // Màu tin nhắn hệ thống

    // ===== FONT =====
    private static final Font FONT_HEADER_TITLE = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font FONT_HEADER_SUB = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_SIDEBAR_TITLE = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_USERNAME = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font FONT_MESSAGE = new Font("Dialog", Font.PLAIN, 14);
    private static final Font FONT_TIMESTAMP = new Font("Segoe UI", Font.PLAIN, 10);
    private static final Font FONT_INPUT = new Font("Dialog", Font.PLAIN, 14);
    private static final Font FONT_SEND_BTN = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_EMOJI_BTN = new Font("Segoe UI", Font.PLAIN, 16);
    private static final Font FONT_AVATAR = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_ONLINE_COUNT = new Font("Segoe UI", Font.BOLD, 12);

    // ===== COMPONENTS =====
    private JPanel chatPanel; // Panel chứa tất cả bubble tin nhắn
    private JScrollPane chatScroll; // Scroll pane cho chat
    private PlaceholderTextField inputField; // Ô nhập tin nhắn
    private JButton sendButton; // Nút gửi
    private JButton emojiButton; // Nút mở danh sách icon
    private JPanel userListPanel; // Panel danh sách người dùng
    private JPanel roomListPanel; // Panel danh sách phòng (MỚI)
    private JLabel onlineCountLabel; // Label số người online
    private JLabel headerStatusLabel; // Label trạng thái ở header
    private JLabel headerRoomLabel;   // Label tên phòng ở header (MỚI)
    private int currentRoomId = 1;    // Phòng đang chat (MỚI)
    private List<String[]> cachedRooms = new ArrayList<>(); // Cache để redraw highlight ngay khi đổi phòng

    // ===== LISTENER =====
    public interface ChatFrameListener {
        void onSendMessage(String text);
        void onConnect(String username);
        void onDisconnect();
        void onJoinRoom(int roomId);
        void onCreateRoom(String name, int limit);
        void onDeleteRoom(int roomId);
        void onSearchRoomCode(String code);
        void onKickMember(String username);
    }

    private ChatFrameListener frameListener;
    private String currentUsername;

    public ChatFrame() {
        initUI();
    }

    /**
     * Khởi tạo toàn bộ giao diện
     */
    private void initUI() {
        // Cài đặt Look and Feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Dùng mặc định nếu không được
        }

        // Cài đặt JFrame
        setTitle("[Chat] Chat App - Lap Trinh Mang");
        setSize(900, 650);
        setMinimumSize(new Dimension(700, 500));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);// căn giữa

        // Xử lý khi đóng cửa sổ
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (frameListener != null) {
                    frameListener.onDisconnect();
                }
            }
        });

        // Layout chính
        setLayout(new BorderLayout());
        getContentPane().setBackground(COLOR_BG_MAIN);

        // Tạo các panel
        add(createHeader(), BorderLayout.NORTH);
        add(createSidebar(), BorderLayout.WEST);
        add(createChatArea(), BorderLayout.CENTER);

        setVisible(true);
    }

    // ─────────────────────────────────────────────────────────────
    // HEADER
    // ─────────────────────────────────────────────────────────────
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(COLOR_BG_HEADER);
        header.setPreferredSize(new Dimension(0, 60));
        header.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftPanel.setOpaque(false);

        // Icon chat (khong dung [C] text)
        JLabel iconLabel = new JLabel("\uD83D\uDCAC"); // 💬 icon chat
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        iconLabel.setForeground(COLOR_TEXT_WHITE);

        JPanel titlePanel = new JPanel(new GridLayout(2, 1));
        titlePanel.setOpaque(false);

        // Đặt thành field để cập nhật khi vào phòng
        headerRoomLabel = new JLabel("Sảnh Chung");
        headerRoomLabel.setFont(FONT_HEADER_TITLE);
        headerRoomLabel.setForeground(COLOR_TEXT_WHITE);

        headerStatusLabel = new JLabel("● Đang kết nối...");
        headerStatusLabel.setFont(FONT_HEADER_SUB);
        headerStatusLabel.setForeground(new Color(0xBBDEFB));

        titlePanel.add(headerRoomLabel);
        titlePanel.add(headerStatusLabel);

        leftPanel.add(iconLabel);
        leftPanel.add(titlePanel);

        header.add(leftPanel, BorderLayout.WEST);

        // Nút ba chấm ở góc phải
        JButton moreBtn = new JButton("<html><b style='font-size:16px'>\u22EE</b></html>"); //  ⋮
        moreBtn.setOpaque(false);
        moreBtn.setContentAreaFilled(false);
        moreBtn.setBorder(null);
        moreBtn.setForeground(Color.WHITE);
        moreBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        moreBtn.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem leaveItem = new JMenuItem("Rời phòng");
            leaveItem.addActionListener(ev -> {
                if (frameListener != null) frameListener.onJoinRoom(1);
            });
            menu.add(leaveItem);
            menu.show(moreBtn, 0, moreBtn.getHeight());
        });
        header.add(moreBtn, BorderLayout.EAST);

        return header;
    }

    // ─────────────────────────────────────────────────────────────
    // SIDEBAR - Danh sách người dùng
    // ─────────────────────────────────────────────────────────────
    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(COLOR_BG_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(230, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, COLOR_SIDEBAR_DIVIDER));

        // Chia đôi sidebar: trên = danh sách phòng, dưới = thành viên
        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                createRoomListSection(),
                createMembersSection());
        split.setDividerLocation(240);
        split.setDividerSize(3);
        split.setBorder(null);
        split.setResizeWeight(0.4);

        sidebar.add(split, BorderLayout.CENTER);
        return sidebar;
    }

    /** Panel danh sach phong (phan tren sidebar) */
    private JPanel createRoomListSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COLOR_BG_SIDEBAR);

        // ── Header: chi co tieu de ──
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(COLOR_BG_SIDEBAR);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_SIDEBAR_DIVIDER),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        JLabel title = new JLabel("Phong Chat");
        title.setFont(FONT_SIDEBAR_TITLE);
        title.setForeground(COLOR_TEXT_DARK);
        header.add(title, BorderLayout.WEST);

        // ── Danh sach phong (giua) ──
        roomListPanel = new JPanel();
        roomListPanel.setLayout(new BoxLayout(roomListPanel, BoxLayout.Y_AXIS));
        roomListPanel.setBackground(COLOR_BG_SIDEBAR);
        JScrollPane scroll = new JScrollPane(roomListPanel);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        // ── Footer: o tim kiem + nut Tao phong ──
        JPanel footer = new JPanel(new BorderLayout(6, 0));
        footer.setBackground(COLOR_BG_SIDEBAR);
        footer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_SIDEBAR_DIVIDER),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        // O tim kiem co kieu search bar
        JPanel searchBox = new JPanel(new BorderLayout(4, 0));
        searchBox.setBackground(new Color(0xEAEBED));
        searchBox.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xCCD0D5), 1),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)));

        JLabel searchIcon = new JLabel("Q");
        searchIcon.setFont(new Font("Segoe UI", Font.BOLD, 11));
        searchIcon.setForeground(new Color(0x65676B));

        JTextField searchField = new JTextField();
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        searchField.setBorder(null);
        searchField.setBackground(new Color(0xEAEBED));
        searchField.setForeground(new Color(0x65676B));
        searchField.setText("Tim phong bang ma...");
        searchField.setForeground(new Color(0x999999));

        // Xoa placeholder khi focus
        searchField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                if (searchField.getText().equals("Tim phong bang ma...")) {
                    searchField.setText("");
                    searchField.setForeground(COLOR_TEXT_DARK);
                }
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                if (searchField.getText().isEmpty()) {
                    searchField.setText("Tim phong bang ma...");
                    searchField.setForeground(new Color(0x999999));
                }
            }
        });

        // Nhan Enter de tim
        searchField.addActionListener(e -> {
            String code = searchField.getText().trim().toUpperCase();
            if (code.length() == 5 && frameListener != null) {
                frameListener.onSearchRoomCode(code);
            } else if (!code.isEmpty() && !code.equals("TIM PHONG BANG MA...")) {
                addSystemMessage("Ma phong phai co dung 5 ky tu (VD: AB12C)");
            }
        });

        searchBox.add(searchIcon, BorderLayout.WEST);
        searchBox.add(searchField, BorderLayout.CENTER);

        // Nut Tao phong - full width, co mau nen ro rang
        JButton createBtn = new JButton("+ Tao phong moi") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed() ? new Color(0x0066CC)
                          : getModel().isRollover() ? new Color(0x0078FF)
                          : COLOR_ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        createBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        createBtn.setForeground(Color.WHITE);
        createBtn.setOpaque(false);
        createBtn.setContentAreaFilled(false);
        createBtn.setBorderPainted(false);
        createBtn.setFocusPainted(false);
        createBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        createBtn.setPreferredSize(new Dimension(0, 36));
        createBtn.addActionListener(e -> showCreateRoomDialog());

        footer.add(searchBox,  BorderLayout.CENTER);
        footer.add(createBtn,  BorderLayout.SOUTH);
        // Them khoang cach giua 2 phan tu
        JPanel footerInner = new JPanel(new BorderLayout(0, 6));
        footerInner.setOpaque(false);
        footerInner.add(searchBox, BorderLayout.NORTH);
        footerInner.add(createBtn, BorderLayout.SOUTH);
        footer.removeAll();
        footer.add(footerInner, BorderLayout.CENTER);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /** Hien thi dialog tao phong */
    private void showCreateRoomDialog() {
        JPanel p = new JPanel(new GridLayout(2, 2, 8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
        JTextField nameField = new JTextField("Phong cua toi", 16);
        JSpinner limitSpin = new JSpinner(new SpinnerNumberModel(20, 2, 200, 1));
        p.add(new JLabel("Ten phong:")); p.add(nameField);
        p.add(new JLabel("Gioi han nguoi:")); p.add(limitSpin);
        int r = JOptionPane.showConfirmDialog(this, p, "Tao phong moi",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r == JOptionPane.OK_OPTION && frameListener != null) {
            String name = nameField.getText().trim();
            if (!name.isEmpty()) frameListener.onCreateRoom(name, (int) limitSpin.getValue());
        }
    }

    /** Hien thi dialog tim kiem phong bang ma 5 ky tu */
    private void showSearchRoomDialog() {
        JTextField codeField = new JTextField(6);
        codeField.setFont(new Font("Monospaced", Font.BOLD, 16));
        JPanel p = new JPanel(new BorderLayout(8, 4));
        p.add(new JLabel("Nhap ma phong (5 ky tu):"), BorderLayout.NORTH);
        p.add(codeField, BorderLayout.CENTER);
        int r = JOptionPane.showConfirmDialog(this, p, "Tim kiem phong",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r == JOptionPane.OK_OPTION && frameListener != null) {
            String code = codeField.getText().trim().toUpperCase();
            if (code.length() == 5) frameListener.onSearchRoomCode(code);
            else addSystemMessage("Ma phong phai co dung 5 ky tu!");
        }
    }


    /** Panel danh sách thành viên (phần dưới sidebar) */
    private JPanel createMembersSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COLOR_BG_SIDEBAR);

        JPanel sidebarHeader = new JPanel(new BorderLayout());
        sidebarHeader.setBackground(COLOR_BG_SIDEBAR);
        sidebarHeader.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 16));

        JLabel membersLabel = new JLabel("Thành viên");
        membersLabel.setFont(FONT_SIDEBAR_TITLE);
        membersLabel.setForeground(COLOR_TEXT_DARK);

        onlineCountLabel = new JLabel("Đang online: 0");
        onlineCountLabel.setFont(FONT_ONLINE_COUNT);
        onlineCountLabel.setForeground(COLOR_ACCENT);

        sidebarHeader.add(membersLabel,    BorderLayout.NORTH);
        sidebarHeader.add(onlineCountLabel, BorderLayout.SOUTH);

        userListPanel = new JPanel();
        userListPanel.setLayout(new BoxLayout(userListPanel, BoxLayout.Y_AXIS));
        userListPanel.setBackground(COLOR_BG_SIDEBAR);
        userListPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));

        JScrollPane userScroll = new JScrollPane(userListPanel);
        userScroll.setBorder(null);
        userScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        userScroll.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(sidebarHeader, BorderLayout.NORTH);
        panel.add(userScroll,    BorderLayout.CENTER);
        return panel;
    }

    // ─────────────────────────────────────────────────────────────
    // CHAT AREA - Khu vực hiển thị tin nhắn + Input
    // ─────────────────────────────────────────────────────────────

    private JPanel createChatArea() {
        JPanel chatArea = new JPanel(new BorderLayout());
        chatArea.setBackground(COLOR_BG_CHAT);

        // Panel tin nhắn (scroll)
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(COLOR_BG_CHAT);
        chatPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        chatScroll = new JScrollPane(chatPanel);
        chatScroll.setBorder(null);
        chatScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        chatScroll.getVerticalScrollBar().setUnitIncrement(20);
        chatScroll.setBackground(COLOR_BG_CHAT);
        chatScroll.getViewport().setBackground(COLOR_BG_CHAT);

        // Input area
        JPanel inputArea = createInputArea();

        chatArea.add(chatScroll, BorderLayout.CENTER);
        chatArea.add(inputArea, BorderLayout.SOUTH);

        return chatArea;
    }

    private JPanel createInputArea() {
        JPanel inputWrapper = new JPanel(new BorderLayout());
        inputWrapper.setBackground(COLOR_BG_INPUT);
        inputWrapper.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_SIDEBAR_DIVIDER));

        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        inputPanel.setBackground(COLOR_BG_INPUT);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        // Ô nhập tin nhắn (có placeholder)
        inputField = new PlaceholderTextField();
        inputField.setFont(FONT_INPUT);
        inputField.setForeground(COLOR_TEXT_DARK);
        inputField.setBackground(new Color(0xF0F2F5));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(22, new Color(0xE4E6EB)),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        inputField.setPlaceholder("Nhập tin nhắn...");

        // Gửi bằng phím Enter
        inputField.addActionListener(e -> sendMessage());

        // Gợi ý placeholder
        inputField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                inputField.setBackground(Color.WHITE);
            }

            @Override
            public void focusLost(FocusEvent e) {
                inputField.setBackground(new Color(0xF0F2F5));
            }
        });

        // Nút gửi với hiệu ứng hover
        sendButton = createSendButton();
        emojiButton = createEmojiButton();

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(emojiButton);
        actionPanel.add(sendButton);

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(actionPanel, BorderLayout.EAST);

        inputWrapper.add(inputPanel);
        return inputWrapper;
    }

    private JButton createEmojiButton() {
        JButton btn = new JButton("\uD83D\uDE04"); // 😄
        btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        btn.setPreferredSize(new Dimension(44, 40));
        btn.setBorder(BorderFactory.createLineBorder(new Color(0xDADDE1)));
        btn.setBackground(Color.WHITE);
        btn.setForeground(new Color(0x666666));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> showEmojiPopup(btn));
        return btn;
    }

    private void showEmojiPopup(Component anchor) {
        // Real Unicode emoji - su dung Segoe UI Emoji font tren Windows
        final String[] emojis = {
            "\uD83D\uDC4B", // 👋 vẫy tay chào (MỚI - ĐƯA LÊN ĐẦU)
            "\uD83D\uDE04", // 😄 cuoi to
            "\uD83D\uDE02", // 😂 cuoi chay nuoc mat
            "\uD83E\uDD23", // 🤣 lan lon cuoi
            "\uD83D\uDE0A", // 😊 cuoi nhe
            "\uD83D\uDE0D", // 😍 yeu qua
            "\uD83D\uDE18", // 😘 hon gio
            "\uD83D\uDE0E", // 😎 ngau
            "\uD83E\uDD17", // 🤗 om
            "\uD83D\uDE22", // 😢 khoc
            "\uD83D\uDE21", // 😡 tuc gian
            "\uD83D\uDC4D", // 👍 like
            "\uD83D\uDC4E", // 👎 dislike
            "\u2764\uFE0F", // ❤️ tim do
            "\uD83D\uDC9C", // 💜 tim tim
            "\uD83D\uDC95", // 💕 hai tim
            "\uD83D\uDE4F", // 🙏 cam on
            "\uD83D\uDD25", // 🔥 fire
            "\uD83C\uDF89", // 🎉 bong bay
            "\uD83D\uDC4F", // 👏 vo tay
            "\u2705"        // ✅ check
        };

        JPopupMenu popup = new JPopupMenu();
        JPanel panel = new JPanel(new GridLayout(4, 5, 4, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.setBackground(Color.WHITE);

        Font emojiFont = new Font("Segoe UI Emoji", Font.PLAIN, 18); // Nhỏ lại một chút cho dễ nhìn
        for (String emoji : emojis) {
            JButton emojiItem = new JButton(emoji);
            emojiItem.setFont(emojiFont);
            emojiItem.setMargin(new Insets(2, 2, 2, 2));
            emojiItem.setFocusPainted(false);
            emojiItem.setBackground(new Color(0xF7F8FA));
            emojiItem.setBorder(BorderFactory.createLineBorder(new Color(0xE4E6EB)));
            emojiItem.setCursor(new Cursor(Cursor.HAND_CURSOR));
            emojiItem.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { emojiItem.setBackground(new Color(0xDCEEFE)); }
                @Override public void mouseExited(MouseEvent e)  { emojiItem.setBackground(new Color(0xF7F8FA)); }
            });
            emojiItem.addActionListener(e -> {
                int pos = inputField.getCaretPosition();
                String old = inputField.getText();
                String next = old.substring(0, pos) + emoji + old.substring(pos);
                inputField.setText(next);
                inputField.requestFocus();
                inputField.setCaretPosition(Math.min(pos + emoji.length(), next.length()));
                popup.setVisible(false);
            });
            panel.add(emojiItem);
        }

        popup.add(panel);
        popup.show(anchor, 0, -(panel.getPreferredSize().height + 20));
    }

    private JButton createSendButton() {
        JButton btn = new JButton("Gui") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bgColor = getModel().isRollover() ? COLOR_SEND_HOVER : COLOR_SEND_BTN;
                if (getModel().isPressed()) {
                    bgColor = new Color(0x004999);
                }

                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
                g2.dispose();
                super.paintComponent(g);
            }
        };

        btn.setFont(FONT_SEND_BTN);
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(90, 40));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Hiệu ứng hover
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.repaint();
            }
        });

        btn.addActionListener(e -> sendMessage());
        return btn;
    }

    // ─────────────────────────────────────────────────────────────
    // HIỂN THỊ TIN NHẮN BUBBLE
    // ─────────────────────────────────────────────────────────────

    // Chiều rộng tối đa của bubble tính bằng pixel (không kể padding)
    private static final int MAX_BUBBLE_WIDTH = 260;

    /**
     * Thêm tin nhắn chat dưới dạng bubble
     */
    public void addChatMessage(String sender, String content, String timestamp, boolean isMyMessage) {
        SwingUtilities.invokeLater(() -> {
            JPanel msgRow = new JPanel();
            msgRow.setLayout(new BoxLayout(msgRow, BoxLayout.X_AXIS));
            msgRow.setOpaque(false);
            msgRow.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
            msgRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

            if (isMyMessage) {
                msgRow.add(Box.createHorizontalGlue());
                msgRow.add(createMyBubble(content, timestamp));
            } else {
                msgRow.add(createAvatar(sender));
                msgRow.add(Box.createHorizontalStrut(8));
                msgRow.add(createOtherBubble(sender, content, timestamp));
                msgRow.add(Box.createHorizontalGlue());
            }

            chatPanel.add(msgRow);
            chatPanel.add(Box.createVerticalStrut(2));
            chatPanel.revalidate();
            scrollToBottom();
        });
    }

    /**
     * Tạo JTextArea trong suốt dùng làm nội dung bubble.
     * JTextArea hỗ trợ xuống dòng tự nhiên và tính height chính xác sau wrap.
     */
    private JTextArea makeContentArea(String content, Color fg) {
        JTextArea area = new JTextArea(content);
        area.setFont(FONT_MESSAGE);
        area.setForeground(fg);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(null);
        area.setCursor(Cursor.getDefaultCursor());
        return area;
    }

    /**
     * Đo kích thước chính xác mà bubble cần:
     * 1. Tắt lineWrap → đo chiều rộng tự nhiên (1 dòng thẳng, không wrap)
     * 2. Nếu vượt MAX_BUBBLE_WIDTH → cap lại 260px
     * 3. Bật wrap lại → đo chiều cao tại chiều rộng đã cap để lấy số dòng đúng
     *
     * Lý do PHẢI tắt wrap trước khi đo width:
     * JTextArea với lineWrap=true báo preferredWidth = chiều rộng component
     * hiện tại (KHÔNG phải chiều rộng tự nhiên của text) → đo sẽ sai.
     */
    private static Dimension measureTextSize(JTextArea area) {
        // Bước 1: Tắt wrap → preferredWidth = độ rộng thực của text (1 dòng)
        area.setLineWrap(false);
        area.setSize(new Dimension(10000, 10000));
        int naturalW = area.getPreferredSize().width;

        // Bước 2: Giới hạn tối đa MAX_BUBBLE_WIDTH
        int textW = Math.min(naturalW, MAX_BUBBLE_WIDTH);

        // Bước 3: Bật wrap lại → đo chiều cao chính xác tại textW
        area.setLineWrap(true);
        area.setSize(new Dimension(textW, 10000));
        int textH = area.getPreferredSize().height;

        return new Dimension(textW, textH);
    }

    /**
     * Cố định kích thước bubble = kích thước text + padding cứng (14px mỗi bên
     * ngang, 10px mỗi bên dọc)
     */
    private static void lockBubbleSize(JPanel bubble, JTextArea area) {
        Dimension text = measureTextSize(area);
        // Đặt lại preferred size của area để BorderLayout không co giãn sai
        area.setPreferredSize(text);
        area.setMinimumSize(text);
        area.setMaximumSize(text);
        // Bubble = text + padding (EmptyBorder: top=10 left=14 bot=10 right=14)
        Dimension bubbleSize = new Dimension(text.width + 28, text.height + 20);
        bubble.setPreferredSize(bubbleSize);
        bubble.setMinimumSize(bubbleSize);
        bubble.setMaximumSize(bubbleSize);
    }

    /** Bubble của MÌNH (xanh, căn phải) */
    private JPanel createMyBubble(String content, String timestamp) {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);

        JTextArea area = makeContentArea(content, COLOR_TEXT_MINE);

        JPanel bubble = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(COLOR_BUBBLE_MINE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setLayout(new BorderLayout());
        bubble.setOpaque(false);
        bubble.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        bubble.add(area, BorderLayout.CENTER);
        bubble.setAlignmentX(Component.RIGHT_ALIGNMENT);
        lockBubbleSize(bubble, area);

        JLabel timeLabel = new JLabel(timestamp);
        timeLabel.setFont(FONT_TIMESTAMP);
        timeLabel.setForeground(COLOR_TEXT_GRAY);
        timeLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        timeLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 4));

        wrapper.add(bubble);
        wrapper.add(timeLabel);
        return wrapper;
    }

    /** Bubble của NGƯỜI KHÁC (xám, căn trái) */
    private JPanel createOtherBubble(String sender, String content, String timestamp) {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);

        JLabel senderLabel = new JLabel(sender);
        senderLabel.setFont(FONT_USERNAME);
        senderLabel.setForeground(COLOR_ACCENT);
        senderLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 0));
        senderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea area = makeContentArea(content, COLOR_TEXT_DARK);

        JPanel bubble = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(COLOR_BUBBLE_OTHER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setLayout(new BorderLayout());
        bubble.setOpaque(false);
        bubble.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        bubble.add(area, BorderLayout.CENTER);
        bubble.setAlignmentX(Component.LEFT_ALIGNMENT);
        lockBubbleSize(bubble, area);

        JLabel timeLabel = new JLabel(timestamp);
        timeLabel.setFont(FONT_TIMESTAMP);
        timeLabel.setForeground(COLOR_TEXT_GRAY);
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        timeLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 0));

        wrapper.add(senderLabel);
        wrapper.add(bubble);
        wrapper.add(timeLabel);
        return wrapper;
    }

    /**
     * Tạo avatar chữ cái đầu của người gửi
     */
    private JPanel createAvatar(String name) {
        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Màu avatar dựa trên hash của tên
                Color[] colors = {
                        new Color(0xFF6B6B), new Color(0x4ECDC4), new Color(0x45B7D1),
                        new Color(0x96CEB4), new Color(0xFECA57), new Color(0xA29BFE),
                        new Color(0xFD79A8), new Color(0x00B894), new Color(0xE17055)
                };
                int colorIdx = Math.abs(name.hashCode()) % colors.length;

                g2.setColor(colors[colorIdx]);
                g2.fillOval(0, 0, getWidth(), getHeight());

                // Chữ cái đầu
                g2.setColor(Color.WHITE);
                g2.setFont(FONT_AVATAR);
                String letter = name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(letter)) / 2;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(letter, x, y);
                g2.dispose();
            }
        };
        avatar.setOpaque(false);
        avatar.setPreferredSize(new Dimension(36, 36));
        avatar.setMinimumSize(new Dimension(36, 36));
        avatar.setMaximumSize(new Dimension(36, 36));
        avatar.setAlignmentY(Component.TOP_ALIGNMENT);
        return avatar;
    }

    /**
     * Thêm tin nhắn hệ thống (join/leave/thông báo)
     */
    public void addSystemMessage(String content) {
        SwingUtilities.invokeLater(() -> {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

            // Dung HTML va chi dinh font ho tro emoji (Segoe UI Emoji)
            JLabel label = new JLabel("<html><body style='font-family: Segoe UI, Segoe UI Emoji, Dialog;'>" + content + "</body></html>") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0xE4E6EB));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            label.setFont(new Font("Dialog", Font.ITALIC, 12)); // Dung Dialog font de ho tro emoji tot hon
            label.setForeground(COLOR_SYSTEM_MSG);
            label.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
            label.setOpaque(false);

            row.add(label);
            chatPanel.add(row);
            chatPanel.add(Box.createVerticalStrut(4));
            chatPanel.revalidate();
            scrollToBottom();
        });
    }

    /**
     * Hien thi thong bao ma phong noi bat (sau khi tao phong thanh cong).
     * Ma phong duoc hien thi trong bang xanh la noi bat de nguoi tao de copy / chia se.
     */
    public void addRoomCodeMessage(String roomCode) {
        SwingUtilities.invokeLater(() -> {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

            // Card xanh la noi bat
            JPanel card = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Vien ngoai xanh la
                    g2.setColor(new Color(0x27AE60));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                    // Nen xanh la nhat
                    g2.setColor(new Color(0xE8F8F0));
                    g2.fillRoundRect(2, 2, getWidth()-4, getHeight()-4, 12, 12);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setOpaque(false);
            card.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
            card.setPreferredSize(new Dimension(440, 140));
            card.setMaximumSize(new Dimension(440, 140));
            card.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel titleLbl = new JLabel("<html><body style='font-family: Segoe UI, Segoe UI Emoji, Dialog;'>&#127881; Phong da duoc tao thanh cong!</body></html>");
            titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
            titleLbl.setForeground(new Color(0x1E8449));
            titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel codeLbl = new JLabel("<html>Mã mời bạn bè của bạn là : <b style='color:#27AE60; font-size:16px'>" + roomCode + "</b></html>");
            codeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            codeLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            codeLbl.setCursor(new Cursor(Cursor.HAND_CURSOR));
            codeLbl.setToolTipText("Click de sao chep ma");
            
            // Su kien click de copy ma
            codeLbl.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(roomCode);
                    java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(selection, selection);
                    JOptionPane.showMessageDialog(ChatFrame.this, "Da sao chep ma phong: " + roomCode);
                }
            });

            JLabel hintLbl = new JLabel("(Click vao ma o tren de sao chep)");
            hintLbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            hintLbl.setForeground(new Color(0x5D6D7E));
            hintLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            card.add(titleLbl);
            card.add(Box.createVerticalStrut(8));
            card.add(codeLbl);
            card.add(Box.createVerticalStrut(4));
            card.add(hintLbl);

            row.add(card);
            chatPanel.add(row);
            chatPanel.add(Box.createVerticalStrut(8));
            chatPanel.revalidate();
            scrollToBottom();
        });
    }


    // ─────────────────────────────────────────────────────────────
    // CẬP NHẬT SIDEBAR - Danh sách người dùng
    // ─────────────────────────────────────────────────────────────

    /**
     * Cập nhật danh sách user trong sidebar
     */
    public void updateUserList(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            userListPanel.removeAll();

            for (String user : users) {
                userListPanel.add(createUserItem(user));
                userListPanel.add(Box.createVerticalStrut(2));
            }

            // Cập nhật số người online
            onlineCountLabel.setText("Đang online: " + users.size());
            headerStatusLabel.setText("● " + users.size() + " người đang online");

            userListPanel.revalidate();
            userListPanel.repaint();
        });
    }

    /**
     * Tạo item người dùng trong sidebar
     */
    private JPanel createUserItem(String username) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        item.setBackground(COLOR_BG_SIDEBAR);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        item.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));

        // Avatar nhỏ
        JPanel miniAvatar = createMiniAvatar(username);

        // Tên + chấm xanh online
        JPanel namePanel = new JPanel(new BorderLayout(4, 0));
        namePanel.setOpaque(false);

        String displayName = username;
        boolean isOwner = false;
        if (username.endsWith("*")) {
            displayName = username.substring(0, username.length() - 1);
            isOwner = true;
        }

        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setFont(new Font("Segoe UI", isOwner ? Font.BOLD : Font.PLAIN, 13));
        nameLabel.setForeground(COLOR_TEXT_DARK);
        
        final String targetName = displayName; // Dung bien final de truy cap tu inner class

        if (isOwner) {
            JLabel starLabel = new JLabel(" \u2B50"); // ⭐
            starLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 10));
            namePanel.add(starLabel, BorderLayout.EAST);
        }

        JPanel onlineDotPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        onlineDotPanel.setOpaque(false);

        JLabel dotLabel = new JLabel("● Online");
        dotLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        dotLabel.setForeground(COLOR_ONLINE_DOT);
        
        onlineDotPanel.add(dotLabel);

        namePanel.add(nameLabel, BorderLayout.CENTER);
        namePanel.add(onlineDotPanel, BorderLayout.SOUTH);

        item.add(miniAvatar);
        item.add(namePanel);

        // Hiệu ứng hover
        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                item.setBackground(new Color(0xF0F2F5));
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3 && currentRoomId != 1) {
                    // Check if current user is owner (has * in the list)
                    boolean iAmOwner = false;
                    for (Component c : userListPanel.getComponents()) {
                        if (c instanceof JPanel) {
                            for (Component inner : ((JPanel)c).getComponents()) {
                                if (inner instanceof JPanel) { // namePanel
                                    for (Component n : ((JPanel)inner).getComponents()) {
                                        if (n instanceof JLabel && ((JLabel)n).getText().equals(currentUsername) 
                                            && ((JLabel)n).getFont().isBold()) {
                                            iAmOwner = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (iAmOwner && !targetName.equals(currentUsername)) {
                        JPopupMenu menu = new JPopupMenu();
                        JMenuItem kickItem = new JMenuItem("Kick khoi phong");
                        kickItem.setForeground(Color.RED);
                        kickItem.addActionListener(ev -> {
                            if (frameListener != null) {
                                frameListener.onKickMember(targetName);
                            }
                        });
                        menu.add(kickItem);
                        menu.show(item, e.getX(), e.getY());
                    }
                }
            }
        });

        return item;
    }

    /**
     * Tạo avatar thu nhỏ cho sidebar
     */
    private JPanel createMiniAvatar(String name) {
        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color[] colors = {
                        new Color(0xFF6B6B), new Color(0x4ECDC4), new Color(0x45B7D1),
                        new Color(0x96CEB4), new Color(0xFECA57), new Color(0xA29BFE),
                        new Color(0xFD79A8), new Color(0x00B894), new Color(0xE17055)
                };
                int colorIdx = Math.abs(name.hashCode()) % colors.length;

                g2.setColor(colors[colorIdx]);
                g2.fillOval(0, 0, getWidth(), getHeight());

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                String letter = name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(letter)) / 2;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(letter, x, y);
                g2.dispose();
            }
        };
        avatar.setOpaque(false);
        avatar.setPreferredSize(new Dimension(38, 38));
        avatar.setMinimumSize(new Dimension(38, 38));
        avatar.setMaximumSize(new Dimension(38, 38));
        return avatar;
    }

    // ─────────────────────────────────────────────────────────────
    // TIỆN ÍCH
    // ─────────────────────────────────────────────────────────────

    /**
     * Gửi tin nhắn (được gọi khi nhấn nút Gửi hoặc Enter)
     */
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && frameListener != null) {
            frameListener.onSendMessage(text);
            inputField.setText("");
        }
        inputField.requestFocus();
    }

    /**
     * Cuộn xuống cuối danh sách tin nhắn
     */
    public void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar scrollBar = chatScroll.getVerticalScrollBar();
            scrollBar.setValue(scrollBar.getMaximum());
        });
    }

    /**
     * Hien thi dialog dang nhap / dang ky chuyen nghiep
     * - Dang nhap: theme xanh duong
     * - Dang ky: theme xanh la
     */
    public AuthRequest showLoginDialog() {
        // Theme mau cho tung mode
        final Color COLOR_LOGIN_ACCENT  = new Color(0x0084FF); // Xanh duong
        final Color COLOR_REG_ACCENT    = new Color(0x27AE60); // Xanh la
        final Color COLOR_REG_HOVER     = new Color(0x1E8449); // Xanh la dam khi hover
        final Color COLOR_REG_PRESSED   = new Color(0x196F3D);

        final boolean[] registerMode = {false};
        final AuthRequest[] result    = {null};

        // ── Tao dialog ──
        JDialog dlg = new JDialog(this, "Chat App", true);
        dlg.setUndecorated(false);
        dlg.setSize(400, 500);
        dlg.setResizable(false);
        dlg.setLocationRelativeTo(this);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // ── Root panel: nen xam nhat ──
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(new Color(0xF0F2F5));
        dlg.setContentPane(root);

        // ── Card trang chinh giua ──
        JPanel card = new JPanel();
        card.setLayout(new BorderLayout(0, 0));
        card.setBackground(Color.WHITE);
        card.setPreferredSize(new Dimension(320, 420));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xDDDEE0), 1),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));

        // ── Header xanh (doi mau khi chuyen mode) ──
        JPanel headerPanel = new JPanel(new GridBagLayout());
        headerPanel.setBackground(COLOR_LOGIN_ACCENT);
        headerPanel.setPreferredSize(new Dimension(320, 70));

        JLabel appTitle = new JLabel("Chat App");
        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 26));
        appTitle.setForeground(Color.WHITE);
        headerPanel.add(appTitle);

        // ── Body form ──
        JPanel body = new JPanel();
        body.setLayout(new GridBagLayout());
        body.setBackground(Color.WHITE);
        body.setBorder(BorderFactory.createEmptyBorder(28, 32, 24, 32));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill      = GridBagConstraints.HORIZONTAL;
        gbc.weightx   = 1.0;
        gbc.gridx     = 0;
        gbc.insets    = new Insets(0, 0, 0, 0);

        // Mode title
        JLabel modeLbl = new JLabel("Dang Nhap");
        modeLbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        modeLbl.setForeground(new Color(0x1C1E21));
        modeLbl.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 20, 0);
        body.add(modeLbl, gbc);

        // Label: Ten tai khoan
        JLabel userLbl = new JLabel("Ten tai khoan");
        userLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        userLbl.setForeground(new Color(0x606770));
        gbc.gridy  = 1;
        gbc.insets = new Insets(0, 0, 4, 0);
        body.add(userLbl, gbc);

        // Input: Ten tai khoan
        JTextField userFld = new JTextField();
        userFld.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userFld.setPreferredSize(new Dimension(0, 42));
        userFld.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xCCD0D5), 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        userFld.setBackground(new Color(0xF5F6F7));
        gbc.gridy  = 2;
        gbc.insets = new Insets(0, 0, 16, 0);
        body.add(userFld, gbc);

        // Label: Mat khau
        JLabel passLbl = new JLabel("Mat khau");
        passLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        passLbl.setForeground(new Color(0x606770));
        gbc.gridy  = 3;
        gbc.insets = new Insets(0, 0, 4, 0);
        body.add(passLbl, gbc);

        // Input: Mat khau
        JPasswordField passFld = new JPasswordField();
        passFld.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        passFld.setPreferredSize(new Dimension(0, 42));
        passFld.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xCCD0D5), 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        passFld.setBackground(new Color(0xF5F6F7));
        gbc.gridy  = 4;
        gbc.insets = new Insets(0, 0, 24, 0);
        body.add(passFld, gbc);

        // Nut submit (mau thay doi theo mode)
        final Color[] btnColorHolder = { COLOR_LOGIN_ACCENT }; // dung array de lambda co the ghi
        JButton submitBtn = new JButton("DANG NHAP") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = btnColorHolder[0];
                Color hov  = registerMode[0] ? COLOR_REG_HOVER   : new Color(0x0066CC);
                Color prs  = registerMode[0] ? COLOR_REG_PRESSED : new Color(0x0050B3);
                Color bg = getModel().isPressed()  ? prs
                         : getModel().isRollover() ? hov
                         : base;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        submitBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        submitBtn.setForeground(Color.WHITE);
        submitBtn.setOpaque(false);
        submitBtn.setContentAreaFilled(false);
        submitBtn.setBorderPainted(false);
        submitBtn.setFocusPainted(false);
        submitBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        submitBtn.setPreferredSize(new Dimension(0, 46));
        gbc.gridy  = 5;
        gbc.insets = new Insets(0, 0, 16, 0);
        body.add(submitBtn, gbc);

        // Duong ke ngang
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(0xDDDEE0));
        gbc.gridy  = 6;
        gbc.insets = new Insets(0, 0, 16, 0);
        body.add(sep, gbc);

        // Link chuyen doi mode
        JLabel toggleLbl = new JLabel(
            "<html><div style='text-align:center'>Chua co tai khoan? " +
            "<span style='color:#0084FF'><u>Dang ky ngay</u></span></div></html>");
        toggleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        toggleLbl.setHorizontalAlignment(SwingConstants.CENTER);
        toggleLbl.setCursor(new Cursor(Cursor.HAND_CURSOR));
        gbc.gridy  = 7;
        gbc.insets = new Insets(0, 0, 0, 0);
        body.add(toggleLbl, gbc);

        // ── Toggle logic (thay doi mau theo mode) ──
        toggleLbl.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                registerMode[0] = !registerMode[0];
                if (registerMode[0]) {
                    // --- Che do DANG KY: xanh la ---
                    btnColorHolder[0] = COLOR_REG_ACCENT;
                    headerPanel.setBackground(COLOR_REG_ACCENT);
                    root.setBackground(new Color(0xE8F8F5));
                    card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(COLOR_REG_ACCENT, 1),
                        BorderFactory.createEmptyBorder(0, 0, 0, 0)));
                    modeLbl.setText("Dang Ky Tai Khoan");
                    modeLbl.setForeground(COLOR_REG_ACCENT);
                    submitBtn.setText("TAO TAI KHOAN");
                    submitBtn.repaint();
                    toggleLbl.setText("<html><div style='text-align:center'>Da co tai khoan? " +
                        "<span style='color:#27AE60'><u>Dang nhap</u></span></div></html>");
                    // border focus xanh la
                } else {
                    // --- Che do DANG NHAP: xanh duong ---
                    btnColorHolder[0] = COLOR_LOGIN_ACCENT;
                    headerPanel.setBackground(COLOR_LOGIN_ACCENT);
                    root.setBackground(new Color(0xF0F2F5));
                    card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0xDDDEE0), 1),
                        BorderFactory.createEmptyBorder(0, 0, 0, 0)));
                    modeLbl.setText("Dang Nhap");
                    modeLbl.setForeground(new Color(0x1C1E21));
                    submitBtn.setText("DANG NHAP");
                    submitBtn.repaint();
                    toggleLbl.setText("<html><div style='text-align:center'>Chua co tai khoan? " +
                        "<span style='color:#0084FF'><u>Dang ky ngay</u></span></div></html>");
                }
            }
        });

        // ── Submit logic ──
        java.awt.event.ActionListener doSubmit = ev -> {
            String u = userFld.getText().trim();
            String p = new String(passFld.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                JOptionPane.showMessageDialog(dlg,
                    "Vui long nhap day du thong tin!", "Thieu thong tin",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            result[0] = new AuthRequest(u, p, registerMode[0]);
            dlg.dispose();
        };
        submitBtn.addActionListener(doSubmit);
        passFld.addActionListener(doSubmit);
        userFld.addActionListener(ev -> passFld.requestFocus());

        // ── Hover: vien mau theo mode khi focus vao input ──
        java.awt.event.FocusAdapter inputFocus = new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                Color focusColor = registerMode[0] ? COLOR_REG_ACCENT : COLOR_LOGIN_ACCENT;
                ((JComponent) e.getSource()).setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(focusColor, 2),
                    BorderFactory.createEmptyBorder(7, 11, 7, 11)));
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                ((JComponent) e.getSource()).setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xCCD0D5), 1),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)));
            }
        };
        userFld.addFocusListener(inputFocus);
        passFld.addFocusListener(inputFocus);

        // ── Ghep card va hien thi ──
        card.add(headerPanel, BorderLayout.NORTH);
        card.add(body,        BorderLayout.CENTER);
        root.add(card);

        dlg.setVisible(true);
        return result[0];
    }


    /**
     * Hiển thị thông báo lỗi
     */
    public void showError(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
        });
    }

    /**
     * Cập nhật trạng thái kết nối ở header
     */
    public void setConnectionStatus(boolean connected, String username) {
        SwingUtilities.invokeLater(() -> {
            if (connected) {
                headerStatusLabel.setText("● Đã kết nối với tư cách: " + username);
                headerStatusLabel.setForeground(new Color(0xBBF0C0));
                setTitle("💬 Chat App - " + username);
            } else {
                headerStatusLabel.setText("● Đã ngắt kết nối");
                headerStatusLabel.setForeground(new Color(0xFFCDD2));
            }
        });
    }

    /** Khóa toàn bộ tính năng nhắn tin khi bị kick hoặc mất kết nối */
    public void disableChatUI() {
        SwingUtilities.invokeLater(() -> {
            if (inputField != null) inputField.setEnabled(false);
            if (sendButton != null) sendButton.setEnabled(false);
        });
    }

    // ─────────────────────────────────────────────────────────────
    // SETTER / GETTER
    // ─────────────────────────────────────────────────────────────

    public void setFrameListener(ChatFrameListener listener) {
        this.frameListener = listener;
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    // ─────────────────────────────────────────────────────────────
    // ROOM MANAGEMENT (MỚI)
    // ─────────────────────────────────────────────────────────────

    /**
     * Cập nhật danh sách phòng trên sidebar.
     * rooms: mỗi phần tử là String[] { id, name, currentUsers, limit }
     */
    public void updateRoomList(java.util.List<String[]> rooms) {
        SwingUtilities.invokeLater(() -> {
            cachedRooms = new ArrayList<>(rooms);
            renderRoomList();
        });
    }

    private void renderRoomList() {
        roomListPanel.removeAll();
        for (String[] room : cachedRooms) {
            try {
                int    id      = Integer.parseInt(room[0].trim());
                String name    = room[1].trim();
                int    current = Integer.parseInt(room[2].trim());
                int    limit   = Integer.parseInt(room[3].trim());
                roomListPanel.add(createRoomItem(id, name, current, limit));
            } catch (Exception ignored) {}
        }
        roomListPanel.revalidate();
        roomListPanel.repaint();
    }

    /** Tao mot hang phong trong sidebar */
    private JPanel createRoomItem(int roomId, String name, int currentUsers, int limit) {
        boolean isActive = (roomId == currentRoomId);
        Color   bgColor  = isActive ? new Color(0xDCEEFE) : COLOR_BG_SIDEBAR;

        JPanel item = new JPanel(new BorderLayout(8, 0));
        item.setBackground(bgColor);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        item.setCursor(isActive
                ? Cursor.getDefaultCursor()
                : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        item.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xF0F2F5)),
                BorderFactory.createEmptyBorder(10, 16, 10, 12)));

        // Icon phong: hinh tron mau ve bang code (khong dung emoji)
        Color[] roomColors = {
            new Color(0x4ECDC4), new Color(0xFF6B6B), new Color(0x45B7D1),
            new Color(0x96CEB4), new Color(0xFECA57), new Color(0xA29BFE),
            new Color(0xFD79A8), new Color(0x00B894), new Color(0xE17055)
        };
        Color roomColor = roomColors[Math.abs(name.hashCode()) % roomColors.length];

        JPanel iconPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isActive ? COLOR_ACCENT : roomColor);
                g2.fillRoundRect(2, 2, getWidth()-4, getHeight()-4, 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                String letter = name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(letter, (getWidth()-fm.stringWidth(letter))/2, (getHeight()-fm.getHeight())/2+fm.getAscent());
                g2.dispose();
            }
        };
        iconPanel.setOpaque(false);
        iconPanel.setPreferredSize(new Dimension(32, 32));
        iconPanel.setMinimumSize(new Dimension(32, 32));
        iconPanel.setMaximumSize(new Dimension(32, 32));

        // Left: ten phong + so nguoi
        JPanel leftPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        leftPanel.setOpaque(false);

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(isActive ? FONT_USERNAME : new Font("Segoe UI", Font.PLAIN, 13));
        nameLabel.setForeground(isActive ? COLOR_ACCENT : COLOR_TEXT_DARK);

        JLabel infoLabel = new JLabel(currentUsers + " / " + limit + " nguoi");
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        infoLabel.setForeground(COLOR_TEXT_GRAY);

        leftPanel.add(nameLabel);
        leftPanel.add(infoLabel);

        item.add(iconPanel, BorderLayout.WEST);
        item.add(leftPanel, BorderLayout.CENTER);

        // Cham xanh neu dang o phong nay
        if (isActive) {
            JLabel activeDot = new JLabel("●");
            activeDot.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            activeDot.setForeground(COLOR_ACCENT);
            item.add(activeDot, BorderLayout.EAST);
        }

        // Click de vao phong (chi khi chua o phong do)
        if (!isActive) {
            item.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (frameListener != null) frameListener.onJoinRoom(roomId);
                }
                @Override public void mouseEntered(MouseEvent e) {
                    item.setBackground(new Color(0xEBF5FE)); item.repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    item.setBackground(bgColor); item.repaint();
                }
            });
        }

        // Chuot phai: xoa phong (neu la chu phong)
        item.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3 && roomId != 1) {
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem delItem = new JMenuItem("Xoa phong nay");
                    delItem.setForeground(new Color(0xE53935));
                    delItem.addActionListener(ev -> {
                        int confirm = JOptionPane.showConfirmDialog(ChatFrame.this,
                            "Ban co chac muon xoa phong \"" + name + "\"?",
                            "Xac nhan xoa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                        if (confirm == JOptionPane.YES_OPTION && frameListener != null) {
                            frameListener.onDeleteRoom(roomId);
                        }
                    });
                    menu.add(delItem);
                    menu.show(item, e.getX(), e.getY());
                }
            }
        });
        return item;
    }

    /**
     * Cập nhật UI khi người dùng vào phòng mới:
     * - Cập nhật tiêu đề header
     * - Xóa chat cũ
     * - Highlight phòng đang vào
     */
    public void setCurrentRoom(int roomId, String roomName) {
        this.currentRoomId = roomId;
        SwingUtilities.invokeLater(() -> {
            if (headerRoomLabel != null) headerRoomLabel.setText(roomName);
            clearChat();
            renderRoomList(); // redraw để phòng hiện tại đổi màu ngay
        });
    }

    /** Xóa toàn bộ tin nhắn trong khu chat (khi đổi phòng) */
    public void clearChat() {
        SwingUtilities.invokeLater(() -> {
            chatPanel.removeAll();
            chatPanel.revalidate();
            chatPanel.repaint();
        });
    }

    // ─────────────────────────────────────────────────────────────
    // HELPER CLASS - Bo góc border
    // ─────────────────────────────────────────────────────────────

    /**
     * Border bo góc tùy chỉnh
     */
    private static class RoundedBorder implements Border {
        private int radius;
        private Color color;

        RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius / 2, radius, radius / 2, radius);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // HELPER - Chuyển text sang HTML để JLabel tự co vừa nội dung
    // ─────────────────────────────────────────────────────────────

    /**
     * Chuyển plain text sang HTML với max-width để JLabel tự wrap dòng.
     * 
     * @param text  Nội dung tin nhắn
     * @param maxPx Chiều rộng tối đa tính bằng pixel trước khi xuống dòng
     */
    private static String toHtml(String text, int maxPx) {
        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
        // max-width: HTML renderer sẽ wrap dòng khi vượt quá maxPx.
        // fitBubbleTight() sẽ shrink bubble lại nếu text ngắn hơn.
        return "<html><div style='max-width:" + maxPx + "px; font-size:11pt;'>" + escaped + "</div></html>";
    }

    // ─────────────────────────────────────────────────────────────
    // INNER CLASS - JTextField với Placeholder
    // ─────────────────────────────────────────────────────────────

    /**
     * JTextField tùy chỉnh hỗ trợ hiển thị placeholder text
     */
    static class PlaceholderTextField extends javax.swing.JTextField {
        private String placeholder;

        public PlaceholderTextField() {
            super();
        }

        public void setPlaceholder(String placeholder) {
            this.placeholder = placeholder;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && placeholder != null && !hasFocus()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(0xAAAAAA));
                g2.setFont(getFont().deriveFont(Font.ITALIC));
                Insets ins = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(placeholder, ins.left, y);
                g2.dispose();
            }
        }
    }
}
