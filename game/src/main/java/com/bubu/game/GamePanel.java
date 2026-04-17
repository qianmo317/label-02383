package com.bubu.game;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * 游戏面板 - 带动画效果
 */
public class GamePanel extends JPanel {
    private static final int BOARD_OFFSET_X = 50;
    private static final int BOARD_OFFSET_Y = 120;
    private static final int CELL_SIZE = 70;
    private static final int IMAGE_SIZE = 60;
    private static final int IMAGE_PADDING = 5;

    private static final Color BG_COLOR = new Color(44, 62, 80);
    private static final Color BOARD_BG_COLOR = new Color(52, 73, 94);
    private static final Color CELL_COLOR = new Color(41, 128, 185);
    private static final Color CELL_BORDER_COLOR = new Color(26, 188, 156);
    private static final Color SELECTED_COLOR = new Color(243, 156, 18);
    private static final Color TEXT_COLOR = new Color(236, 240, 241);
    private static final Color WIN_COLOR = new Color(39, 174, 96);
    private static final Color LOSE_COLOR = new Color(231, 76, 60);

    private static final int INITIAL_MOVES = 20;
    private static final int TARGET_SCORE = 200;

    private GameBoard board;
    private Block selectedBlock;
    private int score;
    private int movesLeft;
    private GameState state;
    private boolean isAnimating;
    
    // 动画相关
    private Timer animationTimer;
    private float[][] blockOffsetY;  // 下落动画偏移
    private float[][] blockScale;    // 消除动画缩放
    private static final int ANIMATION_FPS = 60;
    private static final float DROP_SPEED = 15f;  // 下落速度
    private static final float SCALE_SPEED = 0.1f; // 缩放速度

    public GamePanel() {
        setPreferredSize(new Dimension(700, 800));
        setBackground(BG_COLOR);
        setDoubleBuffered(true);
        
        blockOffsetY = new float[GameBoard.ROWS][GameBoard.COLS];
        blockScale = new float[GameBoard.ROWS][GameBoard.COLS];
        
        initGame();
        setupMouseListener();
        setupAnimationTimer();
    }

    private void initGame() {
        board = new GameBoard();
        selectedBlock = null;
        score = 0;
        movesLeft = INITIAL_MOVES;
        state = GameState.PLAYING;
        isAnimating = false;
        resetAnimationState();
    }
    
    private void resetAnimationState() {
        for (int r = 0; r < GameBoard.ROWS; r++) {
            for (int c = 0; c < GameBoard.COLS; c++) {
                blockOffsetY[r][c] = 0;
                blockScale[r][c] = 1.0f;
            }
        }
    }

    private void setupAnimationTimer() {
        animationTimer = new Timer(1000 / ANIMATION_FPS, e -> {
            if (isAnimating) {
                updateAnimation();
                repaint();
            }
        });
        animationTimer.start();
    }

    private void updateAnimation() {
        boolean stillAnimating = false;
        
        for (int r = 0; r < GameBoard.ROWS; r++) {
            for (int c = 0; c < GameBoard.COLS; c++) {
                // 下落动画
                if (blockOffsetY[r][c] < 0) {
                    blockOffsetY[r][c] = Math.min(0, blockOffsetY[r][c] + DROP_SPEED);
                    stillAnimating = true;
                }
                // 消除缩放动画
                if (blockScale[r][c] < 1.0f && blockScale[r][c] > 0) {
                    blockScale[r][c] = Math.max(0, blockScale[r][c] - SCALE_SPEED);
                    stillAnimating = true;
                }
            }
        }
        
        if (!stillAnimating) {
            isAnimating = false;
        }
    }

    private void setupMouseListener() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        });
    }

    private void handleClick(int x, int y) {
        // 游戏结束时的重新开始按钮
        if (state != GameState.PLAYING) {
            int btnX = 250, btnY = 450, btnW = 200, btnH = 50;
            if (x >= btnX && x <= btnX + btnW && y >= btnY && y <= btnY + btnH) {
                initGame();
                repaint();
            }
            return;
        }

        if (isAnimating) return;

        int col = (x - BOARD_OFFSET_X) / CELL_SIZE;
        int row = (y - BOARD_OFFSET_Y) / CELL_SIZE;

        if (row < 0 || row >= GameBoard.ROWS || col < 0 || col >= GameBoard.COLS) {
            return;
        }

        Block clicked = board.getBlock(row, col);
        if (clicked == null) return;

        if (selectedBlock == null) {
            selectedBlock = clicked;
        } else if (selectedBlock == clicked) {
            selectedBlock = null;
        } else if (selectedBlock.isAdjacentTo(clicked)) {
            trySwap(selectedBlock, clicked);
            selectedBlock = null;
        } else {
            selectedBlock = clicked;
        }
        repaint();
    }

    private void trySwap(Block a, Block b) {
        if (board.canSwapAndMatch(a, b)) {
            board.swapBlocks(a, b);
            movesLeft--;
            repaint();
            
            // 延迟处理消除，让交换先显示
            Timer delayTimer = new Timer(100, e -> {
                ((Timer)e.getSource()).stop();
                processMatchesAnimated();
            });
            delayTimer.setRepeats(false);
            delayTimer.start();
        } else {
            // 无效交换动画：交换后再换回
            board.swapBlocks(a, b);
            repaint();
            Timer swapBack = new Timer(150, e -> {
                ((Timer)e.getSource()).stop();
                board.swapBlocks(a, b);
                repaint();
            });
            swapBack.setRepeats(false);
            swapBack.start();
        }
    }

    private void processMatchesAnimated() {
        if (!board.hasMatches()) {
            checkGameState();
            repaint();
            return;
        }

        isAnimating = true;
        
        // 找到匹配的方块，设置缩放动画
        List<Block> matched = board.findMatches();
        int matchScore = board.calculateScore(matched);
        score += matchScore;
        
        for (Block block : matched) {
            blockScale[block.getRow()][block.getCol()] = 0.9f; // 开始缩小
        }
        
        // 等待消除动画完成后处理下落
        Timer removeTimer = new Timer(200, e -> {
            ((Timer)e.getSource()).stop();
            
            board.removeMatches();
            
            // 计算下落偏移
            for (int col = 0; col < GameBoard.COLS; col++) {
                int dropCount = 0;
                for (int row = GameBoard.ROWS - 1; row >= 0; row--) {
                    if (board.getBlock(row, col) == null) {
                        dropCount++;
                    }
                }
                // 设置上方方块的下落偏移
                if (dropCount > 0) {
                    for (int row = 0; row < GameBoard.ROWS; row++) {
                        if (board.getBlock(row, col) != null) {
                            // 这个方块需要下落
                        }
                    }
                }
            }
            
            board.dropBlocks();
            board.fillEmpty();
            board.resetMatchedState();
            
            // 设置新方块的下落动画
            for (int col = 0; col < GameBoard.COLS; col++) {
                for (int row = 0; row < GameBoard.ROWS; row++) {
                    Block block = board.getBlock(row, col);
                    if (block != null) {
                        blockScale[row][col] = 1.0f;
                        // 新填充的方块从上方落下
                        if (row < 3) {
                            blockOffsetY[row][col] = -(row + 1) * CELL_SIZE * 0.5f;
                        }
                    }
                }
            }
            
            isAnimating = true;
            
            // 继续检查是否有新的匹配
            Timer nextCheck = new Timer(300, e2 -> {
                ((Timer)e2.getSource()).stop();
                processMatchesAnimated();
            });
            nextCheck.setRepeats(false);
            nextCheck.start();
        });
        removeTimer.setRepeats(false);
        removeTimer.start();
    }

    private void checkGameState() {
        if (score >= TARGET_SCORE) {
            state = GameState.WIN;
        } else if (movesLeft <= 0) {
            state = GameState.LOSE;
        } else if (!board.hasValidMoves()) {
            board.initBoard();
        }
    }

    public void resetGame() {
        initGame();
        repaint();
    }


    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        drawHeader(g2d);
        drawBoard(g2d);
        
        if (state != GameState.PLAYING) {
            drawGameOver(g2d);
        } else {
            drawHint(g2d);
        }
    }

    private void drawHeader(Graphics2D g2d) {
        g2d.setColor(TEXT_COLOR);
        g2d.setFont(new Font("Microsoft YaHei", Font.BOLD, 28));
        String title = "布布一二消消乐";
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(title, (getWidth() - fm.stringWidth(title)) / 2, 40);

        g2d.setColor(BOARD_BG_COLOR);
        g2d.fillRoundRect(50, 55, 600, 50, 10, 10);

        g2d.setColor(TEXT_COLOR);
        g2d.setFont(new Font("Microsoft YaHei", Font.BOLD, 18));
        g2d.drawString("分数: " + score, 80, 87);
        g2d.drawString("步数: " + movesLeft, 290, 87);
        g2d.drawString("目标: " + TARGET_SCORE, 490, 87);
    }

    private void drawBoard(Graphics2D g2d) {
        // 棋盘背景
        int boardW = GameBoard.COLS * CELL_SIZE + 10;
        int boardH = GameBoard.ROWS * CELL_SIZE + 10;
        g2d.setColor(BOARD_BG_COLOR);
        g2d.fillRoundRect(BOARD_OFFSET_X - 5, BOARD_OFFSET_Y - 5, boardW, boardH, 15, 15);

        // 绘制格子和方块
        for (int row = 0; row < GameBoard.ROWS; row++) {
            for (int col = 0; col < GameBoard.COLS; col++) {
                int x = BOARD_OFFSET_X + col * CELL_SIZE;
                int y = BOARD_OFFSET_Y + row * CELL_SIZE;

                // 格子背景
                g2d.setColor(CELL_COLOR);
                g2d.fillRoundRect(x + 2, y + 2, CELL_SIZE - 4, CELL_SIZE - 4, 8, 8);
                g2d.setColor(CELL_BORDER_COLOR);
                g2d.setStroke(new BasicStroke(1));
                g2d.drawRoundRect(x + 2, y + 2, CELL_SIZE - 4, CELL_SIZE - 4, 8, 8);

                Block block = board.getBlock(row, col);
                if (block != null) {
                    Image img = block.getImage();
                    if (img != null) {
                        float scale = blockScale[row][col];
                        float offsetY = blockOffsetY[row][col];
                        
                        int imgSize = (int)(IMAGE_SIZE * scale);
                        int imgX = x + IMAGE_PADDING + (IMAGE_SIZE - imgSize) / 2;
                        int imgY = (int)(y + IMAGE_PADDING + offsetY) + (IMAGE_SIZE - imgSize) / 2;
                        
                        if (scale > 0.1f) {
                            g2d.drawImage(img, imgX, imgY, imgSize, imgSize, this);
                        }
                    }

                    // 选中高亮
                    if (block == selectedBlock && !isAnimating) {
                        g2d.setColor(SELECTED_COLOR);
                        g2d.setStroke(new BasicStroke(4));
                        g2d.drawRoundRect(x + 2, y + 2, CELL_SIZE - 4, CELL_SIZE - 4, 10, 10);
                    }
                }
            }
        }
    }

    private void drawHint(Graphics2D g2d) {
        g2d.setColor(TEXT_COLOR);
        g2d.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        String hint = "点击选择方块，再点击相邻方块进行交换";
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(hint, (getWidth() - fm.stringWidth(hint)) / 2, 710);
    }

    private void drawGameOver(Graphics2D g2d) {
        // 半透明遮罩
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // 结果
        g2d.setFont(new Font("Microsoft YaHei", Font.BOLD, 42));
        String msg = state.getMessage();
        FontMetrics fm = g2d.getFontMetrics();
        g2d.setColor(state == GameState.WIN ? WIN_COLOR : LOSE_COLOR);
        g2d.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, 340);

        // 分数
        g2d.setColor(TEXT_COLOR);
        g2d.setFont(new Font("Microsoft YaHei", Font.PLAIN, 26));
        String scoreText = "最终得分: " + score;
        fm = g2d.getFontMetrics();
        g2d.drawString(scoreText, (getWidth() - fm.stringWidth(scoreText)) / 2, 400);

        // 重新开始按钮
        int btnX = 250, btnY = 450, btnW = 200, btnH = 50;
        
        // 按钮阴影
        g2d.setColor(new Color(0, 0, 0, 50));
        g2d.fillRoundRect(btnX + 3, btnY + 3, btnW, btnH, 12, 12);
        
        // 按钮背景
        g2d.setColor(CELL_BORDER_COLOR);
        g2d.fillRoundRect(btnX, btnY, btnW, btnH, 12, 12);
        
        // 按钮高光
        g2d.setColor(new Color(255, 255, 255, 40));
        g2d.fillRoundRect(btnX + 4, btnY + 4, btnW - 8, btnH / 2 - 4, 8, 8);

        // 按钮文字
        g2d.setColor(TEXT_COLOR);
        g2d.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
        String btnText = "重新开始";
        fm = g2d.getFontMetrics();
        int textX = btnX + (btnW - fm.stringWidth(btnText)) / 2;
        int textY = btnY + (btnH + fm.getAscent() - fm.getDescent()) / 2;
        g2d.drawString(btnText, textX, textY);
    }
}
