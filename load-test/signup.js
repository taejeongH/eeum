import http from "k6/http";
import { check, sleep } from "k6";

// 회원가입 부하 테스트 시나리오
// 유니크한 이메일로 다수의 사용자가 가입하는 상황 가정
export let options = {
  stages: [
    { duration: "30s", target: 20 }, // 30초 동안 20명까지 증가
    { duration: "1m", target: 50 }, // 1분 동안 50명 유지
    { duration: "30s", target: 0 }, // 종료
  ],
  thresholds: {
    http_req_duration: ["p(95)<1000"], // 95%의 요청이 1초 이내여야 함
  },
};

const BASE_URL = __ENV.BASE_URL || "https://i14a105.p.ssafy.io/api";

export default function () {
  const uniqueId = __VU + "-" + __ITER; // 가상 유저 ID와 반복 횟수를 조합해 고유 이메일 생성
  const payload = JSON.stringify({
    email: `testuser_${uniqueId}@example.com`,
    password: "Password123!",
    name: `테스트유저_${uniqueId}`,
  });

  const params = {
    headers: {
      "Content-Type": "application/json",
    },
  };

  let res = http.post(`${BASE_URL}/auth/signup`, payload, params);

  check(res, {
    "signup status is 200": (r) => r.status === 200,
    "signup success message": (r) =>
      r.body.includes("회원가입이 완료되었습니다"),
  });

  // 회원가입은 보통 한 유저가 한 번만 하므로 중복 가입 방지를 위해
  // 실제 테스트 시에는 DB 초기화가 필요할 수 있습니다.
  sleep(1);
}
