# systemd_journal_영구_저장_설정.md

## 목적
- journal 로그를 재부팅 후에도 유지(Storage=persistent)
---
## 1) persistent 설정 + 디렉터리 준비
```bash
sudo sed -i 's/^#\?Storage=.*/Storage=persistent/' /etc/systemd/journald.conf

sudo mkdir -p /var/log/journal
sudo chown root:systemd-journal /var/log/journal
sudo chmod 2755 /var/log/journal

sudo systemctl restart systemd-journald
```
---
## 2) 확인
```bash
ls /var/log/journal/$(cat /etc/machine-id)
journalctl --list-boots
```
재부팅 후 journalctl --list-boots에 -1 등이 보이면 정상
---
## 3) 자주 쓰는 로그 명령
- 현재 부팅(boot 0)
```bash
sudo journalctl -u eeum.service -b
```
- 현재 부팅 tail
```bash
sudo journalctl -u eeum.service -b -n 300 --no-pager
```
- 시간 범위(예: 최근 30분)
```bash
sudo journalctl -u eeum.service --since "30 min ago" --no-pager
```
- 부팅 시각 기준
```bash
uptime -s
sudo journalctl -u eeum.service --since "$(uptime -s)" --no-pager
```