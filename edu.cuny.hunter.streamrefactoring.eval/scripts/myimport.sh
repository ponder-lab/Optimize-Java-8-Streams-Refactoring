#!/bin/bash
DATABASE=$1
TABLE=$2
. myimport.cfg
dos2unix $TABLE.csv
mysql --local-infile -D $DATABASE -u $USER -p$PASSWORD -h $HOST --execute="\
    TRUNCATE $TABLE; \
    LOAD DATA LOCAL INFILE '$TABLE.csv' \
    INTO TABLE $TABLE \
    FIELDS TERMINATED BY ','\
    ENCLOSED BY '\"'\
    LINES TERMINATED BY '\n'\
    IGNORE 1 LINES;\
    SHOW WARNINGS" > $TABLE.output
cat $TABLE.output
