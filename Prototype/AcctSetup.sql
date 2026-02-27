-- 1. Create the new user account (replace 'secure_password' with your desired password)
CREATE USER 'sentencebuilder'@'localhost' IDENTIFIED BY 'Yo457S<DWL.D';

-- 2. Grant only read and write privileges to the BuilderWords database
GRANT SELECT, INSERT, UPDATE, DELETE ON BuilderWords.* TO 'sentencebuilder'@'localhost';

-- 3. Reload the grant tables to ensure the new privileges take effect immediately
FLUSH PRIVILEGES;