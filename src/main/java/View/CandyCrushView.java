package View;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;      // <--- Xử lý sự kiện click chuột
import java.util.List;        // <--- Quản lý danh sách kẹo
import java.util.ArrayList;

public class CandyCrushView extends JFrame {
    private BufferedImage[] candyImages;

    private void loadImages() {
        candyImages = new BufferedImage[6];
        String[] fileNames = {"red.png", "blue.png", "green.png", "pink.png", "purple.png", "orange.png"};
        
        for (int i = 0; i < 6; i++) {
            String path = "src/main/java/assets/" + fileNames[i];
            try {
                candyImages[i] = ImageIO.read(new File(path));
            } catch (Exception e) {
                System.out.println("❌ LỖI đọc file ảnh -> " + path);
            }
        }
    }
    
    public CandyCrushView() {
        setTitle("Candy Crush - View Module");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        topPanel.setBackground(new Color(40, 40, 40));
        
        // Chỉ giữ lại thông tin điểm số và lượt đi
        JLabel scoreLabel = new JLabel("Score: 0   |   Moves: 20   ");
        scoreLabel.setFont(new Font("Arial", Font.BOLD, 18));
        scoreLabel.setForeground(Color.WHITE);
        topPanel.add(scoreLabel);
        
        loadImages();
        BoardPanel boardPanel = new BoardPanel(candyImages);
        
        add(topPanel, BorderLayout.NORTH);
        add(boardPanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new CandyCrushView().setVisible(true);
        });
    }
}


// =========================================================
// INTERFACE ĐỂ GIAO TIẾP VỚI CONTROLLER
// =========================================================
interface BoardInteractionListener {
    // Hàm này sẽ được Controller lắng nghe
    void onSwapRequested(int r1, int c1, int r2, int c2);
}

// =========================================================
// CLASS BOARDPANEL - NƠI XỬ LÝ VẼ VÀ ANIMATION 
// =========================================================
class BoardPanel extends JPanel {
    private final int ROWS = 8;
    private final int COLS = 8;
    private final int TILE_SIZE = 60;
    private final int PADDING = 20;

    private int[][] board;
    private Color[] colors = {Color.RED, Color.CYAN, Color.GREEN, Color.YELLOW, Color.MAGENTA, Color.ORANGE};
    private BufferedImage[] candyImages;

    private int selectedRow = -1;
    private int selectedCol = -1;

    // --- BIẾN KẾT NỐI CONTROLLER ---
    private BoardInteractionListener listener;

    // --- BIẾN ANIMATION ---
    private boolean isAnimating = false;
    private Timer timer;
    private int animProgress = 0; 
    private final int ANIM_SPEED = 5; 
    private int r1, c1, r2, c2; 
    private int dx, dy; 

    private boolean isExploding = false;
    private Timer explosionTimer;
    private int currentExplosionSize; 
    private List<Point> explodingCandies = new ArrayList<>();

    private boolean isDropping = false;
    private Timer dropTimer;
    private List<FallingCandy> fallingCandies = new ArrayList<>();

    // Lớp phụ trợ lưu thông tin kẹo rơi
    class FallingCandy {
        int type, col, endRow;
        float currentY, targetY;

        public FallingCandy(int type, int col, int startRow, int endRow) {
            this.type = type;
            this.col = col;
            this.endRow = endRow;
            this.currentY = PADDING + (startRow * TILE_SIZE);
            this.targetY = PADDING + (endRow * TILE_SIZE);
        }
    }

    public BoardPanel(BufferedImage[] images) {
        this.candyImages = images;
        
        int width = (COLS * TILE_SIZE) + (PADDING * 2);
        int height = (ROWS * TILE_SIZE) + (PADDING * 2);
        setPreferredSize(new Dimension(width, height));
        setBackground(new Color(30, 30, 30));
        
        initMockBoard();
        setupMouseListener();
    }

    // Hàm để Controller đăng ký lắng nghe sự kiện
    public void setInteractionListener(BoardInteractionListener listener) {
        this.listener = listener;
    }

    // Hàm API để Controller cập nhật dữ liệu mới từ Model
    public void updateBoard(int[][] newBoard) {
        this.board = newBoard;
        repaint();
    }

    private void initMockBoard() {
        board = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                board[r][c] = (int) (Math.random() * 6);
            }
        }
    }

    private void setupMouseListener() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isAnimating || isExploding || isDropping) return;

                int c = (e.getX() - PADDING) / TILE_SIZE;
                int r = (e.getY() - PADDING) / TILE_SIZE;

                if (r >= 0 && r < ROWS && c >= 0 && c < COLS) {
                    if (selectedRow == -1) {
                        selectedRow = r;
                        selectedCol = c;
                        repaint();
                    } else {
                        boolean isAdjacent = (Math.abs(selectedRow - r) + Math.abs(selectedCol - c) == 1);
                        if (isAdjacent) {
                            // THAY ĐỔI: Không tự chạy Animation nữa, mà BÁO CHO CONTROLLER
                            if (listener != null) {
                                listener.onSwapRequested(selectedRow, selectedCol, r, c);
                            } else {
                                // Nếu chưa có Controller (đang test View), tự chạy tạm
                                startSwapAnimation(selectedRow, selectedCol, r, c, null);
                            }
                        }
                        selectedRow = -1;
                        selectedCol = -1;
                        repaint();
                    }
                }
            }
        });
    }

    // THAY ĐỔI: Thêm tham số Runnable onComplete cho tất cả các hàm Animation
    public void startSwapAnimation(int row1, int col1, int row2, int col2, Runnable onComplete) {
        isAnimating = true;
        r1 = row1; c1 = col1;
        r2 = row2; c2 = col2;
        dx = c2 - c1;
        dy = r2 - r1;
        animProgress = 0;

        timer = new Timer(10, e -> {
            animProgress += ANIM_SPEED; 
            if (animProgress >= TILE_SIZE) {
                timer.stop();
                isAnimating = false;
                animProgress = 0;
                
                // Cập nhật mảng trên View (Controller cũng sẽ cập nhật trên Model)
                int temp = board[r1][c1];
                board[r1][c1] = board[r2][c2];
                board[r2][c2] = temp;
                
                // Gọi ngược lại Controller báo là đã trượt xong
                if (onComplete != null) onComplete.run();
            }
            repaint();
        });
        timer.start();
    }

    public void startExplosionAnimation(List<Point> candies, Runnable onComplete) {
        if (candies == null || candies.isEmpty() || isExploding || isAnimating || isDropping) {
            if (onComplete != null) onComplete.run();
            return;
        }
        
        this.explodingCandies = candies;
        this.isExploding = true;
        this.currentExplosionSize = TILE_SIZE - 10; 

        explosionTimer = new Timer(15, e -> {
            currentExplosionSize -= 5; 
            if (currentExplosionSize <= 0) {
                explosionTimer.stop();
                isExploding = false;
                for (Point p : explodingCandies) board[p.x][p.y] = -1; 
                explodingCandies.clear();
                
                // Báo cho Controller: Đã nổ xong!
                if (onComplete != null) onComplete.run();
            }
            repaint();
        });
        explosionTimer.start();
    }

    public void startDropAnimation(Runnable onComplete) {
        if (isAnimating || isExploding || isDropping) return;
        fallingCandies.clear();

        for (int c = 0; c < COLS; c++) {
            int emptySpaces = 0;
            for (int r = ROWS - 1; r >= 0; r--) {
                if (board[r][c] == -1) {
                    emptySpaces++; 
                } else if (emptySpaces > 0) {
                    fallingCandies.add(new FallingCandy(board[r][c], c, r, r + emptySpaces));
                    board[r + emptySpaces][c] = board[r][c]; 
                    board[r][c] = -1; 
                }
            }
            for (int i = 0; i < emptySpaces; i++) {
                int newType = (int) (Math.random() * 6); 
                fallingCandies.add(new FallingCandy(newType, c, -emptySpaces + i, i));
                board[i][c] = newType;
            }
        }

        if (fallingCandies.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        
        isDropping = true;

        dropTimer = new Timer(15, e -> {
            boolean allDone = true;
            for (FallingCandy fc : fallingCandies) {
                if (fc.currentY < fc.targetY) {
                    fc.currentY += 8; 
                    if (fc.currentY > fc.targetY) fc.currentY = fc.targetY;
                    allDone = false; 
                }
            }

            if (allDone) {
                dropTimer.stop();
                isDropping = false;
                fallingCandies.clear();
                
                // Báo cho Controller: Kẹo đã rơi xong, kiểm tra xem có chuỗi 3 mới không!
                if (onComplete != null) onComplete.run();
            }
            repaint();
        });
        dropTimer.start();
    }

    // ==========================================
    // PHẦN VẼ (paintComponent và drawCandy) 
    // ==========================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = PADDING + (c * TILE_SIZE);
                int y = PADDING + (r * TILE_SIZE);

                g2d.setColor(new Color(50, 50, 50));
                g2d.fillRoundRect(x, y, TILE_SIZE - 2, TILE_SIZE - 2, 12, 12);

                if (board[r][c] == -1) continue;

                if (isAnimating && ((r == r1 && c == c1) || (r == r2 && c == c2))) continue; 
                
                boolean isFallingTarget = false;
                if (isDropping) {
                    for (FallingCandy fc : fallingCandies) {
                        if (fc.col == c && fc.endRow == r) {
                            isFallingTarget = true; break;
                        }
                    }
                }
                if (isFallingTarget) continue;

                boolean isThisCandyExploding = isExploding && explodingCandies.contains(new Point(r, c));
                drawCandy(g2d, board[r][c], x, y, isThisCandyExploding);

                if (r == selectedRow && c == selectedCol) {
                    g2d.setColor(Color.WHITE);
                    g2d.setStroke(new BasicStroke(3));
                    g2d.drawRoundRect(x, y, TILE_SIZE - 2, TILE_SIZE - 2, 12, 12);
                }
            }
        }

        if (isAnimating) {
            int baseX1 = PADDING + (c1 * TILE_SIZE);
            int baseY1 = PADDING + (r1 * TILE_SIZE);
            int baseX2 = PADDING + (c2 * TILE_SIZE);
            int baseY2 = PADDING + (r2 * TILE_SIZE);

            int currentX1 = baseX1 + (dx * animProgress);
            int currentY1 = baseY1 + (dy * animProgress);
            int currentX2 = baseX2 - (dx * animProgress); 
            int currentY2 = baseY2 - (dy * animProgress);

            drawCandy(g2d, board[r1][c1], currentX1, currentY1, false);
            drawCandy(g2d, board[r2][c2], currentX2, currentY2, false);
        }

        if (isDropping) {
            Shape oldClip = g2d.getClip();
            g2d.clipRect(PADDING, PADDING, COLS * TILE_SIZE, ROWS * TILE_SIZE);

            for (FallingCandy fc : fallingCandies) {
                int drawX = PADDING + (fc.col * TILE_SIZE);
                drawCandy(g2d, fc.type, drawX, (int) fc.currentY, false);
            }
            g2d.setClip(oldClip);
        }
    }

    private void drawCandy(Graphics2D g2d, int type, int x, int y, boolean isExplodingCandy) {
        if (type == -1) return; 
        if (isExplodingCandy && currentExplosionSize <= 0) return;

        int drawSize = TILE_SIZE - 10; 
        int drawX = x + 5;
        int drawY = y + 5;

        if (isExplodingCandy) {
            drawSize = currentExplosionSize;
            drawX = x + (TILE_SIZE - drawSize) / 2;
            drawY = y + (TILE_SIZE - drawSize) / 2;
        }

        if (candyImages != null && type >= 0 && type < candyImages.length && candyImages[type] != null) {
            g2d.drawImage(candyImages[type], drawX, drawY, drawSize, drawSize, null);
        } else {
            if(type >= 0 && type < colors.length) {
                g2d.setColor(colors[type]);
                g2d.fillOval(drawX, drawY, drawSize, drawSize);
            }
        }
    }
}