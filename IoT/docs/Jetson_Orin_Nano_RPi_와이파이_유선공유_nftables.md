# Jetson_Orin_Nano_RPi_와이파이_유선공유_nftables.md

## 목적
- RPi5(wlan0) 인터넷을 Jetson Orin Nano에 유선(eth0 ↔ enP8p1s0)로 공유(NAT)
- 재부팅 후에도 유지

## 네트워크
- RPi wlan0: 인터넷 연결
- RPi eth0: 10.10.0.1/24
- Jetson enP8p1s0: 10.10.0.2/24
- Jetson → RPi(wlan0)로 NAT
---
## 1) Jetson: 유선 IP 고정 (enP8p1s0 = 10.10.0.2/24)
```bash
sudo nmcli con add type ethernet ifname enP8p1s0 con-name static-enp8p1s0 ip4 10.10.0.2/24
sudo nmcli con mod "Wired connection 1" connection.autoconnect no || true
sudo nmcli con mod static-enp8p1s0 connection.autoconnect yes
sudo nmcli con down static-enp8p1s0 || true
sudo nmcli con up static-enp8p1s0
```
---
## 2) RPi: 유선 IP 고정 (eth0 = 10.10.0.1/24)
```bash
sudo nmcli con add type ethernet ifname eth0 con-name static-eth0 ip4 10.10.0.1/24
sudo nmcli con mod "Wired connection 1" connection.autoconnect no || true
sudo nmcli con mod static-eth0 connection.autoconnect yes
sudo nmcli con down static-eth0 || true
sudo nmcli con up static-eth0
```
---
## 3) RPi: IP forwarding 영구 설정
```bash
sudo tee /etc/sysctl.d/99-ipforward.conf >/dev/null <<'EOF'
net.ipv4.ip_forward=1
EOF

sudo sysctl --system
sysctl net.ipv4.ip_forward
```
---
## 4) RPi: nftables 규칙(영구)
```bash
sudo cp /etc/nftables.conf /etc/nftables.conf.bak.$(date +%Y%m%d_%H%M%S) 2>/dev/null || true

sudo tee /etc/nftables.conf >/dev/null <<'EOF'
#!/usr/sbin/nft -f
flush ruleset

table ip nat {
  chain postrouting {
    type nat hook postrouting priority 100;
    oifname "wlan0" masquerade
  }
}

table ip filter {
  chain forward {
    type filter hook forward priority 0;
    policy drop;

    ct state related,established accept
    iifname "eth0" oifname "wlan0" accept
  }
}
EOF
```
---
## 5) RPi: 적용 + 부팅 자동 적용
```bash
sudo nft -c -f /etc/nftables.conf
sudo systemctl enable --now nftables
sudo nft -f /etc/nftables.conf
sudo nft list ruleset
```
---
## 6) Jetson: 라우팅 (Jetson 기본 게이트웨이 = 10.10.0.1)

유선만 쓸 경우
```bash
sudo ip route replace default via 10.10.0.1 dev enP8p1s0
```

Wi-Fi(예: wlP1p1s0)도 있고 “유선 fallback”으로 쓰고 싶을 때(메트릭)
```bash
sudo nmcli con modify "<WIFI_CONN_NAME>" ipv4.route-metric 100
sudo nmcli con modify "static-enp8p1s0" ipv4.route-metric 700
sudo nmcli con down "<WIFI_CONN_NAME>" && sudo nmcli con up "<WIFI_CONN_NAME>"
sudo nmcli con down "static-enp8p1s0" && sudo nmcli con up "static-enp8p1s0"
```
---
## 7) 확인
```bash
ip route | head -n 20
ip route get 8.8.8.8
ping -c 2 8.8.8.8
```