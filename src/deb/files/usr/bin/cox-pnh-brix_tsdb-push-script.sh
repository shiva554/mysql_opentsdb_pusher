#!/bin/sh
sleep 45;
LOGFILE=/var/log/cox-pnh-brix_tsdb-service.log
DATADIRINC=/var/cox/pnh/incoming
DATADIRPROC=/var/cox/pnh/processed
DATADIRJAR=/usr/share/cox/pnh/jars
DATE=`date`
.  /etc/cox/pnh/cox-pnh-opentsdb-service/config.properties
(for csvfile in ${DATADIRINC}/*
do
if [ -f "$csvfile" ];
then
echo "${DATE} $csvfile is pushing to opentsdb" >> $LOGFILE
#-Xms512m -Xmx512m -XX:MaxPermSize=256m -XX:MaxNewSize=256m -Xss228k
java -cp ${DATADIRJAR}/cox-pnh-opentsdb-service-0.0.1-SNAPSHOT.jar  com.cox.sweng.clienttest.opentsdbClientTest "$csvfile" "/var/log/cox-pnh-opentsdb-data.log"  $avg
echo  "${DATE} $csvfile  pushed successfully to db." >>  $LOGFILE && \
mv $csvfile ${DATADIRPROC} && \
echo "${DATE} $csvfile successfully moved to processed directory." >> $LOGFILE
else
 echo "${DATE} Files does not exist in $DATADIRINC" >> $LOGFILE
fi
done)|| ( echo "Process failed"  >> $LOGFILE && exit 1)
