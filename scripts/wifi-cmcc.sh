#!/system/bin/sh

wpa_cli -i wlan0 remove_network 0 >/dev/null 2>&1
id=$(wpa_cli -i wlan0 add_network | tail -1)
wpa_cli -i wlan0 set_network "$id" ssid '"CMCC-u5sh"'
wpa_cli -i wlan0 set_network "$id" psk '"8nwnwx59"'
wpa_cli -i wlan0 enable_network "$id"
wpa_cli -i wlan0 select_network "$id"
wpa_cli -i wlan0 save_config

sleep 8
wpa_cli -i wlan0 status
ip -4 addr show wlan0
