// package connect4;

// import java.util.Arrays;
import java.util.Scanner;
import java.util.Stack;
import java.io.*;

public class Main {
    // ANSIカラーコードの定義（必要なものだけに限定）
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_YELLOW = "\u001B[33m"; // COMリーチ (X黄色)
    public static final String ANSI_CYAN = "\u001B[36m"; // MANリーチ (O青)
    public static final String ANSI_RED = "\u001B[31m"; // 勝利ライン (OorX赤)
    public static int dynamicDepth;
    static Stack<Move> history = new Stack<>();
    static boolean playerFirst = true; // 先攻管理

    public static void main(String[] args) {
        int[][] board = new int[Connect4AI.WIDTH][Connect4AI.HEIGHT];
        Scanner scanner = new Scanner(System.in);
        int turn = playerFirst ? Connect4AI.MAN : Connect4AI.COM;

        while (true) { // ゲーム終了までループ
            while (true) { // 1ゲーム分ループ
                displayBoard(board);
                // ======================
                // MAN の手番
                // ======================
                if (turn == Connect4AI.MAN) {
                    System.out.print("列(0-6) 保存=7 読込=8 戻す=9: ");

                    String input = scanner.nextLine();
                    int x;
                    try {
                        x = Integer.parseInt(input);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    // 盤面＆履歴セーブ
                    if (x == 7) {
                        saveGame(board);
                        continue;
                    }
                    // 盤面＆履歴ロード
                    if (x == 8) {
                        turn = loadGame(board);
                        continue;
                    }
                    // 戻す
                    if (x == 9) {
                        if (history.size() < 2) {
                            System.out.println("これ以上戻せません。");
                            continue;
                        }
                        int last = undoOneTurn(board);
                        turn = last; // 手番を戻す
                        continue;
                    }

                    // 不正手
                    if (x < 0 || x >= Connect4AI.WIDTH || Connect4AI.nextEmptyY(board, x) == -1) {
                        System.out.println("そこには置けません。");
                        continue;
                    }

                    int y = Connect4AI.nextEmptyY(board, x);
                    board[x][y] = Connect4AI.MAN;

                    // 履歴に保存（MANは理由なし）
                    history.push(new Move(x, y, Connect4AI.MAN));
                } else {
                    // ======================
                    // COM の手番
                    // ======================
                    System.out.println("AIが考えています...");
                    // 手数をカウントして深さを決定
                    dynamicDepth = Connect4AI.getDynamicDepth(board);
                    // dynamicDepth = 7; // debug
                    int first = playerFirst ? Connect4AI.MAN : Connect4AI.COM;
                    stripHighlights(board); // ハイライトを消す
                    int x = Connect4AI.chooseBestMove(board, dynamicDepth, first);

                    if (x != -1) {
                        int y = Connect4AI.nextEmptyY(board, x);
                        board[x][y] = Connect4AI.COM;
                        // 履歴に保存
                        history.push(new Move(x, y, Connect4AI.COM));
                        System.out.println("AIは " + x + " 列目に置きました。depth=" + dynamicDepth);
                    }
                }

                // ======================
                // ハイライト更新
                // ======================
                clearHighlights(board);
                Connect4AI.markAllThreateningThree(board, board, Connect4AI.MAN, Connect4AI.HIGHLIGHT_MAN);
                Connect4AI.markAllThreateningThree(board, board, Connect4AI.COM, Connect4AI.HIGHLIGHT_COM);

                // ======================
                // 勝敗判定
                // ======================
                if (Connect4AI.checkWinAndMark(board, turn)) {
                    displayBoard(board);
                    System.out.println(ANSI_RED + (turn == Connect4AI.MAN ? "Human wins!" : "COM wins!") + ANSI_RESET);
                    break;
                }

                if (!Connect4AI.hasAnyMove(board)) {
                    displayBoard(board);
                    System.out.println("Draw game!");
                    break;
                }

                // 手番交代
                turn = (turn == Connect4AI.MAN) ? Connect4AI.COM : Connect4AI.MAN;
            }
            // ======================
            // 勝敗後の処理
            // ======================
            System.out.print("Play again? (y/n, undo=9): ");
            String ans = scanner.nextLine().trim();

            // 9 = undo
            if (ans.equals("9")) {
                if (history.size() < 2) {
                    System.out.println("これ以上戻せません。");
                    continue;
                }
                int last = undoOneTurn(board);
                turn = last;
                continue; // ゲームループへ
            }

            // y = 新規ゲーム
            if (ans.equalsIgnoreCase("y")) {
                playerFirst = !playerFirst; // 手番交代
                board = new int[Connect4AI.WIDTH][Connect4AI.HEIGHT]; // 盤面初期化
                // Arrays.fill(Connect4AI.forbidden, Connect4AI.EMPTY);
                history.clear(); // 履歴クリア
                turn = playerFirst ? Connect4AI.MAN : Connect4AI.COM;
            }

            // n = 終了
            if (ans.equalsIgnoreCase("n")) {
                System.out.println("Game Over.");
                scanner.close();
                break;
            }
        }
    }

    public static void displayBoard(int[][] board) {
        System.out.print(playerFirst == true ? "\nO " : "\nX ");
        System.out.println("0 1 2 3 4 5 6");
        for (int y = Connect4AI.HEIGHT - 1; y >= 0; y--) {
            System.out.print(y + " ");
            for (int x = 0; x < Connect4AI.WIDTH; x++) {
                int cell = board[x][y];
                int stone = cell & Connect4AI.STONE_MASK;

                // 石の記号を決定（空きならドット、人間ならO、AIならX）
                String mark = (stone == Connect4AI.MAN) ? "O " : (stone == Connect4AI.COM) ? "X " : ". ";
                String color = "";

                // 色の優先順位判定
                if ((cell & Connect4AI.HIGHLIGHT_WIN) != 0) {
                    color = ANSI_RED; // 勝利（赤）
                } else if ((cell & Connect4AI.HIGHLIGHT_COM) != 0) {
                    color = ANSI_YELLOW; // AIリーチ（黄）
                } else if ((cell & Connect4AI.HIGHLIGHT_MAN) != 0) {
                    color = ANSI_CYAN; // 人間リーチ（青）
                }

                // 色がある場合は色付きで、ない場合はそのまま出力
                if (!color.isEmpty()) {
                    System.out.print(color + mark + ANSI_RESET);
                } else {
                    System.out.print(mark);
                }
            }
            System.out.println();
        }
        System.out.println("-----------------");
    }

    private static void clearHighlights(int[][] board) {
        for (int x = 0; x < Connect4AI.WIDTH; x++) {
            for (int y = 0; y < Connect4AI.HEIGHT; y++) {
                board[x][y] &= Connect4AI.STONE_MASK;
            }
        }
    }

    static class Move {
        int x, y, player;

        Move(int x, int y, int player) {
            this.x = x;
            this.y = y;
            this.player = player;
        }
    }

    static int undoOneTurn(int[][] board) {

        int lastPlayer = -1;

        for (int i = 0; i < 2; i++) {
            if (history.isEmpty())
                break;

            Move m = history.pop();
            board[m.x][m.y] = Connect4AI.EMPTY;
            lastPlayer = m.player;
        }

        return lastPlayer;
    }

    // 盤面＆履歴セーブ
    static void saveGame(int[][] board) {
        try (PrintWriter out = new PrintWriter("connect4_save.txt")) {

            // 先手情報
            out.println(playerFirst ? 1 : 0);

            // history
            out.println(history.size());
            for (Move m : history) {
                out.println(m.x + " " + m.y + " " + m.player);
            }

            // board
            for (int y = 0; y < Connect4AI.HEIGHT; y++) {
                for (int x = 0; x < Connect4AI.WIDTH; x++) {
                    out.print((board[x][y] & Connect4AI.STONE_MASK) + " ");
                }
                out.println();
            }

            System.out.println("ゲームを保存しました");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 盤面＆履歴ロード
    static int loadGame(int[][] board) {

        try (BufferedReader br = new BufferedReader(new FileReader("connect4_save.txt"))) {

            playerFirst = br.readLine().equals("1");

            // history
            history.clear();
            int n = Integer.parseInt(br.readLine());

            for (int i = 0; i < n; i++) {
                String[] p = br.readLine().split(" ");
                int x = Integer.parseInt(p[0]);
                int y = Integer.parseInt(p[1]);
                int player = Integer.parseInt(p[2]);

                history.push(new Move(x, y, player));
            }

            // board
            for (int y = 0; y < Connect4AI.HEIGHT; y++) {
                String[] row = br.readLine().trim().split(" ");
                for (int x = 0; x < Connect4AI.WIDTH; x++) {
                    board[x][y] = Integer.parseInt(row[x]);
                }
            }

            System.out.println("ゲームを読み込みました");

        } catch (Exception e) {
            System.out.println("セーブデータがありません");
        }

        if (history.isEmpty())
            return playerFirst ? Connect4AI.MAN : Connect4AI.COM;

        Move last = history.peek();
        return (last.player == Connect4AI.MAN) ? Connect4AI.COM : Connect4AI.MAN;
    }

    // ハイライトを消す
    static void stripHighlights(int[][] board) {
        for (int x = 0; x < Connect4AI.WIDTH; x++) {
            for (int y = 0; y < Connect4AI.HEIGHT; y++) {
                board[x][y] &= Connect4AI.STONE_MASK;
            }
        }
    }
}
