# 1단계: Node.js를 이용한 빌드
FROM node:lts-alpine as build-stage
WORKDIR /app

# 의존성 설치 (캐싱 활용을 위해 분리)
COPY package*.json ./
RUN npm install

# 소스 코드 복사 및 빌드
COPY . .
RUN npm run build

# 2단계: 빌드된 결과물을 Nginx에 담기
FROM nginx:stable-alpine as production-stage

# 1단계에서 생성된 dist 폴더 내용을 Nginx 배포 폴더로 복사
# 만약 빌드 결과 폴더가 'build'라면 dist 대신 build로 수정하세요.
COPY --from=build-stage /app/dist /usr/share/nginx/html

# 위에서 만든 nginx.conf 파일을 Nginx 설정 폴더로 복사
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]