// package connect4;

import java.util.*;

public class Connect4AI {

    // ===== 盤面設定 =====
    static final int WIDTH = 7;
    static final int HEIGHT = 6;

    static final int EMPTY = 0x00;
    static final int COM = 0x01;
    static final int MAN = 0x02;
    static final int HIGHLIGHT_COM = 0x20;
    static final int HIGHLIGHT_MAN = 0x40;
    static final int HIGHLIGHT_WIN = 0x80;
    static final int STONE_MASK = MAN | COM;

    static final int WIN_SCORE = 10_000_000;
    static final int FORK_SCORE = 1_000_000;
    static final int THREE_SCORE = 1_000;
    static final int TWO_SCORE = 100;
    // static final int CENTER_SCORE = 200;
    // static final int PARITY_SCORE = 150;
    // static final int MOBILITY_SCORE = 50;
    // static final int THREAT_SCORE = 200;

    static final int[] ORDERED_COLS = { 3, 2, 4, 1, 5, 0, 6 };
    static final int[][] DIRECTIONS = { { 1, 0 }, { 0, 1 }, { 1, 1 }, { 1, -1 } };
    static final Random rand = new Random();
    // static int[] forbidden = new int[WIDTH]; // 禁止列のマーク

    // piece-square table方式(駒位置の重み)
    static final int[][] POSITION_SCORE = {
            { 3, 4, 5, 5, 4, 3 }, // x=0
            { 4, 6, 8, 8, 6, 4 }, // x=1
            { 5, 8, 11, 11, 8, 5 }, // x=2
            { 7, 10, 13, 13, 10, 7 }, // x=3
            { 5, 8, 11, 11, 8, 5 }, // x=4
            { 4, 6, 8, 8, 6, 4 }, // x=5
            { 3, 4, 5, 5, 4, 3 } // x=6
    };

    static final boolean DEBUG = false;
    static final boolean DEBUG1 = false;
    static long nodeCount = 0;

    // ===== Window（4マス連続パターン）の事前計算 =====
    static class Window {
        int[][] pos = new int[4][2];

        Window(int x, int y, int dx, int dy) {
            for (int i = 0; i < 4; i++) {
                pos[i][0] = x + dx * i;
                pos[i][1] = y + dy * i;
            }
        }
    }

    static final List<Window> ALL_WINDOWS = new ArrayList<>();
    static List<Window>[][] WINDOWS_FROM_CELL;

    static {
        for (int y = 0; y < HEIGHT; y++)
            for (int x = 0; x <= WIDTH - 4; x++)
                ALL_WINDOWS.add(new Window(x, y, 1, 0)); // 横
        for (int x = 0; x < WIDTH; x++)
            for (int y = 0; y <= HEIGHT - 4; y++)
                ALL_WINDOWS.add(new Window(x, y, 0, 1)); // 縦
        for (int x = 0; x <= WIDTH - 4; x++)
            for (int y = 0; y <= HEIGHT - 4; y++)
                ALL_WINDOWS.add(new Window(x, y, 1, 1)); // 斜め右上
        for (int x = 0; x <= WIDTH - 4; x++)
            for (int y = 3; y < HEIGHT; y++)
                ALL_WINDOWS.add(new Window(x, y, 1, -1)); // 斜め右下

        WINDOWS_FROM_CELL = new ArrayList[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++)
            for (int y = 0; y < HEIGHT; y++)
                WINDOWS_FROM_CELL[x][y] = new ArrayList<>();

        for (Window w : ALL_WINDOWS)
            for (int[] p : w.pos)
                WINDOWS_FROM_CELL[p[0]][p[1]].add(w);
    }

    // ===== AI思考エンジン =====
    public static int chooseBestMove(int[][] board, int depth, int first) {
        nodeCount = 0;
        int[] scores = new int[WIDTH];
        Arrays.fill(scores, Integer.MIN_VALUE);
        List<int[]> moves = new ArrayList<>();

        // ===== 自分の４勝ち =====
        for (int x : ORDERED_COLS) {
            int y = nextEmptyY(board, x);
            if (y < 0)
                continue;

            board[x][y] = COM;
            if (check4(board, x, y, COM)) {
                board[x][y] = EMPTY;
                System.out.println("４が出来ました");
                return x;
            }
            board[x][y] = EMPTY;
        }

        // ===== 相手の４止め =====
        for (int x : ORDERED_COLS) {
            int y = nextEmptyY(board, x);
            if (y < 0)
                continue;

            board[x][y] = MAN;
            if (check4(board, x, y, MAN)) {
                board[x][y] = EMPTY;
                System.out.println("４を防ぎました");
                return x;
            }
            board[x][y] = EMPTY;
        }

        // ===== 自分の３３勝ち =====
        int x33 = checkWin33(board, COM);
        if (x33 != -1) {
            System.out.println("３３が出来ました");
            return x33;
        }

        // ===== 相手の３３止め =====
        // x33 = checkWin33(board, MAN);
        // if (x33 != -1) {
        // System.out.println("３３を防ぎました");
        // return x33;
        // }

        // ===== move生成 =====
        for (int x : ORDERED_COLS) {
            int score;
            int y = nextEmptyY(board, x);
            if (y < 0) {
                score = -(WIN_SCORE * 2) - depth; // 満杯で置けない
            } else {
                boolean losing = isLosingMove(board, x);
                if (losing) {
                    score = -WIN_SCORE - depth; // 置けるが次で負ける
                } else {
                    board[x][y] = COM;
                    score = evaluatePosition(board, COM, first);
                    board[x][y] = EMPTY;
                }
            }
            moves.add(new int[] { x, score });
        }
        moves.sort((a, b) -> b[1] - a[1]); // ソート

        // ===== 探索 =====
        int bestScore = Integer.MIN_VALUE;
        int bestMove = -1;
        for (int[] m : moves) {
            int x = m[0];
            int y = nextEmptyY(board, x);
            if (y < 0)
                continue;
            board[x][y] = COM;
            int score = minimax(board, depth - 1, false,
                    Integer.MIN_VALUE, Integer.MAX_VALUE, first);
            board[x][y] = EMPTY;

            scores[x] = score;
            if (score > bestScore) {
                bestScore = score;
                bestMove = x;
            }
        }

        // ===== デバッグ =====
        System.out.println("=== AI思考結果 ===");
        System.out.print("各列スコア: ");
        for (int x = 0; x < WIDTH; x++) {
            if (scores[x] == Integer.MIN_VALUE) {
                System.out.printf("%d:   --- ", x);
            } else {
                System.out.printf("%d:%6d ", x, scores[x]);
            }
        }
        System.out.println();

        System.out.println("最善手 [" + bestMove + "] " + bestScore);

        if (bestScore >= WIN_SCORE) {
            System.out.println(" ４で勝ち");
        } else if (bestScore <= -WIN_SCORE) {
            System.out.println(" ４で負け");
        } else if (bestScore >= FORK_SCORE) {
            System.out.println(" ３３で勝ち");
        } else if (bestScore <= -FORK_SCORE) {
            System.out.println(" ３３で負け");
        }

        System.out.println("==================nodes=" + nodeCount);

        return bestMove;
    }

    // ===== Minimax =====
    static int minimax(int[][] board, int depth, boolean isMax, int alpha, int beta, int first) {
        int player = isMax ? COM : MAN;
        int opponent = (player == COM) ? MAN : COM;
        int best = isMax ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int score = 0;

        nodeCount++;

        if (depth == 0) {
            return evaluatePosition(board, COM, first);
        }

        // 1手評価でソート(枝刈りを高速化)
        List<int[]> moves = new ArrayList<>();
        for (int x : ORDERED_COLS) {
            int s;
            int y = nextEmptyY(board, x);
            if (y < 0) {
                continue;
            } else {
                boolean losing = isLosingMove(board, x);
                if (losing) {
                    continue;
                } else {
                    board[x][y] = player;
                    s = evaluatePosition(board, COM, first);
                    board[x][y] = EMPTY;
                }
            }
            moves.add(new int[] { x, s });
        }
        // COMなら降順、MANなら昇順
        if (isMax) {
            moves.sort((a, b) -> b[1] - a[1]); // 降順
        } else {
            moves.sort((a, b) -> a[1] - b[1]); // 昇順
        }

        // 4. 次の手を再帰的に全探索
        boolean hasValidMove = false;
        // for (int x : ORDERED_COLS) {
        for (int[] m : moves) {
            int x = m[0];

            int y = nextEmptyY(board, x);
            if (y < 0) {
                continue;
            }

            hasValidMove = true;

            // 自分が４ならWIN 相手が４なら-WIN
            board[x][y] = player;
            if (check4(board, x, y, player)) {
                board[x][y] = EMPTY;
                return isMax ? WIN_SCORE + depth : -WIN_SCORE - depth;
            }
            // // 相手が即4できるならこの手はダメ
            // for (int x2 : ORDERED_COLS) {
            // int y2 = nextEmptyY(board, x2);
            // if (y2 < 0)
            // continue;
            // board[x2][y2] = opponent;
            // if (check4(board, x2, y2, opponent)) {
            // // Main.displayBoard(board); // debug
            // board[x2][y2] = EMPTY;
            // board[x][y] = EMPTY;
            // return isMax ? -WIN_SCORE - depth : WIN_SCORE + depth;
            // }
            // board[x2][y2] = EMPTY;
            // }

            // // 自分が３３ならFORK 相手が３３なら-FORK
            // int x33 = checkWin33(board, player);
            // if (x33 != -1) {
            // // Main.displayBoard(board); // debug
            // board[x][y] = EMPTY;
            // return isMax ? FORK_SCORE + depth : -FORK_SCORE - depth;
            // }

            // // ③ 相手が３３なら-FORK
            // if (checkWin33(board, opponent) != -1) {
            // Main.displayBoard(board); // debug
            // board[x][y] = EMPTY;
            // return isMax ? -FORK_SCORE - depth : FORK_SCORE + depth;
            // }

            // COMが置いてMANが４なら-WIN
            if (isMax && isLosingMove(board, x)) {
                score = -WIN_SCORE - depth;
            } else {
                int childAlpha = alpha;
                int childBeta = beta;
                score = minimax(board, depth - 1, !isMax, childAlpha, childBeta, first);
            }
            board[x][y] = EMPTY;

            if (isMax) {
                best = Math.max(best, score);
                alpha = Math.max(alpha, best);
            } else {
                best = Math.min(best, score);
                beta = Math.min(beta, best);
            }
            // アルファベータ枝刈り
            if (beta <= alpha) {
                // System.out.println("CUT! depth=" + depth +
                // " player=" + (isMax ? "COM" : "MAN") +
                // " best=" + best +
                // " alpha=" + alpha +
                // " beta=" + beta);
                break;
            }
        }
        if (!hasValidMove)
            // return evaluatePosition(board, COM, first);
            return isMax ? -WIN_SCORE : WIN_SCORE;
        return best;
    }

    // ===== 評価関数 =====
    static int evaluatePosition(int[][] board, int player, int first) {
        // int opponent = (player == COM) ? MAN : COM;
        int score = 0, com_score = 0, man_score = 0;
        int[] res = new int[2];

        // 評価ループ
        for (Window w : ALL_WINDOWS) {
            // COMの窓を評価
            analyzeWindow(board, w, COM, res);
            com_score += getWindowScore(board, w, res[0], res[1], COM, first);

            // MANの窓を評価
            analyzeWindow(board, w, MAN, res);
            man_score += getWindowScore(board, w, res[0], res[1], MAN, first);
        }

        // 中央列加点
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                int stone = board[x][y] & STONE_MASK;
                if (stone == COM)
                    com_score += POSITION_SCORE[x][y] * 1;
                else if (stone == MAN)
                    man_score += POSITION_SCORE[x][y] * 1;
            }
        }
        // man *= 3
        score = com_score - (man_score * 2);

        // 3を作れる手のボーナス
        // for (int x = 0; x < WIDTH; x++) {
        // int y = nextEmptyY(board, x);
        // if (y < 0)
        // continue;

        // board[x][y] = COM;
        // if (canMakeThreeDirectional(board, x, y, COM)) {
        // score += FORK_SCORE / 4; // 3を作れる手を優先
        // }
        // board[x][y] = EMPTY;

        // board[x][y] = MAN;
        // if (canMakeThreeDirectional(board, x, y, MAN)) {
        // score -= FORK_SCORE / 4; // 相手の3作成手を阻止する
        // }
        // board[x][y] = EMPTY;
        // }

        if (DEBUG1) {
            System.out.println(String.format("score =%6d(%6d %6d)", score, com_score, man_score));
        }
        return score;
    }

    // Windowの解析：4マスに、自石の数、空マスの数、敵石があるかを判定する関数です。
    private static void analyzeWindow(int[][] board, Window w, int player, int[] results) {
        int mine = 0, empty = 0;
        for (int[] p : w.pos) { // 4マスを順番に見る。
            int cell = board[p[0]][p[1]] & STONE_MASK;
            if (cell == player) // 自石の数
                mine++;
            else if (cell == EMPTY) // 空マスの数
                empty++;
            else { // 敵石があった(評価対象外)
                results[0] = -1;
                results[1] = -1;
                return;
            }
        }
        results[0] = mine;
        results[1] = empty;
    }

    private static int getWindowScore(int[][] board, Window w, int mine, int empty, int player, int first) {

        if (mine == 4) {
            // Main.displayBoard(board); // debug
            return WIN_SCORE;
        }
        if (mine == 3 && empty == 1) {
            // Main.displayBoard(board); // debug
            int[] e = findSingleEmpty(board, w);
            // if (e == null)
            // return THREE_SCORE;

            int ex = e[0]; // ３の空マスの x
            int ey = e[1]; // ３の空マスの y
            int nextY = nextEmptyY(board, ex); // 次に置ける y

            int base = THREE_SCORE;

            // ３の空マスの y = 次に置けない位置か判定
            if (nextY < ey) {
                base *= 2;

                // playerが先手か？
                boolean isFirst = (player == first);

                // 偶数段か？
                boolean evenMoves = (ey % 2 == 0);

                // 先手が偶数段に打てる（有利）
                if (isFirst && evenMoves) {
                    if (ey == 2) {
                        // Main.displayBoard(board); // debug
                        base *= 4;
                    } else if (ey == 4) {
                        base *= 2;
                    }
                }

                // 後手が奇数段に打てる（有利）
                if (!isFirst && !evenMoves) {
                    if (ey == 1) {
                        // Main.displayBoard(board); // debug
                        base *= 4;
                    } else if (ey == 3) {
                        base *= 2;
                    }
                }
            }
            return base;
        }

        if (mine == 2 && empty == 2)
            return TWO_SCORE;

        return 0;
    }

    // (x, y) を中心に4方向について正方向＋逆方向に連続石を数え合計が4以上かを判定
    static boolean check4(int[][] b, int x, int y, int p) {
        for (int[] d : DIRECTIONS) {
            if (1 + countDir(b, x, y, d[0], d[1], p)
                    + countDir(b, x, y, -d[0], -d[1], p) >= 4)
                return true;
        }
        return false;
    }

    // 一方向に連続石を数える
    static int countDir(int[][] b, int x, int y, int dx, int dy, int p) {
        int c = 0, nx = x + dx, ny = y + dy;
        while (inBoard(nx, ny) && (b[nx][ny] & STONE_MASK) == p) {
            c++;
            nx += dx;
            ny += dy;
        }
        return c;
    }

    private static int[] findSingleEmpty(int[][] board, Window w) {
        for (int[] p : w.pos) {
            if ((board[p[0]][p[1]] & STONE_MASK) == EMPTY)
                return p; // {x,y}
        }
        return null;
    }

    // 指定列の置ける段を返す
    static int nextEmptyY(int[][] b, int x) {
        for (int y = 0; y < HEIGHT; y++)
            if ((b[x][y] & STONE_MASK) == EMPTY)
                return y;
        return -1;
    }

    // 盤面内の位置か判定
    static boolean inBoard(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }

    // 勝敗判定(Main.java)
    static boolean hasAnyMove(int[][] board) {
        for (int x = 0; x < WIDTH; x++)
            if (nextEmptyY(board, x) >= 0)
                return true;
        return false;
    }

    // 表示用ハイライト(Main.java)
    static boolean checkWinAndMark(int[][] display, int player) {
        boolean won = false;
        int[] res = new int[2];
        for (Window w : ALL_WINDOWS) {
            analyzeWindow(display, w, player, res);
            if (res[0] == 4) {
                for (int[] p : w.pos)
                    display[p[0]][p[1]] |= HIGHLIGHT_WIN;
                won = true;
            }
        }
        return won;
    }

    // ハイライト更新
    static void markAllThreateningThree(int[][] src, int[][] dst, int player, int flag) {
        int[] res = new int[2];
        for (Window w : ALL_WINDOWS) {
            analyzeWindow(src, w, player, res);
            if (res[0] == 3 && res[1] == 1) {
                for (int[] p : w.pos)
                    if ((src[p[0]][p[1]] & STONE_MASK) == player)
                        dst[p[0]][p[1]] |= flag;
            }
        }
    }

    // 現在の合計手数をカウントする(Main.java)
    static int countTotalStones(int[][] board) {
        int count = 0;
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if ((board[x][y] & STONE_MASK) != EMPTY) {
                    count++;
                }
            }
        }
        return count;
    }

    // 置ける列の数をカウントする(Main.java)
    static int countPlayableCols(int[][] board) {
        int count = 0;
        for (int x = 0; x < WIDTH; x++) {
            if (nextEmptyY(board, x) != -1) {
                count++;
            }
        }
        return count;
    }

    // 動的に深さを決定する(Main.java)
    static int getDynamicDepth(int[][] board) {
        int totalStones = countTotalStones(board); // 置いた石の合計
        int remaining = WIDTH * HEIGHT - totalStones; // 残りのマス
        int playableCols = countPlayableCols(board); // 空マスがある列の数
        int baseDepth; // 深度

        // 置ける列が少ない程、depthを深くする(0,2,4,...)
        baseDepth = (7 - playableCols);
        // 手数により深くする
        if (totalStones <= 7) { // 初盤（〜7手）
            baseDepth += 8;
        } else if (totalStones <= 14) { // 前盤（〜14手）
            baseDepth += 10;
        } else if (totalStones <= 21) { // 中盤（〜21手）
            baseDepth += 12;
        } else { // 終盤（22手〜）
            baseDepth = remaining; // 残りを全て読む(max=20)
        }
        // 偶数化（COM手番評価固定）
        baseDepth &= ~1;
        return baseDepth;
    }

    // COMが置くとMANに４を作られる
    static boolean isLosingMove(int[][] board, int x) {
        boolean lose = false;
        int y = nextEmptyY(board, x);
        if (y < 0)
            return lose;

        // COMが置いた真上にMANが置いた場合の４を判定
        if (y + 1 < HEIGHT) {
            board[x][y] = COM;
            board[x][y + 1] = MAN;
            lose = check4(board, x, y + 1, MAN);
            board[x][y + 1] = EMPTY;
            board[x][y] = EMPTY;
        }
        return lose;
    }

    // 全列のどこかに４ができるかを判定
    static int checkWin4(int[][] board, int p) {
        for (int x : ORDERED_COLS) {
            int y = nextEmptyY(board, x);
            if (y < 0)
                continue;

            board[x][y] = p;
            boolean is4 = check4(board, x, y, p);
            board[x][y] = EMPTY;
            if (is4 == true) {
                return x;
            }
        }
        return -1;
    }

    // 全列のどこかに３３ができるかを判定
    static int checkWin33(int[][] board, int p) {
        int o = (p == COM) ? MAN : COM;

        for (int x : ORDERED_COLS) {
            int y = nextEmptyY(board, x);
            if (y < 0)
                continue; // いっぱいで置けない

            board[x][y] = p; // 自分が仮置き

            boolean isWin33 = true;
            boolean hasOpponentMove = false;

            for (int x2 : ORDERED_COLS) {
                int y2 = nextEmptyY(board, x2);
                if (y2 < 0)
                    continue;

                hasOpponentMove = true;

                board[x2][y2] = o;
                if (check4(board, x2, y2, o)) {
                    board[x2][y2] = EMPTY;
                    isWin33 = false;
                    break;
                }

                int isWin4 = checkWin4(board, p);
                if (isWin4 == -1) {
                    board[x2][y2] = EMPTY;
                    isWin33 = false;
                    break;
                }
                board[x2][y2] = EMPTY;
            }

            board[x][y] = EMPTY;

            if (isWin33 && hasOpponentMove) {
                return x;
            }
        }
        return -1;
    }

    // 置いた位置 (x,y) に石 p を置いた場合、
    // ±3範囲で３や飛び３を判定
    // static boolean canMakeThreeDirectional(int[][] b, int x, int y, int p) {
    // for (int[] d : DIRECTIONS) {
    // // 正方向
    // int stonesPos = 0;
    // int emptiesPos = 0;
    // for (int i = 1; i <= 3; i++) {
    // int nx = x + d[0] * i;
    // int ny = y + d[1] * i;
    // if (!inBoard(nx, ny))
    // break;
    // if (b[nx][ny] == p)
    // stonesPos++;
    // else if (b[nx][ny] == EMPTY)
    // emptiesPos++;
    // else
    // break; // 相手石で終了
    // }

    // // 逆方向
    // int stonesNeg = 0;
    // int emptiesNeg = 0;
    // for (int i = 1; i <= 3; i++) {
    // int nx = x - d[0] * i;
    // int ny = y - d[1] * i;
    // if (!inBoard(nx, ny))
    // break;
    // if (b[nx][ny] == p)
    // stonesNeg++;
    // else if (b[nx][ny] == EMPTY)
    // emptiesNeg++;
    // else
    // break; // 相手石で終了
    // }

    // int totalStones = 1 + stonesPos + stonesNeg; // 中心の石を含む
    // int totalEmpties = emptiesPos + emptiesNeg;

    // // 自石3個＋空で4連可能なら3を作れる
    // if (totalStones == 3 && totalStones + totalEmpties >= 4) {
    // return true;
    // }
    // }
    // return false;
    // }
}
