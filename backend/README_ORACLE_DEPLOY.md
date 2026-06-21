# Oracle Cloud Free VM 배포 안내

이 문서는 만날농사 FastAPI backend를 Oracle Cloud Free VM에 배포하는 절차입니다. 이번 작업에서는 Android를 수정하지 않습니다.

## 1. 준비 사항

- GitHub 저장소: `https://github.com/johnpark236-tech/MANNAL_smart_farm_260616.git`
- Oracle Cloud 계정
- SSH 접속에 사용할 개인 키
- KAMIS API 키와 사용자 ID
- 기상청 KMA API 키

실제 API 키는 GitHub에 올리지 않고 VM의 `backend/.env`에만 입력합니다.

## 2. Always Free VM 생성

1. Oracle Cloud에 가입하고 Console에 로그인합니다.
2. **Compute > Instances > Create instance**로 이동합니다.
3. 이름을 정하고 Ubuntu 24.04 이미지를 선택합니다.
4. Always Free 대상 Shape에서 다음 항목을 선택합니다.

```text
VM.Standard.E2.1.Micro
```

5. 공용 IPv4 주소 할당을 활성화합니다.
6. 기존 SSH 공개 키를 업로드하거나 새 키를 생성합니다.
7. VM을 생성하고 공인 IP를 기록합니다.

리전의 무료 자원 또는 Shape 수용량이 부족하면 생성이 제한될 수 있습니다. Console에서 **Always Free eligible** 표시를 반드시 확인합니다.

## 3. 필요한 포트

| 포트 | 용도 |
|---|---|
| 22 | SSH 접속 |
| 8000 | FastAPI 직접 접속 |
| 80 | 추후 HTTP reverse proxy |
| 443 | 추후 HTTPS reverse proxy |

현재 FastAPI 직접 접속에는 22와 8000이 필요합니다. 80과 443은 Nginx와 HTTPS를 구성할 때 사용합니다. 가능하면 SSH 22번 포트의 Source CIDR은 관리자 공인 IP로 제한합니다.

## 4. Oracle Security List 설정

1. Oracle Console에서 **Networking > Virtual Cloud Networks**로 이동합니다.
2. VM이 사용하는 VCN과 Subnet을 선택합니다.
3. 연결된 Security List를 엽니다.
4. **Add Ingress Rules**를 누릅니다.
5. FastAPI용 규칙을 추가합니다.

```text
Source CIDR: 0.0.0.0/0
IP Protocol: TCP
Destination Port Range: 8000
```

필요하면 같은 방식으로 80과 443도 추가합니다. 8000을 전 세계에 공개하는 대신 운영 단계에서는 HTTPS reverse proxy와 접근 제한을 권장합니다.

## 5. SSH 접속

Windows PowerShell 예시입니다.

```powershell
ssh -i "C:\path\to\oracle-key.key" ubuntu@111.222.333.444
```

처음 접속할 때 fingerprint 확인 질문이 나오면 IP가 올바른지 확인한 뒤 `yes`를 입력합니다.

## 6. GitHub Clone과 설치

VM에서 실행합니다.

```bash
cd ~
git clone https://github.com/johnpark236-tech/MANNAL_smart_farm_260616.git
cd ~/MANNAL_smart_farm_260616/backend
bash deploy_oracle.sh
```

`deploy_oracle.sh`는 Ubuntu 패키지를 설치하고, 기존 저장소가 있으면 `git pull --ff-only`을 수행하며, Python 가상환경과 requirements를 설치합니다.

## 7. 환경변수 입력

배포 스크립트가 처음 실행되면 `.env.example`을 이용해 `.env`를 만듭니다.

```bash
cd ~/MANNAL_smart_farm_260616/backend
nano .env
```

다음 값을 입력합니다. 실제 키를 터미널 로그나 문서에 남기지 않습니다.

```env
DATABASE_URL=sqlite:///./market_dashboard.db
KRX_API_KEY=
KAMIS_API_KEY=
KAMIS_USER_ID=
KMA_API_KEY=
USE_DUMMY_DATA=false
```

저장 후 권한을 제한합니다.

```bash
chmod 600 .env
```

## 8. Ubuntu UFW 설정

```bash
sudo ufw allow OpenSSH
sudo ufw allow 8000/tcp
sudo ufw enable
sudo ufw status
```

Oracle Security List와 Ubuntu UFW 양쪽에서 8000번 포트가 허용되어야 외부에서 접속할 수 있습니다.

## 9. systemd 서비스 등록

```bash
cd ~/MANNAL_smart_farm_260616/backend
sudo cp systemd/mannal-backend.service /etc/systemd/system/mannal-backend.service
sudo systemctl daemon-reload
sudo systemctl enable mannal-backend
sudo systemctl start mannal-backend
```

상태 확인:

```bash
sudo systemctl status mannal-backend
```

실시간 로그 확인:

```bash
sudo journalctl -u mannal-backend -f
```

서비스가 실패하면 먼저 `.env`, 가상환경 경로, VM 사용자 이름이 `ubuntu`인지 확인합니다.

## 10. 배포 후 테스트

공인 IP가 `111.222.333.444`라면 다음 주소를 확인합니다.

```text
http://111.222.333.444:8000/docs
http://111.222.333.444:8000/api/weather/farm
http://111.222.333.444:8000/api/prices/kamis
```

VM 내부에서도 확인할 수 있습니다.

```bash
curl -I http://127.0.0.1:8000/docs
curl http://127.0.0.1:8000/api/weather/farm
```

## 11. GitHub 업데이트 반영

코드를 GitHub에 push한 뒤 Oracle VM에서 실행합니다.

```bash
cd ~/MANNAL_smart_farm_260616
git pull --ff-only
cd backend
source .venv/bin/activate
python -m pip install -r requirements.txt
sudo systemctl restart mannal-backend
sudo systemctl status mannal-backend
```

## 12. Android 변경 시점

Oracle 배포와 API 테스트가 모두 성공한 뒤에만 Android의 backend 주소를 변경합니다.

현재:

```java
private static final String BACKEND_BASE_URL =
        "http://172.30.1.51:8000";
```

다음 Android 작업에서 변경할 예:

```java
private static final String BACKEND_BASE_URL =
        "http://111.222.333.444:8000";
```

이번 작업에서는 `MainActivity.java`, `AndroidManifest.xml`, APK를 수정하거나 다시 빌드하지 않습니다.

## 13. 운영 참고

- 무료 VM 수용량은 리전별로 다를 수 있습니다.
- SQLite와 cache 파일은 VM 디스크에 저장되므로 정기 백업을 권장합니다.
- 공인 IP가 바뀌지 않도록 Reserved Public IP 사용을 검토합니다.
- 실제 운영에서는 도메인, Nginx, HTTPS를 추가하고 8000번 직접 공개를 닫는 구성이 더 안전합니다.
