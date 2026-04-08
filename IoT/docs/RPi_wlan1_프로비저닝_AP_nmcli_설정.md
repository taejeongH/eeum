# RPi_wlan1_프로비저닝_AP_nmcli_설정.md

## 목적
- wlan1을 프로비저닝용 AP로 구성(NetworkManager)
- SSID/PW 지정 + 고정 IP + 자동 재연결(autoconnect)
---
## 1) 기존 wlan1 연결 내리기(있으면)
```bash
sudo nmcli connection down SSAFY_801 2>/dev/null || true
```

### 확인:
```bash
nmcli dev status
```
---
## 2) wlan1에 핫스팟(AP) 생성
```bash
sudo nmcli dev wifi hotspot \
  ifname wlan1 \
  con-name A105_AP \
  ssid A105-RPI-PROV \
  password A1051234
```
---
## 3) AP 설정 고정(shared + IP + autoconnect)
```bash
sudo nmcli connection modify A105_AP connection.interface-name wlan1
sudo nmcli connection modify A105_AP ipv4.method shared
sudo nmcli connection modify A105_AP ipv4.addresses 192.168.4.1/24
sudo nmcli connection modify A105_AP connection.autoconnect yes
```

### (선택) 숨김 SSID:
```bash
sudo nmcli connection modify A105_AP 802-11-wireless.hidden yes
```

### 적용:
```bash
sudo nmcli connection down A105_AP 2>/dev/null || true
sudo nmcli connection up A105_AP
```
---
## 4) 동작 확인
```bash
nmcli connection show --active
ip -4 addr show wlan1
```

### DHCP(dnsmasq) 확인(선택):
```bash
ps -ef | grep dnsmasq | grep -v grep
sudo ss -lunp | grep ':67'
```
---
## 5) 안정화 설정(선택: 2.4GHz 고정)
```bash
sudo nmcli connection modify A105_AP 802-11-wireless.band bg
sudo nmcli connection modify A105_AP 802-11-wireless.channel 6
sudo nmcli connection down A105_AP
sudo nmcli connection up A105_AP
```

### 확인:
```bash
nmcli -f 802-11-wireless.band,802-11-wireless.channel,802-11-wireless.ssid connection show A105_AP
```