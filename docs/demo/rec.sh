# usage: rec.sh <name>  — records the phone while demo.txt plays, pulls <name>.mp4 and the start log
export MSYS_NO_PATHCONV=1
adb shell input keyevent HOME; sleep 1
adb push demo.txt /data/local/tmp/s.txt >/dev/null
adb shell "screenrecord --bit-rate 20000000 --time-limit 40 /sdcard/Movies/risco-demo.mp4" & REC=$!
sleep 1.5
adb shell "echo rec_up \$(cat /proc/uptime)" > "$1.log"
adb shell "CLASSPATH=/data/local/tmp/inject.dex app_process / Inject /data/local/tmp/s.txt" >> "$1.log"
sleep 1; adb shell pkill -INT screenrecord; wait $REC; sleep 1
adb pull /sdcard/Movies/risco-demo.mp4 "$1.mp4" >/dev/null && adb shell rm /sdcard/Movies/risco-demo.mp4
adb shell input keyevent HOME
cat "$1.log"; ffprobe -v error -show_entries stream=width,height,r_frame_rate,nb_frames,duration -of compact "$1.mp4"
