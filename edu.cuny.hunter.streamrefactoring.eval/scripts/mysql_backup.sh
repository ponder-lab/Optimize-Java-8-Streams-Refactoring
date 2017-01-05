#!/bin/bash
. mysql_backup.cfg 
for db in ${DATABASES[*]}; do
	FILE=$OUTPUT/`date +%Y%m%d`.$db.sql
	echo "Dumping database: $db to: $FILE"
	mysqldump --force --opt --user=$DB_USER --password=$DB_PASSWORD --databases $db > $FILE
	gzip --force $FILE
	curl -T $FILE.gz $FTP_SERVER --user $FTP_USER:$FTP_PASSWORD
done

cd "$OUTPUT"
find *.gz -mtime +$DAYS_TO_KEEP -delete > /dev/null 2>&1
cd -
