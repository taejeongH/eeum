import { ref, computed, onUnmounted } from 'vue';

/**
 * 와이파이 API 기본 URL
 * @constant {string}
 */
const WIFI_API_BASE = 'http://localhost:8080';

/**
 * 와이파이 관리 컴포저블 (Composable)
 * 와이파이 스캔, 연결, 저장된 프로필 관리 로직을 담당합니다.
 * @module useWifi
 * @param {object} Store - 와이파이 상태 업데이트를 위한 Pinia 스토어 (SlideshowStore 등)
 */
export function useWifi(Store) {
  // --- 상태 (State) ---
  const isScanning = ref(false); // 스캔 진행 중 여부
  const isConnecting = ref(false); // 연결 시도 중 여부
  const connectionError = ref(null); // 연결 오류 메시지

  const activeSSID = ref(null); // 현재 연결된 SSID
  const scannedAPs = ref([]); // 스캔된 AP 목록
  const savedProfiles = ref([]); // 저장된 프로필 목록

  // UI 상태
  const connectingSSID = ref(null); // 연결 시도 중인 SSID
  const selectedAP = ref(null); // 선택된 AP 객체
  const showPasswordModal = ref(false); // 비밀번호 입력 모달 표시 여부
  const passwordInput = ref(''); // 입력된 비밀번호

  // 타이머
  let pingTimer = null;
  let statusTimer = null;

  // --- 계산된 속성 (Computed) ---

  /**
   * 빠른 조회를 위해 스캔된 AP들의 SSID 집합을 생성합니다.
   * @type {import('vue').ComputedRef<Set<string>>}
   */
  const availableSSIDs = computed(() => {
    return new Set(scannedAPs.value.map((ap) => ap.ssid));
  });

  // --- API 호출 함수 ---

  /**
   * 스캔 세션을 활성 상태로 유지하기 위해 UI 엔드포인트에 주기적으로 요청을 보냅니다.
   */
  const pingUi = async () => {
    try {
      const response = await fetch(`${WIFI_API_BASE}/api/wifi/ui/ping`, { method: 'POST' });
      if (!response.ok && response.status !== 401) {
        console.warn('[useWifi] UI 핑 실패:', response.status);
      }
    } catch (error) {
      // 오류 무시 (Silent fail)
    }
  };

  /**
   * 주변 와이파이 네트워크를 스캔합니다.
   * @param {boolean} [force=false] - 강제 재스캔 여부
   */
  const scanNetworks = async (force = false) => {
    if (isScanning.value) return;
    isScanning.value = true;

    try {
      const url = `${WIFI_API_BASE}/api/wifi/scan${force ? '?scan=true' : ''}`;
      const response = await fetch(url);

      if (!response.ok) {
        if (response.status === 401) console.warn('[useWifi] 인증 필요');
        return;
      }

      const data = await response.json();

      if (data.ok) {
        activeSSID.value = data.active_ssid;
        scannedAPs.value = data.aps || [];
        if (data.skipped) console.log('[useWifi] 스캔 건너뜀 (다른 작업 중)');
      }
    } catch (error) {
      console.error('[useWifi] 스캔 실패:', error);
    } finally {
      isScanning.value = false;
    }
  };

  /**
   * 현재 활성화된 와이파이 연결 상태를 확인합니다.
   */
  const fetchActiveConnection = async () => {
    try {
      const response = await fetch(`${WIFI_API_BASE}/api/wifi/active`);
      if (!response.ok) {
        Store.wifiStatus = false;
        return;
      }

      const data = await response.json();
      activeSSID.value = data.ssid;
      Store.wifiStatus = !!data.ssid;
    } catch (error) {
      Store.wifiStatus = false;
    }
  };

  /**
   * 저장된 와이파이 프로필 목록을 가져옵니다.
   * @param {boolean} [refresh=false] - 시스템에서 강제 갱신 여부
   */
  const fetchProfiles = async (refresh = false) => {
    try {
      const url = `${WIFI_API_BASE}/api/wifi/profiles${refresh ? '?refresh=true' : ''}`;
      const response = await fetch(url);

      if (!response.ok) return;

      const data = await response.json();
      if (data.ok) {
        activeSSID.value = data.active_ssid;
        savedProfiles.value = data.profiles || [];
      }
    } catch (error) {
      console.error('[useWifi] 프로필 조회 실패:', error);
    }
  };

  /**
   * SSID와 비밀번호를 사용하여 와이파이 네트워크에 연결합니다.
   * @param {string} ssid - 네트워크 이름
   * @param {string} password - 비밀번호
   * @returns {Promise<{success: boolean, message: string}>} 연결 결과
   */
  const connectToNetwork = async (ssid, password) => {
    if (isConnecting.value) return { success: false, message: '이미 연결 시도 중입니다.' };

    isConnecting.value = true;
    connectingSSID.value = ssid;
    connectionError.value = null;

    try {
      const response = await fetch(`${WIFI_API_BASE}/api/wifi/connect`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ssid, password }),
      });
      const data = await response.json();

      if (data.ok) {
        if (data.skipped) {
          isConnecting.value = false;
          await fetchActiveConnection();
          return { success: true, message: '이미 연결되어 있습니다.' };
        }

        // 연결 성공 여부를 폴링(Polling)으로 확인
        return new Promise((resolve) => {
          let attempts = 8;
          const interval = setInterval(async () => {
            await fetchActiveConnection();
            attempts--;
            console.log(`[useWifi] 연결 확인 중... (${attempts}) 현재 활성: ${activeSSID.value}`);

            if (activeSSID.value === ssid || attempts <= 0) {
              clearInterval(interval);
              isConnecting.value = false;
              const success = activeSSID.value === ssid;

              if (success) {
                await scanNetworks();
                await fetchProfiles();
              }

              resolve({
                success,
                message: success ? '연결 성공' : '연결 시간 초과 또는 실패',
              });
            }
          }, 1000);
        });
      } else {
        throw new Error(data.message || '알 수 없는 오류');
      }
    } catch (error) {
      console.error('[useWifi] 연결 요청 실패:', error);
      isConnecting.value = false;
      connectingSSID.value = null;
      return { success: false, message: error.message };
    }
  };

  /**
   * 저장된 프로필을 사용하여 와이파이에 연결합니다.
   * @param {string} profileName - 프로필 이름 (보통 SSID와 동일)
   */
  const connectToProfile = async (profileName) => {
    if (isConnecting.value) return { success: false };

    const profile = savedProfiles.value.find((p) => p.name === profileName);
    const targetSSID = profile ? profile.ssid : null;

    isConnecting.value = true;
    connectingSSID.value = targetSSID;

    try {
      const response = await fetch(`${WIFI_API_BASE}/api/wifi/profile/connect`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: profileName }),
      });
      const data = await response.json();

      if (data.ok) {
        // 폴링으로 연결 확인
        return new Promise((resolve) => {
          let attempts = 8;
          const interval = setInterval(async () => {
            await fetchActiveConnection();
            attempts--;

            if ((targetSSID && activeSSID.value === targetSSID) || attempts <= 0) {
              clearInterval(interval);
              isConnecting.value = false;
              const success = targetSSID && activeSSID.value === targetSSID;
              if (success) {
                await scanNetworks();
              }
              resolve({ success, message: success ? '연결됨' : '실패' });
            }
          }, 1000);
        });
      } else {
        isConnecting.value = false;
        return { success: false, message: data.message };
      }
    } catch (error) {
      isConnecting.value = false;
      return { success: false, message: error.message };
    }
  };

  /**
   * 저장된 와이파이 프로필을 삭제합니다.
   * @param {string} profileName - 삭제할 프로필 이름
   */
  const deleteProfile = async (profileName) => {
    try {
      const response = await fetch(`${WIFI_API_BASE}/api/wifi/profile/delete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: profileName }),
      });
      const data = await response.json();
      if (data.ok) {
        await fetchProfiles(true);
        return { success: true };
      }
      return { success: false, message: data.message };
    } catch (error) {
      return { success: false, message: error.message };
    }
  };

  // --- 라이프사이클 헬퍼 ---

  const startMonitoring = () => {
    pingUi();
    scanNetworks();
    fetchProfiles();
    fetchActiveConnection();

    pingTimer = setInterval(pingUi, 3000);
    statusTimer = setInterval(fetchActiveConnection, 1000);
  };

  const stopMonitoring = () => {
    if (pingTimer) clearInterval(pingTimer);
    if (statusTimer) clearInterval(statusTimer);
  };

  onUnmounted(() => {
    stopMonitoring();
  });

  return {
    isScanning,
    isConnecting,
    activeSSID,
    scannedAPs,
    savedProfiles,
    availableSSIDs,
    connectingSSID,
    selectedAP,
    showPasswordModal,
    passwordInput,
    scanNetworks,
    fetchActiveConnection,
    fetchProfiles,
    connectToNetwork,
    connectToProfile,
    deleteProfile,
    startMonitoring,
    stopMonitoring,
  };
}
