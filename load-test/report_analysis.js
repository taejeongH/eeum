import http from "k6/http";
import { check, sleep } from "k6";

// AI 리포트 분석 요청 시나리오 (무거운 작업)
// 분석 요청이 동시에 몰릴 때 서버의 처리 능력 확인
export let options = {
  vus: 10, // 동시에 10명의 사용자가 분석 요청
  duration: "1m",
};

const BASE_URL = "http://host.docker.internal:8080/api/health";

export default function () {
  const groupId = 1;
  const today = new Date().toISOString().split("T")[0];

  // analyze API는 POST 방식이며 groupId와 date를 쿼리 파라미터로 받음
  let res = http.post(`${BASE_URL}/analyze?groupId=${groupId}&date=${today}`);

  check(res, {
    "analysis status is 200": (r) => r.status === 200,
    "has report data": (r) => r.json().data !== null,
  });

  sleep(5); // 분석은 무거운 작업이므로 긴 간격을 둠
}
