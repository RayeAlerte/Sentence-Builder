# Sentence-Builder

This is a fork for Victoria Alerte to develop an isolated instance for generation testing.
This model implements GUI in JavaFX and a word depository storing monograms, bigrams and trigrams for generation in MySQL.

Please do not copy unless absolutely necessary. Do not redistribute data sources!

---

Requires:
JavaFX - launch configured in launch.json (for VSCode, should be settings.json)
MySQL Connector/J - launch configured in launch.json (for VSCode, should be settings.json)

Optional:
clean-text - text cleaning application used to clean COCA.

Included .sh files show how to launch from command line on Mac OS.

DataSources folder should be at the same level as the executable.

Create these subfolders for DataSources:
Gutenberg - for Gutenberg formatted text files
CocaText - for COCA text corpuses
