@_list:
  {{just_executable()}} --justfile {{source_file()}} --list --unsorted

package:
  ./gradlew buildPlugin

release:
  ./release.sh

publish version:
  ./gradlew publishPlugin -PpluginVersion="{{version}}"
