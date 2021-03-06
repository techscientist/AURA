#!/bin/bash

export AURAHOME=/datapool/aura
export POLICY=${AURAHOME}/dist/jsk-all.policy
export REGHOST=$1

export DIR=`dirname $0`

if [ -z "${REGHOST}" ] ; then
    echo Usage: $DIR/dsh.go registry_host
    exit
fi

nohup java -Xmx512m -DregHost=${REGHOST} -jar $DIR/../dist/grid.jar \
     /com/sun/labs/aura/grid/ec2/resource/dataStoreHeadConfig.xml starter \
     "${AURAHOME}/logs/dshead.%g.out" &> ${AURAHOME}/logs/dsh.stdout &

export JPID=$!
echo ${JPID} > ${AURAHOME}/logs/dsh.pid
