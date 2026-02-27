use BuilderWords;
select * from WordCorpus;
select * from Bigrams;
select * from Trigrams;
select * from ImportedFiles;

TRUNCATE TABLE Bigrams;
TRUNCATE TABLE Trigrams;
TRUNCATE TABLE ImportedFiles;
SET FOREIGN_KEY_CHECKS = 0; 
TRUNCATE TABLE WordCorpus; 
SET FOREIGN_KEY_CHECKS = 1;