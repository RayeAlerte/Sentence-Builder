#!/bin/bash
source .env
echo $FX
javac --module-path $FX --add-modules javafx.controls \
      -cp $MYSQLITE:. \
      Main.java CorpusParser.java SentenceBuilderApp.java SentenceBuilder.java DBMan.java
java --module-path $FX --add-modules javafx.controls \
     -cp $MYSQLITE:. Main $1
rm -f *.class
