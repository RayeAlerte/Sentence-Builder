ALTER TABLE WordCorpus ADD COLUMN boost_total_count INT DEFAULT 0;
ALTER TABLE WordCorpus ADD COLUMN boost_start_count INT DEFAULT 0;
ALTER TABLE Bigrams ADD COLUMN boost_frequency INT DEFAULT 0;
ALTER TABLE Trigrams ADD COLUMN boost_frequency INT DEFAULT 0;
