# usage: run.sh <script-file>  — pushes and plays a script on the phone
export MSYS_NO_PATHCONV=1
adb push "$1" /data/local/tmp/s.txt >/dev/null && adb shell "CLASSPATH=/data/local/tmp/inject.dex app_process / Inject /data/local/tmp/s.txt"
