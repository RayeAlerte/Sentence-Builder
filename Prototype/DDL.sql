use BuilderWords;

-- Table for single word metadata
CREATE TABLE WordCorpus (
    word VARCHAR(100) PRIMARY KEY,
    total_count INT DEFAULT 1,
    start_count INT DEFAULT 0,
    end_count INT DEFAULT 0
);

-- Table for Frequency-based next word (Bigrams: Word A -> Word B)
CREATE TABLE Bigrams (
    word1 VARCHAR(100),
    word2 VARCHAR(100),
    frequency INT DEFAULT 1,
    PRIMARY KEY (word1, word2),
    FOREIGN KEY (word1) REFERENCES WordCorpus(word)
);

-- Table for N-Gram based next word (Trigrams: Word A + Word B -> Word C)
CREATE TABLE Trigrams (
    word1 VARCHAR(100),
    word2 VARCHAR(100),
    word3 VARCHAR(100),
    frequency INT DEFAULT 1,
    PRIMARY KEY (word1, word2, word3)
);

-- Table to track imported files metadata
CREATE TABLE ImportedFiles (
    file_name VARCHAR(255) PRIMARY KEY,
    word_count INT,
    import_date DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Table to track generated sentences
CREATE TABLE IF NOT EXISTS UserHistory (
    id INT AUTO_INCREMENT PRIMARY KEY,
    activity_type VARCHAR(50), 
    content TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);