#!/bin/sh
############################################################
#Description   : Script to copy data from Oracle to PNH
#Date          : 01242018
#Author        : Shiva  Lokasani
#Frequency     : Every 5 minutes
#Modification  : Initial
#############################################################
LOGFILE=/var/log/cox-pnh-brix_tsdb-service.log
DATADIR=/var/cox/pnh/incoming
BRIXDIR=/var/cox/pnh
USERNAME=brix
SERVER=184.176.220.70
DATE=`date`
(echo "${DATE} Running bxextract on "${SERVER} >> $LOGFILE && \
ssh -t ${USERNAME}@${SERVER} '/usr/bin/cox-pnh-data-collection-script.sh' && \
echo "${DATE} Fetching files" >> $LOGFILE && \
scp ${USERNAME}@${SERVER}:${BRIXDIR}/* ${DATADIR}/. && \
echo "${DATE} Removing files from Brix server" >> $LOGFILE && \
ssh ${USERNAME}@${SERVER} 'rm '${BRIXDIR}'/*' ) || ( echo "Process failed"  >> $LOGFILE && exit 1)
