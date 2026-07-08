#!/bin/bash

for csv_file in csv/*.csv; do
  echo "$csv_file"
  java -jar target/localization-csv-importer-1.0-SNAPSHOT.jar "$csv_file"
done

 