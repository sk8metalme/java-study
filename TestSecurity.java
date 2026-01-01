public class TestSecurity {
    // TODO: SQLインジェクション対策が必要
    public void executeQuery(String userInput) {
        // 脆弱性: ユーザー入力を直接SQLに連結している
        String query = "SELECT * FROM users WHERE name = '" + userInput + "'";
        // execute query...
    }

    public void processUserData(String data) {
        // パフォーマンス改善の余地あり
        for (int i = 0; i < 1000; i++) {
            System.out.println(data + i);
        }
    }
}
