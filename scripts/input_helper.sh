#!/system/bin/sh
# Root input helper for remote-receiver.
# The receiver queues commands in its own files dir; this helper runs them
# as root so `input` can inject key events into apps and the system UI.
# Lines prefixed with "shell:" are executed as root shell commands.

CMD_DIR=/data/data/com.zysj.speaker.remote/files
CMD_FILE=$CMD_DIR/input_cmd
ALIVE_FILE=$CMD_DIR/input_helper_alive
APP_UID=10071

if [ ! -d "$CMD_DIR" ]; then
  mkdir -p "$CMD_DIR"
fi
chown "$APP_UID:$APP_UID" "$CMD_DIR"
chmod 700 "$CMD_DIR"

# Exit early when another healthy helper is already running.
if [ -f "$ALIVE_FILE" ]; then
  old_pid=$(cat "$ALIVE_FILE" 2>/dev/null)
  old_time=$(stat -c %Y "$ALIVE_FILE" 2>/dev/null)
  now=$(date +%s)
  if [ -n "$old_pid" ] && [ -n "$old_time" ] && [ "$old_pid" != "$$" ] \
      && kill -0 "$old_pid" 2>/dev/null && [ $((now - old_time)) -lt 3 ]; then
    exit 0
  fi
fi

echo $$ > "$ALIVE_FILE"
chmod 644 "$ALIVE_FILE"

count=0
while true; do
  if [ -f "$CMD_FILE" ]; then
    mv "$CMD_FILE" "$CMD_FILE.processing"
    cmd=$(cat "$CMD_FILE.processing" 2>/dev/null)
    rm -f "$CMD_FILE.processing"
    if [ -n "$cmd" ]; then
      case "$cmd" in
        shell:*)
          sh -c "${cmd#shell:}" ;;
        *)
          /system/bin/input $cmd ;;
      esac
    fi
  fi
  count=$((count + 1))
  if [ $((count % 10)) -eq 0 ]; then
    echo $$ > "$ALIVE_FILE"
    chmod 644 "$ALIVE_FILE"
  fi
  sleep 0.05
done
