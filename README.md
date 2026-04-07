# How to run this project!

## Dependencies

You will need mariadb/mysql and javafx.

## .env File

Create a .env file with FX and MYSQL environment variables.
They will point to the 2 dependencies you need to run this. 

```

FX=~/javafx-sdk-21.0.5/lib
MYSQL=mysql-connector-j-9.6.0.jar

```

## Setting up local db for testing

```

CREATE DATABASE BuilderWords;
CREATE USER 'sentencebuilder'@'localhost' IDENTIFIED BY 'Yo457S<DWL.D';
GRANT ALL PRIVILEGES ON BuilderWords.* TO 'sentencebuilder'@'localhost';
FLUSH PRIVILEGES;
USE BuilderWords;
SOURCE /your/own/path/Sentence-Builder/DDL.sql;
EXIT;

```

## Running

To run the gui, use `./run.sh -gui`, for the cli use `./run.sh`.

