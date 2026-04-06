package com.bhg.localization;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import com.opencsv.CSVReader;

public class LocCsvImporter {

    private static final String USAGE = "Usage: java -jar localization-csv-importer.jar <csv_file> [--skip-first-line=true|false]";
    private static final String DB_PROPERTIES_FILE = "db.properties";

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int run(String[] args) {
        if (args.length < 1) {
            System.out.println(USAGE);
            return 1;
        }

        String csvFile = args[0];
        boolean skipFirstLine = parseSkipFirstLine(args);
        String language = extractLanguage(csvFile);
        if (language == null) {
            System.err.println("Invalid file name, expected format: loc_<language>_<version>_name.csv");
            return 1;
        }

        Path dbPropertiesPath = Path.of(DB_PROPERTIES_FILE);
        if (!Files.exists(dbPropertiesPath)) {
            if (!createDefaultDatabaseProperties(dbPropertiesPath)) {
                return 1;
            }
        }

        Properties dbProperties = loadDatabaseProperties(dbPropertiesPath);
        if (dbProperties == null) {
            return 1;
        }

        return processCsv(csvFile, skipFirstLine, language, dbProperties);
    }

    private static boolean parseSkipFirstLine(String[] args) {
        if (args.length < 2) {
            return false;
        }

        String option = args[1];
        return option.startsWith("--skip-first-line=") && Boolean.parseBoolean(option.substring(option.indexOf('=') + 1));
    }

    private static String extractLanguage(String csvFile) {
        String[] parts = csvFile.split("_");
        return parts.length >= 4 ? parts[1] : null;
    }

    private static Properties loadDatabaseProperties(Path propertiesPath) {
        Properties props = new Properties();

        try (FileInputStream fis = new FileInputStream(propertiesPath.toFile())) {
            props.load(fis);
            return props;
        } catch (Exception e) {
            System.err.println("Failed to load database properties from " + propertiesPath);
            e.printStackTrace();
            return null;
        }
    }

    private static boolean createDefaultDatabaseProperties(Path propertiesPath) {
        Properties props = new Properties();
        props.setProperty("db.url", "jdbc:mysql://localhost:3306/localizationdb");
        props.setProperty("db.username", "root");
        props.setProperty("db.password", "");

        try (FileOutputStream fos = new FileOutputStream(propertiesPath.toFile())) {
            props.store(fos, "Database connection properties");
            System.out.println("Created default " + propertiesPath.getFileName() + " with initial values.");
            return true;
        } catch (Exception e) {
            System.err.println("Failed to create default database properties file: " + propertiesPath);
            e.printStackTrace();
            return false;
        }
    }

    private static int processCsv(String csvFile, boolean skipFirstLine, String language, Properties dbProperties) {
        String dbUrl = dbProperties.getProperty("db.url");
        String dbUser = dbProperties.getProperty("db.username");
        String dbPassword = dbProperties.getProperty("db.password");

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            conn.setAutoCommit(false);

            try {
                readAndImportRows(conn, csvFile, skipFirstLine, language);
                conn.commit();
                System.out.println("All insertions completed successfully!");
                return 0;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            System.err.println("Database connection error:");
            e.printStackTrace();
            return 1;
        }
    }

    private static void readAndImportRows(Connection conn, String csvFile, boolean skipFirstLine, String language) throws Exception {
        try (CSVReader reader = new CSVReader(new FileReader(csvFile))) {
            String[] nextLine;
            boolean firstLine = skipFirstLine;

            while ((nextLine = reader.readNext()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                if (nextLine.length < 2) {
                    System.err.println("Skipping invalid CSV row: expected at least 2 columns");
                    continue;
                }

                int locID = Integer.parseInt(nextLine[0].trim());
                String stringValue = nextLine[1].trim();
                importString(conn, locID, stringValue, language);
            }
        }
    }

    private static void importString(Connection conn, int locID, String stringValue, String language) throws SQLException {
        int hash = stringValue.hashCode();

        String selectUniqueSql = "SELECT id FROM unique_strings WHERE string = ?";
        String insertUniqueSql = "INSERT INTO unique_strings (string, hash) VALUES (?, ?)";
        String selectBaseLocSql = "SELECT 1 FROM base_loc WHERE locID = ? AND language = ?";
        String insertBaseLocSql = "INSERT INTO base_loc (locID, language, uniqueStringID) VALUES (?, ?, ?)";

        int uniqueStringID = -1;

        try (PreparedStatement psSelectUnique = conn.prepareStatement(selectUniqueSql);
             PreparedStatement psInsertUnique = conn.prepareStatement(insertUniqueSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psSelectBaseLoc = conn.prepareStatement(selectBaseLocSql);
             PreparedStatement psInsertBaseLoc = conn.prepareStatement(insertBaseLocSql)) {

            psSelectUnique.setString(1, stringValue);
            try (ResultSet rsUnique = psSelectUnique.executeQuery()) {
                if (rsUnique.next()) {
                    uniqueStringID = rsUnique.getInt("id");
                    System.out.println("unique_strings: '" + stringValue + "' already exists with id=" + uniqueStringID);
                } else {
                    psInsertUnique.setString(1, stringValue);
                    psInsertUnique.setInt(2, hash);
                    psInsertUnique.executeUpdate();

                    try (ResultSet rsGenerated = psInsertUnique.getGeneratedKeys()) {
                        if (rsGenerated.next()) {
                            uniqueStringID = rsGenerated.getInt(1);
                        }
                    }
                    System.out.println("unique_strings: inserted '" + stringValue + "' with id=" + uniqueStringID);
                }
            }

            psSelectBaseLoc.setInt(1, locID);
            psSelectBaseLoc.setString(2, language);
            try (ResultSet rsBase = psSelectBaseLoc.executeQuery()) {
                if (!rsBase.next()) {
                    psInsertBaseLoc.setInt(1, locID);
                    psInsertBaseLoc.setString(2, language);
                    psInsertBaseLoc.setInt(3, uniqueStringID);
                    psInsertBaseLoc.executeUpdate();
                    System.out.println("base_loc: inserted locID=" + locID + ", language=" + language + ", uniqueStringID=" + uniqueStringID);
                } else {
                    System.out.println("base_loc: skipping locID=" + locID + ", language=" + language + " (already exists)");
                }
            }
        }
    }
}
