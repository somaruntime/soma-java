#!/bin/sh

soma_project_version() {
  project_pom=${1:-pom.xml}
  project_version=$(sed -n \
    's:.*<version>\([^<]*\)</version>.*:\1:p' \
    "$project_pom" | sed -n '1p')
  if [ -z "$project_version" ]; then
    printf '%s\n' \
      "project-version: unable to read reactor version from $project_pom" >&2
    return 1
  fi
  printf '%s\n' "$project_version"
}
