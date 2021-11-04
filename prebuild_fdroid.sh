#!/bin/bash

# Run from TMessagesProj.

vars=../gradle.properties

echo "DUMMY_CONST=0" >> $vars
echo "ADDITIONAL_BUILD_NUMBER=$1" >> $vars
echo "APP_ID=$2" >> $vars
echo "APP_HASH=$3" >> $vars
echo "F_DROID=1" >> $vars
echo "org.gradle.workers.max=1" >> $vars
