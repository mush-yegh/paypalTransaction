package domain.siteName;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DbHelper {

    private static final String DB_URL = "jdbc:mysql://127.0.0.1:3306/paypal";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "password";

    private static final Connection connection = getConnection();

    private static Connection getConnection() {
        try {
            Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            System.out.println("Connection established!");
            return c;
        } catch (SQLException e) {
            System.out.println("Failed to connect to db!\n");
            e.printStackTrace();
            return null;
        }
    }

    static int createUser(String firstName, String lastName, double balance) {
        try {
            String query = "insert into users (first_name,last_name,balance) values(?,?,?)";

            PreparedStatement pstmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, firstName);
            pstmt.setString(2, lastName);
            pstmt.setDouble(3, balance);

            int rowAffected = pstmt.executeUpdate();
            /*String txtMsg = rowAffected > 1 ? "rows are affected" : "row is affected";
            System.out.println(rowAffected + " " + txtMsg);*/
            System.out.println(rowAffected + "row is affected");

            if (rowAffected == 1) {
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next())
                    return rs.getInt(1);
            }

        } catch (SQLException e) {
            System.out.println("trying to create first statement\n");
            e.printStackTrace();
        }
        return -1;
    }

    static List<User> listUsers() {

        List<User> usrs = new ArrayList<>();
        try {
            String query = "SELECT * FROM users";
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                int id = rs.getInt("id");
                String fName = rs.getString("first_name");
                String lName = rs.getString("last_name");
                double balance = rs.getDouble("balance");
                //System.out.println(id + "\t" + fName + "\t" + lName + "\t" + balance);
                usrs.add(new User(id, fName, lName, balance));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return usrs;
    }

    static User findUserById(int userId) {
        String query = "SELECT first_name, last_name, balance FROM users WHERE id = ?";
        try {
            PreparedStatement pstm = connection.prepareStatement(query);
            pstm.setInt(1, userId);

            ResultSet rs = pstm.executeQuery();
            while (rs.next()) {
                String fName = rs.getString("first_name");
                String lName = rs.getString("last_name");
                double balance = rs.getDouble("balance");
                return new User(userId, fName, lName, balance);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Updates the user balance in database
     * Sets balance = balance + amount
     *
     * @param userId id of the user in users table
     * @param amount double value of the amount to insert
     */
    static void cashFlow(int userId, double amount) {

        if( findUserById(userId) == null){
            System.out.println("There is no user by given id!");
            return;
        }
        String query = "UPDATE users SET balance = balance + ? WHERE id = ?";
        try {
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setDouble(1, Math.abs(amount));
            pstmt.setInt(2, userId);
            int rowAffected = pstmt.executeUpdate();
                System.out.println(rowAffected + " row is affected");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Emulates a transaction between 2 users
     * Takes money from one account and adds to another account
     *
     * @param userFrom source user id
     * @param userTo   target user id
     * @param amount   transaction amount
     */
    static void transaction(int userFrom, int userTo, double amount) {
        //check both users exist
        if( findUserById(userFrom) == null || findUserById(userTo) == null){
            System.out.println("There is no user by given id!");
            return;
        }
        //check sender balance is enough
        double senderBalance = getUserBalance(userFrom);
        if (senderBalance < amount){
            System.out.println("Ooops, not enough money...");
            return;
        }

        PreparedStatement updateUserFrom = null;
        PreparedStatement updateUserTo = null;
        PreparedStatement insertTransaction = null;
        String cashOut ="UPDATE paypal.users SET balance = balance - ? WHERE id = ?";
        String cashIn ="UPDATE paypal.users SET balance = balance + ? WHERE id = ?";
        String transactRecord ="INSERT INTO paypal.transactions " +
                "(user_from, user_to, transaction_amount) VALUES(?, ?, ?)";
        try {
            connection.setAutoCommit(false);
            updateUserFrom = connection.prepareStatement(cashOut);
            updateUserFrom.setDouble(1, amount);
            updateUserFrom.setInt(2, userFrom);
            updateUserFrom.executeUpdate();

            updateUserTo = connection.prepareStatement(cashIn);
            updateUserTo.setDouble(1, amount);
            updateUserTo.setInt(2, userTo);
            updateUserTo.executeUpdate();

            insertTransaction = connection.prepareStatement(transactRecord);
            insertTransaction.setInt(1, userFrom);
            insertTransaction.setInt(2, userTo);
            insertTransaction.setDouble(3, amount);
            insertTransaction.executeUpdate();

            connection.commit();
        } catch (SQLException e ) {
            System.out.println("Transaction failed!\n");
            e.printStackTrace();
        } finally {
            if (updateUserFrom != null) {
                try {
                    updateUserFrom.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (updateUserTo != null) {
                try {
                    updateUserTo.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (insertTransaction != null) {
                try {
                    insertTransaction.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        /*
        String changeBalance = "UPDATE users SET balance = balance + ? WHERE id = ?";
        try {
            PreparedStatement pstmt = connection.prepareStatement(changeBalance);
            pstmt.setDouble(1, -amount);
            pstmt.setInt(2, userFrom);
            pstmt.executeUpdate();
            pstmt.close();

            pstmt = connection.prepareStatement(changeBalance);
            pstmt.setDouble(1, amount);
            pstmt.setInt(2, userTo);
            pstmt.executeUpdate();
            pstmt.close();
            //how can I be sure that both queries are executed together only???

        } catch (SQLException e) {
            e.printStackTrace();
        }*/

    }
    private static double getUserBalance(int userId){
        String query = "SELECT balance FROM users WHERE id = ?";

        try {
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()){
                return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("");
        return -1;
    }


}
