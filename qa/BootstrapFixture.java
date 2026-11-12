import main.java.db.DatabaseInitializer;
import main.java.db.DatabaseManager;

import java.sql.Connection;
import java.sql.Statement;

final class BootstrapFixture {
    public static void main(String[] args) throws Exception {
        String database = args.length == 0 ? "postgres" : "mac_bootstrap_check";
        System.setProperty("vitanova.db.url", "jdbc:postgresql://127.0.0.1:55472/" + database + "?connectTimeout=3&socketTimeout=8");
        System.setProperty("vitanova.db.user", "merge_test");
        System.setProperty("vitanova.db.password", "synthetic-only");
        try {
            DatabaseInitializer.initializeDatabase();
            try (Connection connection = DatabaseManager.getConnection(); Statement sql = connection.createStatement()) {
                sql.executeUpdate("CREATE OR REPLACE VIEW public.reporting_youth_profiles AS SELECT * FROM public.profiles");
                sql.executeUpdate("INSERT INTO users (id,name,username,password,role) VALUES (11,'Preview Staff','preview','synthetic-only','ADMIN') ON CONFLICT DO NOTHING");
                sql.executeUpdate("INSERT INTO profiles (id,first_name,last_name,name,birth_date,age) VALUES (1101,'Appearance','AgeTwentyFour','Appearance AgeTwentyFour','01-01-2002',24) ON CONFLICT DO NOTHING");
            }
            System.out.println("Created a blank, synthetic-only application database.");
        } finally {
            DatabaseManager.shutdown();
        }
    }
}
