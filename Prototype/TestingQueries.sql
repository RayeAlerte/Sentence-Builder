use BuilderWords;

-- View Content
select * from WordCorpus;
select * from Bigrams;
select * from Trigrams;
select * from ImportedFiles;

-- Clear the Database
TRUNCATE TABLE Bigrams;
TRUNCATE TABLE Trigrams;
TRUNCATE TABLE ImportedFiles;
SET FOREIGN_KEY_CHECKS = 0; 
TRUNCATE TABLE WordCorpus; 
SET FOREIGN_KEY_CHECKS = 1;

-- Top Bigrams
SELECT word1, word2, frequency FROM Bigrams ORDER BY frequency DESC LIMIT 1000;
-- Top Trigrams
SELECT word1, word2, word3, frequency FROM Trigrams ORDER BY frequency DESC LIMIT 1000;
-- Top Words
SELECT * FROM WordCorpus
ORDER BY total_count DESC
LIMIT 1000;

-- Current Selection Queries
