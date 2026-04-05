CREATE SCHEMA `localizationdb` DEFAULT CHARACTER SET utf8;

-- Base localization table
CREATE TABLE localizationdb.base_loc (
                                         locID          INT NOT NULL,
                                         language       VARCHAR(7) COLLATE utf8_unicode_ci NOT NULL,
                                         uniqueStringID INT NULL,
                                         updated        TIMESTAMP DEFAULT CURRENT_TIMESTAMP() NULL ON UPDATE CURRENT_TIMESTAMP(),
                                         CONSTRAINT locID_language UNIQUE (locID, language)
) CHARSET=utf8;

CREATE INDEX language_idx
    ON localizationdb.base_loc (language);

CREATE INDEX uniqueString_idx
    ON localizationdb.base_loc (uniqueStringID);

-- Bucket locID usage table
CREATE TABLE localizationdb.bucket_locid_usage (
                                                   bucket SMALLINT NOT NULL,
                                                   locID  INT NOT NULL,
                                                   PRIMARY KEY (bucket, locID)
) CHARSET=utf8;

CREATE INDEX bucket_idx
    ON localizationdb.bucket_locid_usage (bucket);

CREATE INDEX locID_idx
    ON localizationdb.bucket_locid_usage (locID);

-- Bucket overrides table
CREATE TABLE localizationdb.bucket_overrides (
                                                 locID          INT NOT NULL,
                                                 language       VARCHAR(7) COLLATE utf8_unicode_ci NOT NULL,
                                                 bucket         SMALLINT NOT NULL,
                                                 uniqueStringID INT NULL,
                                                 updated        TIMESTAMP DEFAULT CURRENT_TIMESTAMP() NULL ON UPDATE CURRENT_TIMESTAMP(),
                                                 PRIMARY KEY (locID, language, bucket)
) CHARSET=utf8;

CREATE INDEX bucket_override_idx
    ON localizationdb.bucket_overrides (bucket);

CREATE INDEX language_override_idx
    ON localizationdb.bucket_overrides (language);

CREATE INDEX uniqueString_override_idx
    ON localizationdb.bucket_overrides (uniqueStringID);

-- Buckets table
CREATE TABLE localizationdb.buckets (
                                        bucket  SMALLINT NOT NULL PRIMARY KEY,
                                        branch  VARCHAR(64) CHARSET utf8 NOT NULL,
                                        updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP() NOT NULL ON UPDATE CURRENT_TIMESTAMP()
) CHARSET=utf8;

-- Localization mappings table
CREATE TABLE localizationdb.localization_mappings (
                                                      xpath       VARCHAR(255) NOT NULL,
                                                      text        VARCHAR(4096) NOT NULL,
                                                      description VARCHAR(256) NULL,
                                                      bucket      VARCHAR(45) NOT NULL,
                                                      language    VARCHAR(16) NOT NULL,
                                                      update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP() NOT NULL ON UPDATE CURRENT_TIMESTAMP(),
                                                      PRIMARY KEY (xpath, bucket, language)
) CHARSET=utf8;

-- LocID descriptions table
CREATE TABLE localizationdb.locid_descriptions (
                                                   locID       INT NOT NULL PRIMARY KEY,
                                                   description VARCHAR(255) NULL
) CHARSET=utf8;

-- Unique strings table
CREATE TABLE localizationdb.unique_strings (
                                               id     INT AUTO_INCREMENT PRIMARY KEY,
                                               string VARCHAR(4096) COLLATE utf8_unicode_ci NOT NULL,
                                               hash   INT NOT NULL
) COLLATE=utf8_bin;

CREATE INDEX hash_idx
    ON localizationdb.unique_strings (hash);

-- XPath to locIDs table
CREATE TABLE localizationdb.xpath_locids (
                                             xPath VARCHAR(255) COLLATE utf8_unicode_ci NOT NULL,
                                             locID INT,
                                             PRIMARY KEY (xPath(191), locID) -- limit index length to avoid errors
) CHARSET=utf8;

CREATE INDEX locID_xpath_idx
    ON localizationdb.xpath_locids (locID);

ALTER TABLE localizationdb.xpath_locids
    MODIFY locID INT AUTO_INCREMENT;

-- XPath to ID mapping table
CREATE TABLE localizationdb.xpath_to_id_mapping (
                                                    xpath VARCHAR(255) NOT NULL PRIMARY KEY,
                                                    locID INT AUTO_INCREMENT,
                                                    CONSTRAINT locID_UNIQUE UNIQUE (locID),
                                                    CONSTRAINT xpath_UNIQUE UNIQUE (xpath)
) CHARSET=utf8;