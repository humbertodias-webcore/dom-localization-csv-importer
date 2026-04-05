package com.bhg;

import java.io.FileReader;
import java.io.FileInputStream;
import java.util.Properties;
import java.sql.*;
import com.opencsv.CSVReader;

public class LocalizationCsvImporter {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar localization-csv-importer.jar <csv_file> [--skip-first-line=true|false]");
            return;
        }

        String csvFile = args[0];
        boolean skipFirstLine = false; // default

        // parse optional argument
        if (args.length >= 2 && args[1].startsWith("--skip-first-line=")) {
            skipFirstLine = Boolean.parseBoolean(args[1].split("=")[1]);
        }

        // Extract language from filename: loc_<language>_<version>_name.csv
        String[] parts = csvFile.split("_");
        if (parts.length < 4) {
            System.out.println("Invalid file name, expected format: loc_<language>_<version>_name.csv");
            return;
        }
        String language = parts[1];

        // Load database properties from db.properties
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("db.properties")) {
            props.load(fis);
        } catch (Exception e) {
            System.err.println("Failed to load database properties from db.properties");
            e.printStackTrace();
            return;
        }

        String dbUrl = props.getProperty("db.url");
        String dbUser = props.getProperty("db.user");
        String dbPassword = props.getProperty("db.password");

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            conn.setAutoCommit(false);

            String selectUniqueSql = "SELECT id FROM unique_strings WHERE string = ?";
            String insertUniqueSql = "INSERT INTO unique_strings (string, hash) VALUES (?, ?)";
            String selectBaseLocSql = "SELECT 1 FROM base_loc WHERE locID = ? AND language = ?";
            String insertBaseLocSql = "INSERT INTO base_loc (locID, language, uniqueStringID) VALUES (?, ?, ?)";

            try (PreparedStatement psSelectUnique = conn.prepareStatement(selectUniqueSql);
                 PreparedStatement psInsertUnique = conn.prepareStatement(insertUniqueSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement psSelectBaseLoc = conn.prepareStatement(selectBaseLocSql);
                 PreparedStatement psInsertBaseLoc = conn.prepareStatement(insertBaseLocSql);
                 CSVReader reader = new CSVReader(new FileReader(csvFile))) {

                String[] nextLine;
                boolean firstLine = skipFirstLine;

                while ((nextLine = reader.readNext()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        continue;
                    }

                    int locID = Integer.parseInt(nextLine[0].trim());
                    String stringValue = nextLine[1].trim();
                    int hash = stringValue.hashCode();

                    int uniqueStringID = -1;

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

                conn.commit();
                System.out.println("All insertions completed successfully!");

            } catch (Exception e) {
                conn.rollback();
                System.err.println("Error during insertion, rolling back!");
                e.printStackTrace();
            }

        } catch (Exception e) {
            System.err.println("Database connection error:");
            e.printStackTrace();
        }
    }
}