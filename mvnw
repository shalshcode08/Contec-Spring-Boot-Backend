#!/bin/sh
# ----------------------------------------------------------------------------
# Maven wrapper (script-only distribution).
#
# Downloads the Maven distribution declared in .mvn/wrapper/maven-wrapper.properties
# into ~/.m2/wrapper/dists on first use, then delegates to it. No jar is committed
# to the repository and no global Maven installation is required.
# ----------------------------------------------------------------------------
set -e

script_dir=$(cd -- "$(dirname -- "$0")" >/dev/null 2>&1 && pwd)
props="$script_dir/.mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$props" ]; then
  echo "mvnw: cannot find $props" >&2
  exit 1
fi

distribution_url=$(sed -n 's/^distributionUrl=//p' "$props" | tr -d '\r')
if [ -z "$distribution_url" ]; then
  echo "mvnw: distributionUrl is not set in $props" >&2
  exit 1
fi

archive=$(basename "$distribution_url")
# apache-maven-3.9.9-bin.zip -> apache-maven-3.9.9
dist_name=$(echo "$archive" | sed -e 's/-bin\.zip$//' -e 's/-bin\.tar\.gz$//')

maven_home="${MAVEN_USER_HOME:-$HOME/.m2}/wrapper/dists/$dist_name"
mvn_cmd="$maven_home/bin/mvn"

if [ ! -x "$mvn_cmd" ]; then
  echo "mvnw: downloading $distribution_url" >&2
  mkdir -p "$maven_home"
  tmp_dir=$(mktemp -d)
  trap 'rm -rf "$tmp_dir"' EXIT

  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$tmp_dir/$archive" "$distribution_url"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$tmp_dir/$archive" "$distribution_url"
  else
    echo "mvnw: neither curl nor wget is available to download Maven" >&2
    exit 1
  fi

  case "$archive" in
    *.zip)    unzip -q "$tmp_dir/$archive" -d "$tmp_dir" ;;
    *.tar.gz) tar -xzf "$tmp_dir/$archive" -C "$tmp_dir" ;;
    *)        echo "mvnw: unsupported archive type: $archive" >&2; exit 1 ;;
  esac

  # The archive contains a single top-level directory; move its contents into place.
  extracted=$(find "$tmp_dir" -maxdepth 1 -mindepth 1 -type d | head -n 1)
  rm -rf "$maven_home"
  mkdir -p "$(dirname "$maven_home")"
  mv "$extracted" "$maven_home"
  chmod +x "$mvn_cmd"
fi

MAVEN_PROJECTBASEDIR="$script_dir"
export MAVEN_PROJECTBASEDIR
exec "$mvn_cmd" "$@"
