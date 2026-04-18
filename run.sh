#!/bin/bash
source .env
echo $FX
javac --module-path $FX --add-modules javafx.controls \
      -cp $MYSQL:. \
      Main.java CorpusParser.java SentenceBuilderApp.java SentenceBuilder.java
java --module-path $FX --add-modules javafx.controls \
     -cp $MYSQL:. Main $1
rm -f *.class
