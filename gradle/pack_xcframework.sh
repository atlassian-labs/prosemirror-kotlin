#!/bin/bash
module=$1
file_name=${module//[-]/_}
tag=$2
zip -r ${module}/build/${file_name}.xcframework.zip ${module}/build/XCFrameworks/release/${file_name}.xcframework
# Prepare Package.swift file
CHECKSUM=`swift package compute-checksum ${module}/build/${file_name}.xcframework.zip`

export MODULE=$module
export CHECKSUM=$CHECKSUM
export XCFRAMEWORK_URL="https://github.com/atlassian-labs/prosemirror-kotlin/releases/${tag}/${file_name}.xcframework.zip"
cat gradle/templates/package.swift.template | envsubst '$MODULE $CHECKSUM $XCFRAMEWORK_URL' > ${module}/build/Package.swift
