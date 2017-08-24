#!/usr/bin/env bash
self=$(readlink -f $BASH_SOURCE)
self_dir=$(dirname $self)
root_dir=$(readlink -f "$self_dir/..")
language=$1
api_url=$2
. $root_dir/scripts/common.sh

build_distribution() {
  echo "Building distribution ..."
  mvn release:clean package
}

upload_to_maven_central() {
  echo "Uploading to Maven Central (skipped) ..."
}

upload_to_cloudsmith() {
  echo "Uploading to Cloudsmith ..."
  mvn deploy
}

build_distribution
upload_to_cloudsmith
