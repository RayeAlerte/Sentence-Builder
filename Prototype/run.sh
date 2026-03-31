#!/bin/bash
FX=~/Projects/Sentence-Builder/javafx-sdk-21.0.5/lib
MYSQL=../mysql-connector-j-9.6.0.jar

if [ "$1" = "gui" ]; then
  javac --module-path $FX --add-modules javafx.controls -cp $MYSQL:. CorpusParserApp.java CorpusParser.java
  java --module-path $FX --add-modules javafx.controls -cp $MYSQL:. CorpusParserApp
  rm -f *.class
else
  javac -cp $MYSQL:. SentenceBuilder.java
  java -cp $MYSQL:. SentenceBuilder
  rm -f *.class
fi
